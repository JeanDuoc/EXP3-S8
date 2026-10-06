param([switch]$SinCompilar)
$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot
Set-Location $raiz
# Solo credenciales de OAuth2 e infraestructura; la base local sigue siendo H2.
if (Test-Path "$raiz\.env") {
    $permitidas=@('CLOUD_USER','CLOUD_PASSWORD','OAUTH_ADMIN_SECRET','OAUTH_READER_SECRET','ADMIN_USER','ADMIN_PASSWORD','READER_USER','READER_PASSWORD')
    foreach ($linea in Get-Content "$raiz\.env") {
        if ($linea.Trim() -and -not $linea.Trim().StartsWith('#') -and $linea.Contains('=')) {
            $par=$linea.Split('=',2)
            if ($permitidas -contains $par[0]) { [Environment]::SetEnvironmentVariable($par[0],$par[1].Trim(),'Process') }
        }
    }
}
if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw 'Instala Java 17 y configura JAVA_HOME antes de continuar.' }
foreach ($puerto in @(8888,8761,9000,8081)) {
    if (Get-NetTCPConnection -LocalPort $puerto -State Listen -ErrorAction SilentlyContinue) { throw "El puerto $puerto está ocupado. Detén la ejecución anterior." }
}
if (-not $SinCompilar) {
    & "$raiz\mvnw.cmd" clean verify
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo compilar. Revisa el mensaje de Maven.' }
}
New-Item -ItemType Directory -Force "$raiz\logs" | Out-Null
function Iniciar-Servicio($nombre,$puerto,$argumentosExtra) {
    $jar = Join-Path $raiz "$nombre\target\$nombre-2.0.0.jar"
    if (-not (Test-Path $jar)) { throw "No existe $jar. Compila el proyecto primero." }
    $argumentos = "-jar `"$jar`" $argumentosExtra"
    $proceso = Start-Process -FilePath 'java' -ArgumentList $argumentos -WorkingDirectory $raiz -RedirectStandardOutput "$raiz\logs\$nombre.log" -RedirectStandardError "$raiz\logs\$nombre-error.log" -PassThru
    $proceso.Id | Set-Content "$raiz\logs\$nombre.pid"
    $listo = $false
    for ($i=0; $i -lt 90; $i++) {
        Start-Sleep -Seconds 2
        if ($proceso.HasExited) { throw "$nombre se cerró. Revisa logs\$nombre.log y logs\$nombre-error.log." }
        try {
            $salud=Invoke-RestMethod -Uri "http://localhost:$puerto/actuator/health" -TimeoutSec 2
            if ($salud.status -eq 'UP') { $listo=$true; break }
        } catch { }
    }
    if (-not $listo) { throw "$nombre no inició dentro del tiempo esperado. Revisa logs." }
    Write-Host "$nombre listo en el puerto $puerto" -ForegroundColor Green
}
Iniciar-Servicio 'config-server' 8888 ''
Iniciar-Servicio 'discovery-server' 8761 ''
Iniciar-Servicio 'auth-server' 9000 ''
Iniciar-Servicio 'cuentas-service' 8081 '--spring.profiles.active=demo,local-jms'
Write-Host 'Servicios listos. Importa postman\Banco-XYZ.postman_collection.json en Postman o Insomnia.'
Write-Host 'Para detener: powershell -ExecutionPolicy Bypass -File .\scripts\parar.ps1'
