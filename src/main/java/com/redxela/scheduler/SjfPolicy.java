package com.redxela.scheduler;

import com.redxela.model.Order;

import java.util.Comparator;

/**
 * Política de Trabajo Más Corto Primero (SJF / Menor Cantidad de Artículos):
 * Atiende primero a los pedidos con menor cantidad total de artículos,
 * con el objetivo de liberar estaciones de trabajo más rápido y maximizar el rendimiento.
 */
public class SjfPolicy implements SchedulingPolicy {
    @Override
    public String getName() {
        return "Trabajo Más Corto Primero (SJF / Cantidad de Artículos)";
    }

    @Override
    public String getDescription() {
        return "Procesa primero los pedidos con menor número de artículos para liberar recursos y estaciones rápidamente.";
    }

    @Override
    public Comparator<Order> getComparator() {
        return Comparator.comparingInt(Order::getTotalUnits)
                .thenComparingLong(Order::getCreatedAt);
    }

    @Override
    public String toString() {
        return getName();
    }
}
