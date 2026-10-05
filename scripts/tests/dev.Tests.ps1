$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$fixture = Join-Path ([IO.Path]::GetTempPath()) ('dev-script-tests-' + [Guid]::NewGuid())
$shell = (Get-Process -Id $PID).Path
$previousPath = $env:PATH

function Assert-True($condition, $message) {
    if (-not $condition) { throw $message }
}

function Invoke-Dev {
    $ErrorActionPreference = 'Continue'
    $output = & $shell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $fixture 'dev.ps1') 2>&1
    return @{ Code = $LASTEXITCODE; Output = ($output -join "`n") }
}

New-Item -ItemType Directory -Path $fixture | Out-Null
try {
    Copy-Item -LiteralPath (Join-Path $repoRoot 'dev.ps1') -Destination $fixture
    # Replace Docker and Maven in the temporary fixture; no Docker or Java required.
    $env:PATH = "$fixture;$previousPath"
    @'
@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0docker-capture.ps1" %*
exit /b %ERRORLEVEL%
'@ | Set-Content -LiteralPath (Join-Path $fixture 'docker.cmd') -Encoding ASCII
    @'
($args -join '|') | Add-Content -LiteralPath (Join-Path $PSScriptRoot 'docker-calls.txt')
if (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'docker-fail')) { exit 17 }
'@ | Set-Content -LiteralPath (Join-Path $fixture 'docker-capture.ps1') -Encoding UTF8
    @'
@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0capture.ps1" %*
exit /b 23
'@ | Set-Content -LiteralPath (Join-Path $fixture 'mvnw.cmd') -Encoding ASCII
    @'
@{
    Password = $env:POSTGRES_PASSWORD
    Database = $env:POSTGRES_DB
    Port = $env:SERVER_PORT
    RedisNamespace = $env:REDIS_NAMESPACE
    RedisCacheTtl = $env:REDIS_CACHE_TTL
    JwtSecret = $env:JWT_SECRET
    JwtIssuer = $env:JWT_ISSUER
    JwtAudience = $env:JWT_AUDIENCE
    JwtTtl = $env:JWT_ACCESS_TOKEN_TTL
    RefreshCookieSecure = $env:AUTH_REFRESH_COOKIE_SECURE
    RefreshCookieSameSite = $env:AUTH_REFRESH_COOKIE_SAME_SITE
    RefreshCookieMaxAge = $env:AUTH_REFRESH_COOKIE_MAX_AGE
    Directory = (Get-Location).Path
    Arguments = $args
    DockerStarted = Test-Path -LiteralPath (Join-Path $PSScriptRoot 'docker-calls.txt')
} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'capture.json')
'@ | Set-Content -LiteralPath (Join-Path $fixture 'capture.ps1') -Encoding UTF8

    $result = Invoke-Dev
    Assert-True ($result.Code -eq 1 -and $result.Output.Contains('Missing .env.dev')) 'Missing file must fail clearly.'

    'POSTGRES_PASSWORD=   # empty' | Set-Content -LiteralPath (Join-Path $fixture '.env.dev')
    $result = Invoke-Dev
    Assert-True ($result.Code -eq 1 -and $result.Output.Contains('non-empty')) 'Empty password must fail.'

    @'
# Fake credentials for parser tests only.
POSTGRES_PASSWORD='fake$literal#with=equals' # comment
POSTGRES_DB="dev database" # comment
SERVER_PORT=8088 # comment
REDIS_PASSWORD=ignored
REDIS_NAMESPACE=dev-cache-test
REDIS_CACHE_TTL=3m
JWT_SECRET=fake-test-secret
JWT_ISSUER=dev-issuer
JWT_AUDIENCE=dev-api
JWT_ACCESS_TOKEN_TTL=5m
AUTH_REFRESH_COOKIE_SECURE=false
AUTH_REFRESH_COOKIE_SAME_SITE=Strict
AUTH_REFRESH_COOKIE_MAX_AGE=2d
'@ | Set-Content -LiteralPath (Join-Path $fixture '.env.dev') -Encoding UTF8
    $result = Invoke-Dev
    Assert-True ($result.Code -eq 23) 'Maven exit code must propagate.'
    $captured = Get-Content -Raw -LiteralPath (Join-Path $fixture 'capture.json') | ConvertFrom-Json
    Assert-True ($captured.Password -ceq 'fake$literal#with=equals') 'Password must remain literal.'
    Assert-True ($captured.Database -eq 'dev database' -and $captured.Port -eq '8088') 'Quotes and comments must be parsed.'
    Assert-True ($captured.RedisNamespace -eq 'dev-cache-test' -and $captured.RedisCacheTtl -eq '3m') 'Redis settings must reach Maven.'
    Assert-True ($captured.JwtSecret -eq 'fake-test-secret' -and $captured.JwtIssuer -eq 'dev-issuer' -and $captured.JwtAudience -eq 'dev-api' -and $captured.JwtTtl -eq '5m') 'JWT settings must reach Maven.'
    Assert-True (-not $result.Output.Contains('fake-test-secret')) 'Script must not print the JWT secret.'
    Assert-True ($captured.RefreshCookieSecure -eq 'false' -and $captured.RefreshCookieSameSite -eq 'Strict' -and $captured.RefreshCookieMaxAge -eq '2d') 'Refresh cookie settings must reach Maven.'
    Assert-True ($captured.Directory -eq $fixture) 'Maven must run relative to the script directory.'
    Assert-True ($captured.DockerStarted) 'Docker must start before Maven.'
    Assert-True (($captured.Arguments -join '|') -eq 'spring-boot:run|-Dspring-boot.run.profiles=dev') 'Maven must receive the dev profile.'
    Assert-True (-not $result.Output.Contains('fake$literal')) 'Script must not print credentials.'

    $result = Invoke-Dev
    Assert-True ($result.Code -eq 23) 'Repeated startup must still reach Maven.'
    $dockerCalls = @(Get-Content -LiteralPath (Join-Path $fixture 'docker-calls.txt'))
    Assert-True ($dockerCalls.Count -eq 2) 'Each valid startup must invoke Docker once.'
    foreach ($call in $dockerCalls) {
        Assert-True ($call -eq 'compose|--env-file|.env.dev|-f|compose.dev.yaml|up|-d|--wait') 'Docker must start dev services detached and wait for health, without stopping them.'
    }

    Remove-Item -LiteralPath (Join-Path $fixture 'capture.json')
    New-Item -ItemType File -Path (Join-Path $fixture 'docker-fail') | Out-Null
    $result = Invoke-Dev
    Assert-True ($result.Code -eq 17 -and $result.Output -match 'Docker Compose\s+failed') 'Docker failure must propagate clearly.'
    Assert-True (-not (Test-Path -LiteralPath (Join-Path $fixture 'capture.json'))) 'Docker failure must prevent backend startup.'
    Remove-Item -LiteralPath (Join-Path $fixture 'docker-fail')

    'POSTGRES_PASSWORD=''unterminated' | Set-Content -LiteralPath (Join-Path $fixture '.env.dev')
    $result = Invoke-Dev
    Assert-True ($result.Code -eq 1 -and $result.Output.Contains('Invalid quoted value')) 'Malformed quotes must fail.'
    Assert-True (-not (Test-Path -LiteralPath (Join-Path $fixture 'capture.json'))) 'Invalid configuration must not start Maven.'

    Write-Host 'All dev script tests passed.'
} finally {
    $env:PATH = $previousPath
    $resolvedFixture = [IO.Path]::GetFullPath($fixture)
    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    if ($resolvedFixture.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase) -and
        (Split-Path $resolvedFixture -Leaf) -like 'dev-script-tests-*') {
        Remove-Item -LiteralPath $resolvedFixture -Recurse -Force
    }
}
