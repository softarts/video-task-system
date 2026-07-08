package com.video.task.client.command;

import com.video.task.common.constant.ZkConstants;
import com.video.task.common.model.CameraTask;
import com.video.task.common.model.WorkerInfo;
import com.video.task.common.util.JsonUtil;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.CreateMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;
import java.util.List;

@Component
@Command(name = "client", subcommands = {
        MyCommand.LeaderStatus.class,
        MyCommand.WorkerList.class,
        MyCommand.WorkerLoad.class,
        MyCommand.ClusterStatus.class,
        MyCommand.TaskSubmit.class,
        MyCommand.TaskList.class
})
public class MyCommand implements Callable<Integer> {
    @Override public Integer call() { return 0; }

    @Component
    @Command(name = "leader", description = "Leader status")
    public static class LeaderStatus implements Callable<Integer> {
        @Autowired private CuratorFramework zkClient;
        @Override public Integer call() throws Exception {
            byte[] data = zkClient.getData().forPath(ZkConstants.STATUS);
            String leaderId = new String(data);
            
            String workerPath = ZkConstants.WORKERS + "/" + leaderId;
            if (zkClient.checkExists().forPath(workerPath) != null) {
                WorkerInfo info = JsonUtil.deserialize(zkClient.getData().forPath(workerPath), WorkerInfo.class);
                System.out.printf("Current Leader: %s (Host: %s, Port: %d)%n", leaderId, info.getHost(), info.getPort());
            } else {
                System.out.println("Current Leader ID: " + leaderId + " (Metadata not found)");
            }
            return 0;
        }
    }

    @Component
    @Command(name = "worker-list", description = "List workers")
    public static class WorkerList implements Callable<Integer> {
        @Autowired private CuratorFramework zkClient;
        @Override public Integer call() throws Exception {
            List<String> workers = zkClient.getChildren().forPath(ZkConstants.WORKERS);
            System.out.println("Active Workers:");
            for (String w : workers) {
                byte[] data = zkClient.getData().forPath(ZkConstants.WORKERS + "/" + w);
                WorkerInfo info = JsonUtil.deserialize(data, WorkerInfo.class);
                if (info != null) {
                    System.out.printf("- %s [Host: %s, Port: %d, Load: %d/%d]%n", 
                        w, info.getHost(), info.getPort(), info.getCurrentLoad(), info.getMaxCapacity());
                } else {
                    System.out.println("- " + w);
                }
            }
            return 0;
        }
    }

    @Component
    @Command(name = "worker-load", description = "Worker load")
    public static class WorkerLoad implements Callable<Integer> {
        @Autowired private CuratorFramework zkClient;
        @Override public Integer call() throws Exception {
            for (String w : zkClient.getChildren().forPath(ZkConstants.WORKERS)) {
                WorkerInfo info = JsonUtil.deserialize(zkClient.getData().forPath(ZkConstants.WORKERS + "/" + w), WorkerInfo.class);
                System.out.println(info.getWorkerId() + ": " + info.getCurrentLoad() + "/" + info.getMaxCapacity());
            }
            return 0;
        }
    }

    @Component
    @Command(name = "cluster-status", description = "Cluster status")
    public static class ClusterStatus implements Callable<Integer> {
        @Autowired private CuratorFramework zkClient;
        @Override public Integer call() throws Exception {
            int workers = zkClient.getChildren().forPath(ZkConstants.WORKERS).size();
            int tasks = zkClient.getChildren().forPath(ZkConstants.TASKS).size();
            System.out.println("Workers: " + workers + ", Tasks: " + tasks);
            return 0;
        }
    }

    @Component
    @Command(name = "task-submit", description = "Submit task")
    public static class TaskSubmit implements Callable<Integer> {
        @Autowired private CuratorFramework zkClient;
        @Parameters(index = "0") private String id;
        @Parameters(index = "1") private String ip;
        @Override public Integer call() throws Exception {
            CameraTask task = CameraTask.builder().cameraId(id).cameraIp(ip).timestamp(System.currentTimeMillis()).build();
            zkClient.create().withMode(CreateMode.PERSISTENT).forPath(ZkConstants.TASKS + "/" + id, JsonUtil.serialize(task));
            System.out.println("Submitted " + id);
            return 0;
        }
    }

    @Component
    @Command(name = "task-list", description = "List tasks")
    public static class TaskList implements Callable<Integer> {
        @Autowired private CuratorFramework zkClient;
        @Override public Integer call() throws Exception {
            zkClient.getChildren().forPath(ZkConstants.TASKS).forEach(t -> System.out.println("- " + t));
            return 0;
        }
    }
}
