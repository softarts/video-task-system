#!/bin/bash

PORT=$1
STRATEGY=${2:-ROUND_ROBIN}

if [ -z "$PORT" ]; then
    echo "Usage: start-worker.sh <port> [strategy]"
    echo "Example: ./start-worker.sh 8080 ROUND_ROBIN"
    exit 1
fi

echo "Starting worker on port $PORT with strategy $STRATEGY..."

cd "$(dirname "$0")/.." || exit
java -DPORT=$PORT -DSTRATEGY=$STRATEGY -jar scheduler/target/scheduler-0.0.1-SNAPSHOT.jar
