$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$javaHome = Join-Path $env:USERPROFILE '.jdk\jdk-21.0.10'
if (Test-Path (Join-Path $javaHome 'bin\java.exe')) {
    $env:JAVA_HOME = $javaHome
    $env:PATH = "$javaHome\bin;$env:PATH"
}

$mavenBin = Join-Path $env:USERPROFILE '.maven\maven-3.9.15\bin'
if (Test-Path (Join-Path $mavenBin 'mvn.cmd')) {
    $env:PATH = "$mavenBin;$env:PATH"
}

if (-not $env:DB_USER) {
    $env:DB_USER = Read-Host 'MySQL username'
}
if (-not $env:DB_USER) {
    throw 'DB_USER is required to start the server.'
}

if (-not $env:DB_PASSWORD) {
    $securePassword = Read-Host 'MySQL password (press Enter if none)' -AsSecureString
    $env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $securePassword).Password
}

if (-not $env:DB_URL) {
    $env:DB_URL = 'jdbc:mysql://localhost:3306/distributed_event_booking?serverTimezone=UTC'
}
if (-not $env:SERVER_PORT) {
    $env:SERVER_PORT = '5050'
}

& mvn exec:java
exit $LASTEXITCODE
