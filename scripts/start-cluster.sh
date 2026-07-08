#!/bin/bash

# Configuration
ZK_HOME="C:/cwork/zookeeper/bin"
STRATEGY=${1:-ROUND_ROBIN}
BASE_DIR=$(pwd)

echo "Starting Video Task System Cluster with strategy: $STRATEGY"

# Create logs directory
mkdir -p logs
# Note: On Windows via shell, this might need adjustment. 
# Assuming calling the cmd version from Git Bash or similar.
cd "$ZK_HOME" || exit
./zkServer.cmd start &
sleep 5
cd "$BASE_DIR" || exit

# 2. Build Project
echo "Building project..."
mvn clean install -DskipTests

# 3. Start 3 Instances
echo "Starting 3 instances..."
nohup java -DPORT=8080 -DSTRATEGY=$STRATEGY -jar scheduler/target/scheduler-0.0.1-SNAPSHOT.jar > logs/node1.log 2>&1 &
nohup java -DPORT=8081 -DSTRATEGY=$STRATEGY -jar scheduler/target/scheduler-0.0.1-SNAPSHOT.jar > logs/node2.log 2>&1 &
nohup java -DPORT=8082 -DSTRATEGY=$STRATEGY -jar scheduler/target/scheduler-0.0.1-SNAPSHOT.jar > logs/node3.log 2>&1 &

echo "Cluster started on ports 8080, 8081, 8082"
echo "Logs are in the logs/ directory"
