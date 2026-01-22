# test_metrics.ps1

Write-Host "Testing fixed metrics..." -ForegroundColor Cyan
Write-Host "=" * 50

# 1. 发送几个测试请求
Write-Host "`n1. Sending test requests..." -ForegroundColor Yellow

for ($i = 1; $i -le 5; $i++) {
    $requestBody = @{
        requestId = "fix-test-$i"
        tenantId = "tenant_001"
        resourceKey = "/api/test"
        tokens = 1
        timestamp = "$([int][double]::Parse((Get-Date -UFormat %s)))000"
    } | ConvertTo-Json

    Write-Host " Request #$($i): $requestBody" -ForegroundColor Gray

    try {
        $response = Invoke-RestMethod -Uri "http://localhost:8082/api/v1/check" `
            -Method Post `
            -ContentType "application/json" `
            -Body $requestBody `
            -ErrorAction Stop

        Write-Host "  ✅ Response: $($response | ConvertTo-Json -Compress)" -ForegroundColor Green

        # 显示关键响应信息
        if ($response.allowed -ne $null) {
            $status = if ($response.allowed) { "ALLOWED" } else { "DENIED" }
            Write-Host "    Status: $status" -ForegroundColor $(if ($response.allowed) { "Green" } else { "Red" })
        }
        if ($response.reason) {
            Write-Host "    Reason: $($response.reason)" -ForegroundColor Yellow
        }
        if ($response.remaining -ne $null) {
            Write-Host "    Remaining: $($response.remaining)" -ForegroundColor Cyan
        }

    } catch {
        Write-Host "  ❌ Request failed: $_" -ForegroundColor Red
    }

    # 稍微等待一下，避免请求过快
    Start-Sleep -Milliseconds 100
}

Write-Host "`n2. Checking metrics endpoints..." -ForegroundColor Yellow
Write-Host "=" * 50

# 2. 检查所有服务的指标端点
$services = @(
    @{ Name = "Data Plane (8082)"; Url = "http://localhost:8082/actuator/prometheus" },
    @{ Name = "Control Plane (8081)"; Url = "http://localhost:8081/actuator/prometheus" },
    @{ Name = "Accounting (8083)"; Url = "http://localhost:8083/actuator/prometheus" }
)

foreach ($service in $services) {
    Write-Host "`nChecking $($service.Name)..." -ForegroundColor Cyan

    try {
        $response = Invoke-WebRequest -Uri $service.Url -TimeoutSec 5 -ErrorAction Stop

        Write-Host "  ✅ Endpoint accessible" -ForegroundColor Green

        # 提取限流相关指标
        $metrics = $response.Content -split "`n" | Where-Object { $_ -match "rate_limit" }

        if ($metrics.Count -gt 0) {
            Write-Host "  Found $($metrics.Count) rate limit metrics:" -ForegroundColor Green

            # 只显示前10个指标
            $metrics | Select-Object -First 10 | ForEach-Object {
                Write-Host "    $_" -ForegroundColor White
            }

            if ($metrics.Count -gt 10) {
                Write-Host "    ... and $($metrics.Count - 10) more metrics" -ForegroundColor Gray
            }
        } else {
            # 如果没有找到 rate_limit 指标，搜索其他相关指标
            $otherMetrics = $response.Content -split "`n" | Where-Object {
                $_ -match "policy_" -or $_ -match "kafka_" -or $_ -match "audit_" -or
                        $_ -match "requests_total" -or $_ -match "latency_" -or $_ -match "duration_"
            }

            if ($otherMetrics.Count -gt 0) {
                Write-Host "  No 'rate_limit' metrics found, but found other relevant metrics:" -ForegroundColor Yellow
                $otherMetrics | Select-Object -First 10 | ForEach-Object {
                    Write-Host "    $_" -ForegroundColor White
                }
            } else {
                Write-Host "  ⚠️ No relevant metrics found" -ForegroundColor Yellow
            }
        }

    } catch {
        Write-Host "  ❌ Cannot access metrics endpoint: $_" -ForegroundColor Red
    }
}

Write-Host "`n3. Checking service health..." -ForegroundColor Yellow
Write-Host "=" * 50

# 3. 检查服务健康状态
$healthEndpoints = @(
    @{ Name = "Data Plane Health"; Url = "http://localhost:8082/actuator/health" },
    @{ Name = "Control Plane Health"; Url = "http://localhost:8081/actuator/health" },
    @{ Name = "Accounting Health"; Url = "http://localhost:8083/actuator/health" }
)

foreach ($endpoint in $healthEndpoints) {
    try {
        $health = Invoke-RestMethod -Uri $endpoint.Url -TimeoutSec 3 -ErrorAction Stop
        $status = $health.status

        if ($status -eq "UP") {
            Write-Host "  ✅ $($endpoint.Name): UP" -ForegroundColor Green
        } else {
            Write-Host "  ⚠️ $($endpoint.Name): $status" -ForegroundColor Yellow
        }

        # 显示详细状态信息
        if ($health.components) {
            foreach ($component in $health.components.PSObject.Properties) {
                $componentName = $component.Name
                $componentStatus = $component.Value.status
                Write-Host " $($componentName): $componentStatus" -ForegroundColor Gray
            }
        }

    } catch {
        Write-Host "  ❌ $($endpoint.Name): Cannot connect" -ForegroundColor Red
    }
}

Write-Host "`n4. Testing different response scenarios..." -ForegroundColor Yellow
Write-Host "=" * 50

# 4. 测试不同场景
$testScenarios = @(
    @{ Name = "Valid tenant with policy"; tenantId = "tenant_001"; resourceKey = "/api/v1/orders" },
    @{ Name = "Unknown tenant"; tenantId = "tenant_unknown"; resourceKey = "/api/v1/orders" },
    @{ Name = "Different resource"; tenantId = "tenant_001"; resourceKey = "/api/v1/users" }
)

foreach ($scenario in $testScenarios) {
    Write-Host "`nTesting: $($scenario.Name)" -ForegroundColor Cyan

    $requestBody = @{
        requestId = "scenario-test-$(Get-Date -Format 'yyyyMMddHHmmssfff')"
        tenantId = $scenario.tenantId
        resourceKey = $scenario.resourceKey
        tokens = 1
        timestamp = "$([int][double]::Parse((Get-Date -UFormat %s)))000"
    } | ConvertTo-Json

    try {
        $response = Invoke-RestMethod -Uri "http://localhost:8082/api/v1/check" `
            -Method Post `
            -ContentType "application/json" `
            -Body $requestBody

        $status = if ($response.allowed) { "✅ ALLOWED" } else { "❌ DENIED" }
        Write-Host "  $status - Reason: $($response.reason)" -ForegroundColor $(if ($response.allowed) { "Green" } else { "Yellow" })

    } catch {
        Write-Host "  ❌ Request failed: $_" -ForegroundColor Red
    }
}

Write-Host "`n5. Summary and next steps..." -ForegroundColor Green
Write-Host "=" * 50

# 5. 汇总信息
Write-Host "`nTest Summary:" -ForegroundColor White
Write-Host "-" * 30

Write-Host "📊 Metrics are available at:" -ForegroundColor Cyan
Write-Host "  Data Plane: http://localhost:8082/actuator/prometheus" -ForegroundColor White
Write-Host "  Control Plane: http://localhost:8081/actuator/prometheus" -ForegroundColor White
Write-Host "  Accounting: http://localhost:8083/actuator/prometheus" -ForegroundColor White

Write-Host "`n🔍 To view all rate_limit metrics, run:" -ForegroundColor Cyan
Write-Host '  curl -s http://localhost:8082/actuator/prometheus | Select-String -Pattern "^rate_limit"' -ForegroundColor White

Write-Host "`n📈 Grafana Dashboard:" -ForegroundColor Cyan
Write-Host "  http://localhost:3000" -ForegroundColor White
Write-Host "  (Import the dashboard JSON if not already configured)" -ForegroundColor Gray

Write-Host "`n🐳 Docker containers (if using Docker):" -ForegroundColor Cyan
Write-Host '  docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | Select-String "rate"' -ForegroundColor White

Write-Host "`n📝 Logs can be checked with:" -ForegroundColor Cyan
Write-Host '  # Data Plane logs' -ForegroundColor White
Write-Host '  docker logs -f rate-limiter-data-plane --tail 50' -ForegroundColor White
Write-Host '  ' -ForegroundColor White
Write-Host '  # All services logs' -ForegroundColor White
Write-Host '  docker-compose logs --tail=50 --follow' -ForegroundColor White

Write-Host "`n✅ Done!" -ForegroundColor Green

# 可选：自动打开浏览器查看 Grafana
$openGrafana = Read-Host "`nOpen Grafana in browser? (y/n)"
if ($openGrafana -eq 'y') {
    Start-Process "http://localhost:3000"
}

# 可选：自动打开 Prometheus
$openPrometheus = Read-Host "Open Prometheus in browser? (y/n)"
if ($openPrometheus -eq 'y') {
    Start-Process "http://localhost:9090"
}