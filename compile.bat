@echo off
echo [*] Compiling all Java sources...

:: === Paths ===
set FX="C:\Users\ishma\Desktop\Architect\JServer\lib\openjfx-24.0.1_windows-x64_bin-sdk\javafx-sdk-24.0.1\lib"
set JSON="C:\Users\ishma\Desktop\Architect\JServer\lib\json-20210307.jar"
set CP=.;%JSON%;%FX%\*

:: Create build dir if not exists
if not exist build (
    mkdir build
)

:: Compile all server + client sources
javac -cp %CP% --module-path %FX% --add-modules javafx.controls -d build src\server\*.java src\client\*.java

:: Check result
if %ERRORLEVEL% NEQ 0 (
    echo [!] Compilation failed.
    echo     See error messages above or try compiling manually.
) else (
    echo [✓] Compilation successful!
)

pause
