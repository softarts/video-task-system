package com.video.task.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkerInfo {
    private String workerId;
    private String host;
    private int port;
    private int currentLoad;
    private int maxCapacity;
    private long lastUpdate;
}
