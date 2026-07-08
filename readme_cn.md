# 视频分布式任务调度系统技术文档

## 系统概述

该系统是一个基于ZooKeeper的分布式视频监控任务调度系统，采用Leader-Worker架构。每个节点同时运行Scheduler（可成为Leader）和Worker组件，通过ZooKeeper实现协调和故障转移。

## 核心组件分析

### ZooKeeper数据结构

```
/video-cluster/
├── leader/           # Leader选举路径
├── workers/{workerId}  # Worker临时节点（存储WorkerInfo）
├── tasks/{cameraId}     # 持久任务节点（存储CameraTask）
├── assignments/{workerId} # 任务分配节点（存储Assignment）
└── status           # 当前Leader ID
```

---

## 问题1：当Worker挂掉时，任务重新分配机制

### 检测机制

1. **临时节点自动删除**：Worker在ZooKeeper中创建的是**临时节点**（EPHEMERAL），当Worker进程崩溃或网络断开时，ZooKeeper会自动删除该节点
2. **Leader监听变化**：Leader通过`CuratorCache`监听`/video-cluster/workers`路径的节点变化

### 重新分配流程

```java
// LeaderService.java 第99-102行
CuratorCacheListener workerListener = CuratorCacheListener.builder()
    .forCreates(node -> rebalance())  // 新Worker加入时重新平衡
    .forDeletes(node -> rebalance())  // Worker挂掉时重新平衡
    .build();
```

### 具体步骤

1. **检测Worker失效**：ZooKeeper自动删除失效Worker的临时节点
2. **触发重新平衡**：Leader监听到节点删除事件，调用`rebalance()`方法
3. **重新计算分配**：Leader获取当前存活Worker列表和所有任务，按照策略重新分配
4. **更新分配信息**：将新的分配方案写入`/video-cluster/assignments/{workerId}`节点
5. **Worker响应**：存活Worker监听自己的分配节点变化，自动启动新分配的任务

---

## 问题2：Client提交新任务时的分配机制

### 任务提交流程

```java
// MyCommand.java 第101-103行
CameraTask task = CameraTask.builder()
    .cameraId(id).cameraIp(ip).timestamp(System.currentTimeMillis()).build();
zkClient.create().withMode(CreateMode.PERSISTENT)
    .forPath(ZkConstants.TASKS + "/" + id, JsonUtil.serialize(task));
```

### Leader分配机制

1. **任务创建**：Client在`/video-cluster/tasks/{cameraId}`创建持久节点
2. **Leader监听**：Leader监听tasks路径变化，新任务创建时触发重新平衡
3. **分配策略**：
   - **轮询（ROUND_ROBIN）**：按顺序循环分配给Worker
   - **最少负载（LEAST_WORKLOAD）**：分配给当前任务数最少的Worker

```java
// LeaderService.java 第139-145行（轮询策略）
int idx = 0;
for (String taskId : taskNodes) {
    String workerId = workerNodes.get(idx % workerNodes.size());
    newAssignments.get(workerId).add(taskId);
    idx++;
}
```

4. **更新分配**：将分配结果写入对应Worker的assignment节点
5. **Worker执行**：Worker监听到新分配后开始处理任务

---

## 问题3：Leader挂掉后的选举机制

### 选举机制

系统使用Apache Curator的`LeaderSelector`实现Leader选举：

```java
// LeaderService.java 第57-59行
leaderSelector = new LeaderSelector(zkClient, ZkConstants.LEADER, this);
leaderSelector.autoRequeue();  // 自动重新排队
leaderSelector.start();
```

### 选举流程

1. **Leader失效检测**：ZooKeeper通过心跳机制检测Leader会话失效
2. **自动触发选举**：Curator框架自动在候选节点中选举新Leader
3. **获得领导权**：新Leader调用`takeLeadership()`方法
4. **更新状态**：新Leader将自己的ID写入`/video-cluster/status`节点

```java
// LeaderService.java 第64-82行
@Override
public void takeLeadership(CuratorFramework client) throws Exception {
    log.info("!!!! I AM THE LEADER: {} !!!!", leaderId);
    isLeader.set(true);
    updateLeaderStatusInZk();  // 更新Leader状态
    
    startMonitoring();  // 开始监控Worker和Task变化
    
    try {
        Thread.sleep(Long.MAX_VALUE);  // 保持领导权
    } catch (InterruptedException e) {
        // 领导权被中断
    } finally {
        isLeader.set(false);
        stopMonitoring();
    }
}
```

---

## 问题4：新Leader选举后的信息同步机制

### 同步策略

新Leader通过**重新扫描ZooKeeper状态**来确保信息同步：

#### 1. 启动监控机制

```java
// LeaderService.java 第95-116行
private void startMonitoring() {
    workersCache = CuratorCache.build(zkClient, ZkConstants.WORKERS);
    tasksCache = CuratorCache.build(zkClient, ZkConstants.TASKS);
    
    // 监听Worker和Task变化
    workersCache.listenable().addListener(workerListener);
    tasksCache.listenable().addListener(taskListener);
}
```

#### 2. 立即触发重新平衡

新Leader启动时会立即调用`rebalance()`方法：
- 扫描`/video-cluster/workers`获取当前存活Worker列表
- 扫描`/video-cluster/tasks`获取所有待处理任务
- 根据配置策略重新计算任务分配
- 更新所有Worker的assignment节点

#### 3. 持续监听变化

通过CuratorCache持续监听ZooKeeper变化，确保状态实时同步：
- Worker上线/下线时自动重新平衡
- 新任务提交时自动分配
- 任务删除时自动调整

### 数据一致性保证

1. **ZooKeeper原子性**：所有ZooKeeper操作都是原子的
2. **单点写入**：只有Leader能修改assignment节点
3. **事件驱动**：通过监听机制确保状态变化及时响应
4. **全量同步**：新Leader通过全量扫描重建完整状态

---

## 系统容错特性

### Worker容错
- 临时节点自动清理
- 任务自动重新分配
- 负载均衡策略

### Leader容错
- 自动选举新Leader
- 状态完全恢复
- 无单点故障

### 网络分区处理
- ZooKeeper保证强一致性
- 分区恢复后自动重新平衡
- 数据不会丢失

---

## 总结

该系统通过ZooKeeper的特性和Curator框架的recipes，实现了完整的分布式协调和故障恢复机制，确保了系统的高可用性和数据一致性。

## 系统架构与设计

### 概览
系统采用 Leader-Worker 模型，基于 ZooKeeper 提供服务发现、Leader 选举与配置存储。Leader 负责全局任务调度与分配，Workers 执行摄像头任务与汇报状态。

### 部署拓扑
- 多节点部署，建议至少 3 个 ZooKeeper 节点以保证 Quorum。
- 每个应用节点可同时运行 Scheduler（参与选举）和 Worker。
- 推荐将应用节点和 ZooKeeper 部署在网络延迟可控的环境内以提高稳定性。

### 组件职责
- ZooKeeper：负责协调、元数据存储（tasks、workers、assignments）与临时节点机制。
- Leader / Scheduler：Leader 选举、全局任务分配、负载均衡和故障恢复逻辑。
- Worker：监听分配、执行任务、上报任务状态并维护临时节点以表示存活。
- Client：提交/撤销任务、查询任务与节点状态。

### 任务生命周期（数据流）
1. Client 在 /video-cluster/tasks/{cameraId} 创建持久任务节点。
2. Leader 监听到新任务并根据调度策略计算目标 Worker。
3. Leader 将分配结果写入 /video-cluster/assignments/{workerId}。
4. 对应 Worker 监听 assignment，获取任务并开始执行，同时更新状态节点。
5. 任务完成后 Worker 上报结果并清理本地任务状态；Leader/Client 可根据需要删除任务节点或归档。

### 一致性与容错
- 单一写入源：仅 Leader 写入 assignment，避免并发冲突。
- 临时节点（EPHEMERAL）用于检测 Worker 下线并触发重分配。
- 新 Leader 启动时执行全量扫描（workers + tasks），并触发一次完整的 rebalance，以保证状态收敛。

### 扩展性与性能
- 支持通过 cameraId 哈希或分片策略实现任务分区，降低单个 Leader 的计算压力。
- 通过批量写入 assignment 和减少 ZooKeeper 写频率来降低写放大。
- 可水平扩展 Worker 数量；当任务规模极大时，可考虑将调度分层或按租户/地域分区。

### 安全与配置要点
- 启用 ZooKeeper 的 ACL 与传输加密（TLS）以保护元数据与通信。
- 调整会话超时和重试策略以在容错与快速感知之间取得平衡。
- 监控关键指标：活跃 Worker 数、挂起任务数、ZooKeeper 延迟与重试率。

### 设计权衡
- 采用 ZooKeeper 提供强一致性，带来更简单且可证明的收敛性，但会有写延迟与单点写入的吞吐限制。
- 将调度逻辑集中在 Leader 简化一致性设计，但需关注 Leader 的负载与可用性；必要时可采用分区或多级调度以提升伸缩性。

以上内容可作为系统设计概要与运维参考，便于部署、扩展与故障排查。
