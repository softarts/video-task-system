#!/bin/bash

# Configuration
PORT=${1:-8080}

echo "Checking Video Task System Status..."

# Check Java processes
echo "--- Running Processes ---"
ps -ef | grep "video-task-system" | grep -v grep

# Check ZooKeeper
echo ""
echo "--- ZooKeeper Status ---"
netstat -ano | grep 2181

# Run CLI client for cluster summary
echo ""
echo "--- Cluster Summary ---"
./client.sh cluster-status
./client.sh leader
./client.sh worker-load
