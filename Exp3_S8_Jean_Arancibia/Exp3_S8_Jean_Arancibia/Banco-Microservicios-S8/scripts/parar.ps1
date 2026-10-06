$raiz=Split-Path -Parent $PSScriptRoot
foreach ($nombre in @('cuentas-service','auth-server','discovery-server','config-server')) {
    $archivo=Join-Path $raiz "logs\$nombre.pid"
    if (Test-Path $archivo) {
        $identificador=[int](Get-Content $archivo)
        $proceso=Get-CimInstance Win32_Process -Filter "ProcessId=$identificador"
        $jar=Join-Path $raiz "$nombre\target\$nombre-2.0.0.jar"
        if ($proceso -and $proceso.Name -like 'java*' -and $proceso.CommandLine.Contains($jar)) {
            Stop-Process -Id $identificador
            Write-Host "$nombre detenido"
        }
        Remove-Item $archivo
    }
}
