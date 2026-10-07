param([ValidateSet('start','discover','snapshot-only')][string]$Mode='start')
$ErrorActionPreference='Stop'
$taskRoot=(Get-Location).Path
$taskOutput=Join-Path $taskRoot 'results/experimental-study-2026-10-07-run-2-scanner-v2'
$allowed=@('DB_URL','DB_USERNAME','DB_PASSWORD','SERVER_PORT','SCANNER_TIMEOUT_MS','SCANNER_SETTLE_MS','SCANNER_READINESS_TIMEOUT_MS','CAPTURE_STORAGE_PATH')
$scannerOverrides=@{}
foreach($line in Get-Content -LiteralPath (Join-Path $taskRoot '.env.local') -Encoding UTF8) {
    if($line -match '^\s*(#|$)'){continue}
    if($line -notmatch '^\s*([A-Z_]+)\s*=(.*)$'){throw 'Invalid local environment format'}
    $name=$matches[1];$value=$matches[2].Trim()
    if($name -notin $allowed){throw 'Unexpected local environment name'}
    if($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {$value=$value.Substring(1,$value.Length-2)}
    [Environment]::SetEnvironmentVariable($name,$value,'Process')
    if($name -like 'SCANNER_*') {
        $taskNumeric=0
        $scannerOverrides[$name]=if([int]::TryParse($value,[ref]$taskNumeric)){$taskNumeric}else{'non-numeric; overridden by approved CLI settings'}
    }
}
foreach($name in @('DB_URL','DB_USERNAME','DB_PASSWORD')) {
    if([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name,'Process'))){throw 'Database configuration incomplete'}
}
$env:SPRING_DATASOURCE_URL=$env:DB_URL
$env:SPRING_DATASOURCE_USERNAME=$env:DB_USERNAME
$env:SPRING_DATASOURCE_PASSWORD=$env:DB_PASSWORD
$env:SPRING_APPLICATION_JSON=$null
$env:SPRING_CONFIG_LOCATION=$null
$env:SPRING_CONFIG_ADDITIONAL_LOCATION=$null
$env:PLAYWRIGHT_BROWSERS_PATH=Join-Path $taskRoot '.playwright'
$env:PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD='1'
$env:RUN2_OUTPUT_PATH=$taskOutput
$env:RUN2_CAPTURE_PATH=Join-Path $taskRoot 'storage/run-2-scanner-v2-2026-10-07'
$jar=Join-Path $taskOutput 'backend-used.jar'
$launcher=Join-Path $taskRoot '.maven-cache/run2-launcher'
$socketPath=Join-Path $taskRoot 'backend/target-v2/disabled-unix-sockets'
if(Test-Path -LiteralPath $socketPath){throw 'NIO fallback path must remain absent'}
$javaArgs=@('-Xmx1024m','-Dfile.encoding=UTF-8',"-Djdk.net.unixdomain.tmpdir=$socketPath", "-Dloader.path=$launcher",'-Dloader.main=Run2Launcher','-cp',$jar,'org.springframework.boot.loader.launch.PropertiesLauncher')
if($Mode -eq 'start') {
    $overridePath=Join-Path $taskOutput 'local-scanner-overrides.json'
    if(Test-Path -LiteralPath $overridePath){throw 'Run 2 startup already attempted; inspect preserved state'}
    $scannerOverrides | ConvertTo-Json | Set-Content -LiteralPath $overridePath -Encoding UTF8
    $javaArgs+=@('--server.port=8080','--spring.profiles.active=default','--kidocolors.scanner.timeout-ms=30000','--kidocolors.scanner.readiness-timeout-ms=5000','--kidocolors.scanner.settle-ms=500','--kidocolors.scanner.max-elements=2000','--kidocolors.scanner.max-page-height=12000',"--kidocolors.scanner.storage-path=$env:RUN2_CAPTURE_PATH")
    & java @javaArgs *> (Join-Path $taskRoot '.maven-cache/run2-runtime.log')
} else {
    $javaArgs+= "--$Mode"
    & java @javaArgs
}
if($LASTEXITCODE -ne 0){throw 'Run 2 support process failed; inspect private runtime log without exposing credentials'}
