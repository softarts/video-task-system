package com.video.task.worker.service;

import com.video.task.common.constant.ZkConstants;
import com.video.task.common.model.Assignment;
import com.video.task.common.model.CameraTask;
import com.video.task.common.model.WorkerInfo;
import com.video.task.common.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import org.apache.curator.framework.recipes.cache.CuratorCacheListener;
import org.apache.zookeeper.CreateMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class WorkerService {

    @Autowired
    @Lazy
    private CuratorFramework zkClient;

    @Value("${server.port:8080}")
    private int port;

    @Value("${worker.capacity:3}")
    private int maxCapacity;

    private String workerId;
    private final Map<String, CameraTask> currentTasks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(5);
    private CuratorCache assignmentCache;

    @EventListener(ApplicationReadyEvent.class)
    public void start() throws Exception {
        this.workerId = "worker-" + port;
        registerWorker();
        startAssignmentWatcher();
        startHeartbeat();
    }

    private void registerWorker() throws Exception {
        WorkerInfo info = WorkerInfo.builder()
                .workerId(workerId)
                .host(InetAddress.getLocalHost().getHostAddress())
                .port(port)
                .currentLoad(0)
                .maxCapacity(maxCapacity)
                .lastUpdate(System.currentTimeMillis())
                .build();

        String path = ZkConstants.WORKERS + "/" + workerId;
        if (zkClient.checkExists().forPath(path) != null) {
            zkClient.delete().deletingChildrenIfNeeded().forPath(path);
        }
        
        zkClient.create()
                .withMode(CreateMode.EPHEMERAL)
                .forPath(path, JsonUtil.serialize(info));
        
        log.info("Worker registered: {}", workerId);
    }

    private void startAssignmentWatcher() {
        String assignmentPath = ZkConstants.ASSIGNMENTS + "/" + workerId;
        
        try {
            if (zkClient.checkExists().forPath(assignmentPath) == null) {
                zkClient.create().creatingParentsIfNeeded().forPath(assignmentPath, JsonUtil.serialize(new Assignment(workerId, new ArrayList<>())));
            }
        } catch (Exception e) {
            log.error("Failed to create assignment path", e);
        }

        assignmentCache = CuratorCache.build(zkClient, assignmentPath);
        CuratorCacheListener listener = CuratorCacheListener.builder()
                .forChanges((oldNode, newNode) -> {
                    Assignment assignment = JsonUtil.deserialize(newNode.getData(), Assignment.class);
                    if (assignment != null) {
                        handleNewAssignment(assignment);
                    }
                })
                .build();
        
        assignmentCache.listenable().addListener(listener);
        assignmentCache.start();
        log.info("Started assignment watcher for {}", workerId);
    }

    private synchronized void handleNewAssignment(Assignment assignment) {
        log.info("Received new assignment for {}: {}", workerId, assignment.getTaskIds());
        Set<String> assignedTaskIds = new HashSet<>(assignment.getTaskIds());
        
        // Remove tasks no longer assigned
        currentTasks.keySet().removeIf(taskId -> {
            if (!assignedTaskIds.contains(taskId)) {
                log.info("Stopping task: {}", taskId);
                return true;
            }
            return false;
        });

        // Start new tasks
        for (String taskId : assignedTaskIds) {
            if (!currentTasks.containsKey(taskId)) {
                startTask(taskId);
            }
        }
        
        updateWorkload();
    }

    private void startTask(String taskId) {
        // In a real system, we'd fetch task details from ZK
        // For dummy, we just track it
        log.info("Starting processing camera: {}", taskId);
        CameraTask task = CameraTask.builder().cameraId(taskId).build();
        currentTasks.put(taskId, task);
        
        executorService.submit(() -> {
            while (currentTasks.containsKey(taskId)) {
                log.info("[{}] Processing camera <{}>", workerId, taskId);
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            log.info("Task {} stopped on {}", taskId, workerId);
        });
    }

    private void updateWorkload() {
        try {
            String path = ZkConstants.WORKERS + "/" + workerId;
            byte[] data = zkClient.getData().forPath(path);
            WorkerInfo info = JsonUtil.deserialize(data, WorkerInfo.class);
            if (info != null) {
                info.setCurrentLoad(currentTasks.size());
                info.setLastUpdate(System.currentTimeMillis());
                zkClient.setData().forPath(path, JsonUtil.serialize(info));
            }
        } catch (Exception e) {
            log.error("Failed to update workload", e);
        }
    }

    private void startHeartbeat() {
        executorService.scheduleAtFixedRate(this::updateWorkload, 10, 10, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void stop() {
        if (assignmentCache != null) assignmentCache.close();
        executorService.shutdownNow();
    }
}
