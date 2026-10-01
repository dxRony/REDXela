package com.redxela.metrics;

import com.redxela.concurrency.ResourceManager;
import com.redxela.model.Order;
import com.redxela.model.ResourceType;
import com.redxela.model.ServiceLevel;
import com.redxela.scheduler.OrderScheduler;

import java.util.*;

/**
 * Recopila y calcula estadísticas en tiempo real del Centro de Distribución:
 * Tiempos promedio de espera, tiempos de procesamiento, ocupación de recursos y métricas por política.
 */
public class SimulationMetrics {
    private final OrderScheduler scheduler;
    private final ResourceManager resourceManager;

    public SimulationMetrics(OrderScheduler scheduler, ResourceManager resourceManager) {
        this.scheduler = scheduler;
        this.resourceManager = resourceManager;
    }

    public int getCompletedOrdersCount() {
        return scheduler.getCompletedOrders().size();
    }

    public int getProcessingOrdersCount() {
        return scheduler.getProcessingOrders().size();
    }

    public int getWaitingOrdersCount() {
        return scheduler.getWaitingQueue().size();
    }

    public double getAverageWaitTimeSeconds() {
        List<Order> completed = scheduler.getCompletedOrders();
        if (completed.isEmpty()) return 0.0;
        long totalWaitMs = 0;
        for (Order o : completed) {
            totalWaitMs += o.getTotalWaitTimeMillis();
        }
        return (totalWaitMs / (double) completed.size()) / 1000.0;
    }

    public double getAverageProcessingTimeSeconds() {
        List<Order> completed = scheduler.getCompletedOrders();
        if (completed.isEmpty()) return 0.0;
        long totalProcMs = 0;
        for (Order o : completed) {
            totalProcMs += o.getTotalProcessingTimeMillis();
        }
        return (totalProcMs / (double) completed.size()) / 1000.0;
    }

    public Map<ServiceLevel, Double> getAverageWaitTimeByServiceLevel() {
        Map<ServiceLevel, List<Long>> map = new EnumMap<>(ServiceLevel.class);
        for (ServiceLevel sl : ServiceLevel.values()) {
            map.put(sl, new ArrayList<>());
        }

        for (Order o : scheduler.getCompletedOrders()) {
            map.get(o.getServiceLevel()).add(o.getTotalWaitTimeMillis());
        }

        Map<ServiceLevel, Double> result = new EnumMap<>(ServiceLevel.class);
        for (Map.Entry<ServiceLevel, List<Long>> entry : map.entrySet()) {
            List<Long> times = entry.getValue();
            if (times.isEmpty()) {
                result.put(entry.getKey(), 0.0);
            } else {
                long sum = times.stream().mapToLong(Long::longValue).sum();
                result.put(entry.getKey(), (sum / (double) times.size()) / 1000.0);
            }
        }
        return result;
    }

    public Map<ResourceType, Double> getResourceUtilization() {
        Map<ResourceType, Double> map = new EnumMap<>(ResourceType.class);
        for (ResourceType type : ResourceType.values()) {
            map.put(type, resourceManager.getUtilizationPercentage(type));
        }
        return map;
    }
}
