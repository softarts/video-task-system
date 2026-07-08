package com.video.task.common.constant;

public class ZkConstants {
    public static final String ROOT = "/video-cluster";
    public static final String WORKERS = ROOT + "/workers";
    public static final String TASKS = ROOT + "/tasks";
    public static final String ASSIGNMENTS = ROOT + "/assignments";
    public static final String LEADER = ROOT + "/leader";
    public static final String STATUS = ROOT + "/status";

    public static final int DEFAULT_ZK_SESSION_TIMEOUT = 60000;
    public static final int DEFAULT_ZK_CONNECTION_TIMEOUT = 15000;
}
