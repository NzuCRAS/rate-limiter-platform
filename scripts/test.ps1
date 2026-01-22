# 保存为 performance-test.ps1
# 在 PowerShell 中运行: .\performance-test.ps1

Write-Host "=== Kafka Performance Test ===" -ForegroundColor Green

$totalRequests = 1000
$batchSize = 100

# 高并发测试
for ($i = 1; $i -le $totalRequests; $i++) {
    $body = @{
        requestId = "perf-test-$i"
        tenantId = "tenant_001"
        resourceKey = "/api/test"
        tokens = 1
    } | ConvertTo-Json

    # 使用 Invoke-RestMethod 代替 curl
    $job = Start-Job -ScriptBlock {
        param($url, $body)
        try {
            Invoke-RestMethod -Uri $url -Method Post -ContentType "application/json" -Body $body -UseBasicParsing
        } catch {
            # 静默处理错误，避免输出过多信息
        }
    } -ArgumentList "http://localhost:8082/api/v1/check", $body

    # 每100个请求暂停一下，避免overwhelm
    if ($i % $batchSize -eq 0) {
        # 等待当前批次的所有作业完成
        Get-Job | Wait-Job | Out-Null

        # 清理已完成作业
        Get-Job | Remove-Job

        Write-Host "Sent $i requests..." -ForegroundColor Yellow
        Start-Sleep -Seconds 1
    }
}

# 等待所有剩余作业完成
Get-Job | Wait-Job | Out-Null
Get-Job | Remove-Job

Write-Host "All requests sent, monitoring Kafka lag..." -ForegroundColor Green

# 监控 Kafka 消费延迟
for ($i = 1; $i -le 10; $i++) {
    Write-Host "Checking consumer lag at $(Get-Date)..." -ForegroundColor Cyan

    # 假设你已经有 Kafka 在 PATH 中，或者需要指定完整路径
    try {
        # 注意：这里需要根据你的实际 kafka-consumer-groups 命令路径调整
        # 如果是 WSL 中的 Kafka，可能需要使用 wsl 命令
        kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group accounting-service
    } catch {
        Write-Host "无法执行 Kafka 命令，请确保 Kafka 已正确安装并添加到 PATH" -ForegroundColor Red
        Write-Host "或者使用 WSL 版本: wsl kafka-consumer-groups.sh ..." -ForegroundColor Yellow
    }

    Start-Sleep -Seconds 10
}