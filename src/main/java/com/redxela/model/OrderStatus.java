package com.redxela.model;

import lombok.Getter;

/**
 * Estados del ciclo de vida de un pedido en la simulacion
 * (Pendiente, Espera de Inventario, Espera de Recursos, En Proceso, Deadlock, Completado, Cancelado).
 */
@Getter
public enum OrderStatus {
    PENDIENTE("Pendiente de Planificación", "#808080"),
    EN_ESPERA_INVENTARIO("Esperando Stock", "#E67E22"),
    EN_ESPERA_RECURSOS("En Espera de Recursos", "#F1C40F"),
    EN_PROCESO("En Procesamiento Activo", "#3498DB"),
    BLOQUEADO_DEADLOCK("¡Bloqueado en Deadlock!", "#E74C3C"),
    COMPLETADO("Completado y Despachado", "#2ECC71"),
    CANCELADO("Cancelado / Reencolado", "#95A5A6");

    private final String label;
    private final String hexColor;

    OrderStatus(String label, String hexColor) {
        this.label = label;
        this.hexColor = hexColor;
    }

    @Override
    public String toString() {
        return label;
    }
}
