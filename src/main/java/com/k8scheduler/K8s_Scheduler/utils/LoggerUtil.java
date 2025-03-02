
package com.k8scheduler.K8s_Scheduler.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggerUtil {
    private static final Logger logger = LoggerFactory.getLogger(LoggerUtil.class);

    public static void logSuccess(String message) {
        logger.info("SUCCESS: {}", message);
    }

    public static void logError(String message) {
        logger.error("ERROR: {}", message);
    }

    public static void logWarning(String message) {
        logger.warn("WARN: {}", message);
    }
}
