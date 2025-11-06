package com.vendingmachine.inventory.config;

import java.util.Map;

public class AccessConstants {
    public static final String INTERNAL_SERVICE_HEADER = "X-Internal-Service";
    public static final String ADMIN_ROLE = "ADMIN";
    public static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";

    // local ip directions
    public static final Map<String, String> LOCAL_IP_MAP = Map.of(
            "127.0.0.1", "localhost",
            "0:0:0:0:0:0:0:1", "localhost",
            "localhost", "127.0.0.1");

    private AccessConstants() {
        // Utility class
    }

}
