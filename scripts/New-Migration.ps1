[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidatePattern('^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$')]
    [string]$Name
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$changes = Join-Path $repoRoot 'src/main/resources/db/changelog/changes'
$master = Join-Path $repoRoot 'src/main/resources/db/changelog/db.changelog-master.yaml'
$target = Join-Path $repoRoot 'target'
$utf8 = New-Object System.Text.UTF8Encoding($false)
$lock = $null
$generated = $null

foreach ($variable in @('POSTGRES_HOST', 'POSTGRES_PORT', 'POSTGRES_DB', 'POSTGRES_USER', 'POSTGRES_PASSWORD')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($variable))) {
        throw "Set $variable in this terminal before generating a migration."
    }
}

[System.IO.Directory]::CreateDirectory($target) | Out-Null
Push-Location $repoRoot
try {
    # Serialize generators so they cannot choose the same migration number.
    $lock = [System.IO.File]::Open((Join-Path $target 'migration-generator.lock'),
        [System.IO.FileMode]::OpenOrCreate, [System.IO.FileAccess]::ReadWrite,
        [System.IO.FileShare]::None)
    $originalMaster = [System.IO.File]::ReadAllText($master)
    $highest = 0
    foreach ($file in Get-ChildItem -LiteralPath $changes -File -Filter '*.sql') {
        if ($file.Name -match '^(\d+)-') {
            $highest = [Math]::Max($highest, [int]$Matches[1])
        }
    }
    $filename = '{0:D3}-{1}.sql' -f ($highest + 1), $Name
    $destination = Join-Path $changes $filename
    $generated = Join-Path $target ('entity-diff-{0}.postgresql.sql' -f [Guid]::NewGuid().ToString('N'))

    & (Join-Path $repoRoot 'mvnw.cmd') compile liquibase:diff "-Dliquibase.diffChangeLogFile=$generated"
    if ($LASTEXITCODE -ne 0) {
        throw "Liquibase generation failed (exit code $LASTEXITCODE). Master changelog was not changed."
    }
    if (-not (Test-Path -LiteralPath $generated)) {
        Write-Host 'No schema changes generated. Master changelog was not changed.'
        return
    }
    $sql = [System.IO.File]::ReadAllText($generated)
    if ($sql -notmatch '(?im)^\s*--\s*changeset\s+\S+') {
        if ($sql -match '(?im)^\s*[^\s-]') {
            throw 'Generated SQL is not a formatted Liquibase changelog. Master changelog was not changed.'
        }
        Write-Host 'No changesets generated. Master changelog was not changed.'
        return
    }
    if ($sql -notmatch '(?im)^\s*--\s*liquibase formatted sql\s*$') {
        throw 'Generated SQL is missing the Liquibase formatted SQL header.'
    }
    if ([System.IO.File]::ReadAllText($master) -cne $originalMaster) {
        throw 'Master changelog changed during generation. Run the script again.'
    }
    # Copy without overwriting; register only a successfully generated migration.
    [System.IO.File]::Copy($generated, $destination, $false)
    try {
        $newline = "`n"
        if ($originalMaster.Contains("`r`n")) { $newline = "`r`n" }
        $updatedMaster = $originalMaster.TrimEnd("`r", "`n") + $newline +
            '  - include:' + $newline +
            "      file: changes/$filename" + $newline +
            '      relativeToChangelogFile: true' + $newline
        [System.IO.File]::WriteAllText($master, $updatedMaster, $utf8)
    }
    catch {
        [System.IO.File]::Delete($destination)
        throw
    }
    Write-Host "Generated and registered: src/main/resources/db/changelog/changes/$filename"
    Write-Host 'Review the SQL, then run:'
    Write-Host '.\mvnw.cmd compile liquibase:updateSQL'
    Write-Host '.\mvnw.cmd liquibase:update'
    Write-Host '.\mvnw.cmd test'
}
finally {
    if ($generated -and (Test-Path -LiteralPath $generated)) {
        [System.IO.File]::Delete($generated)
    }
    if ($null -ne $lock) { $lock.Dispose() }
    Pop-Location
}
