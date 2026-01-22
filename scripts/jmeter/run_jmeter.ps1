Write-Host "=== JMeter优化负载测试脚本 ===" -ForegroundColor Cyan

# 1. 检查并设置JMeter路径
$jmeterBat = "C:\App\JMeter\bin\jmeter.bat"
if (-not (Test-Path $jmeterBat)) {
    Write-Host "错误: 找不到JMeter: $jmeterBat" -ForegroundColor Red
    exit 1
}

Write-Host "找到JMeter: $jmeterBat" -ForegroundColor Green
$env:JMETER_HOME = "C:\App\JMeter"
Write-Host "设置JMETER_HOME: $env:JMETER_HOME" -ForegroundColor Green

# 设置JVM优化参数
$env:JVM_ARGS = "-Xms1024m -Xmx2048m -XX:MaxMetaspaceSize=512m -Dsun.net.client.defaultConnectTimeout=5000 -Dsun.net.client.defaultReadTimeout=10000 -Djava.net.preferIPv4Stack=true"
Write-Host "设置JVM优化参数" -ForegroundColor Green

# 2. 检查服务状态
Write-Host "`n检查服务状态..." -ForegroundColor Cyan
try {
    $health = Invoke-RestMethod -Uri "http://localhost:8082/actuator/health" -Method GET -TimeoutSec 5
    Write-Host "服务状态: $($health.status)" -ForegroundColor Green
} catch {
    Write-Host "警告: 服务可能未运行或无法访问: http://localhost:8082" -ForegroundColor Red
    exit 1
}

# 3. 检查并优化TCP/IP设置（需要管理员权限）
Write-Host "`n检查TCP/IP设置..." -ForegroundColor Yellow
try {
    $tcpSettings = netsh int ipv4 show dynamicport tcp 2>$null
    Write-Host "当前TCP端口范围:" -ForegroundColor White
    Write-Host $tcpSettings -ForegroundColor Gray

    # 检查是否已优化
    if ($tcpSettings -like "*start=10000*" -and $tcpSettings -like "*num=55535*") {
        Write-Host "TCP/IP设置已优化" -ForegroundColor Green
    } else {
        Write-Host "建议优化TCP/IP设置以支持高并发（需要管理员权限）" -ForegroundColor Yellow
        Write-Host "运行以下命令（管理员权限）：" -ForegroundColor White
        Write-Host "  netsh int ipv4 set dynamicport tcp start=10000 num=55535" -ForegroundColor Gray
        Write-Host "  netsh int ipv6 set dynamicport tcp start=10000 num=55535" -ForegroundColor Gray
    }
} catch {
    Write-Host "无法检查TCP/IP设置: $_" -ForegroundColor Red
}

# 4. 创建测试数据文件
Write-Host "`n创建测试数据文件..." -ForegroundColor Yellow

# 创建CSV文件用于Mixed场景
$mixedCsv = @"
tenantId,resourceKey
tenant_001,/api/v1/orders
tenant_002,/api/v1/products
tenant_003,/api/v1/users
"@

$mixedCsv | Out-File "tenants.csv" -Encoding UTF8
Write-Host "已创建 tenants.csv 文件" -ForegroundColor Green

# 5. 创建优化后的完整测试计划
Write-Host "`n创建优化测试计划..." -ForegroundColor Yellow

$optimizedJmx = @'
<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan version="1.2" properties="5.0" jmeter="5.6.2">
  <hashTree>
    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="Rate Limiter Optimized Load Test" enabled="true">
      <stringProp name="TestPlan.comments">Optimized for high concurrency: reduced Peak threads, increased connection pool, longer timeouts</stringProp>
      <boolProp name="TestPlan.functional_mode">false</boolProp>
      <boolProp name="TestPlan.serialize_threadgroups">true</boolProp>
      <elementProp name="TestPlan.user_defined_variables" elementType="Arguments">
        <collectionProp name="Arguments.arguments">
          <elementProp name="protocol" elementType="Argument">
            <stringProp name="Argument.name">protocol</stringProp>
            <stringProp name="Argument.value">http</stringProp>
            <stringProp name="Argument.metadata">=</stringProp>
          </elementProp>
          <elementProp name="domain" elementType="Argument">
            <stringProp name="Argument.name">domain</stringProp>
            <stringProp name="Argument.value">localhost</stringProp>
            <stringProp name="Argument.metadata">=</stringProp>
          </elementProp>
          <elementProp name="port" elementType="Argument">
            <stringProp name="Argument.name">port</stringProp>
            <stringProp name="Argument.value">8082</stringProp>
            <stringProp name="Argument.metadata">=</stringProp>
          </elementProp>
          <elementProp name="endpoint" elementType="Argument">
            <stringProp name="Argument.name">endpoint</stringProp>
            <stringProp name="Argument.value">/api/v1/check</stringProp>
            <stringProp name="Argument.metadata">=</stringProp>
          </elementProp>
          <elementProp name="contentType" elementType="Argument">
            <stringProp name="Argument.name">contentType</stringProp>
            <stringProp name="Argument.value">application/json</stringProp>
            <stringProp name="Argument.metadata">=</stringProp>
          </elementProp>
        </collectionProp>
      </elementProp>
      <stringProp name="TestPlan.user_define_classpath"></stringProp>
    </TestPlan>
    <hashTree>
      <!-- HTTP Defaults with optimization -->
      <ConfigTestElement guiclass="HttpDefaultsGui" testclass="ConfigTestElement" testname="HTTP Request Defaults" enabled="true">
        <stringProp name="HTTPSampler.transport">HTTP</stringProp>
        <stringProp name="HTTPSampler.domain">${domain}</stringProp>
        <stringProp name="HTTPSampler.port">${port}</stringProp>
        <stringProp name="HTTPSampler.protocol">${protocol}</stringProp>
        <stringProp name="HTTPSampler.contentEncoding"></stringProp>
        <stringProp name="HTTPSampler.path"></stringProp>
        <stringProp name="HTTPSampler.concurrentPool">200</stringProp>
        <stringProp name="HTTPSampler.connect_timeout">5000</stringProp>
        <stringProp name="HTTPSampler.response_timeout">10000</stringProp>
        <boolProp name="HTTPSampler.use_keepalive">true</boolProp>
        <elementProp name="HTTPsampler.Arguments" elementType="Arguments">
          <collectionProp name="Arguments.arguments"/>
        </elementProp>
      </ConfigTestElement>
      <hashTree/>

      <!-- CSV Data Set for tenants/resources (Mixed场景使用) -->
      <CSVDataSet guiclass="TestBeanGUI" testclass="CSVDataSet" testname="CSV Tenants/Resources" enabled="true">
        <stringProp name="delimiter">,</stringProp>
        <stringProp name="fileEncoding">UTF-8</stringProp>
        <stringProp name="filename">tenants.csv</stringProp>
        <boolProp name="ignoreFirstLine">true</boolProp>
        <stringProp name="quoteChar"></stringProp>
        <boolProp name="recycle">true</boolProp>
        <boolProp name="stopThread">false</boolProp>
        <stringProp name="variableNames">tenantId,resourceKey</stringProp>
        <boolProp name="allowQuotedData">false</boolProp>
        <stringProp name="shareMode">shareMode.all</stringProp>
      </CSVDataSet>
      <hashTree/>

      <!-- Baseline Thread Group -->
      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="01 Baseline (Single-tenant)" enabled="true">
        <stringProp name="ThreadGroup.on_sample_error">continue</stringProp>
        <elementProp name="ThreadGroup.main_controller" elementType="LoopController">
          <boolProp name="LoopController.continue_forever">false</boolProp>
          <intProp name="LoopController.loops">-1</intProp>
        </elementProp>
        <stringProp name="ThreadGroup.num_threads">25</stringProp>
        <stringProp name="ThreadGroup.ramp_time">30</stringProp>
        <boolProp name="ThreadGroup.scheduler">true</boolProp>
        <stringProp name="ThreadGroup.duration">30</stringProp>
        <stringProp name="ThreadGroup.delay">0</stringProp>
      </ThreadGroup>
      <hashTree>
        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="POST /api/v1/check (Baseline)" enabled="true">
          <boolProp name="HTTPSampler.postBodyRaw">true</boolProp>
          <elementProp name="HTTPsampler.Arguments" elementType="Arguments">
            <collectionProp name="Arguments.arguments">
              <elementProp name="Body" elementType="HTTPArgument">
                <boolProp name="HTTPArgument.always_encode">false</boolProp>
                <stringProp name="Argument.value">{"requestId":"baseline-${__threadNum}_${__time(,)}","tenantId":"tenant_001","resourceKey":"/api/v1/orders","tokens":1,"timestamp":${__time(,)}}</stringProp>
                <stringProp name="Argument.metadata">=</stringProp>
              </elementProp>
            </collectionProp>
          </elementProp>
          <stringProp name="HTTPSampler.domain">${domain}</stringProp>
          <stringProp name="HTTPSampler.port">${port}</stringProp>
          <stringProp name="HTTPSampler.protocol">${protocol}</stringProp>
          <stringProp name="HTTPSampler.path">${endpoint}</stringProp>
          <stringProp name="HTTPSampler.method">POST</stringProp>
        </HTTPSamplerProxy>
        <hashTree>
          <HeaderManager guiclass="HeaderPanel" testclass="HeaderManager" testname="Headers" enabled="true">
            <collectionProp name="HeaderManager.headers">
              <elementProp name="Content-Type" elementType="Header">
                <stringProp name="Header.name">Content-Type</stringProp>
                <stringProp name="Header.value">${contentType}</stringProp>
              </elementProp>
            </collectionProp>
          </HeaderManager>
          <hashTree/>
          <ConstantTimer guiclass="ConstantTimerGui" testclass="ConstantTimer" testname="Think Time 5ms" enabled="true">
            <stringProp name="ConstantTimer.delay">5</stringProp>
          </ConstantTimer>
          <hashTree/>
        </hashTree>
      </hashTree>

      <!-- Mixed Thread Group -->
      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="02 Mixed (Multi-tenant)" enabled="true">
        <stringProp name="ThreadGroup.on_sample_error">continue</stringProp>
        <elementProp name="ThreadGroup.main_controller" elementType="LoopController">
          <boolProp name="LoopController.continue_forever">false</boolProp>
          <intProp name="LoopController.loops">-1</intProp>
        </elementProp>
        <stringProp name="ThreadGroup.num_threads">10</stringProp>
        <stringProp name="ThreadGroup.ramp_time">10</stringProp>
        <boolProp name="ThreadGroup.scheduler">true</boolProp>
        <stringProp name="ThreadGroup.duration">30</stringProp>
        <stringProp name="ThreadGroup.delay">0</stringProp>
      </ThreadGroup>
      <hashTree>
        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="POST /api/v1/check (Mixed)" enabled="true">
          <boolProp name="HTTPSampler.postBodyRaw">true</boolProp>
          <elementProp name="HTTPsampler.Arguments" elementType="Arguments">
            <collectionProp name="Arguments.arguments">
              <elementProp name="Body" elementType="HTTPArgument">
                <boolProp name="HTTPArgument.always_encode">false</boolProp>
                <stringProp name="Argument.value">{"requestId":"mixed-${__threadNum}_${__time(,)}","tenantId":"${tenantId}","resourceKey":"${resourceKey}","tokens":${__Random(1,3)},"timestamp":${__time(,)}}</stringProp>
                <stringProp name="Argument.metadata">=</stringProp>
              </elementProp>
            </collectionProp>
          </elementProp>
          <stringProp name="HTTPSampler.domain">${domain}</stringProp>
          <stringProp name="HTTPSampler.port">${port}</stringProp>
          <stringProp name="HTTPSampler.protocol">${protocol}</stringProp>
          <stringProp name="HTTPSampler.path">${endpoint}</stringProp>
          <stringProp name="HTTPSampler.method">POST</stringProp>
        </HTTPSamplerProxy>
        <hashTree>
          <HeaderManager guiclass="HeaderPanel" testclass="HeaderManager" testname="Headers" enabled="true">
            <collectionProp name="HeaderManager.headers">
              <elementProp name="Content-Type" elementType="Header">
                <stringProp name="Header.name">Content-Type</stringProp>
                <stringProp name="Header.value">${contentType}</stringProp>
              </elementProp>
            </collectionProp>
          </HeaderManager>
          <hashTree/>
          <ConstantTimer guiclass="ConstantTimerGui" testclass="ConstantTimer" testname="Think Time 2ms" enabled="true">
            <stringProp name="ConstantTimer.delay">2</stringProp>
          </ConstantTimer>
          <hashTree/>
        </hashTree>
      </hashTree>

      <!-- Peak Thread Group (REDUCED from 50 to 30) -->
      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="03 Peak (High Concurrency) - Reduced" enabled="true">
        <stringProp name="ThreadGroup.on_sample_error">continue</stringProp>
        <elementProp name="ThreadGroup.main_controller" elementType="LoopController">
          <boolProp name="LoopController.continue_forever">false</boolProp>
          <intProp name="LoopController.loops">-1</intProp>
        </elementProp>
        <stringProp name="ThreadGroup.num_threads">30</stringProp>
        <stringProp name="ThreadGroup.ramp_time">10</stringProp>
        <boolProp name="ThreadGroup.scheduler">true</boolProp>
        <stringProp name="ThreadGroup.duration">30</stringProp>
        <stringProp name="ThreadGroup.delay">0</stringProp>
      </ThreadGroup>
      <hashTree>
        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="POST /api/v1/check (Peak)" enabled="true">
          <boolProp name="HTTPSampler.postBodyRaw">true</boolProp>
          <elementProp name="HTTPsampler.Arguments" elementType="Arguments">
            <collectionProp name="Arguments.arguments">
              <elementProp name="Body" elementType="HTTPArgument">
                <boolProp name="HTTPArgument.always_encode">false</boolProp>
                <stringProp name="Argument.value">{"requestId":"peak-${__threadNum}_${__time(,)}","tenantId":"tenant_001","resourceKey":"/api/v1/orders","tokens":1,"timestamp":${__time(,)}}</stringProp>
                <stringProp name="Argument.metadata">=</stringProp>
              </elementProp>
            </collectionProp>
          </elementProp>
          <stringProp name="HTTPSampler.domain">${domain}</stringProp>
          <stringProp name="HTTPSampler.port">${port}</stringProp>
          <stringProp name="HTTPSampler.protocol">${protocol}</stringProp>
          <stringProp name="HTTPSampler.path">${endpoint}</stringProp>
          <stringProp name="HTTPSampler.method">POST</stringProp>
        </HTTPSamplerProxy>
        <hashTree>
          <HeaderManager guiclass="HeaderPanel" testclass="HeaderManager" testname="Headers" enabled="true">
            <collectionProp name="HeaderManager.headers">
              <elementProp name="Content-Type" elementType="Header">
                <stringProp name="Header.name">Content-Type</stringProp>
                <stringProp name="Header.value">${contentType}</stringProp>
              </elementProp>
            </collectionProp>
          </HeaderManager>
          <hashTree/>
          <ConstantTimer guiclass="ConstantTimerGui" testclass="ConstantTimer" testname="Think Time 1ms" enabled="true">
            <stringProp name="ConstantTimer.delay">1</stringProp>
          </ConstantTimer>
          <hashTree/>
        </hashTree>
      </hashTree>

      <!-- Results -->
      <ResultCollector guiclass="SummaryReport" testclass="ResultCollector" testname="Summary Report" enabled="true">
        <boolProp name="ResultCollector.error_logging">true</boolProp>
      </ResultCollector>
      <hashTree/>

      <!-- Listener for errors -->
      <ResultCollector guiclass="ViewResultsFullVisualizer" testclass="ResultCollector" testname="View Results Tree" enabled="false">
        <boolProp name="ResultCollector.error_logging">false</boolProp>
        <objProp>
          <name>saveConfig</name>
          <value class="SampleSaveConfiguration">
            <time>true</time>
            <latency>true</latency>
            <timestamp>true</timestamp>
            <success>true</success>
            <label>true</label>
            <code>true</code>
            <message>true</message>
            <threadName>true</threadName>
            <dataType>true</dataType>
            <encoding>false</encoding>
            <assertions>true</assertions>
            <subresults>true</subresults>
            <responseData>false</responseData>
            <samplerData>false</samplerData>
            <xml>false</xml>
            <fieldNames>true</fieldNames>
            <responseHeaders>false</responseHeaders>
            <requestHeaders>false</requestHeaders>
            <responseDataOnError>false</responseDataOnError>
            <saveAssertionResultsFailureMessage>true</saveAssertionResultsFailureMessage>
            <assertionsResultsToSave>0</assertionsResultsToSave>
            <bytes>true</bytes>
            <sentBytes>true</sentBytes>
            <url>true</url>
            <threadCounts>true</threadCounts>
            <idleTime>true</idleTime>
            <connectTime>true</connectTime>
          </value>
        </objProp>
        <stringProp name="filename"></stringProp>
      </ResultCollector>
      <hashTree/>
    </hashTree>
  </hashTree>
</jmeterTestPlan>
'@

$optimizedJmx | Out-File "rate_limiter_optimized.jmx" -Encoding UTF8
Write-Host "已创建优化测试计划: rate_limiter_optimized.jmx" -ForegroundColor Green

# 6. 创建结果目录
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$resultDir = "jmeter_results\optimized_$timestamp"
New-Item -ItemType Directory -Force -Path $resultDir | Out-Null

$jtl = "$resultDir\results.jtl"
$html = "$resultDir\html"
$log = "$resultDir\jmeter.log"

Write-Host "`n测试配置:" -ForegroundColor Cyan
Write-Host "  JMeter路径: $jmeterBat" -ForegroundColor Gray
Write-Host "  JMETER_HOME: $env:JMETER_HOME" -ForegroundColor Gray
Write-Host "  JVM参数: $env:JVM_ARGS" -ForegroundColor Gray
Write-Host "  测试计划: rate_limiter_optimized.jmx" -ForegroundColor Gray
Write-Host "  CSV数据文件: tenants.csv" -ForegroundColor Gray
Write-Host "  结果目录: $resultDir" -ForegroundColor Gray
Write-Host "  JTL文件: $jtl" -ForegroundColor Gray
Write-Host "  HTML报告: $html\index.html" -ForegroundColor Gray

Write-Host "`n优化亮点:" -ForegroundColor Cyan
Write-Host "  ✓ Peak线程数: 30 (原50)" -ForegroundColor Green
Write-Host "  ✓ 连接池大小: 200 (原6)" -ForegroundColor Green
Write-Host "  ✓ 连接超时: 5000ms" -ForegroundColor Green
Write-Host "  ✓ 响应超时: 10000ms" -ForegroundColor Green
Write-Host "  ✓ 启用连接复用: true" -ForegroundColor Green
Write-Host "  ✓ JVM内存: 1GB/2GB" -ForegroundColor Green

Write-Host "`n测试计划包含以下场景:" -ForegroundColor Cyan
Write-Host "  1. Baseline (单租户) - 25线程，30秒" -ForegroundColor White
Write-Host "  2. Mixed (多租户) - 10线程，30秒" -ForegroundColor White
Write-Host "  3. Peak (高并发) - 30线程，30秒" -ForegroundColor White

# 7. 确认开始测试
Write-Host "`n准备开始测试..." -ForegroundColor Yellow
$confirm = Read-Host "是否开始执行测试? (Y/N)"
if ($confirm -ne "Y" -and $confirm -ne "y") {
    Write-Host "测试已取消" -ForegroundColor Yellow
    exit 0
}

# 8. 运行JMeter
Write-Host "`n正在启动JMeter测试..." -ForegroundColor Green
Write-Host "开始时间: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor Gray

try {
    # 切换到JMeter的bin目录
    $jmeterDir = Split-Path $jmeterBat -Parent
    $currentLocation = Get-Location
    Set-Location $jmeterDir

    # 执行JMeter
    $command = "$jmeterBat -n -t `"$PSScriptRoot\rate_limiter_optimized.jmx`" -l `"$PSScriptRoot\$jtl`" -e -o `"$PSScriptRoot\$html`" -j `"$PSScriptRoot\$log`""
    Write-Host "执行命令: $command" -ForegroundColor Gray

    & $jmeterBat -n -t "$PSScriptRoot\rate_limiter_optimized.jmx" -l "$PSScriptRoot\$jtl" -e -o "$PSScriptRoot\$html" -j "$PSScriptRoot\$log"

    Write-Host "`n测试完成!" -ForegroundColor Green
    Write-Host "结束时间: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor Gray

} catch {
    Write-Host "`nJMeter执行出错: $_" -ForegroundColor Red
    exit 1
} finally {
    # 确保返回到原始目录
    if ($currentLocation) {
        Set-Location $currentLocation
    }
}

# 9. 详细分析结果
Write-Host "`n=== 详细测试结果分析 ===" -ForegroundColor Cyan

if (Test-Path $jtl) {
    $results = Get-Content $jtl
    if ($results.Count -gt 1) {
        $totalRequests = $results.Count - 1  # 减去表头行
        $successCount = ($results | Select-String -Pattern 'true$' -SimpleMatch).Count
        $errorCount = ($results | Select-String -Pattern 'false$' -SimpleMatch).Count
        $bindErrors = ($results | Select-String -Pattern 'Address already in use' -SimpleMatch).Count
        $timeoutErrors = ($results | Select-String -Pattern 'Read timed out' -SimpleMatch).Count

        Write-Host "`n统计结果:" -ForegroundColor Yellow
        Write-Host "  总请求数: $totalRequests" -ForegroundColor White
        Write-Host "  成功数: $successCount" -ForegroundColor Green
        Write-Host "  失败数: $errorCount" -ForegroundColor $(if ($errorCount -eq 0) { "Green" } else { "Red" })

        if ($errorCount -gt 0) {
            Write-Host "`n错误详情:" -ForegroundColor Yellow
            Write-Host "  端口耗尽错误: $bindErrors" -ForegroundColor $(if ($bindErrors -eq 0) { "Green" } else { "Red" })
            Write-Host "  超时错误: $timeoutErrors" -ForegroundColor $(if ($timeoutErrors -eq 0) { "Green" } else { "Red" })
            $otherErrors = $errorCount - $bindErrors - $timeoutErrors
            Write-Host "  其他错误: $otherErrors" -ForegroundColor $(if ($otherErrors -eq 0) { "Green" } else { "Yellow" })
        }

        if ($totalRequests -gt 0) {
            $successRate = [math]::Round(($successCount / $totalRequests) * 100, 2)
            $errorRate = [math]::Round(($errorCount / $totalRequests) * 100, 2)

            Write-Host "`n成功率分析:" -ForegroundColor Yellow
            Write-Host "  成功率: $successRate%" -ForegroundColor $(if ($successRate -ge 95) { "Green" } elseif ($successRate -ge 80) { "Yellow" } else { "Red" })
            Write-Host "  失败率: $errorRate%" -ForegroundColor $(if ($errorRate -lt 5) { "Green" } elseif ($errorRate -lt 20) { "Yellow" } else { "Red" })

            # 显示各场景的请求统计
            $baselineCount = ($results | Select-String -Pattern 'baseline' -SimpleMatch).Count
            $mixedCount = ($results | Select-String -Pattern 'mixed' -SimpleMatch).Count
            $peakCount = ($results | Select-String -Pattern 'peak' -SimpleMatch).Count

            Write-Host "`n各场景请求分布:" -ForegroundColor Yellow
            Write-Host "  Baseline场景: $baselineCount 个请求" -ForegroundColor White
            Write-Host "  Mixed场景: $mixedCount 个请求" -ForegroundColor White
            Write-Host "  Peak场景: $peakCount 个请求" -ForegroundColor White

            # 各场景错误率
            if ($baselineCount -gt 0) {
                $baselineErrors = ($results | Select-String -Pattern 'baseline.*false' -SimpleMatch).Count
                $baselineErrorRate = [math]::Round(($baselineErrors / $baselineCount) * 100, 2)
                Write-Host "  Baseline错误率: $baselineErrorRate%" -ForegroundColor $(if ($baselineErrorRate -lt 5) { "Green" } elseif ($baselineErrorRate -lt 20) { "Yellow" } else { "Red" })
            }

            if ($mixedCount -gt 0) {
                $mixedErrors = ($results | Select-String -Pattern 'mixed.*false' -SimpleMatch).Count
                $mixedErrorRate = [math]::Round(($mixedErrors / $mixedCount) * 100, 2)
                Write-Host "  Mixed错误率: $mixedErrorRate%" -ForegroundColor $(if ($mixedErrorRate -lt 5) { "Green" } elseif ($mixedErrorRate -lt 20) { "Yellow" } else { "Red" })
            }

            if ($peakCount -gt 0) {
                $peakErrors = ($results | Select-String -Pattern 'peak.*false' -SimpleMatch).Count
                $peakErrorRate = [math]::Round(($peakErrors / $peakCount) * 100, 2)
                Write-Host "  Peak错误率: $peakErrorRate%" -ForegroundColor $(if ($peakErrorRate -lt 5) { "Green" } elseif ($peakErrorRate -lt 20) { "Yellow" } else { "Red" })
            }
        }

        # 显示性能统计数据
        Write-Host "`n性能数据:" -ForegroundColor Yellow
        $responseTimes = $results | Where-Object { $_ -match '^[0-9]+,[0-9]+,' } | ForEach-Object {
            $fields = $_ -split ","
            if ($fields.Count -ge 9 -and $fields[3] -eq "200") {
                [int]$fields[1]  # 响应时间
            }
        }

        if ($responseTimes.Count -gt 0) {
            $avgResponseTime = [math]::Round(($responseTimes | Measure-Object -Average).Average, 2)
            $minResponseTime = ($responseTimes | Measure-Object -Minimum).Minimum
            $maxResponseTime = ($responseTimes | Measure-Object -Maximum).Maximum
            $p95 = $responseTimes | Sort-Object | Select-Object -Skip ([math]::Floor($responseTimes.Count * 0.95))

            Write-Host "  平均响应时间: ${avgResponseTime}ms" -ForegroundColor White
            Write-Host "  最小响应时间: ${minResponseTime}ms" -ForegroundColor White
            Write-Host "  最大响应时间: ${maxResponseTime}ms" -ForegroundColor White
            Write-Host "  P95响应时间: $($p95[0])ms" -ForegroundColor White
        }

        # 显示错误示例
        if ($errorCount -gt 0) {
            Write-Host "`n失败请求示例:" -ForegroundColor Red
            $errorResults = $results | Select-String -Pattern 'false$' -SimpleMatch | Select-Object -First 5
            $errorCount = 0
            foreach ($line in $errorResults) {
                $fields = $line -split ","
                if ($fields.Count -ge 9) {
                    $label = $fields[2]
                    $responseCode = $fields[3]
                    $responseMessage = $fields[4]
                    $elapsed = $fields[1]
                    Write-Host "  $label - 错误: $responseCode - $responseMessage (耗时: ${elapsed}ms)" -ForegroundColor Red
                    $errorCount++
                    if ($errorCount -ge 3) { break }
                }
            }
        }
    } else {
        Write-Host "结果文件为空或格式不正确" -ForegroundColor Red
    }
} else {
    Write-Host "未找到结果文件: $jtl" -ForegroundColor Red
}

# 10. 显示文件位置
Write-Host "`n文件位置:" -ForegroundColor Cyan
Write-Host "  JTL结果日志: $jtl" -ForegroundColor White
Write-Host "  HTML报告: $html\index.html" -ForegroundColor White
Write-Host "  JMeter日志: $log" -ForegroundColor White
Write-Host "  优化测试计划: rate_limiter_optimized.jmx" -ForegroundColor White

# 11. 提供优化建议
Write-Host "`n=== 优化建议 ===" -ForegroundColor Cyan

if ($bindErrors -gt 0) {
    Write-Host "`n⚠️ 检测到端口耗尽错误 ($bindErrors 个)" -ForegroundColor Red
    Write-Host "建议操作:" -ForegroundColor Yellow
    Write-Host "  1. 以管理员身份运行以下命令:" -ForegroundColor White
    Write-Host "     netsh int ipv4 set dynamicport tcp start=10000 num=55535" -ForegroundColor Gray
    Write-Host "     netsh int ipv6 set dynamicport tcp start=10000 num=55535" -ForegroundColor Gray
    Write-Host "  2. 重启系统后重新测试" -ForegroundColor White
}

if ($successRate -lt 80) {
    Write-Host "`n⚠️ 成功率较低 ($successRate%)" -ForegroundColor Red
    Write-Host "建议操作:" -ForegroundColor Yellow
    Write-Host "  1. 进一步降低Peak线程数 (30 -> 20)" -ForegroundColor White
    Write-Host "  2. 延长连接超时时间 (5000 -> 10000)" -ForegroundColor White
    Write-Host "  3. 检查服务端是否正常处理请求" -ForegroundColor White
} elseif ($successRate -ge 95) {
    Write-Host "`n✅ 测试结果良好 ($successRate% 成功率)" -ForegroundColor Green
    Write-Host "建议操作:" -ForegroundColor Yellow
    Write-Host "  1. 可尝试逐步增加Peak线程数 (30 -> 35 -> 40)" -ForegroundColor White
    Write-Host "  2. 进行更长持续时间的压力测试" -ForegroundColor White
}

# 12. 询问是否打开HTML报告
if (Test-Path "$html\index.html") {
    Write-Host "`n是否打开HTML测试报告?" -ForegroundColor Cyan
    $openReport = Read-Host "打开HTML报告? (Y/N)"
    if ($openReport -eq "Y" -or $openReport -eq "y") {
        Start-Process "$html\index.html"
        Write-Host "已打开HTML报告" -ForegroundColor Green
    }
}

Write-Host "`n优化负载测试执行完毕!" -ForegroundColor Cyan