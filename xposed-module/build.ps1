$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }
$bt = Get-ChildItem "$sdk\build-tools" | Sort-Object Name | Select-Object -Last 1
$androidJar = Get-ChildItem "$sdk\platforms" | Sort-Object Name | Select-Object -Last 1 | ForEach-Object { "$($_.FullName)\android.jar" }
$out = "$root\build"
Remove-Item $out -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory "$out\classes", "$out\dex" | Out-Null

$sources = @(Get-ChildItem "$root\src\main\java", "$root\stubs" -Recurse -Filter *.java | ForEach-Object FullName)
javac --release 8 -Xlint:-options -cp $androidJar -d "$out\classes" $sources
if ($LASTEXITCODE) { throw 'javac failed' }

# stubs are compile-only; real Xposed classes come from Vector at runtime
Remove-Item "$out\classes\de" -Recurse -Force
$classFiles = Get-ChildItem "$out\classes" -Recurse -Filter *.class | ForEach-Object FullName
& "$($bt.FullName)\d8.bat" --min-api 26 --lib $androidJar --output "$out\dex" $classFiles
if ($LASTEXITCODE) { throw 'd8 failed' }

$unsigned = "$out\unsigned.apk"
& "$($bt.FullName)\aapt2.exe" compile --dir "$root\src\main\res" -o "$out\res.zip"
& "$($bt.FullName)\aapt2.exe" link -o $unsigned -I $androidJar --manifest "$root\src\main\AndroidManifest.xml" -A "$root\src\main\assets" --min-sdk-version 26 --target-sdk-version 34 --version-code 102 --version-name 1.0.2 "$out\res.zip"
if ($LASTEXITCODE) { throw 'aapt2 failed' }

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open($unsigned, 'Update')
[void][System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, "$out\dex\classes.dex", 'classes.dex')
$zip.Dispose()

$aligned = "$out\aligned.apk"
& "$($bt.FullName)\zipalign.exe" -f 4 $unsigned $aligned
$ks = "$root\debug.keystore"
if (-not (Test-Path $ks)) {
    keytool -genkeypair -keystore $ks -storepass android -keypass android -alias key -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=HyperOS UI Skipper" | Out-Null
}
$final = "$root\HyperOS-UI-Skipper.apk"
& "$($bt.FullName)\apksigner.bat" sign --ks $ks --ks-pass pass:android --key-pass pass:android --out $final $aligned
if ($LASTEXITCODE) { throw 'apksigner failed' }
"Built $final"
