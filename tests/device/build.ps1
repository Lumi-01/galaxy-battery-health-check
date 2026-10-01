$ErrorActionPreference='Stop'
$taskRoot=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
Set-Location -LiteralPath $taskRoot
$taskJdk=(Get-ChildItem .tools/jdk -Directory | Select-Object -First 1).FullName
$taskOut=Join-Path $taskRoot 'build/device-tests'
New-Item -ItemType Directory -Force -Path "$taskOut/classes", "$taskOut/dex" | Out-Null
$taskAndroid=Join-Path $taskRoot '.tools/sdk/android-36/android.jar'
if (!(Test-Path -LiteralPath $taskAndroid)) { $taskAndroid=Join-Path $taskRoot '.tools/sdk/platforms/android-36/android.jar' }
$taskStdlib=Join-Path $taskRoot '.tools/kotlin/kotlinc/lib/kotlin-stdlib.jar'
& "$taskJdk/bin/javac.exe" -encoding UTF-8 --release 8 -classpath "$taskAndroid;build/classes.jar;$taskStdlib" -d "$taskOut/classes" tests/device/RegressionChecks.java
if ($LASTEXITCODE) { throw 'Visual test compilation failed' }
& "$taskJdk/bin/jar.exe" cf "$taskOut/classes.jar" -C "$taskOut/classes" .
& "$taskJdk/bin/java.exe" -cp .tools/r8-8.9.35.jar com.android.tools.r8.D8 --min-api 26 --lib $taskAndroid --classpath build/classes.jar --classpath $taskStdlib --output "$taskOut/dex" "$taskOut/classes.jar"
if ($LASTEXITCODE) { throw 'Visual test dex failed' }
$taskManifest='<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="kr.local.galaxybattery.visualtests"><uses-sdk android:minSdkVersion="26"/><application android:label="Device regression checks" android:testOnly="true"/><instrumentation android:name="kr.local.galaxybattery.visualtests.RegressionChecks" android:targetPackage="kr.local.galaxybattery"/></manifest>'
[IO.File]::WriteAllText("$taskOut/AndroidManifest.xml",$taskManifest)
& .tools/sdk/android-15/aapt2.exe link -o build/device-tests/unsigned.apk -I (Resolve-Path -LiteralPath $taskAndroid -Relative) --manifest build/device-tests/AndroidManifest.xml
if ($LASTEXITCODE) { throw 'Visual test packaging failed' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.IO.Compression
$taskArchive=[IO.Compression.ZipFile]::Open("$taskOut/unsigned.apk",[IO.Compression.ZipArchiveMode]::Update)
try { [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($taskArchive,"$taskOut/dex/classes.dex",'classes.dex') | Out-Null } finally { $taskArchive.Dispose() }
& .tools/sdk/android-15/zipalign.exe -f -p 4 "$taskOut/unsigned.apk" "$taskOut/aligned.apk"
& "$taskJdk/bin/java.exe" -jar .tools/sdk/android-15/lib/apksigner.jar sign --ks .tools/diagnostic.keystore --ks-key-alias diagnostic --ks-pass pass:android --out "$taskOut/visual-checks.apk" "$taskOut/aligned.apk"
if ($LASTEXITCODE) { throw 'Visual test signing failed' }
Write-Output 'Emulator-only visual harness ready; no production hooks added'
