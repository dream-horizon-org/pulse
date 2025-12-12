package org.dreamhorizon.pulsealertscron.util;

import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class MaintenanceUtil {

    private static final String MAINTENANCE_KEY = "maintenance-status";
    private static final AtomicBoolean shutdownStatus = new AtomicBoolean(false);

    public static void setShutdownStatus(Vertx vertx) {
        shutdownStatus.set(true);
        SharedDataUtils.put(vertx, MAINTENANCE_KEY, true);
        log.info("Shutdown status set to true");
    }

    public static boolean isUnderMaintenance(Vertx vertx) {
        Boolean status = SharedDataUtils.get(vertx, MAINTENANCE_KEY);
        return status != null ? status : shutdownStatus.get();
    }

    public static boolean getShutdownStatus() {
        return shutdownStatus.get();
    }

    public static void clearMaintenanceStatus(Vertx vertx) {
        shutdownStatus.set(false);
        SharedDataUtils.remove(vertx, MAINTENANCE_KEY);
        log.info("Maintenance status cleared");
    }
}

