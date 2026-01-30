@echo off
setlocal
set DIR=%~dp0..
"%DIR%\jdk\bin\java.exe" -cp "%DIR%\target\classes;%DIR%\lib\*" es.age.dgpe.placsp.risp.parser.cli.AtomToExcelCLI %*
endlocal
