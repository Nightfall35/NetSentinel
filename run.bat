@echo off
echo [*] Starting ServerControlPanel GUI...

:: === Paths ===
set FX="C:\Users\ishma\Desktop\Architect\JServer\lib\openjfx-24.0.1_windows-x64_bin-sdk\javafx-sdk-24.0.1\lib"
set JSON="C:\Users\ishma\Desktop\Architect\JServer\lib\json-20210307.jar"
set CP=build;%JSON%

java --module-path %FX% --add-modules javafx.controls -cp %CP% server.ServerControlPanel


pause
