package com.redxela.scheduler;

import com.redxela.model.Order;

import java.util.Comparator;

/**
 * Politica de planificacion de trabajo mas corto primero (SJF).
 * Prioriza los pedidos con menor cantidad de articulos para reducir
 * el tiempo promedio de espera en el sistema.
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

    /**
     * Retorna el comparador que ordena ascendentemente por cantidad total de
     * articulos
     * y luego por tiempo de creacion en la cola.
     */
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
