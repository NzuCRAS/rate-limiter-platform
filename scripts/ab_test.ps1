# RateLimiter_MultiTenant_LoadTest.ps1

Write-Host "=== Rate Limiter Multi-Tenant Apache Bench Load Test ===" -ForegroundColor Cyan
$startTime = Get-Date
Write-Host "Start Time: $startTime"

# 设置 ab.exe 的绝对路径
$abPath = "C:\App\Apache24\bin\ab.exe"

# 验证 ab.exe 是否存在
if (-not (Test-Path $abPath)) {
    Write-Host "ERROR: ab.exe not found at: $abPath" -ForegroundColor Red
    Write-Host "Please check the path and update the script." -ForegroundColor Yellow
    exit 1
}

Write-Host "Using Apache Bench from: $abPath" -ForegroundColor Green

# 验证 ab 是否可用
try {
    $abVersion = & $abPath -V 2>&1
    Write-Host "Apache Bench version: $abVersion" -ForegroundColor Green
} catch {
    Write-Host "ERROR: Cannot execute ab.exe" -ForegroundColor Red
    Write-Host "Error details: $_" -ForegroundColor Yellow
    exit 1
}

# 辅助函数：显示测试结果
function Show-TestResult {
    param(
        [string]$TestName,
        [string]$ResultFile,
        [string]$TenantName,
        [string]$Tier
    )

    Write-Host "`n=== $TestName - $TenantName ($Tier) ===" -ForegroundColor Yellow

    if (Test-Path $ResultFile) {
        $content = Get-Content $ResultFile -Raw

        # 提取基本指标
        $completeRequests = if ($content -match 'Complete requests:\s+(\d+)') { $matches[1] } else { "N/A" }
        $failedRequests = if ($content -match 'Failed requests:\s+(\d+)') { $matches[1] } else { "0" }
        $rps = if ($content -match 'Requests per second:\s+([\d.]+)') {
            [math]::Round($matches[1], 2)
        } else { "N/A" }

        # 提取不同百分位的响应时间（如果有）
        $timePerRequest = if ($content -match 'Time per request:\s+([\d.]+)\s+\[ms\]\s+\(mean\)') {
            [math]::Round($matches[1], 2)
        } elseif ($content -match 'Time per request:\s+([\d.]+)\s+\[ms\]') {
            [math]::Round($matches[1], 2)
        } else { "N/A" }

        # 提取传输速率
        $transferRate = if ($content -match 'Transfer rate:\s+([\d.]+)') {
            "$([math]::Round($matches[1], 2)) KB/sec"
        } else { "N/A" }

        # 提取50% (中位数) 响应时间
        $medianResponse = if ($content -match '50%\s+(\d+)') {
            $matches[1] + " ms"
        } elseif ($content -match '\s+50%\s+(\d+)') {
            $matches[1] + " ms"
        } else { "N/A" }

        # 提取90% 响应时间
        $p90Response = if ($content -match '90%\s+(\d+)') {
            $matches[1] + " ms"
        } elseif ($content -match '\s+90%\s+(\d+)') {
            $matches[1] + " ms"
        } else { "N/A" }

        # 提取95% 响应时间
        $p95Response = if ($content -match '95%\s+(\d+)') {
            $matches[1] + " ms"
        } elseif ($content -match '\s+95%\s+(\d+)') {
            $matches[1] + " ms"
        } else { "N/A" }

        # 提取99% 响应时间
        $p99Response = if ($content -match '99%\s+(\d+)') {
            $matches[1] + " ms"
        } elseif ($content -match '\s+99%\s+(\d+)') {
            $matches[1] + " ms"
        } else { "N/A" }

        # 提取100% (最大) 响应时间
        $maxResponse = if ($content -match '100%\s+(\d+)') {
            $matches[1] + " ms"
        } elseif ($content -match '\s+100%\s+(\d+)') {
            $matches[1] + " ms"
        } else { "N/A" }

        # 提取连接时间分布
        $connectMin = $connectAvg = $connectMax = "N/A"
        if ($content -match 'Connect:\s+([\d.]+)\s+([\d.]+)\s+([\d.]+)\s+([\d.]+)') {
            $connectMin = [math]::Round($matches[1], 2)
            $connectAvg = [math]::Round($matches[2], 2)
            $connectMax = [math]::Round($matches[4], 2)
        }

        # 提取处理时间分布
        $processingMin = $processingAvg = $processingMax = "N/A"
        if ($content -match 'Processing:\s+([\d.]+)\s+([\d.]+)\s+([\d.]+)\s+([\d.]+)') {
            $processingMin = [math]::Round($matches[1], 2)
            $processingAvg = [math]::Round($matches[2], 2)
            $processingMax = [math]::Round($matches[4], 2)
        }

        # 提取等待时间分布
        $waitingMin = $waitingAvg = $waitingMax = "N/A"
        if ($content -match 'Waiting:\s+([\d.]+)\s+([\d.]+)\s+([\d.]+)\s+([\d.]+)') {
            $waitingMin = [math]::Round($matches[1], 2)
            $waitingAvg = [math]::Round($matches[2], 2)
            $waitingMax = [math]::Round($matches[4], 2)
        }

        # 计算成功率
        if ($completeRequests -ne "N/A" -and $failedRequests -ne "N/A") {
            $successRate = if ([int]$completeRequests -gt 0) {
                [math]::Round(([int]$completeRequests - [int]$failedRequests) / [int]$completeRequests * 100, 2)
            } else { 0 }
        } else {
            $successRate = "N/A"
        }

        # 设置失败请求的颜色
        $failedColor = "White"
        if ($failedRequests -ne "N/A" -and [int]$failedRequests -gt 0) {
            $failedColor = "Red"
        }

        # 设置成功率的颜色
        $successColor = "White"
        if ($successRate -ne "N/A") {
            if ($successRate -ge 99) {
                $successColor = "Green"
            } elseif ($successRate -ge 95) {
                $successColor = "Yellow"
            } else {
                $successColor = "Red"
            }
        }

        # 显示结果
        Write-Host "[+] Requests Summary:" -ForegroundColor Cyan
        Write-Host "    Complete requests: $completeRequests" -ForegroundColor White
        Write-Host "    Failed requests: $failedRequests" -ForegroundColor $failedColor
        Write-Host "    Success rate: $successRate%" -ForegroundColor $successColor

        Write-Host "`n[+] Performance Metrics:" -ForegroundColor Cyan
        Write-Host "    Requests per second: $rps" -ForegroundColor White
        Write-Host "    Time per request (mean): $timePerRequest ms" -ForegroundColor White

        # 显示百分位数响应时间
        Write-Host "`n[+] Response Time Percentiles:" -ForegroundColor Cyan
        if ($medianResponse -ne "N/A") { Write-Host "    50% (median): $medianResponse" -ForegroundColor White }
        if ($p90Response -ne "N/A") { Write-Host "    90%: $p90Response" -ForegroundColor White }
        if ($p95Response -ne "N/A") { Write-Host "    95%: $p95Response" -ForegroundColor Yellow }
        if ($p99Response -ne "N/A") { Write-Host "    99%: $p99Response" -ForegroundColor Magenta }
        if ($maxResponse -ne "N/A") { Write-Host "    100% (max): $maxResponse" -ForegroundColor Red }

        Write-Host "`n[+] Timing Breakdown:" -ForegroundColor Cyan
        if ($connectMin -ne "N/A") {
            Write-Host "    Connect: $connectMin min, $connectAvg avg, $connectMax max ms" -ForegroundColor Gray
        }
        if ($processingMin -ne "N/A") {
            Write-Host "    Processing: $processingMin min, $processingAvg avg, $processingMax max ms" -ForegroundColor Gray
        }
        if ($waitingMin -ne "N/A") {
            Write-Host "    Waiting: $waitingMin min, $waitingAvg avg, $waitingMax max ms" -ForegroundColor Gray
        }

        Write-Host "`n[+] Transfer Metrics:" -ForegroundColor Cyan
        Write-Host "    Transfer rate: $transferRate" -ForegroundColor White

        # 检查是否有警告或错误
        if ($content -match 'apr_socket_recv') {
            Write-Host "`n[!] WARNING: Socket receive errors detected" -ForegroundColor Red
        }

        if ($content -match 'Non-2xx responses') {
            $non2xx = if ($content -match 'Non-2xx responses:\s+(\d+)') { $matches[1] } else { "some" }
            Write-Host "`n[!] WARNING: $non2xx non-2xx responses detected" -ForegroundColor Yellow
        }

        return @{
            RPS = $rps
            SuccessRate = $successRate
            AvgResponse = $timePerRequest
            P50 = $medianResponse -replace ' ms', ''
            P90 = $p90Response -replace ' ms', ''
            P95 = $p95Response -replace ' ms', ''
            P99 = $p99Response -replace ' ms', ''
            Max = $maxResponse -replace ' ms', ''
        }
    } else {
        Write-Host "Result file not found: $ResultFile" -ForegroundColor Red
        return $null
    }
}

# Tenant configuration based on your database table
$tenants = @(
    @{id="tenant_001"; name="Alpha Tech Inc"; tier="VIP"; qps=2000; factor=1.5; status="ACTIVE"},
    @{id="tenant_002"; name="Beta Solutions Ltd"; tier="BASIC"; qps=1000; factor=1.0; status="ACTIVE"},
    @{id="tenant_003"; name="Gamma Startup"; tier="FREE"; qps=500; factor=0.5; status="ACTIVE"},
    @{id="tenant_999"; name="Test Suspended Company"; tier="BASIC"; qps=0; factor=0.0; status="SUSPENDED"}
)

# Active tenants (excluding suspended)
$activeTenants = $tenants | Where-Object { $_.status -eq "ACTIVE" }

Write-Host "`n=== Tenant Configuration ===" -ForegroundColor Cyan
foreach ($tenant in $activeTenants) {
    Write-Host "  $($tenant.id) ($($tenant.name)) - Tier: $($tenant.tier) - Max QPS: $($tenant.qps)" -ForegroundColor White
}

# 获取当前脚本所在目录，作为结果目录的基准
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if ([string]::IsNullOrEmpty($scriptDir)) {
    $scriptDir = Get-Location
}

# Create results directory
$resultDir = Join-Path $scriptDir "load_test_results\multi_tenant_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
New-Item -ItemType Directory -Force -Path $resultDir | Out-Null
Write-Host "`nResults will be saved to: $resultDir" -ForegroundColor Green

# 将结果目录转换为绝对路径
$resultDir = Resolve-Path $resultDir

# Generate multi-tenant test data - 将JSON文件保存在结果目录中
Write-Host "`nGenerating multi-tenant test data..." -ForegroundColor Cyan
foreach ($tenant in $tenants) {
    $timestamp = [int][double]::Parse((Get-Date -UFormat %s))
    $requestData = @{
        requestId = "load-test-$($tenant.id)-$timestamp"
        tenantId = $tenant.id
        resourceKey = "/api/v1/orders"
        tokens = 1
        timestamp = "$($timestamp)000"
    } | ConvertTo-Json -Compress

    # 将JSON文件保存在结果目录中
    $filename = Join-Path $resultDir "check_request_$($tenant.id).json"
    Set-Content -Path $filename -Value $requestData
    Write-Host "  [OK] Generated: $filename" -ForegroundColor Green
}

# Check service status
Write-Host "`nChecking service status..." -ForegroundColor Cyan
try {
    $healthDP = Invoke-RestMethod -Uri "http://localhost:8082/actuator/health" -ErrorAction Stop
    $healthCP = Invoke-RestMethod -Uri "http://localhost:8081/actuator/health" -ErrorAction Stop

    if ($healthDP.status -eq "UP" -and $healthCP.status -eq "UP") {
        Write-Host "[OK] Service status: OK" -ForegroundColor Green
        Write-Host "  Data Plane: $($healthDP.status)" -ForegroundColor White
        Write-Host "  Control Plane: $($healthCP.status)" -ForegroundColor White
    } else {
        Write-Host "[ERROR] Service status: ERROR" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "[ERROR] Cannot connect to services: $_" -ForegroundColor Red
    exit 1
}

# 存储所有测试结果用于后续汇总
$allResults = @{}

# Test 1: Warm-up (all active tenants)
Write-Host "`n=== Test 1: Warm-up - All active tenants (1 minute) ===" -ForegroundColor Magenta
$test1Results = @{}
foreach ($tenant in $activeTenants) {
    $qps = [math]::Round($tenant.qps * 0.05)  # 5% load for warm-up
    $requests = $qps * 60
    $concurrent = [math]::Max(5, [math]::Round($qps / 20))

    Write-Host "`nStarting: $($tenant.name) ($($tenant.tier)) - Target QPS: $qps" -ForegroundColor Yellow

    $resultFile = Join-Path $resultDir "01_warmup_$($tenant.id).txt"
    $jsonFile = Join-Path $resultDir "check_request_$($tenant.id).json"

    & $abPath -n $requests -c $concurrent -t 60 -k -r `
       -H "Content-Type: application/json" `
       -H "X-Trace-Id: warmup-$($tenant.id)-$(Get-Date -UFormat %s)" `
       -p $jsonFile `
       http://localhost:8082/api/v1/check > $resultFile 2>&1

    $result = Show-TestResult -TestName "Warm-up Test" -ResultFile $resultFile -TenantName $tenant.name -Tier $tenant.tier
    if ($result) {
        $test1Results[$tenant.id] = $result
    }

    Start-Sleep -Milliseconds 500
}

Write-Host "`n[OK] Warm-up complete, waiting for system to stabilize..." -ForegroundColor Green
Start-Sleep -Seconds 30

# Test 2: Baseline test (by tier)
Write-Host "`n=== Test 2: Baseline test - By tenant tier (3 minutes) ===" -ForegroundColor Magenta
$test2Results = @{}
foreach ($tenant in $activeTenants) {
    $baseQPS = switch ($tenant.tier) {
        "VIP" { 500 }
        "BASIC" { 200 }
        "FREE" { 50 }
        default { 100 }
    }

    $requests = $baseQPS * 180
    $concurrent = [math]::Round($baseQPS / 20)

    Write-Host "`nStarting: $($tenant.name) ($($tenant.tier)) - Target QPS: $baseQPS" -ForegroundColor Yellow

    $resultFile = Join-Path $resultDir "02_baseline_$($tenant.id).txt"
    $jsonFile = Join-Path $resultDir "check_request_$($tenant.id).json"

    & $abPath -n $requests -c $concurrent -t 180 -k -r `
       -H "Content-Type: application/json" `
       -H "X-Trace-Id: baseline-$($tenant.id)-$(Get-Date -UFormat %s)" `
       -p $jsonFile `
       http://localhost:8082/api/v1/check > $resultFile 2>&1

    $result = Show-TestResult -TestName "Baseline Test" -ResultFile $resultFile -TenantName $tenant.name -Tier $tenant.tier
    if ($result) {
        $test2Results[$tenant.id] = $result
    }

    Start-Sleep -Milliseconds 500
}

Write-Host "`n[OK] Baseline test complete, waiting for system to process..." -ForegroundColor Green
Start-Sleep -Seconds 10

# Test 3: Mixed pressure test (all tenants simultaneously)
Write-Host "`n=== Test 3: Mixed pressure test - All tenants simultaneously (2 minutes) ===" -ForegroundColor Magenta
Write-Host "Note: This test will run all tenant tests in parallel" -ForegroundColor Yellow
$testDuration = 120
$timestamp = Get-Date -UFormat %s

# 改为使用更简单的并行方法 - 使用作业
Write-Host "`nRunning mixed pressure test in parallel using PowerShell Jobs..." -ForegroundColor Yellow

# 清除任何现有的作业
Get-Job | Remove-Job -Force

$jobs = @()
foreach ($tenant in $activeTenants) {
    $loadQPS = [math]::Round($tenant.qps * $tenant.factor * 0.3)  # 30% load
    $requests = $loadQPS * $testDuration
    $concurrent = [math]::Round($loadQPS / 10)

    Write-Host "  Starting: $($tenant.name) - Target QPS: $loadQPS" -ForegroundColor Yellow

    $resultFile = Join-Path $resultDir "03_mixed_$($tenant.id).txt"
    $jsonFile = Join-Path $resultDir "check_request_$($tenant.id).json"

    # 创建作业
    $jobScript = {
        param($abPath, $tenantId, $requests, $concurrent, $duration, $timestamp, $jsonFile, $resultFile)

        # 构建完整的命令行
        $command = "$abPath -n $requests -c $concurrent -t $duration -k -r -H 'Content-Type: application/json' -H 'X-Trace-Id: mixed-$tenantId-$timestamp' -p '$jsonFile' 'http://localhost:8082/api/v1/check'"

        # 执行命令
        Invoke-Expression "$command > '$resultFile' 2>&1"

        # 返回租户ID和结果文件
        return @{
            TenantId = $tenantId
            ResultFile = $resultFile
        }
    }

    # 启动作业
    $job = Start-Job -ScriptBlock $jobScript -ArgumentList $abPath, $tenant.id, $requests, $concurrent, $testDuration, $timestamp, $jsonFile, $resultFile
    $jobs += $job
    Start-Sleep -Milliseconds 100
}

Write-Host "`nWaiting for all mixed tests to complete..."
# 等待所有作业完成
$jobs | Wait-Job | Out-Null

# 收集作业结果
$jobResults = @{}
foreach ($job in $jobs) {
    $result = Receive-Job -Job $job
    if ($result) {
        $jobResults[$result.TenantId] = $result
    }
    Remove-Job -Job $job -Force
}

Write-Host "All mixed tests completed!" -ForegroundColor Green

# 显示混合测试结果
Write-Host "`n=== Mixed Pressure Test Results ===" -ForegroundColor Yellow
$test3Results = @{}
foreach ($tenant in $activeTenants) {
    $resultFile = Join-Path $resultDir "03_mixed_$($tenant.id).txt"
    if (Test-Path $resultFile) {
        $result = Show-TestResult -TestName "Mixed Pressure Test" -ResultFile $resultFile -TenantName $tenant.name -Tier $tenant.tier
        if ($result) {
            $test3Results[$tenant.id] = $result
        }
    } else {
        Write-Host "`n[ERROR] Result file not found for $($tenant.name): $resultFile" -ForegroundColor Red
        # 尝试查看作业错误
        foreach ($job in $jobs) {
            $jobError = Receive-Job -Job $job -ErrorAction SilentlyContinue
            if ($jobError) {
                Write-Host "  Job error: $jobError" -ForegroundColor Red
            }
        }
    }
}

Write-Host "`n[OK] Mixed pressure test complete, waiting for system to process..." -ForegroundColor Green
Start-Sleep -Seconds 10

# Test 4: Peak pressure test (VIP tenants overload)
Write-Host "`n=== Test 4: Peak pressure test - VIP tenants overload (1 minute) ===" -ForegroundColor Magenta
$vipTenants = $activeTenants | Where-Object { $_.tier -eq "VIP" }
$test4Results = @{}

if ($vipTenants.Count -eq 0) {
    Write-Host "[INFO] No VIP tenants, skipping this test" -ForegroundColor Yellow
} else {
    foreach ($tenant in $vipTenants) {
        $peakQPS = $tenant.qps * 1.5  # 150% load
        $requests = $peakQPS * 60
        $concurrent = [math]::Round($peakQPS / 5)  # Higher concurrency

        Write-Host "`nStarting: $($tenant.name) ($($tenant.tier)) - Target QPS: $peakQPS (overload)" -ForegroundColor Red

        $resultFile = Join-Path $resultDir "04_peak_$($tenant.id).txt"
        $jsonFile = Join-Path $resultDir "check_request_$($tenant.id).json"

        & $abPath -n $requests -c $concurrent -t 60 -k -r `
           -H "Content-Type: application/json" `
           -H "X-Trace-Id: peak-$($tenant.id)-$(Get-Date -UFormat %s)" `
           -p $jsonFile `
           http://localhost:8082/api/v1/check > $resultFile 2>&1

        $result = Show-TestResult -TestName "Peak Pressure Test" -ResultFile $resultFile -TenantName $tenant.name -Tier $tenant.tier
        if ($result) {
            $test4Results[$tenant.id] = $result
        }
    }
}

# Test 5: Tenant comparison test
Write-Host "`n=== Test 5: Tenant comparison test - Same load (30 seconds) ===" -ForegroundColor Magenta
$compareQPS = 300
$compareRequests = $compareQPS * 30
$compareConcurrent = 15
$test5Results = @{}

Write-Host "All tenants using same load: $compareQPS QPS" -ForegroundColor Yellow

foreach ($tenant in $activeTenants) {
    Write-Host "`nStarting: $($tenant.name) ($($tenant.tier)) - Target QPS: $compareQPS" -ForegroundColor Yellow

    $resultFile = Join-Path $resultDir "05_compare_$($tenant.id).txt"
    $jsonFile = Join-Path $resultDir "check_request_$($tenant.id).json"

    & $abPath -n $compareRequests -c $compareConcurrent -t 30 -k -r `
       -H "Content-Type: application/json" `
       -H "X-Trace-Id: compare-$($tenant.id)-$(Get-Date -UFormat %s)" `
       -p $jsonFile `
       http://localhost:8082/api/v1/check > $resultFile 2>&1

    $result = Show-TestResult -TestName "Tenant Comparison Test" -ResultFile $resultFile -TenantName $tenant.name -Tier $tenant.tier
    if ($result) {
        $test5Results[$tenant.id] = $result
    }

    Start-Sleep -Seconds 2
}

# Clean up temporary files
Get-ChildItem -Filter "check_request_*.json" -Path $resultDir | Remove-Item

$endTime = Get-Date
$duration = $endTime - $startTime
Write-Host "`n=== Test Summary ===" -ForegroundColor Cyan
Write-Host "Start time: $startTime"
Write-Host "End time: $endTime"
Write-Host "Total duration: $duration"
Write-Host "Results directory: $resultDir" -ForegroundColor Green

# 显示详细指标对比
Write-Host "`n=== Detailed Metrics Comparison ===" -ForegroundColor Cyan
Write-Host "Tenant | Tier | RPS | Success% | Avg(ms) | P50(ms) | P90(ms) | P95(ms) | P99(ms) | Max(ms)"
Write-Host "-------|------|-----|----------|---------|---------|---------|---------|---------|--------"

foreach ($tenant in $activeTenants) {
    $baselineResult = if ($test2Results[$tenant.id]) { $test2Results[$tenant.id] } else { $null }
    $mixedResult = if ($test3Results[$tenant.id]) { $test3Results[$tenant.id] } else { $null }
    $compareResult = if ($test5Results[$tenant.id]) { $test5Results[$tenant.id] } else { $null }

    # 使用基准测试结果作为主要显示
    $result = $baselineResult
    if (-not $result) { $result = $compareResult }
    if (-not $result) { $result = $mixedResult }

    if ($result) {
        $rps = if ($result.RPS -ne "N/A") { $result.RPS } else { "N/A" }
        $success = if ($result.SuccessRate -ne "N/A") { "$($result.SuccessRate)%" } else { "N/A" }
        $avg = if ($result.AvgResponse -ne "N/A") { $result.AvgResponse } else { "N/A" }
        $p50 = if ($result.P50 -ne "N/A") { $result.P50 } else { "N/A" }
        $p90 = if ($result.P90 -ne "N/A") { $result.P90 } else { "N/A" }
        $p95 = if ($result.P95 -ne "N/A") { $result.P95 } else { "N/A" }
        $p99 = if ($result.P99 -ne "N/A") { $result.P99 } else { "N/A" }
        $max = if ($result.Max -ne "N/A") { $result.Max } else { "N/A" }

        # 根据租户级别设置颜色
        if ($tenant.tier -eq "VIP") {
            $color = "Yellow"
        } elseif ($tenant.tier -eq "BASIC") {
            $color = "White"
        } else {
            $color = "Gray"
        }

        Write-Host "$($tenant.name) | $($tenant.tier) | $rps | $success | $avg | $p50 | $p90 | $p95 | $p99 | $max" -ForegroundColor $color
    } else {
        Write-Host "$($tenant.name) | $($tenant.tier) | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A" -ForegroundColor Red
    }
}

# 显示不同测试场景的对比
Write-Host "`n=== Test Scenario Comparison (RPS) ===" -ForegroundColor Cyan
Write-Host "Tenant | Tier | Warm-up | Baseline | Mixed | Peak | Compare"
Write-Host "-------|------|---------|----------|-------|------|--------"

foreach ($tenant in $activeTenants) {
    $warmupRPS = if ($test1Results[$tenant.id]) { $test1Results[$tenant.id].RPS } else { "N/A" }
    $baselineRPS = if ($test2Results[$tenant.id]) { $test2Results[$tenant.id].RPS } else { "N/A" }
    $mixedRPS = if ($test3Results[$tenant.id]) { $test3Results[$tenant.id].RPS } else { "N/A" }
    $peakRPS = if ($test4Results[$tenant.id]) { $test4Results[$tenant.id].RPS } else { "N/A" }
    $compareRPS = if ($test5Results[$tenant.id]) { $test5Results[$tenant.id].RPS } else { "N/A" }

    # 根据租户级别设置颜色
    if ($tenant.tier -eq "VIP") {
        $color = "Yellow"
    } elseif ($tenant.tier -eq "BASIC") {
        $color = "White"
    } else {
        $color = "Gray"
    }

    Write-Host "$($tenant.name) | $($tenant.tier) | $warmupRPS | $baselineRPS | $mixedRPS | $peakRPS | $compareRPS" -ForegroundColor $color
}

# 生成测试总结（保留原功能）
Write-Host "`nGenerating test summary file..." -ForegroundColor Cyan
$summaryContent = @"
# Apache Bench Multi-Tenant Load Test Summary

## Test Time
- Start: $startTime
- End: $endTime
- Total Duration: $duration

## Tenant Configuration
| Tenant ID | Tenant Name | Tier | Status | Base QPS | Load Factor |
|-----------|-------------|------|--------|----------|-------------|
$($tenants | ForEach-Object {
    "| $($_.id) | $($_.name) | $($_.tier) | $($_.status) | $($_.qps) | $($_.factor) |"
} | Out-String)

## Key Test Results
| Test | Tenant | Tier | RPS | Success Rate | Avg(ms) | P50(ms) | P90(ms) | P95(ms) | P99(ms) |
|------|--------|------|-----|--------------|---------|---------|---------|---------|---------|
$($activeTenants | ForEach-Object {
    $tenant = $_
    $tests = @("Baseline Test", "Mixed Test", "Compare Test")
    $results = @($test2Results[$tenant.id], $test3Results[$tenant.id], $test5Results[$tenant.id])

    $output = ""
    for ($i = 0; $i -lt $tests.Count; $i++) {
        if ($results[$i]) {
            $rps = if ($results[$i].RPS -ne "N/A") { $results[$i].RPS } else { "N/A" }
            $success = if ($results[$i].SuccessRate -ne "N/A") { "$($results[$i].SuccessRate)%" } else { "N/A" }
            $avg = if ($results[$i].AvgResponse -ne "N/A") { $results[$i].AvgResponse } else { "N/A" }
            $p50 = if ($results[$i].P50 -ne "N/A") { $results[$i].P50 } else { "N/A" }
            $p90 = if ($results[$i].P90 -ne "N/A") { $results[$i].P90 } else { "N/A" }
            $p95 = if ($results[$i].P95 -ne "N/A") { $results[$i].P95 } else { "N/A" }
            $p99 = if ($results[$i].P99 -ne "N/A") { $results[$i].P99 } else { "N/A" }

            $output += "| $($tests[$i]) | $($tenant.name) | $($tenant.tier) | $rps | $success | $avg | $p50 | $p90 | $p95 | $p99 |`n"
        }
    }
    $output
} | Out-String)

## Test Scenarios
1. **Warm-up Test**: All active tenants, 5% load, 1 minute
2. **Baseline Test**: Different loads by tier, 3 minutes
   - VIP: 500 QPS
   - BASIC: 200 QPS
   - FREE: 50 QPS
3. **Mixed Pressure Test**: All tenants simultaneously, 30% load, 2 minutes (parallel)
4. **Peak Pressure Test**: VIP tenants 150% overload, 1 minute
5. **Tenant Comparison Test**: All tenants same load (300 QPS), 30 seconds

## Monitoring Points
- Grafana: http://localhost:3000
- Prometheus: http://localhost:9090
- Control Plane: http://localhost:8081/actuator/prometheus
- Data Plane: http://localhost:8082/actuator/prometheus
- Accounting: http://localhost:8083/actuator/prometheus

## Key Performance Indicators
- **RPS (Requests Per Second)**: Throughput of the system
- **Success Rate**: Percentage of successful requests
- **Average Response Time**: Mean response time
- **P50 (Median)**: 50th percentile response time
- **P90**: 90th percentile response time (90% of requests are faster than this)
- **P95**: 95th percentile response time (95% of requests are faster than this)
- **P99**: 99th percentile response time (99% of requests are faster than this)
- **Max**: Maximum response time observed
"@

$summaryFile = Join-Path $resultDir "summary.md"
Set-Content -Path $summaryFile -Value $summaryContent
Write-Host "[OK] Test summary generated: $summaryFile" -ForegroundColor Green

Write-Host "`n=== Monitoring URLs ===" -ForegroundColor Cyan
Write-Host "Grafana:     http://localhost:3000" -ForegroundColor White
Write-Host "Prometheus:  http://localhost:9090" -ForegroundColor White
Write-Host "`n=== Complete ===" -ForegroundColor Green
Write-Host "All tests completed! For detailed results, open: explorer.exe `"$resultDir`"" -ForegroundColor Green

# 提供一个快速查看结果的方法
Write-Host "`nQuick results check:" -ForegroundColor Cyan
Write-Host "  # View all result files" -ForegroundColor Yellow
Write-Host "  Get-ChildItem `"$resultDir\*.txt`"" -ForegroundColor White
Write-Host ""
Write-Host "  # Check percentile data in results" -ForegroundColor Yellow
Write-Host "  Get-Content `"$(Join-Path $resultDir "02_baseline_tenant_001.txt")`" | Select-String -Pattern '50%|90%|95%|99%|100%'" -ForegroundColor White