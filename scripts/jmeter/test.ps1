# test.ps1 - 修复版
Write-Host "快速API测试..." -ForegroundColor Cyan

# 0. 设置 JMETER_HOME 环境变量
$env:JMETER_HOME = "C:\App\JMeter"
Write-Host "设置 JMETER_HOME: $env:JMETER_HOME" -ForegroundColor Green

# 1. 先测试基础连接
try {
    Write-Host "`n1. 测试服务健康检查..." -ForegroundColor Yellow
    $health = Invoke-RestMethod -Uri "http://localhost:8082/actuator/health" -Method GET
    Write-Host "   ✓ 服务健康状态: $($health.status)" -ForegroundColor Green
} catch {
    Write-Host "   ✗ 服务未运行或端口错误" -ForegroundColor Red
    Write-Host "   错误详情: $_" -ForegroundColor Red
    exit 1
}

# 2. 直接测试API端点
Write-Host "`n2. 直接测试API端点..." -ForegroundColor Yellow
$testBody = @{
    requestId = "test-ps"
    tenantId = "tenant_001"
    resourceKey = "/api/v1/orders"
    tokens = 1
    timestamp = [int64]((Get-Date).ToUniversalTime() - (Get-Date "1970-01-01")).TotalMilliseconds
} | ConvertTo-Json

try {
    $response = Invoke-RestMethod -Uri "http://localhost:8082/api/v1/check" -Method POST -Body $testBody -ContentType "application/json"
    Write-Host "   ✓ API测试成功，响应: $($response | ConvertTo-Json -Compress)" -ForegroundColor Green
} catch {
    Write-Host "   ✗ API测试失败" -ForegroundColor Red
    Write-Host "   错误详情: $_" -ForegroundColor Red
    if ($_.Exception.Response) {
        $statusCode = $_.Exception.Response.StatusCode.value__
        $statusDescription = $_.Exception.Response.StatusDescription
        Write-Host "   状态码: $statusCode, 描述: $statusDescription" -ForegroundColor Yellow

        # 尝试读取响应体
        try {
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $reader.BaseStream.Position = 0
            $reader.DiscardBufferedData()
            $errorBody = $reader.ReadToEnd()
            Write-Host "   错误响应体: $errorBody" -ForegroundColor Yellow
        } catch {
            Write-Host "   无法读取错误响应体" -ForegroundColor Yellow
        }
    }
    exit 1
}

# 3. 检查JMeter安装
Write-Host "`n3. 检查JMeter安装..." -ForegroundColor Yellow
$jmeterBat = "C:\App\JMeter\bin\jmeter.bat"
$jmeterBatAlt = "C:\App\JMeter\bin\jmeter"

if (Test-Path $jmeterBat) {
    $jmeterExe = $jmeterBat
    Write-Host "   ✓ 找到JMeter: $jmeterExe" -ForegroundColor Green
} elseif (Test-Path $jmeterBatAlt) {
    $jmeterExe = $jmeterBatAlt
    Write-Host "   ✓ 找到JMeter: $jmeterExe" -ForegroundColor Green
} else {
    Write-Host "   ✗ 找不到JMeter" -ForegroundColor Red
    Write-Host "     请检查JMeter是否安装在 C:\App\JMeter" -ForegroundColor Yellow
    exit 1
}

# 4. 创建一个更简单的JMX测试文件
Write-Host "`n4. 创建简单JMeter测试..." -ForegroundColor Yellow

# 创建CSV文件用于参数化
$csvContent = @"
requestId,tenantId,resourceKey,tokens,timestamp
test-1,tenant_001,/api/v1/orders,1,1769096192191
test-2,tenant_001,/api/v1/orders,1,1769096192192
test-3,tenant_001,/api/v1/orders,1,1769096192193
test-4,tenant_001,/api/v1/orders,1,1769096192194
test-5,tenant_001,/api/v1/orders,1,1769096192195
test-6,tenant_001,/api/v1/orders,1,1769096192196
test-7,tenant_001,/api/v1/orders,1,1769096192197
test-8,tenant_001,/api/v1/orders,1,1769096192198
test-9,tenant_001,/api/v1/orders,1,1769096192199
test-10,tenant_001,/api/v1/orders,1,1769096192200
"@

$csvContent | Out-File "test_data.csv" -Encoding UTF8
Write-Host "   创建测试数据文件: test_data.csv" -ForegroundColor Green

# 创建简单的JMeter测试计划
$simpleJmx = @'
<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan version="1.2" properties="5.0" jmeter="5.6.2">
  <hashTree>
    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="Simple API Test" enabled="true"/>
    <hashTree>
      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="API Thread Group" enabled="true">
        <stringProp name="ThreadGroup.on_sample_error">continue</stringProp>
        <elementProp name="ThreadGroup.main_controller" elementType="LoopController" guiclass="LoopControlPanel" testclass="LoopController" testname="Loop Controller" enabled="true">
          <boolProp name="LoopController.continue_forever">false</boolProp>
          <intProp name="LoopController.loops">5</intProp>
        </elementProp>
        <intProp name="ThreadGroup.num_threads">2</intProp>
        <intProp name="ThreadGroup.ramp_time">1</intProp>
        <boolProp name="ThreadGroup.scheduler">false</boolProp>
      </ThreadGroup>
      <hashTree>
        <CSVDataSet guiclass="TestBeanGUI" testclass="CSVDataSet" testname="CSV Data Set Config" enabled="true">
          <stringProp name="delimiter">,</stringProp>
          <stringProp name="fileEncoding">UTF-8</stringProp>
          <stringProp name="filename">test_data.csv</stringProp>
          <boolProp name="ignoreFirstLine">true</boolProp>
          <boolProp name="quotedData">false</boolProp>
          <boolProp name="recycle">true</boolProp>
          <boolProp name="shareMode">shareMode.all</boolProp>
          <stringProp name="variableNames">requestId,tenantId,resourceKey,tokens,timestamp</stringProp>
        </CSVDataSet>
        <hashTree/>
        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="API Test" enabled="true">
          <boolProp name="HTTPSampler.postBodyRaw">true</boolProp>
          <elementProp name="HTTPsampler.Arguments" elementType="Arguments">
            <collectionProp name="Arguments.arguments">
              <elementProp name="" elementType="HTTPArgument">
                <boolProp name="HTTPArgument.always_encode">false</boolProp>
                <stringProp name="Argument.value">{&quot;requestId&quot;:&quot;${requestId}&quot;,&quot;tenantId&quot;:&quot;${tenantId}&quot;,&quot;resourceKey&quot;:&quot;${resourceKey}&quot;,&quot;tokens&quot;:${tokens},&quot;timestamp&quot;:${timestamp}}</stringProp>
              </elementProp>
            </collectionProp>
          </elementProp>
          <stringProp name="HTTPSampler.domain">localhost</stringProp>
          <stringProp name="HTTPSampler.port">8082</stringProp>
          <stringProp name="HTTPSampler.protocol">http</stringProp>
          <stringProp name="HTTPSampler.path">/api/v1/check</stringProp>
          <stringProp name="HTTPSampler.method">POST</stringProp>
          <boolProp name="HTTPSampler.use_keepalive">true</boolProp>
          <boolProp name="HTTPSampler.DO_MULTIPART_POST">false</boolProp>
        </HTTPSamplerProxy>
        <hashTree>
          <HeaderManager guiclass="HeaderPanel" testclass="HeaderManager" testname="HTTP Header Manager" enabled="true">
            <collectionProp name="HeaderManager.headers">
              <elementProp name="" elementType="Header">
                <stringProp name="Header.name">Content-Type</stringProp>
                <stringProp name="Header.value">application/json</stringProp>
              </elementProp>
            </collectionProp>
          </HeaderManager>
          <hashTree/>
        </hashTree>
        <ResultCollector guiclass="SummaryReport" testclass="ResultCollector" testname="Summary Report" enabled="true">
          <boolProp name="ResultCollector.error_logging">true</boolProp>
        </ResultCollector>
        <hashTree/>
      </hashTree>
    </hashTree>
  </hashTree>
</jmeterTestPlan>
'@

# 保存测试计划
$simpleJmx | Out-File "simple_test.jmx" -Encoding UTF8
Write-Host "   创建测试计划: simple_test.jmx" -ForegroundColor Green

# 运行JMeter测试
Write-Host "   正在运行测试..." -ForegroundColor Cyan

# 设置工作目录为JMeter的bin目录
$jmeterDir = Split-Path $jmeterExe -Parent
Push-Location $jmeterDir

try {
    # 清理旧的结果文件
    if (Test-Path "$PSScriptRoot\simple_results.jtl") {
        Remove-Item "$PSScriptRoot\simple_results.jtl" -Force
    }

    # 运行JMeter
    Write-Host "   执行命令: $jmeterExe -n -t `"$PSScriptRoot\simple_test.jmx`" -l `"$PSScriptRoot\simple_results.jtl`"" -ForegroundColor Gray
    & $jmeterExe -n -t "$PSScriptRoot\simple_test.jmx" -l "$PSScriptRoot\simple_results.jtl"
    Write-Host "   测试运行完成!" -ForegroundColor Green
} catch {
    Write-Host "   JMeter执行出错: $_" -ForegroundColor Red
} finally {
    Pop-Location
}

# 5. 分析结果
Write-Host "`n5. 测试结果分析..." -ForegroundColor Yellow

if (Test-Path "simple_results.jtl") {
    $results = Get-Content "simple_results.jtl"
    if ($results.Count -gt 1) {
        # 获取所有结果
        $allResults = $results | Select-Object -Skip 1

        Write-Host "   请求结果概览:" -ForegroundColor Cyan
        $successCount = 0
        $errorCount = 0

        foreach ($line in $allResults) {
            $fields = $line -split ","
            if ($fields.Count -ge 9) {
                $timestamp = $fields[0]
                $elapsed = $fields[1]
                $label = $fields[2]
                $responseCode = $fields[3]
                $responseMessage = $fields[4]
                $success = $fields[7]

                if ($success -eq "true") {
                    $successCount++
                    Write-Host "   ✓ 成功: $responseCode ($elapsed ms)" -ForegroundColor Green
                } else {
                    $errorCount++
                    Write-Host "   ✗ 失败: $responseCode - $responseMessage ($elapsed ms)" -ForegroundColor Red
                }
            }
        }

        Write-Host "`n   统计结果:" -ForegroundColor White
        Write-Host "   总请求数: $($allResults.Count)" -ForegroundColor White
        Write-Host "   成功数: $successCount" -ForegroundColor Green
        Write-Host "   失败数: $errorCount" -ForegroundColor $(if ($errorCount -eq 0) { "Green" } else { "Red" })

        if ($allResults.Count -gt 0) {
            $successRate = [math]::Round(($successCount / $allResults.Count) * 100, 2)
            Write-Host "   成功率: $successRate%" -ForegroundColor $(if ($successRate -eq 100) { "Green" } else { "Red" })
        }

        # 显示一些示例请求和响应
        if ($successCount -gt 0) {
            Write-Host "`n   成功请求示例:" -ForegroundColor Cyan
            $successResults = $allResults | Where-Object { $_ -match "true$" } | Select-Object -First 3
            foreach ($line in $successResults) {
                $fields = $line -split ","
                Write-Host "   请求: $($fields[2]), 耗时: $($fields[1]) ms, 状态码: $($fields[3])" -ForegroundColor Gray
            }
        }
    } else {
        Write-Host "   结果文件为空，没有请求被发送" -ForegroundColor Red
    }
} else {
    Write-Host "   未找到结果文件" -ForegroundColor Red
}

Write-Host "`n测试完成!" -ForegroundColor Cyan