package com.video.task.common.config;

import com.video.task.common.constant.ZkConstants;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class ZkConfig {

    @Value("${zookeeper.connect:localhost:2181}")
    private String zkConnect;

    @Bean(destroyMethod = "close")
    public CuratorFramework curatorFramework() {
        log.info("Connecting to ZooKeeper at {}", zkConnect);
        CuratorFramework client = CuratorFrameworkFactory.builder()
                .connectString(zkConnect)
                .sessionTimeoutMs(ZkConstants.DEFAULT_ZK_SESSION_TIMEOUT)
                .connectionTimeoutMs(ZkConstants.DEFAULT_ZK_CONNECTION_TIMEOUT)
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .build();
        client.start();
        return client;
    }

    @Bean
    public SmartInitializingSingleton zkInitializer(CuratorFramework client) {
        return () -> {
            log.info("Initializing ZooKeeper paths and cleaning up stale data...");
            try {
                ensurePath(client, ZkConstants.ROOT);
                ensurePath(client, ZkConstants.WORKERS);
                ensurePath(client, ZkConstants.TASKS);
                ensurePath(client, ZkConstants.ASSIGNMENTS);
                ensurePath(client, ZkConstants.LEADER);
                
                // Ensure status node exists and is empty or reset
                if (client.checkExists().forPath(ZkConstants.STATUS) == null) {
                    client.create().creatingParentsIfNeeded().forPath(ZkConstants.STATUS, "".getBytes());
                } else {
                    // Check if current status is stale (i.e. not in current ephemeral workers)
                    byte[] data = client.getData().forPath(ZkConstants.STATUS);
                    String currentLeaderId = data != null ? new String(data) : "";
                    if (!currentLeaderId.isEmpty()) {
                        String workerPath = ZkConstants.WORKERS + "/" + currentLeaderId;
                        if (client.checkExists().forPath(workerPath) == null) {
                            log.info("Clearing stale leader status: {}", currentLeaderId);
                            client.setData().forPath(ZkConstants.STATUS, "".getBytes());
                        }
                    }
                }

                // Cleanup stale assignments (nodes whose worker is no longer present)
                List<String> assignedWorkers = client.getChildren().forPath(ZkConstants.ASSIGNMENTS);
                for (String w : assignedWorkers) {
                    if (client.checkExists().forPath(ZkConstants.WORKERS + "/" + w) == null) {
                        log.info("Clearing stale assignment for worker: {}", w);
                        client.delete().deletingChildrenIfNeeded().forPath(ZkConstants.ASSIGNMENTS + "/" + w);
                    }
                }

                log.info("ZooKeeper initialization and cleanup successful.");
            } catch (Exception e) {
                log.error("Failed to initialize or clean ZooKeeper nodes", e);
            }
        };
    }

    private void ensurePath(CuratorFramework client, String path) throws Exception {
        if (client.checkExists().forPath(path) == null) {
            client.create().creatingParentsIfNeeded().forPath(path);
            log.info("Created ZooKeeper path: {}", path);
        }
    }
}
