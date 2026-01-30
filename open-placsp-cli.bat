@echo off
setlocal
set DIR=%~dp0

REM Buscar Java: 1) JAVA_HOME del sistema, 2) jdk/ local, 3) java en PATH
if defined JAVA_HOME (
    set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
) else if exist "%DIR%jdk\bin\java.exe" (
    set "JAVA_CMD=%DIR%jdk\bin\java.exe"
) else (
    set "JAVA_CMD=java"
)

"%JAVA_CMD%" -cp "%DIR%target\classes;%DIR%lib\*" es.age.dgpe.placsp.risp.parser.cli.AtomToExcelCLI %*
endlocal
