@echo off
setlocal enabledelayedexpansion

cd /d "%~dp0"

where javac >nul 2>nul
if %errorlevel% neq 0 (
    for /d %%D in ("%LOCALAPPDATA%\Programs\Java\jdk-*") do (
        if exist "%%D\bin\javac.exe" (
            set "PATH=%%D\bin;%PATH%"
        )
    )
)

if not exist "out" mkdir "out"

echo Compiling Java sources...
dir /s /b src\*.java > sources.txt
javac --release 17 -encoding UTF-8 -d out @sources.txt
set EXIT_CODE=%errorlevel%
del sources.txt

if %EXIT_CODE% neq 0 (
    echo Compilation failed. Ensure JDK 17+ is installed and on PATH.
    exit /b %EXIT_CODE%
)

echo Starting AI Email Spam Detection Server...
java -cp out web.SpamApiServer %*
