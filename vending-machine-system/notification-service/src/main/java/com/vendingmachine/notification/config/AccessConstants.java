package com.vendingmachine.notification.config;

import java.util.Map;

public class AccessConstants {
    // Header names
    public static final String INTERNAL_SERVICE_HEADER = "X-Internal-Service";
    public static final String REQUEST_SOURCE_HEADER = "X-Request-Source";
    
    // Header values
    public static final String REQUEST_SOURCE_GATEWAY = "gateway";
    public static final String REQUEST_SOURCE_INTERNAL = "internal";
    
    // Roles
    public static final String ADMIN_ROLE = "ADMIN";
    public static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";

    // Local IP addresses for development
    public static final Map<String, String> LOCAL_IP_MAP = Map.of(
            "127.0.0.1", "localhost",
            "0:0:0:0:0:0:0:1", "localhost",
            "localhost", "127.0.0.1");

    private AccessConstants() {
        // Utility class
    }

}
