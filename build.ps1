param(
    [string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$Jdk = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
if (!$Jdk) {
    $javaCommand = Get-Command javac -ErrorAction Stop
    $Jdk = Split-Path (Split-Path $javaCommand.Source -Parent) -Parent
}
$env:JAVA_HOME = $Jdk
$env:PATH = "$Jdk\bin;$env:PATH"
$bt = Join-Path $Sdk 'build-tools\34.0.0'
$androidJar = Join-Path $Sdk 'platforms\android-34\android.jar'
Set-Location $PSScriptRoot
New-Item -ItemType Directory -Force build/classes,build/dex,dist | Out-Null
function Check { if ($LASTEXITCODE -ne 0) { throw "Build command failed: $LASTEXITCODE" } }
$sources = (Get-ChildItem app/src/main/java/app/jingman/reader/*.java).FullName
& "$Jdk\bin\javac.exe" -g -encoding UTF-8 --release 8 -classpath $androidJar -d build/classes $sources
Check
& "$Jdk\bin\jar.exe" cf build/classes.jar -C build/classes app
Check
if (!(Test-Path build/r8.jar)) {
    Invoke-WebRequest 'https://dl.google.com/dl/android/maven2/com/android/tools/r8/8.3.37/r8-8.3.37.jar' -OutFile build/r8.jar
}
& "$Jdk\bin\java.exe" -cp build/r8.jar com.android.tools.r8.D8 --lib $androidJar --min-api 24 --output build/dex build/classes.jar
Check
& "$bt\aapt.exe" package -f -M app/src/main/AndroidManifest.xml -I $androidJar -S app/src/main/res -A app/src/main/assets -F build/unsigned.apk --min-sdk-version 24 --target-sdk-version 34 --version-code 22 --version-name 0.4.12
Check
Copy-Item build/dex/classes.dex build/classes.dex -Force
Push-Location build
& "$bt\aapt.exe" add unsigned.apk classes.dex
Check
Pop-Location
& "$bt\zipalign.exe" -f 4 build/unsigned.apk build/aligned.apk
Check
if (!(Test-Path build/debug.keystore)) {
    & "$Jdk\bin\keytool.exe" -genkeypair -keystore build/debug.keystore -storepass android -keypass android -alias androiddebugkey -dname 'CN=Jingman Debug' -keyalg RSA -keysize 2048 -validity 10000
    Check
}
& "$bt\apksigner.bat" sign --ks build/debug.keystore --ks-pass pass:android --key-pass pass:android --out dist/jingman-0.4.12.apk build/aligned.apk
Check
& "$bt\apksigner.bat" verify --verbose dist/jingman-0.4.12.apk
Check
Write-Host "APK: $PSScriptRoot\dist\jingman-0.4.12.apk"
