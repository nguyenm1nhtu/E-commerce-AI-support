$ErrorActionPreference = 'Stop'

$envFile = Join-Path $PSScriptRoot '.env.dev'
$allowedVariables = @(
    'POSTGRES_HOST', 'POSTGRES_PORT', 'POSTGRES_DB', 'POSTGRES_USER',
    'POSTGRES_PASSWORD', 'REDIS_HOST', 'REDIS_PORT', 'REDIS_NAMESPACE',
    'REDIS_CACHE_TTL', 'SERVER_PORT', 'JWT_SECRET', 'JWT_ISSUER',
    'JWT_AUDIENCE', 'JWT_ACCESS_TOKEN_TTL', 'AUTH_REFRESH_COOKIE_SECURE',
    'AUTH_REFRESH_COOKIE_SAME_SITE', 'AUTH_REFRESH_COOKIE_MAX_AGE'
)
$previousValues = @{}
$exitCode = 1

Push-Location $PSScriptRoot
try {
    if (-not (Test-Path -LiteralPath $envFile -PathType Leaf)) {
        throw 'Missing .env.dev. Copy .env.example to .env.dev and fill in POSTGRES_PASSWORD.'
    }

    $values = @{}
    $lineNumber = 0
    foreach ($line in Get-Content -LiteralPath $envFile -Encoding UTF8) {
        $lineNumber++
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith('#')) {
            continue
        }
        if ($trimmed -notmatch '^([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
            throw "Invalid assignment in .env.dev at line $lineNumber. Use KEY=value."
        }
        $name = $Matches[1]
        $value = $Matches[2].Trim()
        if ($name -notin $allowedVariables) {
            continue
        }

        # Treat values as data: never evaluate PowerShell or expand variables.
        if ($value.StartsWith("'") -or $value.StartsWith('"')) {
            if ($value -notmatch '^(?:''([^'']*)''|"([^"]*)")\s*(?:#.*)?$') {
                throw "Invalid quoted value in .env.dev at line $lineNumber. Use a single-line value."
            }
            if ($value.StartsWith("'")) {
                $value = $Matches[1]
            } else {
                $value = $Matches[2]
            }
        } else {
            $value = ($value -replace '(^|\s+)#.*$', '').TrimEnd()
        }
        $values[$name] = $value
    }

    if ([string]::IsNullOrWhiteSpace($values['POSTGRES_PASSWORD'])) {
        throw 'Set a non-empty POSTGRES_PASSWORD in .env.dev before starting dev.'
    }

    foreach ($name in $values.Keys) {
        $previousValues[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        [Environment]::SetEnvironmentVariable($name, $values[$name], 'Process')
    }

    Write-Host 'Loaded .env.dev. Starting PostgreSQL and Redis in the background...'
    & docker compose --env-file .env.dev -f compose.dev.yaml up -d --wait
    if ($LASTEXITCODE -ne 0) {
        $exitCode = $LASTEXITCODE
        throw "Docker Compose failed (exit code $exitCode). Backend was not started."
    }

    Write-Host 'Starting backend with the dev profile. Ctrl+C stops only the backend.'
    Write-Host 'To stop PostgreSQL and Redis: docker compose --env-file .env.dev -f compose.dev.yaml down'
    & (Join-Path $PSScriptRoot 'mvnw.cmd') spring-boot:run '-Dspring-boot.run.profiles=dev'
    $exitCode = $LASTEXITCODE
} catch {
    Write-Error $_ -ErrorAction Continue
} finally {
    foreach ($name in $previousValues.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previousValues[$name], 'Process')
    }
    Pop-Location
}

exit $exitCode
