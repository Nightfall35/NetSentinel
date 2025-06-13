@echo off
echo [*] Compiling all java sources.....

javac -cp ".;lib/json-20210307.jar" -d build src\server\*.java src\client\*.java

if %ERRORLEVEL% NEQ 0 (
     echo[!] Compilation failed.
     echo contact creator or try manual compilation(should show you the stack trace)
) else (
     echo [✓] Compilation successful!
)
pause