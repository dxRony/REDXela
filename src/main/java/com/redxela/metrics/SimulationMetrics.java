package com.redxela.metrics;

import com.redxela.concurrency.ResourceManager;
import com.redxela.model.Order;
import com.redxela.model.enums.ResourceType;
import com.redxela.model.enums.ServiceLevel;
import com.redxela.scheduler.OrderScheduler;
import java.util.*;

/**
 * Motor de metricas y estadisticas operativas del simulador.
 * Calcula tiempos de espera promedio, tiempos de respuesta por SLA,
 * nivel de ocupacion de recursos y metricas de rendimiento global.
 */
public class SimulationMetrics {

    // Referencia al planificador para extraer estados y tiempos de pedidos.
    private final OrderScheduler scheduler;

    // Referencia al gestor de recursos para consultar la ocupacion de semaforos.
    private final ResourceManager resourceManager;

    /**
     * Construye el recolector de metricas vinculando el planificador y el
     * administrador de recursos.
     *
     * @param scheduler       Planificador de pedidos.
     * @param resourceManager Gestor de semaforos fisicos.
     */
    public SimulationMetrics(OrderScheduler scheduler, ResourceManager resourceManager) {
        this.scheduler = scheduler;
        this.resourceManager = resourceManager;
    }

    /**
     * Retorna la cantidad de pedidos que han sido completamente despachados.
     *
     * @return Total de pedidos completados.
     */
    public int getCompletedOrdersCount() {
        return scheduler.getCompletedOrders().size();
    }

    /**
     * Retorna la cantidad de pedidos que actualmente se encuentran en procesamiento
     * concurrente.
     *
     * @return Pedidos activos.
     */
    public int getProcessingOrdersCount() {
        return scheduler.getProcessingOrders().size();
    }

    /**
     * Retorna la cantidad de pedidos que aguardan en la cola del planificador.
     *
     * @return Pedidos en espera.
     */
    public int getWaitingOrdersCount() {
        return scheduler.getWaitingQueue().size();
    }

    /**
     * Calcula el tiempo promedio global en segundos que los pedidos pasan en la
     * cola de espera.
     *
     * @return Tiempo promedio de espera en segundos.
     */
    public double getAverageWaitTimeSeconds() {
        List<Order> completed = scheduler.getCompletedOrders();
        if (completed.isEmpty())
            return 0.0;
        long totalWaitMs = 0;
        for (Order o : completed) {
            totalWaitMs += o.getTotalWaitTimeMillis();
        }
        return (totalWaitMs / (double) completed.size()) / 1000.0;
    }

    /**
     * Calcula el tiempo promedio global en segundos que toma el procesamiento
     * fisico de un pedido.
     *
     * @return Tiempo promedio de procesamiento en segundos.
     */
    public double getAverageProcessingTimeSeconds() {
        List<Order> completed = scheduler.getCompletedOrders();
        if (completed.isEmpty())
            return 0.0;
        long totalProcMs = 0;
        for (Order o : completed) {
            totalProcMs += o.getTotalProcessingTimeMillis();
        }
        return (totalProcMs / (double) completed.size()) / 1000.0;
    }

    /**
     * Calcula el tiempo promedio de espera agrupado y desglosado por cada uno de
     * los 5 niveles de servicio (SLA).
     *
     * @return Mapeo ServiceLevel -> Tiempo promedio en segundos.
     */
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

    /**
     * Calcula el porcentaje de utilizacion de cada recurso fisico compartido.
     *
     * @return Mapeo ResourceType -> Porcentaje de uso (0.0% a 100.0%).
     */
    public Map<ResourceType, Double> getResourceUtilization() {
        Map<ResourceType, Double> map = new EnumMap<>(ResourceType.class);
        for (ResourceType type : ResourceType.values()) {
            map.put(type, resourceManager.getUtilizationPercentage(type));
        }
        return map;
    }
}
