@echo off
setlocal enabledelayedexpansion

:: Colors for output (Windows 10+)
set "GREEN=[92m"
set "YELLOW=[93m"
set "RED=[91m"
set "NC=[0m"

:: Function to print colored output
goto :main

:print_status
echo %GREEN%[INFO]%NC% %~1
exit /b

:print_warning
echo %YELLOW%[WARN]%NC% %~1
exit /b

:print_error
echo %RED%[ERROR]%NC% %~1
exit /b

:stop_runelite
call :print_status "Stopping existing RuneLite instances..."

:: Find and kill RuneLite/Microbot processes
for /f "tokens=2" %%i in ('tasklist /fi "imagename eq java.exe" /fo csv ^| findstr /i "microbot\|client" 2^>nul') do (
    set "pid=%%i"
    set "pid=!pid:"=!"
    if defined pid (
        call :print_warning "Killing RuneLite process !pid!"
        taskkill /pid !pid! /f >nul 2>&1
    )
)

:: Additional check for any java process running microbot or client jars
wmic process where "name='java.exe' and commandline like '%%microbot%%.jar%%' or commandline like '%%client%%.jar%%'" get processid /format:value 2>nul | findstr "ProcessId" >temp_pids.txt
if exist temp_pids.txt (
    for /f "tokens=2 delims==" %%i in (temp_pids.txt) do (
        if "%%i" neq "" (
            call :print_warning "Killing RuneLite process %%i"
            taskkill /pid %%i /f >nul 2>&1
        )
    )
    del temp_pids.txt
) else (
    call :print_status "No running RuneLite instances found"
)

timeout /t 2 /nobreak >nul
exit /b

:main
:: Find the jar file dynamically - try microbot jar first, then client jar
set "JAR_PATH="
for /f "delims=" %%i in ('dir /b /s runelite-client\target\microbot-*.jar 2^>nul') do (
    set "JAR_PATH=%%i"
    goto :jar_found
)

:: Fallback to client jar
for /f "delims=" %%i in ('dir /b /s runelite-client\target\client-*.jar 2^>nul') do (
    set "JAR_PATH=%%i"
    goto :jar_found
)

:jar_not_found
call :print_error "No executable jar file found in runelite-client\target\"
call :print_error "Expected: microbot-*.jar or client-*.jar"
call :print_error "Please run _build_and_start.bat first to build the project"
dir runelite-client\target\ 2>nul
exit /b 1

:jar_found
if not exist "!JAR_PATH!" (
    goto :jar_not_found
)

call :print_status "Found jar: !JAR_PATH!"

:: Stop existing instances
call :stop_runelite

:: Start RuneLite
call :print_status "Starting RuneLite..."
java --add-opens java.desktop/com.apple.eawt=ALL-UNNAMED --add-exports java.desktop/com.apple.eawt=ALL-UNNAMED -jar "!JAR_PATH!"
