@echo off
setlocal enabledelayedexpansion

set PORT=%1
set STRATEGY=%2

if "%PORT%"=="" (
    echo Usage: start-worker.bat ^<port^> [strategy]
    echo Example: start-worker.bat 8080 ROUND_ROBIN
    exit /b 1
)

if "%STRATEGY%"=="" set STRATEGY=ROUND_ROBIN

echo Starting worker on port %PORT% with strategy %STRATEGY%...

cd /d "%~dp0.."
java -DPORT=%PORT% -DSTRATEGY=%STRATEGY% -jar scheduler\target\scheduler-0.0.1-SNAPSHOT.jar
