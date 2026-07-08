@echo off
setlocal enabledelayedexpansion

:: Configuration
set ZK_HOME=C:\cwork\zookeeper\bin
set STRATEGY=%1
if "%STRATEGY%"=="" set STRATEGY=ROUND_ROBIN

echo Starting Video Task System Cluster with strategy: %STRATEGY%

:: Create logs directory
if not exist logs mkdir logs

:: 1. Start ZooKeeper
echo Starting ZooKeeper...
cd /d "%ZK_HOME%"
start zkServer.cmd
timeout /t 5 /nobreak > nul
cd /d "%~dp0.."

:: 2. Build Project
echo Building project...
:: call mvn clean install -DskipTests

:: 3. Start 3 Instances
echo Starting 3 instances...
@REM start "Node 1 - Port 8080" java -DPORT=8080 -DSTRATEGY=%STRATEGY% -jar ..\scheduler\target\scheduler-0.0.1-SNAPSHOT.jar ^> logs\node1.log 2^>^&1
@REM start "Node 2 - Port 8081" java -DPORT=8081 -DSTRATEGY=%STRATEGY% -jar ..\scheduler\target\scheduler-0.0.1-SNAPSHOT.jar ^> logs\node2.log 2^>^&1
@REM start "Node 3 - Port 8082" java -DPORT=8082 -DSTRATEGY=%STRATEGY% -jar ..\scheduler\target\scheduler-0.0.1-SNAPSHOT.jar ^> logs\node3.log 2^>^&1

start "Node 1 - Port 8081" cmd /c "%~dp0start-worker.bat" 8081 %STRATEGY%
start "Node 2 - Port 8082" cmd /c "%~dp0start-worker.bat" 8082 %STRATEGY%
start "Node 3 - Port 8083" cmd /c "%~dp0start-worker.bat" 8083 %STRATEGY%

echo Cluster started. Check logs directory for output.
pause
