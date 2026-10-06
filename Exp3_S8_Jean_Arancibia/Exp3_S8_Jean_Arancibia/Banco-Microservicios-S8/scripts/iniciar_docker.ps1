$ErrorActionPreference='Stop'
$raiz=Split-Path -Parent $PSScriptRoot
Set-Location $raiz
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {throw 'Instala Docker Desktop y espera que esté iniciado.'}
if (-not (Test-Path .env)) {Copy-Item .env.example .env;Write-Host 'Se creó .env con los valores de la demostración local.'}
docker info *> $null
if ($LASTEXITCODE -ne 0) {throw 'Docker Desktop todavía no está disponible. Inícialo y vuelve a ejecutar.'}
docker compose -f docker-compose.yaml config --quiet
if ($LASTEXITCODE -ne 0) {throw 'Revisa las variables de .env.'}
docker compose -f docker-compose.yaml up --build --wait --wait-timeout 420
if ($LASTEXITCODE -ne 0) {throw 'Un componente no inició. Revisa: docker compose logs'}
docker compose -f docker-compose.yaml ps
Write-Host 'Listo: API 8081, OAuth2 9000, Eureka 8761, Config 8888, Artemis 8161.'
