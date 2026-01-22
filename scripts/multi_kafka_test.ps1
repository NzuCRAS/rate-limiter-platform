# Multi-tenant Kafka Performance Test

Write-Host "=== Multi-Tenant Kafka Performance Test ==="

$baseUrl = "http://localhost:8082/api/v1/check"
$tenants = @("tenant_001", "tenant_002", "tenant_003", "tenant_004", "tenant_005")
$resources = @("/api/orders", "/api/users", "/api/payments", "/api/inventory", "/api/reports")

$totalRequests = 1500
$successCount = 0
$failCount = 0

Write-Host "Sending $totalRequests requests across multiple tenants and resources..."
$startTime = Get-Date

for ($i = 1; $i -le $totalRequests; $i++) {
    $tenant = $tenants[($i - 1) % $tenants.Length]
    $resource = $resources[($i - 1) % $resources.Length]

    $requestBody = @{
        requestId = "multi-test-$i"
        tenantId = $tenant
        resourceKey = $resource
        tokens = 1
        timestamp = [int64](([datetime]::UtcNow)-(Get-Date "1970-01-01")).TotalMilliseconds
    } | ConvertTo-Json

    try {
        $response = Invoke-RestMethod -Uri $baseUrl -Method Post -Body $requestBody -ContentType "application/json" -TimeoutSec 5
        if ($response.success) {
            $successCount++
        } else {
            $failCount++
        }
    }
    catch {
        $failCount++
        Write-Warning "Request $i failed: $($_.Exception.Message)"
    }

    if ($i % 25 -eq 0) {
        Write-Host "Sent $i requests (Success: $successCount, Failed: $failCount)..."
    }
}

$endTime = Get-Date
$duration = ($endTime - $startTime).TotalSeconds

Write-Host "Multi-tenant test completed!"
Write-Host "Duration: $([math]::Round($duration, 2)) seconds"
Write-Host "Rate: $([math]::Round($totalRequests / $duration, 2)) requests/second"
Write-Host "Success:  $successCount, Failed: $failCount"

# 等待 Kafka 处理
Write-Host "Waiting for Kafka processing..."
Start-Sleep -Seconds 5

# 检查分区分布 - 修复后的逻辑
Write-Host "`n=== Checking Partition Distribution ==="

# 方法1: 使用Docker命令
try {
    Write-Host "Method 1: Using Docker command" -ForegroundColor Yellow

    # 检查消费者组状态
    $kafkaContainer = "rate-limiter-platform-kafka-1"

    $consumerStatus = docker exec $kafkaContainer kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group accounting-service 2>&1

    if ($LASTEXITCODE -eq 0 -and $consumerStatus) {
        Write-Host "Consumer group status:" -ForegroundColor Green
        $consumerStatus

        # 分析分区分布
        Write-Host "`nPartition Distribution Analysis:" -ForegroundColor Cyan

        $partitionData = @{}
        foreach ($line in ($consumerStatus -split "`n")) {
            if ($line -match "quota-events\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)") {
                $partition = $Matches[1]
                $currentOffset = [int]$Matches[2]
                $logEndOffset = [int]$Matches[3]
                $lag = [int]$Matches[4]

                $partitionData[$partition] = @{
                    CurrentOffset = $currentOffset
                    LogEndOffset = $logEndOffset
                    Lag = $lag
                    MessageCount = $logEndOffset
                }
            }
        }

        # 显示每个分区的消息数量
        Write-Host "Messages per partition:" -ForegroundColor Yellow
        $totalMessages = 0
        $activePartitions = 0

        foreach ($partition in $partitionData.Keys | Sort-Object) {
            $data = $partitionData[$partition]
            Write-Host "  Partition $partition : $($data.MessageCount) messages" -ForegroundColor Gray

            if ($data.MessageCount -gt 0) {
                $activePartitions++
                $totalMessages += $data.MessageCount
            }
        }

        Write-Host "`nSummary:" -ForegroundColor Green
        Write-Host "  Total messages across all partitions: $totalMessages" -ForegroundColor Gray
        Write-Host "  Active partitions (with messages): $activePartitions / $($partitionData.Count)" -ForegroundColor Gray

        if ($activePartitions -eq $partitionData.Count) {
            Write-Host "  ✅ Messages are distributed across all partitions" -ForegroundColor Green
        } elseif ($activePartitions -gt 1) {
            Write-Host "  ⚠️ Messages are distributed across $activePartitions partitions" -ForegroundColor Yellow
        } else {
            Write-Host "  ❌ All messages are in a single partition" -ForegroundColor Red
        }

    } else {
        Write-Host "Failed to get consumer group status: $consumerStatus" -ForegroundColor Red
    }
}
catch {
    Write-Host "Error using Docker command: $_" -ForegroundColor Red
}

# 方法2: 检查topic详情
Write-Host "`nMethod 2: Checking Topic Details" -ForegroundColor Yellow
try {
    $topicInfo = docker exec rate-limiter-platform-kafka-1 kafka-topics --bootstrap-server localhost:9092 --describe --topic quota-events 2>&1

    if ($LASTEXITCODE -eq 0) {
        Write-Host "Topic details:" -ForegroundColor Green
        $topicInfo
    }
}
catch {
    Write-Host "Could not get topic details" -ForegroundColor Yellow
}

# 方法3: 获取消息计数
Write-Host "`nMethod 3: Message Count per Partition" -ForegroundColor Yellow
try {
    # 获取每个分区的消息总数
    Write-Host "Total messages in each partition:" -ForegroundColor Gray

    for ($partition = 0; $partition -lt 3; $partition++) {
        $result = docker exec rate-limiter-platform-kafka-1 kafka-run-class kafka.tools.GetOffsetShell `
            --broker-list localhost:9092 `
            --topic quota-events `
            --partitions $partition `
            --time -1 2>&1

        if ($LASTEXITCODE -eq 0) {
            Write-Host "  Partition $partition : $result" -ForegroundColor Gray
        }
    }
}
catch {
    Write-Host "Could not get message counts" -ForegroundColor Yellow
}

# 方法4: 检查生产者统计信息
Write-Host "`nMethod 4: Checking via Prometheus Metrics" -ForegroundColor Yellow
try {
    $metricsUrl = "http://localhost:9090/api/v1/query?query=kafka_producer_record_send_total"
    $response = Invoke-RestMethod -Uri $metricsUrl -TimeoutSec 5 -ErrorAction SilentlyContinue

    if ($response.data.result.Count -gt 0) {
        Write-Host "Kafka producer metrics found" -ForegroundColor Green
        $response.data.result | ForEach-Object {
            Write-Host "  $($_.metric.topic): $($_.value[1]) records sent" -ForegroundColor Gray
        }
    } else {
        Write-Host "No Kafka producer metrics available" -ForegroundColor Gray
    }
}
catch {
    Write-Host "Prometheus not available or metrics not found" -ForegroundColor Yellow
}

# 显示测试总结
Write-Host "`n=== Test Summary ===" -ForegroundColor Cyan
Write-Host "Requests sent: $totalRequests" -ForegroundColor Gray
Write-Host "Success rate: $([math]::Round(($successCount / $totalRequests) * 100, 1))%" -ForegroundColor Gray

if ($successCount -eq $totalRequests) {
    Write-Host "✅ All requests processed successfully" -ForegroundColor Green
} elseif ($successCount / $totalRequests -gt 0.9) {
    Write-Host "⚠️ High success rate, but some failures" -ForegroundColor Yellow
} else {
    Write-Host "❌ Low success rate, check system health" -ForegroundColor Red
}

Write-Host "`nRecommendations:" -ForegroundColor Magenta
Write-Host "1. Check Kafka partition distribution for load balancing" -ForegroundColor Gray
Write-Host "2. Monitor consumer lag in Grafana: http://localhost:3000" -ForegroundColor Gray
Write-Host "3. Run longer stress tests if partition distribution is good" -ForegroundColor Gray