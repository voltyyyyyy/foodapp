param([string]$ApiUrl = 'http://10.0.2.2:8000')
$ErrorActionPreference = 'Stop'
$toolRoot = Join-Path $PSScriptRoot '.local'
$env:JAVA_HOME = "$toolRoot/jdk"
$env:PATH = "$toolRoot/jdk/bin;" + $env:PATH
Push-Location "$PSScriptRoot/../android"
try {
    & "$toolRoot/gradle-8.13/bin/gradle.bat" --no-daemon "-PapiUrl=$ApiUrl" assembleDebug
    if ($LASTEXITCODE -ne 0) { throw 'Android build failed' }
} finally { Pop-Location }
