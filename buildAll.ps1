# Builds Better PV for every Minecraft version in versions/ and collects the jars in build\dist.
# Usage: .\buildAll.ps1
$ErrorActionPreference = 'Continue'
Set-Location $PSScriptRoot
$version = (Select-String -Path gradle.properties -Pattern '^version=(.+)$').Matches[0].Groups[1].Value.Trim()
$dist = Join-Path $PSScriptRoot 'build\dist'
if (Test-Path $dist) { Remove-Item $dist -Recurse -Force }
New-Item -ItemType Directory -Path $dist | Out-Null
foreach ($file in Get-ChildItem versions -Filter '*.properties') {
	$mc = $file.BaseName
	Write-Host "== Building Better PV $version for Minecraft $mc =="
	& .\gradlew.bat build "-Pmc=$mc" | Out-Host
	if ($LASTEXITCODE -ne 0) { throw "Build for Minecraft $mc failed" }
	Copy-Item "build\libs\notenoughupdates-mc$mc-$version.jar" (Join-Path $dist "Better PV-$version-mc$mc.jar")
}
Get-ChildItem $dist | Select-Object Name, Length
