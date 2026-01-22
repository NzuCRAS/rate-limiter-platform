# RateLimiter_MultiTenant_LoadTest.ps1

Write-Host "=== Rate Limiter Multi-Tenant Apache Bench Load Test ==="
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

Write-Host "Using Apache Bench from: $abPath"

# 验证 ab 是否可用
try {
    $abVersion = & $abPath -V 2>&1
    Write-Host "Apache Bench version: $abVersion"
} catch {
    Write-Host "ERROR: Cannot execute ab.exe" -ForegroundColor Red
    Write-Host "Error details: $_" -ForegroundColor Yellow
    exit 1
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

Write-Host "Active Tenants:"
foreach ($tenant in $activeTenants) {
    Write-Host "  $($tenant.id) ($($tenant.name)) - Tier: $($tenant.tier) - Max QPS: $($tenant.qps)"
}

# Create results directory
$resultDir = "load_test_results\multi_tenant_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
New-Item -ItemType Directory -Force -Path $resultDir | Out-Null
Write-Host "Results will be saved to: $resultDir"

# Generate multi-tenant test data
Write-Host "`nGenerating multi-tenant test data..."
foreach ($tenant in $tenants) {
    $timestamp = [int][double]::Parse((Get-Date -UFormat %s))
    $requestData = @{
        requestId = "load-test-$($tenant.id)-$timestamp"
        tenantId = $tenant.id
        resourceKey = "/api/v1/orders"
        tokens = 1
        timestamp = "$($timestamp)000"
    } | ConvertTo-Json -Compress

    $filename = "check_request_$($tenant.id).json"
    Set-Content -Path $filename -Value $requestData
    Write-Host "  Generated: $filename"
}

# Check service status
Write-Host "`nChecking service status..."
try {
    $healthDP = Invoke-RestMethod -Uri "http://localhost:8082/actuator/health" -ErrorAction Stop
    $healthCP = Invoke-RestMethod -Uri "http://localhost:8081/actuator/health" -ErrorAction Stop

    if ($healthDP.status -eq "UP" -and $healthCP.status -eq "UP") {
        Write-Host "Service status: OK"
        Write-Host "  Data Plane: $($healthDP.status)"
        Write-Host "  Control Plane: $($healthCP.status)"
    } else {
        Write-Host "Service status: ERROR"
        exit 1
    }
} catch {
    Write-Host "Cannot connect to services: $_"
    exit 1
}

# Test 1: Warm-up (all active tenants)
Write-Host "`nTest 1: Warm-up - All active tenants x 1 minute"
foreach ($tenant in $activeTenants) {
    $qps = [math]::Round($tenant.qps * 0.05)  # 5% load for warm-up
    $requests = $qps * 60
    $concurrent = [math]::Max(5, [math]::Round($qps / 20))

    Write-Host "  $($tenant.name) ($($tenant.tier)) - $qps QPS"

    & $abPath -n $requests -c $concurrent -t 60 `
       -H "Content-Type: application/json" `
       -H "X-Trace-Id: warmup-$($tenant.id)-$(Get-Date -UFormat %s)" `
       -p "check_request_$($tenant.id).json" `
       http://localhost:8082/api/v1/check > "$resultDir\01_warmup_$($tenant.id).txt" 2>&1

    Start-Sleep -Milliseconds 500
}

Write-Host "`nWarm-up complete, waiting for system to stabilize..."
Start-Sleep -Seconds 30

# Test 2: Baseline test (by tier)
Write-Host "`nTest 2: Baseline test - By tenant tier x 3 minutes"
foreach ($tenant in $activeTenants) {
    $baseQPS = switch ($tenant.tier) {
        "VIP" { 500 }
        "BASIC" { 200 }
        "FREE" { 50 }
        default { 100 }
    }

    $requests = $baseQPS * 180
    $concurrent = [math]::Round($baseQPS / 20)

    Write-Host "  $($tenant.name) ($($tenant.tier)) - $baseQPS QPS"

    & $abPath -n $requests -c $concurrent -t 180 `
       -H "Content-Type: application/json" `
       -H "X-Trace-Id: baseline-$($tenant.id)-$(Get-Date -UFormat %s)" `
       -p "check_request_$($tenant.id).json" `
       http://localhost:8082/api/v1/check > "$resultDir\02_baseline_$($tenant.id).txt" 2>&1

    Start-Sleep -Milliseconds 500
}

Write-Host "`nWaiting for system to process..."
Start-Sleep -Seconds 60

# Test 3: Mixed pressure test (all tenants simultaneously)
Write-Host "`nTest 3: Mixed pressure test - All tenants simultaneously x 2 minutes"
$testDuration = 120
$timestamp = Get-Date -UFormat %s

# Start parallel tests for all tenants
$jobs = @()
foreach ($tenant in $activeTenants) {
    $loadQPS = [math]::Round($tenant.qps * $tenant.factor * 0.3)  # 30% load
    $requests = $loadQPS * $testDuration
    $concurrent = [math]::Round($loadQPS / 10)

    $jobScript = {
        param($abPath, $tenantId, $requests, $concurrent, $duration, $timestamp, $resultDir)
        $command = "& '$abPath' -n $requests -c $concurrent -t $duration -H 'Content-Type: application/json' -H 'X-Trace-Id: mixed-$tenantId-$timestamp' -p 'check_request_$tenantId.json' http://localhost:8082/api/v1/check > '$resultDir\03_mixed_$tenantId.txt' 2>&1"
        Invoke-Expression $command
    }

    Write-Host "  Starting: $($tenant.name) - $loadQPS QPS"
    $job = Start-Job -ScriptBlock $jobScript -ArgumentList $abPath, $tenant.id, $requests, $concurrent, $testDuration, $timestamp, $resultDir
    $jobs += $job
    Start-Sleep -Milliseconds 100
}

Write-Host "`nWaiting for all tests to complete..."
$jobs | Wait-Job
$jobs | Remove-Job

Write-Host "Waiting for system to process..."
Start-Sleep -Seconds 60

# Test 4: Peak pressure test (VIP tenants overload)
Write-Host "`nTest 4: Peak pressure test - VIP tenants overload x 1 minute"
$vipTenants = $activeTenants | Where-Object { $_.tier -eq "VIP" }

foreach ($tenant in $vipTenants) {
    $peakQPS = $tenant.qps * 1.5  # 150% load
    $requests = $peakQPS * 60
    $concurrent = [math]::Round($peakQPS / 5)  # Higher concurrency

    Write-Host "  $($tenant.name) ($($tenant.tier)) - $peakQPS QPS (overload)"

    & $abPath -n $requests -c $concurrent -t 60 `
       -H "Content-Type: application/json" `
       -H "X-Trace-Id: peak-$($tenant.id)-$(Get-Date -UFormat %s)" `
       -p "check_request_$($tenant.id).json" `
       http://localhost:8082/api/v1/check > "$resultDir\04_peak_$($tenant.id).txt" 2>&1
}

# Test 5: Tenant comparison test
Write-Host "`nTest 5: Tenant comparison test - Same load x 30 seconds"
$compareQPS = 300
$compareRequests = $compareQPS * 30
$compareConcurrent = 15

foreach ($tenant in $activeTenants) {
    Write-Host "  $($tenant.name) ($($tenant.tier)) - $compareQPS QPS"

    & $abPath -n $compareRequests -c $compareConcurrent -t 30 `
       -H "Content-Type: application/json" `
       -H "X-Trace-Id: compare-$($tenant.id)-$(Get-Date -UFormat %s)" `
       -p "check_request_$($tenant.id).json" `
       http://localhost:8082/api/v1/check > "$resultDir\05_compare_$($tenant.id).txt" 2>&1

    Start-Sleep -Seconds 2
}

# Clean up temporary files
Get-ChildItem -Filter "check_request_*.json" | Remove-Item

$endTime = Get-Date
$duration = $endTime - $startTime
Write-Host "`n=== Apache Bench Multi-Tenant Test Complete ==="
Write-Host "End Time: $endTime"
Write-Host "Total Duration: $duration"
Write-Host "Results Directory: $resultDir"

# Generate test summary
Write-Host "`nGenerating test summary..."
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

## Result Files
$(Get-ChildItem $resultDir | ForEach-Object { "- $($_.Name)" } | Out-String)

## Analysis Points
1. **Tenant Isolation**: Check if VIP tenants are less affected under high load
2. **Rate Limiting Accuracy**: Verify if actual QPS matches configured QPS
3. **Tier Differences**: Compare response times and success rates across tiers
4. **System Stability**: Observe system performance under mixed load

## Next Steps
1. Analyze test results for each tenant
2. Check if rate limiting rules work as expected
3. Identify system bottlenecks and optimization points
"@

Set-Content -Path "$resultDir\summary.md" -Value $summaryContent

Write-Host "Test summary generated: $resultDir\summary.md"

# Generate result analysis script
$analysisScript = @"
# Result Analysis Script
Write-Host "Multi-Tenant Test Results Analysis"
Write-Host "==================================`n"

# 使用 ab.exe 的绝对路径
`$abPath = "$abPath"

`$tenants = @('tenant_001', 'tenant_002', 'tenant_003')
`$tests = @('warmup', 'baseline', 'mixed', 'peak', 'compare')

foreach (`$tenant in `$tenants) {
    Write-Host "Tenant: `$tenant"
    Write-Host "-" * 40

    foreach (`$test in `$tests) {
        `$file = "$resultDir\\0`$((`$tests.IndexOf(`$test))+1)_`\${test}_`\${tenant}.txt"
        if (Test-Path `$file) {
            `$content = Get-Content `$file -Raw

            # Extract key metrics
            if (`$content -match 'Requests per second:\s+([\d.]+)')
            { `$rps = "RPS: `$Matches[1]" } else { `$rps = "RPS: N/A" }

            if (`$content -match 'Time per request:\s+([\d.]+)')
            { `$timePerReq = "Avg Response: `$Matches[1] ms" } else { `$timePerReq = "Avg Response: N/A" }

            if (`$content -match 'Failed requests:\s+(\d+)')
            { `$failed = "Failed: `$Matches[1]" } else { `$failed = "Failed: N/A" }

            if (`$content -match 'Complete requests:\s+(\d+)')
            { `$complete = "Complete: `$Matches[1]" } else { `$complete = "Complete: N/A" }

            Write-Host "`\$test`: `\$rps, `\$timePerReq, `\$complete, `\$failed"
        }
    }
    Write-Host ""
}

# 简单汇总统计
Write-Host "`nSummary Statistics:"
Write-Host "-------------------"

`$totalRequests = 0
`$totalFailed = 0

foreach (`$tenant in `$tenants) {
    foreach (`$test in `$tests) {
        `$file = "$resultDir\\0`$((`$tests.IndexOf(`$test))+1)_`\${test}_`\${tenant}.txt"
        if (Test-Path `$file) {
            `$content = Get-Content `$file -Raw

            if (`$content -match 'Complete requests:\s+(\d+)') {
                `$totalRequests += [int]`$Matches[1]
            }

            if (`$content -match 'Failed requests:\s+(\d+)') {
                `$totalFailed += [int]`$Matches[1]
            }
        }
    }
}

if (`$totalRequests -gt 0) {
    `$successRate = (`$totalRequests - `$totalFailed) / `$totalRequests * 100
    Write-Host "Total Requests: `$totalRequests"
    Write-Host "Total Failed: `$totalFailed"
    Write-Host "Success Rate: `$successRate.ToString('0.00')%"
}

Write-Host "`nMonitoring URLs:"
Write-Host "  Grafana: http://localhost:3000"
Write-Host "  Prometheus: http://localhost:9090"
Write-Host "`nNote: Apache Bench location: `$abPath"
"@

Set-Content -Path "$resultDir\analyze_results.ps1" -Value $analysisScript

Write-Host "`nMonitoring URLs:"
Write-Host "  Grafana: http://localhost:3000"
Write-Host "  Prometheus: http://localhost:9090"
Write-Host "`nAnalyze test results:"
Write-Host "  PowerShell -ExecutionPolicy Bypass -File $resultDir\analyze_results.ps1"
Write-Host "`nView test results:"
Write-Host "  explorer.exe $resultDir"

# 显示一些有用的命令
Write-Host "`nUseful commands to check results:" -ForegroundColor Cyan
Write-Host "  # 查看测试结果文件" -ForegroundColor Yellow
Write-Host "  ls $resultDir\*.txt" -ForegroundColor White
Write-Host ""
Write-Host "  # 查看某个租户的测试结果" -ForegroundColor Yellow
Write-Host "  type $resultDir\02_baseline_tenant_001.txt | Select-String -Pattern 'Requests per second|Time per request|Failed requests'" -ForegroundColor White
Write-Host ""
Write-Host "  # 快速查看所有结果的关键指标" -ForegroundColor Yellow
Write-Host "  foreach (`$file in Get-ChildItem `"$resultDir\*.txt`") {" -ForegroundColor White
Write-Host "    Write-Host `"`$(`$file.Name):`"" -ForegroundColor White
Write-Host "    Select-String -Path `$file.FullName -Pattern 'Requests per second|Time per request|Failed requests' | ForEach-Object { `"    `$(`$_.Line)`" }" -ForegroundColor White
Write-Host "  }" -ForegroundColor White