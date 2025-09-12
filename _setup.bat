@echo off
setlocal enabledelayedexpansion

:: Colors for output (Windows 10+)
set "GREEN=[92m"
set "YELLOW=[93m"
set "RED=[91m"
set "BLUE=[94m"
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

:print_header
echo %BLUE%[SETUP]%NC% %~1
exit /b

:command_exists
where %1 >nul 2>&1
exit /b %errorlevel%

:install_chocolatey
call :print_header "Checking Chocolatey installation..."
call :command_exists choco
if %errorlevel% == 0 (
    call :print_status "Chocolatey is already installed"
    exit /b 0
)

call :print_header "Installing Chocolatey..."
call :print_warning "This requires administrator privileges. Please run as administrator if installation fails."

:: Install Chocolatey
powershell -Command "Set-ExecutionPolicy Bypass -Scope Process -Force; [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072; iex ((New-Object System.Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1'))"

if %errorlevel% neq 0 (
    call :print_error "Chocolatey installation failed. Please install manually:"
    call :print_error "1. Run PowerShell as Administrator"
    call :print_error "2. Run: Set-ExecutionPolicy Bypass -Scope Process -Force"
    call :print_error "3. Run: iex ((New-Object System.Net.WebClient).DownloadString('https://chocolatey.org/install.ps1'))"
    exit /b 1
)

:: Refresh environment variables
call refreshenv
exit /b 0

:install_java
call :print_header "Checking Java installation..."

call :command_exists java
if %errorlevel% == 0 (
    for /f "tokens=3" %%i in ('java -version 2^>^&1 ^| findstr /i version') do (
        set JAVA_VERSION=%%i
        set JAVA_VERSION=!JAVA_VERSION:"=!
    )
    call :print_status "Java is already installed: !JAVA_VERSION!"
    
    :: Check if it's Java 11 or higher
    for /f "tokens=1 delims=." %%i in ("!JAVA_VERSION!") do set JAVA_MAJOR=%%i
    if !JAVA_MAJOR! geq 11 (
        call :print_status "Java version is compatible (11+)"
        exit /b 0
    ) else (
        call :print_warning "Java version is too old. Installing Java 17..."
    )
)

call :print_header "Installing Java 17..."

:: Try Chocolatey first
call :command_exists choco
if %errorlevel% == 0 (
    call :print_status "Installing Java 17 via Chocolatey..."
    choco install openjdk17 -y
    if !errorlevel! neq 0 (
        call :print_error "Chocolatey installation failed. Trying manual installation..."
        goto :manual_java
    )
) else (
    call :print_warning "Chocolatey not found. Using manual installation..."
    goto :manual_java
)

goto :java_installed

:manual_java
call :print_status "Please install Java 17 manually:"
call :print_status "1. Go to: https://adoptium.net/temurin/releases/"
call :print_status "2. Download OpenJDK 17 for Windows"
call :print_status "3. Run the installer"
call :print_status "4. Add Java to your PATH environment variable"
call :print_warning "After manual installation, please restart this script."
pause
exit /b 1

:java_installed
:: Refresh environment variables
call refreshenv
exit /b 0

:install_maven
call :print_header "Checking Maven installation..."

call :command_exists mvn
if %errorlevel% == 0 (
    for /f "tokens=3" %%i in ('mvn -version 2^>^&1 ^| findstr /i "Apache Maven"') do (
        set MVN_VERSION=%%i
    )
    call :print_status "Maven is already installed: !MVN_VERSION!"
    exit /b 0
)

call :print_header "Installing Maven..."

:: Try Chocolatey first
call :command_exists choco
if %errorlevel% == 0 (
    call :print_status "Installing Maven via Chocolatey..."
    choco install maven -y
    if !errorlevel! neq 0 (
        call :print_error "Chocolatey installation failed. Trying manual installation..."
        goto :manual_maven
    )
) else (
    call :print_warning "Chocolatey not found. Using manual installation..."
    goto :manual_maven
)

goto :maven_installed

:manual_maven
call :print_status "Please install Maven manually:"
call :print_status "1. Go to: https://maven.apache.org/download.cgi"
call :print_status "2. Download the Binary zip archive"
call :print_status "3. Extract to C:\Program Files\Apache\Maven"
call :print_status "4. Add C:\Program Files\Apache\Maven\bin to your PATH"
call :print_warning "After manual installation, please restart this script."
pause
exit /b 1

:maven_installed
:: Refresh environment variables
call refreshenv
exit /b 0

:verify_installation
call :print_header "Verifying installations..."

call :command_exists java
if %errorlevel% == 0 (
    for /f "tokens=3" %%i in ('java -version 2^>^&1 ^| findstr /i version') do (
        set JAVA_VERSION=%%i
        set JAVA_VERSION=!JAVA_VERSION:"=!
        call :print_status "✓ Java: !JAVA_VERSION!"
    )
) else (
    call :print_error "✗ Java installation failed or not in PATH"
    exit /b 1
)

call :command_exists mvn
if %errorlevel% == 0 (
    for /f "tokens=3" %%i in ('mvn -version 2^>^&1 ^| findstr /i "Apache Maven"') do (
        set MVN_VERSION=%%i
        call :print_status "✓ Maven: !MVN_VERSION!"
    )
) else (
    call :print_error "✗ Maven installation failed or not in PATH"
    exit /b 1
)

call :print_status "✓ All dependencies are installed successfully!"
exit /b 0

:main
call :print_header "Microbot/RuneLite Dependency Setup for Windows"
call :print_status "This script will install Java 17 and Maven"
echo.

:: Check if running as administrator
net session >nul 2>&1
if %errorlevel% neq 0 (
    call :print_warning "Not running as administrator. Some installations may fail."
    call :print_warning "Consider running this script as administrator for best results."
    echo.
)

call :install_chocolatey
if %errorlevel% neq 0 exit /b 1
echo.

call :install_java
if %errorlevel% neq 0 exit /b 1
echo.

call :install_maven
if %errorlevel% neq 0 exit /b 1
echo.

call :verify_installation
if %errorlevel% neq 0 exit /b 1
echo.

call :print_header "Setup completed successfully!"
call :print_status "You can now run '_build_and_start.bat' to build and start the application"
call :print_status "Or create a Windows version of the start script"
echo.
call :print_warning "You may need to restart your command prompt to update PATH variables"
pause
