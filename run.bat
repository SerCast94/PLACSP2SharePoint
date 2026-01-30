@echo off
chcp 65001 >nul
SETLOCAL ENABLEDELAYEDEXPANSION

echo.
echo  ========================================================
echo          PLACSP2SharePoint - Workflow Automatizado
echo    Descarga - Conversion a Excel - Subida a SharePoint
echo  ========================================================
echo.

REM Verificar .env
if not exist .env (
    echo  [ERROR] No existe archivo .env
    echo  Copie .env.example a .env y configure sus credenciales
    exit /b 1
)

REM Cargar variables de entorno desde .env
for /f "usebackq tokens=1,2 delims==" %%A in (".env") do (
    set "line=%%A"
    if not "!line:~0,1!"=="#" (
        if not "%%A"=="" set "%%A=%%B"
    )
)

echo  [INFO] Configuracion cargada desde .env
echo.

REM Buscar Java: 1) JAVA_HOME del sistema, 2) jdk/ local, 3) java en PATH
if defined JAVA_HOME (
    set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
) else if exist "%~dp0jdk\bin\java.exe" (
    set "JAVA_CMD=%~dp0jdk\bin\java.exe"
) else (
    set "JAVA_CMD=java"
)

REM Verificar que Java existe
"%JAVA_CMD%" -version >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo  [ERROR] No se encontro Java 21. Opciones:
    echo          1. Instalar Java 21 y configurar JAVA_HOME
    echo          2. Colocar JDK en la carpeta 'jdk' del proyecto
    exit /b 1
)

echo  [INFO] Ejecutando workflow...
echo.

"%JAVA_CMD%" -cp "%~dp0target\classes;%~dp0lib\*" es.age.dgpe.placsp.risp.parser.workflow.PlacspWorkflow

set EXIT_CODE=%ERRORLEVEL%

echo.
if %EXIT_CODE% EQU 0 (
    echo  ========================================================
    echo                    PROCESO COMPLETADO
    echo  ========================================================
) else (
    echo  [ERROR] El proceso fallo con codigo %EXIT_CODE%
)
echo.

ENDLOCAL
exit /b %EXIT_CODE%
