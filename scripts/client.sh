#!/bin/bash
cd "$(dirname "$0")/.." || exit
mvn -pl client-cli spring-boot:run -Dspring-boot.run.arguments="$*"
