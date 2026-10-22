$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$fixture = Join-Path ([IO.Path]::GetTempPath()) ('migration-script-tests-' + [Guid]::NewGuid())
$shell = (Get-Process -Id $PID).Path
$variables = @('POSTGRES_HOST', 'POSTGRES_PORT', 'POSTGRES_DB', 'POSTGRES_USER', 'POSTGRES_PASSWORD')
$previous = @{}
function Assert-True($condition, $message) {
    if (-not $condition) { throw $message }
}
function Invoke-Generator([string]$name = 'create-example') {
    $ErrorActionPreference = 'Continue'
    $output = & $shell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $fixture 'scripts/New-Migration.ps1') $name 2>&1
    return @{ Code = $LASTEXITCODE; Output = ($output -join "`n") }
}
try {
    foreach ($variable in $variables) {
        $previous[$variable] = [Environment]::GetEnvironmentVariable($variable)
        [Environment]::SetEnvironmentVariable($variable, 'test-placeholder')
    }
    $changes = Join-Path $fixture 'src/main/resources/db/changelog/changes'
    New-Item -ItemType Directory -Path $changes -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $fixture 'scripts') | Out-Null
    Copy-Item (Join-Path $repoRoot 'scripts/New-Migration.ps1') (Join-Path $fixture 'scripts')
    $master = Join-Path $fixture 'src/main/resources/db/changelog/db.changelog-master.yaml'
    $baseline = "databaseChangeLog:`n  - include:`n      file: changes/008-existing.sql`n      relativeToChangelogFile: true`n"
    [IO.File]::WriteAllText($master, $baseline)
    [IO.File]::WriteAllText((Join-Path $changes '008-existing.sql'), '-- existing')
    @'
@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0fake-maven.ps1" %*
exit /b %ERRORLEVEL%
'@ | Set-Content (Join-Path $fixture 'mvnw.cmd') -Encoding ASCII
    @'
$mode = Get-Content (Join-Path $PSScriptRoot 'mode.txt')
$args | ConvertTo-Json | Set-Content (Join-Path $PSScriptRoot 'arguments.json')
if ($mode -eq 'failure') { exit 17 }
if ($mode -eq 'empty') { exit 0 }
$output = ($args | Where-Object { $_ -like '-Dliquibase.diffChangeLogFile=*' }) -replace '^-Dliquibase.diffChangeLogFile=', ''
$sql = "--liquibase formatted sql`n--changeset test:1`nCREATE TABLE example (id bigint);`n"
if ($mode -eq 'invalid') { $sql = 'CREATE TABLE example (id bigint);' }
[IO.File]::WriteAllText($output, $sql)
'@ | Set-Content (Join-Path $fixture 'fake-maven.ps1') -Encoding ASCII
    foreach ($mode in @('failure', 'empty', 'invalid')) {
        Set-Content (Join-Path $fixture 'mode.txt') $mode
        $result = Invoke-Generator
        Assert-True (($mode -eq 'empty') -eq ($result.Code -eq 0)) "Unexpected exit for $mode : $($result.Output)"
        Assert-True ([IO.File]::ReadAllText($master) -ceq $baseline) "Master changed on $mode"
        Assert-True (@(Get-ChildItem $changes -Filter '*.sql').Count -eq 1) "Migration created on $mode"
    }
    Set-Content (Join-Path $fixture 'mode.txt') 'success'
    $result = Invoke-Generator
    Assert-True ($result.Code -eq 0) "Generation failed: $($result.Output)"
    Assert-True (Test-Path (Join-Path $changes '009-create-example.sql')) 'Incorrect sequence or output directory'
    Assert-True ([IO.File]::ReadAllText($master).Contains('file: changes/009-create-example.sql')) 'Missing include'
    $arguments = Get-Content (Join-Path $fixture 'arguments.json') -Raw | ConvertFrom-Json
    Assert-True ($arguments[0] -eq 'compile' -and $arguments[1] -eq 'liquibase:diff') 'Incorrect Maven goals'
    $result = Invoke-Generator 'add-example-index'
    Assert-True ($result.Code -eq 0 -and (Test-Path (Join-Path $changes '010-add-example-index.sql'))) 'Second migration must increment sequence'
    $result = Invoke-Generator '../invalid'
    Assert-True ($result.Code -ne 0) 'Path traversal name must be rejected'
    [Environment]::SetEnvironmentVariable('POSTGRES_PASSWORD', '')
    $result = Invoke-Generator
    Assert-True ($result.Code -ne 0 -and $result.Output.Contains('POSTGRES_PASSWORD')) 'Missing database configuration must fail'
    Write-Host 'Passed: failure, no diff, invalid SQL, generation, includes, sequence, name validation, missing environment.'
}
finally {
    foreach ($variable in $previous.Keys) {
        [Environment]::SetEnvironmentVariable($variable, $previous[$variable])
    }
    $resolved = [IO.Path]::GetFullPath($fixture)
    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    if ($resolved.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase) -and
        (Split-Path $resolved -Leaf) -like 'migration-script-tests-*') {
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
