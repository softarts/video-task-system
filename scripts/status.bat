@echo off
echo Checking Video Task System Status...

echo --- Running Processes ---
tasklist /FI "IMAGENAME eq java.exe" /V | findstr "Node"

echo.
echo --- ZooKeeper Status ---
netstat -ano | findstr :2181

echo.
echo --- Cluster Summary ---
call %~dp0client.bat cluster-status
call %~dp0client.bat leader
call %~dp0client.bat worker-load
pause
