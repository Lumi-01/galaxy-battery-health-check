$ErrorActionPreference = 'Stop'
$toolsDir = Join-Path $PSScriptRoot '.tools'
New-Item -ItemType Directory -Force $toolsDir | Out-Null
function Download([string]$url, [string]$path) {
    & curl.exe -fsSL --retry 2 $url -o $path
    if ($LASTEXITCODE -ne 0) { throw "Download failed: $url" }
}
Download 'https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse' "$toolsDir/jdk.json"
$package = (Get-Content "$toolsDir/jdk.json" -Raw | ConvertFrom-Json)[0].binary.package
Download $package.link "$toolsDir/jdk.zip"
if ((Get-FileHash "$toolsDir/jdk.zip" -Algorithm SHA256).Hash.ToLower() -ne $package.checksum) { throw 'JDK checksum mismatch' }
Expand-Archive "$toolsDir/jdk.zip" "$toolsDir/jdk" -Force
Download 'https://github.com/JetBrains/kotlin/releases/download/v2.1.21/kotlin-compiler-2.1.21.zip' "$toolsDir/kotlin.zip"
Expand-Archive "$toolsDir/kotlin.zip" "$toolsDir/kotlin" -Force
Download 'https://dl.google.com/dl/android/maven2/com/android/tools/r8/8.9.35/r8-8.9.35.jar' "$toolsDir/r8-8.9.35.jar"
Download 'https://dl.google.com/android/repository/repository2-3.xml' "$toolsDir/repository.xml"
[xml]$repo = Get-Content "$toolsDir/repository.xml"
foreach ($pkgId in @('build-tools;35.0.0', 'platforms;android-36', 'platform-tools')) {
    $pkg = $repo.'sdk-repository'.remotePackage | Where-Object path -eq $pkgId
    $archive = $pkg.archives.archive | Where-Object { !$_.'host-os' -or $_.'host-os' -eq 'windows' } | Select-Object -First 1
    if (!$archive) { throw "Package unavailable: $pkgId" }
    $zip = Join-Path $toolsDir $archive.complete.url
    Download ('https://dl.google.com/android/repository/' + $archive.complete.url) $zip
    if ((Get-FileHash $zip -Algorithm SHA1).Hash.ToLower() -ne $archive.complete.checksum.'#text') { throw "Checksum mismatch: $pkgId" }
    Expand-Archive -LiteralPath $zip -DestinationPath "$toolsDir/sdk" -Force
    Write-Output "Installed $pkgId"
}
