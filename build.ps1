param(
    [string]$JdkPath = '',
    [string]$SdkPath = '',
    [string]$BuildToolsPath = ''
)
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
Push-Location $projectRoot
try {
if (!$JdkPath) {
    $candidate = Get-ChildItem (Join-Path $projectRoot '.tools/jdk') -Directory | Select-Object -First 1
    if (!$candidate) { throw 'JDK missing. Run setup-tools.ps1 first or pass -JdkPath.' }
    $JdkPath = $candidate.FullName
}
if (!$SdkPath) { $SdkPath = Join-Path $projectRoot '.tools/sdk' }
if (!$BuildToolsPath) { $BuildToolsPath = Join-Path $SdkPath 'android-15' }
$androidJar = Join-Path $SdkPath 'android-35/android.jar'
if (!(Test-Path $androidJar)) { $androidJar = Join-Path $SdkPath 'platforms/android-35/android.jar' }
if (!(Test-Path $androidJar)) { throw 'Android platform 35 is missing.' }
$java = Join-Path $JdkPath 'bin/java.exe'
$javac = Join-Path $JdkPath 'bin/javac.exe'
$jar = Join-Path $JdkPath 'bin/jar.exe'
$keytool = Join-Path $JdkPath 'bin/keytool.exe'
$build = Join-Path $projectRoot 'build'
$dist = Join-Path $projectRoot 'dist'
foreach ($dir in @($build, "$build/classes", "$build/test-classes", "$build/dex", "$build/generated", $dist, "$projectRoot/.tools")) {
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
}
function Check-Exit([string]$stage) {
    if ($LASTEXITCODE -ne 0) { throw "$stage failed (exit $LASTEXITCODE)" }
}
$sourceRoot = Join-Path $projectRoot 'app/src/main'
$compilerLib = Join-Path $projectRoot '.tools/kotlin/kotlinc/lib'
$stdlib = Join-Path $compilerLib 'kotlin-stdlib.jar'
if (!(Test-Path $stdlib)) { throw 'Kotlin compiler missing. Run setup-tools.ps1 first.' }
# Remove only generated outputs inside this project's build directory; avoid stale Java classes after migration.
$buildBoundary = [IO.Path]::GetFullPath($build).TrimEnd('\') + '\'
foreach ($folder in @('classes', 'test-classes', 'generated', 'dex')) {
    $target = [IO.Path]::GetFullPath((Join-Path $build $folder))
    if (!$target.StartsWith($buildBoundary, [StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe build output path' }
    if (Test-Path -LiteralPath $target) { Remove-Item -LiteralPath $target -Recurse -Force }
    New-Item -ItemType Directory -Path $target | Out-Null
}
$valueSource = Join-Path $sourceRoot 'java/kr/local/galaxybattery/BatteryValues.kt'
$parserSource = Join-Path $sourceRoot 'java/kr/local/galaxybattery/DumpParser.kt'
& $java -cp "$compilerLib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 1.8 -classpath $stdlib -d "$build/test-classes" $valueSource $parserSource
Check-Exit 'Kotlin parser compilation'
$testClasspath = "$build/test-classes;$stdlib"
& $javac -encoding UTF-8 --release 8 -classpath $testClasspath -d "$build/test-classes" "$projectRoot/tests/BatteryValuesTest.java" "$projectRoot/tests/DumpParserTest.java"
Check-Exit 'Test compilation'
& $java -cp $testClasspath BatteryValuesTest
Check-Exit 'Value validation tests'
& $java -cp $testClasspath DumpParserTest
Check-Exit 'Dump parsing tests'

& "$BuildToolsPath/aapt2.exe" compile --dir 'app/src/main/res' -o 'build/resources.zip'
Check-Exit 'Resource compilation'
$nativeAndroidJar = Resolve-Path -LiteralPath $androidJar -Relative
[xml]$manifest = Get-Content "$sourceRoot/AndroidManifest.xml" -Encoding UTF8
$manifest.manifest.SetAttribute('package', 'kr.local.galaxybattery')
$manifest.Save("$build/generated/AndroidManifest.xml")
& "$BuildToolsPath/aapt2.exe" link -o 'build/resources.apk' -I $nativeAndroidJar -A 'app/src/main/assets' --manifest 'build/generated/AndroidManifest.xml' 'build/resources.zip'
Check-Exit 'Resource link'
$binderSources = @(Get-ChildItem "$sourceRoot/java" -Recurse -Filter '*.java' | ForEach-Object FullName)
$libraries = @(Get-ChildItem "$projectRoot/app/libs" -Filter '*.jar' | ForEach-Object FullName)
$libraries += $stdlib
$compileClasspath = (@($androidJar, "$build/classes") + $libraries) -join ';'
& $javac -encoding UTF-8 --release 8 -classpath $compileClasspath -d "$build/classes" @binderSources
Check-Exit 'Generated Binder compilation'
$sources = @(Get-ChildItem "$sourceRoot/java" -Recurse -Filter '*.kt' | ForEach-Object FullName)
& $java -cp "$compilerLib/*" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 1.8 -classpath $compileClasspath -d "$build/classes" @sources
Check-Exit 'Kotlin app compilation'
& $jar cf "$build/classes.jar" -C "$build/classes" .
Check-Exit 'Class archive'
$d8 = Join-Path $projectRoot '.tools/r8-8.9.35.jar'
if (!(Test-Path $d8)) { throw 'Kotlin-compatible D8 missing. Run setup-tools.ps1 first.' }
& $java -cp $d8 com.android.tools.r8.D8 --min-api 26 --lib $androidJar --output "$build/dex" "$build/classes.jar" @libraries
Check-Exit 'Dex compilation'
Copy-Item "$build/resources.apk" "$build/unsigned.apk" -Force
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::Open("$build/unsigned.apk", [IO.Compression.ZipArchiveMode]::Update)
try {
    [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, "$build/dex/classes.dex", 'classes.dex', [IO.Compression.CompressionLevel]::Optimal) | Out-Null
} finally { $archive.Dispose() }
& "$BuildToolsPath/zipalign.exe" -f -p 4 'build/unsigned.apk' 'build/aligned.apk'
Check-Exit 'Zip alignment'
$keystore = Join-Path $projectRoot '.tools/diagnostic.keystore'
if (!(Test-Path $keystore)) {
    & $keytool -genkeypair -keystore $keystore -storepass android -keypass android -alias diagnostic -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Galaxy Battery Local Diagnostic'
    Check-Exit 'Local signing key creation'
}
$apk = Join-Path $dist 'galaxy-battery-0.3.2.apk'
& $java -jar "$BuildToolsPath/lib/apksigner.jar" sign --ks $keystore --ks-key-alias diagnostic --ks-pass pass:android --key-pass pass:android --out $apk "$build/aligned.apk"
Check-Exit 'APK signing'
& $java -jar "$BuildToolsPath/lib/apksigner.jar" verify --verbose $apk
Check-Exit 'APK signature verification'
& "$BuildToolsPath/zipalign.exe" -c -p 4 'dist/galaxy-battery-0.3.2.apk'
Check-Exit 'APK alignment verification'
& "$BuildToolsPath/aapt.exe" dump badging 'dist/galaxy-battery-0.3.2.apk' | Select-String 'package:|sdkVersion|targetSdkVersion|application-label:|launchable-activity:|uses-permission'
Check-Exit 'APK manifest inspection'
$hash = (Get-FileHash $apk -Algorithm SHA256).Hash.ToLower()
"$hash  galaxy-battery-0.3.2.apk" | Set-Content "$dist/SHA256SUMS.txt" -Encoding Ascii
Write-Output "APK ready: $apk"
} finally { Pop-Location }
