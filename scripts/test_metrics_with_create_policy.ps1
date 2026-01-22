# create_policy_and_test.ps1

Write-Host "Creating rate limiting policies..." -ForegroundColor Cyan

# 1. 创建策略
$policy1 = @{
    tenantId = "tenant_001"
    resourceKey = "/api/test"
    policyType = "token_bucket"
    windowSeconds = 1
    capacity = 100
    refillRate = 10
    burstCapacity = 20
    priority = 1
    enabled = $true
    version = "1.0"
    metadata = @{}
    description = "Test policy for /api/test"
} | ConvertTo-Json

$policy2 = @{
    tenantId = "tenant_001"
    resourceKey = "/api/v1/orders"
    policyType = "token_bucket"
    windowSeconds = 1
    capacity = 200
    refillRate = 20
    burstCapacity = 40
    priority = 1
    enabled = $true
    version = "1.0"
    metadata = @{}
    description = "Test policy for /api/v1/orders"
} | ConvertTo-Json

Write-Host "Creating policy for /api/test..." -ForegroundColor Yellow
try {
    $response1 = Invoke-RestMethod -Uri "http://localhost:8081/api/v1/policies" `
        -Method Post `
        -ContentType "application/json" `
        -Body $policy1

    Write-Host "Policy created: $($response1.data.id)" -ForegroundColor Green
} catch {
    Write-Host "Failed to create policy: $_" -ForegroundColor Red
}

Write-Host "Creating policy for /api/v1/orders..." -ForegroundColor Yellow
try {
    $response2 = Invoke-RestMethod -Uri "http://localhost:8081/api/v1/policies" `
        -Method Post `
        -ContentType "application/json" `
        -Body $policy2

    Write-Host "Policy created: $($response2.data.id)" -ForegroundColor Green
} catch {
    Write-Host "Failed to create policy: $_" -ForegroundColor Red
}

# 2. 等待策略同步到Data Plane
Write-Host "`nWaiting for policies to sync to Data Plane..." -ForegroundColor Yellow
Start-Sleep -Seconds 5

# 3. 发送测试请求
Write-Host "`nSending test requests..." -ForegroundColor Cyan

for ($i = 1; $i -le 5; $i++) {
    $requestBody = @{
        requestId = "policy-test-$i"
        tenantId = "tenant_001"
        resourceKey = "/api/test"
        tokens = 1
        timestamp = "$([int][double]::Parse((Get-Date -UFormat %s)))000"
    } | ConvertTo-Json

    Write-Host ("Request #" + $i + ": " + $requestBody) -ForegroundColor Gray

    try {
        $response = Invoke-RestMethod -Uri "http://localhost:8082/api/v1/check" `
            -Method Post `
            -ContentType "application/json" `
            -Body $requestBody

        $allowed = $response.data.allowed
        $reason = $response.data.reason
        $remaining = $response.data.remaining

        if ($allowed) {
            Write-Host ("  ✅ ALLOWED - Remaining: " + $remaining) -ForegroundColor Green
        } else {
            Write-Host ("  ❌ DENIED - Reason: " + $reason + " - Remaining: " + $remaining) -ForegroundColor Red
        }

    } catch {
        Write-Host ("  ❌ Request failed: " + $_.Exception.Message) -ForegroundColor Red
    }

    Start-Sleep -Milliseconds 500
}

# 4. 测试另一个资源
Write-Host "`nTesting another resource (/api/v1/orders)..." -ForegroundColor Cyan

for ($i = 1; $i -le 3; $i++) {
    $requestBody = @{
        requestId = "policy-test-orders-$i"
        tenantId = "tenant_001"
        resourceKey = "/api/v1/orders"
        tokens = 1
        timestamp = "$([int][double]::Parse((Get-Date -UFormat %s)))000"
    } | ConvertTo-Json

    Write-Host ("Request #" + $i + ": " + $requestBody) -ForegroundColor Gray

    try {
        $response = Invoke-RestMethod -Uri "http://localhost:8082/api/v1/check" `
            -Method Post `
            -ContentType "application/json" `
            -Body $requestBody

        $allowed = $response.data.allowed
        $reason = $response.data.reason
        $remaining = $response.data.remaining

        if ($allowed) {
            Write-Host ("  ✅ ALLOWED - Remaining: " + $remaining) -ForegroundColor Green
        } else {
            Write-Host ("  ❌ DENIED - Reason: " + $reason + " - Remaining: " + $remaining) -ForegroundColor Red
        }

    } catch {
        Write-Host ("  ❌ Request failed: " + $_.Exception.Message) -ForegroundColor Red
    }

    Start-Sleep -Milliseconds 500
}

Write-Host "`nDone!" -ForegroundColor Green