# ============================================================
# Script de instalación de Java 21 en el sistema
# REQUIERE: Ejecutar como Administrador
# ============================================================

Write-Host ""
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "  Instalación de Zulu OpenJDK 21" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host ""

# Verificar si se ejecuta como administrador
$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

if (-not $isAdmin) {
    Write-Host "[ERROR] Este script requiere permisos de Administrador" -ForegroundColor Red
    Write-Host "        Haz clic derecho -> 'Ejecutar como administrador'" -ForegroundColor Yellow
    Write-Host ""
    pause
    exit 1
}

Write-Host "[OK] Ejecutando como Administrador" -ForegroundColor Green
Write-Host ""

# Variables
$sourceJdk = "C:\Users\camose\Desktop\test automate\programa\jdk"
$destJdk = "C:\Program Files\Java\zulu-21"

# Paso 1: Crear directorio destino
Write-Host "[1/4] Creando directorio destino..." -ForegroundColor Yellow
if (-not (Test-Path "C:\Program Files\Java")) {
    New-Item -ItemType Directory -Path "C:\Program Files\Java" -Force | Out-Null
}
if (Test-Path $destJdk) {
    Write-Host "      Eliminando instalación anterior..." -ForegroundColor Gray
    Remove-Item -Path $destJdk -Recurse -Force
}
New-Item -ItemType Directory -Path $destJdk -Force | Out-Null
Write-Host "      $destJdk creado" -ForegroundColor Green

# Paso 2: Copiar JDK
Write-Host "[2/4] Copiando JDK (puede tardar unos segundos)..." -ForegroundColor Yellow
Copy-Item -Path "$sourceJdk\*" -Destination $destJdk -Recurse -Force
Write-Host "      JDK copiado correctamente" -ForegroundColor Green

# Paso 3: Configurar JAVA_HOME
Write-Host "[3/4] Configurando variable JAVA_HOME..." -ForegroundColor Yellow
[System.Environment]::SetEnvironmentVariable("JAVA_HOME", $destJdk, "Machine")
Write-Host "      JAVA_HOME = $destJdk" -ForegroundColor Green

# Paso 4: Añadir al PATH
Write-Host "[4/4] Añadiendo al PATH del sistema..." -ForegroundColor Yellow
$currentPath = [System.Environment]::GetEnvironmentVariable("Path", "Machine")

# Quitar referencias antiguas a Java/Oracle si existen
$pathItems = $currentPath -split ";" | Where-Object { 
    $_ -ne "" -and 
    $_ -notlike "*Oracle*Java*" -and 
    $_ -notlike "*zulu-21*"
}

# Añadir nuevo Java al principio del PATH
$newPath = "$destJdk\bin;" + ($pathItems -join ";")
[System.Environment]::SetEnvironmentVariable("Path", $newPath, "Machine")
Write-Host "      PATH actualizado" -ForegroundColor Green

# Verificación
Write-Host ""
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "  Verificación" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host ""

# Actualizar PATH en la sesión actual
$env:JAVA_HOME = $destJdk
$env:Path = "$destJdk\bin;" + $env:Path

Write-Host "Java instalado:" -ForegroundColor Yellow
& "$destJdk\bin\java.exe" -version

Write-Host ""
Write-Host "=========================================" -ForegroundColor Green
Write-Host "  ¡Instalación completada!" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Green
Write-Host ""
Write-Host "IMPORTANTE: Cierra y vuelve a abrir PowerShell/CMD" -ForegroundColor Yellow
Write-Host "            para que los cambios tengan efecto." -ForegroundColor Yellow
Write-Host ""

pause
