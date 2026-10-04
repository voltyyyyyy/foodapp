$ErrorActionPreference = 'Stop'
$toolRoot = Join-Path $PSScriptRoot '.local'
New-Item -ItemType Directory -Force $toolRoot | Out-Null
if (!(Test-Path "$toolRoot/jdk")) {
  Write-Output 'Downloading Java 17'
  Invoke-WebRequest -UseBasicParsing 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse' -OutFile "$toolRoot/jdk.zip"
  Expand-Archive "$toolRoot/jdk.zip" "$toolRoot/java" -Force
  Move-Item (Get-ChildItem "$toolRoot/java" -Directory | Select-Object -First 1).FullName "$toolRoot/jdk"
}
if (!(Test-Path "$toolRoot/gradle-8.13")) {
  Write-Output 'Downloading Gradle 8.13'
  Invoke-WebRequest -UseBasicParsing 'https://services.gradle.org/distributions/gradle-8.13-bin.zip' -OutFile "$toolRoot/gradle.zip"
  Expand-Archive "$toolRoot/gradle.zip" $toolRoot -Force
}
if (!(Test-Path "$toolRoot/sdk/cmdline-tools/latest/bin/sdkmanager.bat")) {
  Write-Output 'Downloading Android SDK tools'
  [xml]$repository = (Invoke-WebRequest -UseBasicParsing 'https://dl.google.com/android/repository/repository2-1.xml').Content
  $package = $repository.SelectSingleNode("//*[local-name()='remotePackage' and @path='cmdline-tools;latest']")
  $archive = $package.SelectSingleNode(".//*[local-name()='archive'][*[local-name()='host-os']='windows']/*[local-name()='complete']/*[local-name()='url']").InnerText
  Invoke-WebRequest -UseBasicParsing ("https://dl.google.com/android/repository/" + $archive) -OutFile "$toolRoot/sdk.zip"
  Expand-Archive "$toolRoot/sdk.zip" "$toolRoot/sdk-unpacked" -Force
  New-Item -ItemType Directory -Force "$toolRoot/sdk/cmdline-tools" | Out-Null
  Move-Item "$toolRoot/sdk-unpacked/cmdline-tools" "$toolRoot/sdk/cmdline-tools/latest"
}
$env:JAVA_HOME = "$toolRoot/jdk"
$env:PATH = "$toolRoot/jdk/bin;" + $env:PATH
Write-Output 'Accepting Android SDK licenses and installing API 35'
# SDK license agreement is handled by the official Android CLI.
& "$toolRoot/sdk/cmdline-tools/latest/bin/android.exe" "--sdk=$toolRoot/sdk" --no-metrics sdk install 'platform-tools' 'platforms;android-35' 'build-tools;35.0.0'
if ($LASTEXITCODE -ne 0 -or !(Test-Path "$toolRoot/sdk/platforms/android-35/android.jar")) { throw 'SDK installation failed or platform archive incomplete' }
$sdkPath = "$toolRoot/sdk".Replace('\','/').Replace(':','\:')
"sdk.dir=$sdkPath" | Set-Content "$PSScriptRoot/../android/local.properties"
Write-Output 'Android toolchain installed'


