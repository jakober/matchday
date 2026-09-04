# Erzeugt einmalig den Upload-Schluessel fuer den Play Store.
#
# Google (Play App Signing) signiert die App fuer die Nutzer mit einem
# eigenen Schluessel. Dieser hier weist nur den Upload aus. Geht er
# verloren, laesst er sich in der Play Console zuruecksetzen - anders als
# frueher, als ein verlorener Schluessel das Ende der App bedeutete.
#
# Das Passwort wird zufaellig erzeugt und nur in die Datei geschrieben,
# nie angezeigt. Beides liegt ausserhalb des Repos in ~/.matchday; der
# Ordner gehoert in eine Sicherung.
#
# Aufruf: powershell -File store/upload-key.ps1

$dir = Join-Path $env:USERPROFILE ".matchday"
$store = Join-Path $dir "upload.jks"
$props = Join-Path $dir "keystore.properties"

if (Test-Path $store) { Write-Host "Schluessel vorhanden: $store"; exit 0 }
New-Item -ItemType Directory -Force $dir | Out-Null

$chars = ([char[]]'abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789')
$pw = -join (1..28 | ForEach-Object { $chars | Get-Random })

$candidates = @()
if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME "bin\keytool.exe") }
$candidates += "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"
$candidates += (Get-ChildItem "C:\Program Files\Java\*\bin\keytool.exe","C:\Program Files\Eclipse Adoptium\*\bin\keytool.exe","$env:USERPROFILE\.jdks\*\bin\keytool.exe" -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
$cmd = Get-Command keytool -ErrorAction SilentlyContinue
if ($cmd) { $candidates = @($cmd.Source) + $candidates }
$keytool = $candidates | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
if (-not $keytool) { Write-Error "keytool nicht gefunden (JDK oder Android Studio?)"; exit 1 }
$dname = "CN=Matchday Upload, O=Mathis Jakober, C=DE"

& $keytool -genkeypair -v -keystore $store -alias upload -keyalg RSA -keysize 2048 `
  -validity 10000 -storepass $pw -keypass $pw `
  -dname $dname | Out-Null
if ($LASTEXITCODE -ne 0) { Write-Error "keytool fehlgeschlagen"; exit 1 }

$content = "storeFile=$($store -replace '\\','/')`nstorePassword=$pw`nkeyAlias=upload`nkeyPassword=$pw`n"
[System.IO.File]::WriteAllText($props, $content)
Write-Host "Schluessel angelegt: $store"
Write-Host "Zugangsdaten: $props (nicht anzeigen, sichern!)"
