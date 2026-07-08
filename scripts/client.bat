@echo off
@REM call mvn -f "%~dp0..\pom.xml" -pl client-cli spring-boot:run -Dspring-boot.run.arguments="%*"

java -jar ..\client-cli\target\client-cli-0.0.1-SNAPSHOT.jar %*


