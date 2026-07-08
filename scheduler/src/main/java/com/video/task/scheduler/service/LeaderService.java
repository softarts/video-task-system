package com.video.task.scheduler.service;

import com.video.task.common.constant.ZkConstants;
import com.video.task.common.model.Assignment;
import com.video.task.common.model.CameraTask;
import com.video.task.common.model.WorkerInfo;
import com.video.task.common.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import org.apache.curator.framework.recipes.cache.CuratorCacheListener;
import org.apache.curator.framework.recipes.leader.LeaderSelector;
import org.apache.curator.framework.recipes.leader.LeaderSelectorListenerAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.io.Closeable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@Slf4j
public class LeaderService extends LeaderSelectorListenerAdapter implements Closeable {

    @Autowired
    @Lazy
    private CuratorFramework zkClient;

    @Value("${server.port:8080}")
    private int port;

    @Value("${scheduler.strategy:ROUND_ROBIN}")
    private String strategy;

    private LeaderSelector leaderSelector;
    private final AtomicBoolean isLeader = new AtomicBoolean(false);
    private String leaderId;

    private CuratorCache workersCache;
    private CuratorCache tasksCache;

    @jakarta.annotation.PostConstruct
    public void init() {
        this.leaderId = "worker-" + port;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        leaderSelector = new LeaderSelector(zkClient, ZkConstants.LEADER, this);
        leaderSelector.autoRequeue();
        leaderSelector.start();
        log.info("Leader selector started for {}", leaderId);
    }

    @Override
    public void takeLeadership(CuratorFramework client) throws Exception {
        log.info("!!!! I AM THE LEADER: {} !!!!", leaderId);
        isLeader.set(true);
        updateLeaderStatusInZk();
        
        startMonitoring();

        try {
            // Keep leadership until JVM crash or interruption
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            log.info("Leadership interupted");
            Thread.currentThread().interrupt();
        } finally {
            isLeader.set(false);
            stopMonitoring();
            log.info("Relinquished leadership");
        }
    }

    private void updateLeaderStatusInZk() {
        try {
            if (zkClient.checkExists().forPath(ZkConstants.STATUS) == null) {
                zkClient.create().creatingParentsIfNeeded().forPath(ZkConstants.STATUS);
            }
            zkClient.setData().forPath(ZkConstants.STATUS, leaderId.getBytes());
        } catch (Exception e) {
            log.error("Failed to update leader status", e);
        }
    }

    private void startMonitoring() {
        workersCache = CuratorCache.build(zkClient, ZkConstants.WORKERS);
        tasksCache = CuratorCache.build(zkClient, ZkConstants.TASKS);

        CuratorCacheListener workerListener = CuratorCacheListener.builder()
                .forCreates(node -> rebalance())
                .forDeletes(node -> rebalance())
                .build();

        CuratorCacheListener taskListener = CuratorCacheListener.builder()
                .forCreates(node -> rebalance())
                .forDeletes(node -> rebalance())
                .build();

        workersCache.listenable().addListener(workerListener);
        tasksCache.listenable().addListener(taskListener);

        workersCache.start();
        tasksCache.start();
        
        log.info("Leader monitoring started");
    }

    private void stopMonitoring() {
        if (workersCache != null) workersCache.close();
        if (tasksCache != null) tasksCache.close();
    }

    private synchronized void rebalance() {
        if (!isLeader.get()) return;

        log.info("Rebalancing tasks...");
        try {
            List<String> workerNodes = zkClient.getChildren().forPath(ZkConstants.WORKERS);
            List<String> taskNodes = zkClient.getChildren().forPath(ZkConstants.TASKS);

            if (workerNodes.isEmpty()) {
                log.warn("No workers available for rebalance");
                return;
            }

            Map<String, List<String>> newAssignments = new HashMap<>();
            workerNodes.forEach(w -> newAssignments.put(w, new ArrayList<>()));

            if ("ROUND_ROBIN".equalsIgnoreCase(strategy)) {
                int idx = 0;
                for (String taskId : taskNodes) {
                    String workerId = workerNodes.get(idx % workerNodes.size());
                    newAssignments.get(workerId).add(taskId);
                    idx++;
                }
            } else { // LEAST_WORKLOAD
                // This is a simplified version. Ideally we'd look at WorkerInfo.currentLoad
                // But for now let's just distribute evenly.
                for (String taskId : taskNodes) {
                    String bestWorker = workerNodes.stream()
                            .min(Comparator.comparingInt(w -> newAssignments.get(w).size()))
                            .orElse(workerNodes.get(0));
                    newAssignments.get(bestWorker).add(taskId);
                }
            }

            // Update ZK assignments
            for (Map.Entry<String, List<String>> entry : newAssignments.entrySet()) {
                String assignmentPath = ZkConstants.ASSIGNMENTS + "/" + entry.getKey();
                Assignment assignment = new Assignment(entry.getKey(), entry.getValue());
                if (zkClient.checkExists().forPath(assignmentPath) == null) {
                    zkClient.create().creatingParentsIfNeeded().forPath(assignmentPath, JsonUtil.serialize(assignment));
                } else {
                    zkClient.setData().forPath(assignmentPath, JsonUtil.serialize(assignment));
                }
            }
            
            log.info("Rebalance complete. Assignments: {}", newAssignments);

        } catch (Exception e) {
            log.error("Rebalance failed", e);
        }
    }

    @Override
    @PreDestroy
    public void close() {
        if (leaderSelector != null) leaderSelector.close();
        stopMonitoring();
    }
}
