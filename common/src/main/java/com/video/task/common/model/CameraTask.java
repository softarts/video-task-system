package com.video.task.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CameraTask {
    private String cameraId;
    private String cameraIp;
    private long timestamp;
}
