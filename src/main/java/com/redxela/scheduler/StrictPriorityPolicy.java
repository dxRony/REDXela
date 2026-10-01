package com.redxela.scheduler;

import com.redxela.model.Order;

import java.util.Comparator;

/**
 * Política de Prioridad Estricta:
 * Atiende primero a los pedidos con mayor nivel de servicio (Nivel 1 Exprés hasta Nivel 5 Económico).
 * En caso de empate, atiende por orden de llegada (FCFS).
 */
public class StrictPriorityPolicy implements SchedulingPolicy {
    @Override
    public String getName() {
        return "Prioridad Estricta (SLA)";
    }

    @Override
    public String getDescription() {
        return "Prioriza siempre el nivel de servicio contratado (Nivel 1 > 2 > 3 > 4 > 5). Puede causar inanición en niveles bajos.";
    }

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
