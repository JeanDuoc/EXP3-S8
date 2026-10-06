$ErrorActionPreference='Stop'
$raiz=Split-Path -Parent $PSScriptRoot
Set-Location $raiz
New-Item -ItemType Directory -Force .\evidencias | Out-Null
$estado=docker compose -f docker-compose.yaml ps
if ($LASTEXITCODE -ne 0) {throw 'No se pudo consultar Docker. Inicia el proyecto primero.'}
$estado | Set-Content -Encoding UTF8 .\evidencias\docker_estado.txt
$imagenes=docker compose -f docker-compose.yaml images
if ($LASTEXITCODE -ne 0) {throw 'No se pudieron consultar las imágenes.'}
$imagenes | Set-Content -Encoding UTF8 .\evidencias\docker_imagenes.txt
$registros=docker compose -f docker-compose.yaml logs --no-color --tail=250
if ($LASTEXITCODE -ne 0) {throw 'No se pudieron leer los logs.'}
$registros | Set-Content -Encoding UTF8 .\evidencias\docker_logs.txt
python .\scripts\probar_api.py
if ($LASTEXITCODE -ne 0) {throw 'Una prueba HTTP falló. Revisa la salida.'}
Write-Host 'Evidencias reales guardadas en la carpeta evidencias.'
