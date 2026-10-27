package com.redxela.scheduler;

import com.redxela.model.Order;

import java.util.Comparator;

/**
 * Politica de planificacion por prioridad estricta segun el SLA del pedido.
 * Da mayor precedencia a los niveles de servicio mas altos (Nivel 1 > Nivel 5).
 */
public class StrictPriorityPolicy implements SchedulingPolicy {

    @Override
    public String getName() {
        return "Prioridad Estricta";
    }

    @Override
    public String getDescription() {
        return "Prioriza siempre el nivel de servicio contratado (Nivel 1 > 2 > 3 > 4 > 5). Puede causar inanición en niveles bajos.";
    }

    /**
     * Retorna el comparador que ordena ascendentemente por numero de nivel (1 es
     * mayor prioridad)
     * y luego por marca de tiempo de creacion FCFS.
     */
    @Override
    public Comparator<Order> getComparator() {
        return Comparator.comparingInt((Order o) -> o.getServiceLevel().getLevel())
                .thenComparingLong(Order::getCreatedAt);
    }

    @Override
    public String toString() {
        return getName();
    }
}
