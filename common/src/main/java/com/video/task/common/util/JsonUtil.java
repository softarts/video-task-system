package com.video.task.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JsonUtil {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static byte[] serialize(Object obj) {
        try {
            return mapper.writeValueAsBytes(obj);
        } catch (Exception e) {
            log.error("Serialization error", e);
            return new byte[0];
        }
    }

    public static <T> T deserialize(byte[] data, Class<T> clazz) {
        try {
            return mapper.readValue(data, clazz);
        } catch (Exception e) {
            log.error("Deserialization error", e);
            return null;
        }
    }
}
