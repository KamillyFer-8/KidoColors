param(
    [string]$EnvFile,
    [switch]$CheckConfigOnly
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
if (-not $EnvFile) { $EnvFile = Join-Path $projectRoot '.env.local' }
if (-not (Test-Path -LiteralPath $EnvFile -PathType Leaf)) {
    throw 'Crie .env.local a partir de .env.example e configure o PostgreSQL conforme docs/local-development.md.'
}

# Importa somente configurações conhecidas, sem executar o conteúdo do arquivo.
$allowedNames = @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'SERVER_PORT',
    'SCANNER_TIMEOUT_MS', 'SCANNER_SETTLE_MS', 'CAPTURE_STORAGE_PATH')
$loadedNames = @{}
foreach ($line in Get-Content -LiteralPath $EnvFile -Encoding UTF8) {
    if ($line -match '^\s*(#|$)') { continue }
    if ($line -notmatch '^\s*([A-Z_]+)\s*=(.*)$') {
        throw 'Formato inválido no arquivo local: use NOME=valor, uma variável por linha.'
    }
    $name = $matches[1]
    $value = $matches[2].Trim()
    if ($name -notin $allowedNames) { throw "Variável não permitida no arquivo local: $name" }
    if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or
        ($value.StartsWith("'") -and $value.EndsWith("'")))) {
        $value = $value.Substring(1, $value.Length - 2)
    }
    [Environment]::SetEnvironmentVariable($name, $value, 'Process')
    $loadedNames[$name] = $true
}
foreach ($name in @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD')) {
    if (-not $loadedNames.ContainsKey($name) -or
        [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name, 'Process'))) {
        throw "Preencha $name no arquivo local. Não envie credenciais pelo chat."
    }
}
if (-not $env:DB_URL.StartsWith('jdbc:postgresql://')) {
    throw 'DB_URL deve ser uma URL JDBC PostgreSQL. Veja docs/local-development.md.'
}
if ($CheckConfigOnly) {
    Write-Output 'Variáveis locais válidas; nenhuma conexão com o banco foi realizada.'
    return
}

$jarPath = Join-Path $projectRoot 'backend/target/backend-0.1.0-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $jarPath)) {
    throw 'Prepare o Backend e o Chromium conforme docs/local-development.md antes de iniciar.'
}
$env:PLAYWRIGHT_BROWSERS_PATH = Join-Path $projectRoot '.playwright'
$env:PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD = '1'
$javaArguments = @()
if ($env:OS -eq 'Windows_NT') {
    # Mesmo contorno usado nos testes Windows: força o pipe NIO a usar TCP local
    # quando AF_UNIX falha nos caminhos temporários remapeados. Não crie esta pasta.
    $socketFallbackPath = Join-Path $projectRoot 'backend/target/disabled-unix-sockets'
    if (Test-Path -LiteralPath $socketFallbackPath) {
        throw 'O caminho backend/target/disabled-unix-sockets deve permanecer inexistente para o contorno de sockets Windows.'
    }
    $javaArguments += "-Djdk.net.unixdomain.tmpdir=$socketFallbackPath"
}
$javaArguments += @('-jar', $jarPath)
Push-Location $projectRoot
try {
    & java @javaArguments
    if ($LASTEXITCODE -ne 0) { throw 'A API não iniciou ou encerrou com erro. Consulte o log do Spring.' }
} finally { Pop-Location }
