package com.redxela.scheduler;

import com.redxela.model.Order;

import java.util.Comparator;

/**
 * Interfaz para las políticas de planificación de pedidos de REDXela.
 */
public interface SchedulingPolicy {
    String getName();
    String getDescription();
    Comparator<Order> getComparator();
}
