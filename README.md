# Video Distributed Task Scheduling System

This project implements a robust, distributed task scheduling system tailored for video surveillance platforms. It utilizes **ZooKeeper** for leader election and cluster coordination, ensuring high availability and efficient task distribution among worker nodes.

## Project Overview

The system manages "camera tasks" (video streams) across multiple worker nodes. Each node runs both a **Scheduler** (Leader-capable) and a **Worker** component within the same process.

- **Leader Election**: Automated via Apache Curator. Only the leader assigns tasks.
- **Worker Execution**: Workers process assigned tasks (dummy processing: log + sleep).
- **Failover**: Automatic leader re-election and task re-assignment if a worker or leader crashes.
- **Scalability**: New workers can join the cluster dynamically.

## Architecture

### ZooKeeper Structure
The system follows a flat ZNode hierarchy for coordination:
- `/video-cluster/leader`: Parent path for curator leader election.
- `/video-cluster/workers/{workerId}`: Ephemeral nodes containing `WorkerInfo` (load, capacity).
- `/video-cluster/tasks/{cameraId}`: Persistent nodes representing the desired state of tasks.
- `/video-cluster/assignments/{workerId}`: Data node containing the list of task IDs assigned to a specific worker.
- `/video-cluster/status`: Current leader ID.

### Scheduling Strategies
The leader rebalances tasks whenever the worker pool or task list changes:
1. **Round-Robin**: Simple cyclic distribution.
2. **Least-Workload**: Assigns tasks to workers with the lowest current load relative to capacity.

## Tech Stack
- **Java 17**
- **Spring Boot 3.2.2**
- **Apache ZooKeeper 3.9.x**
- **Apache Curator 5.5.0** (Recipes: LeaderSelector, CuratorCache)
- **Picocli** (for CLI Client)
- **Maven** (Multi-module build)

## Build Instructions

Ensure you have Java 17 and Maven installed.

```bash
cd video-task-system
mvn clean install
```

## Running the Cluster

### Prerequisites
- ZooKeeper installed at `C:\cwork\zookeeper\bin`.
- Git Bash or a similar shell on Windows.

### Start Cluster
Starts 3 service instances and ZooKeeper.
```bash
./scripts/start-cluster.sh ROUND_ROBIN
```

### Stop Cluster
```bash
./scripts/stop-cluster.sh
```

### Check Status
```bash
./scripts/status.sh
```

## CLI Usage Examples

Use the `client.sh` script to interact with the cluster:

```bash
# Query Leader
./scripts/client.sh leader

# List Workers & Load
./scripts/client.sh worker-list
./scripts/client.sh worker-load

# Submit Task
./scripts/client.sh task-submit camera-001 192.168.1.101

# List Tasks
./scripts/client.sh task-list
```

## Failover Demonstration
1. Start the cluster.
2. Submit 5 tasks.
3. Observe logs (in `logs/`) to see which workers are processing which cameras.
4. Kill the current leader process (check `status.sh` or `logs/`).
5. Verify (via `client.sh leader`) that a new leader is elected.
6. Kill a worker process.
7. Verify (via `logs/`) that the tasks assigned to the dead worker are re-assigned to the remaining workers.

---

## Original AI Prompt
> Generate a complete runnable distributed task scheduling system for a video surveillance platform with the following requirements:
> ... (Full prompt text as provided in the request)
