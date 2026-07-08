@echo off
echo Stopping Video Task System Cluster...

:: Stop ZooKeeper
set ZK_HOME=C:\cwork\zookeeper\bin
cd /d "%ZK_HOME%"
call zkServer.cmd stop
cd /d "%~dp0"

:: Kill Java processes for this project
taskkill /F /FI "WINDOWTITLE eq Node 1 - Port 8081"
taskkill /F /FI "WINDOWTITLE eq Node 2 - Port 8082"
taskkill /F /FI "WINDOWTITLE eq Node 3 - Port 8083"

echo Cluster stopped.
pause
