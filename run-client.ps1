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

if (-not $env:SERVER_HOST) {
    $env:SERVER_HOST = '127.0.0.1'
}
if (-not $env:SERVER_PORT) {
    $env:SERVER_PORT = '5050'
}

& mvn '-Deventbooking.mainClass=com.eventbooking.client.ClientMain' exec:java
exit $LASTEXITCODE
