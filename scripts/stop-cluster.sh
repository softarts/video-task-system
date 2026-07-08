#!/bin/bash

echo "Stopping Video Task System Cluster..."

# Stop ZooKeeper
ZK_HOME="C:/cwork/zookeeper/bin"
cd "$ZK_HOME" || exit
./zkServer.cmd stop
cd - || exit

# Kill Java processes for this project
pkill -f "video-task-system"

echo "Cluster stopped."
