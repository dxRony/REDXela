package com.redxela.scheduler;

import com.redxela.model.Order;

import java.util.Comparator;

/**
 * Interfaz para las politicas de planificacion de pedidos.
 * Define los metodos necesarios para ordenar y priorizar la cola de espera.
 */
public interface SchedulingPolicy {

    /**
     * Nombre descriptivo de la politica para la interfaz grafica.
     *
     * @return Nombre legible de la politica.
     */
    String getName();

    /**
     * Justificacion teorica del criterio de ordenacion que aplica la politica.
     *
     * @return Descripcion de ventajas y comportamiento de despacho.
     */
    String getDescription();

    /**
     * Retorna el comparador utilizado para ordenar los pedidos en la cola de
     * listos/espera.
     *
     * @return Comparator de pedidos.
     */
    Comparator<Order> getComparator();
}
