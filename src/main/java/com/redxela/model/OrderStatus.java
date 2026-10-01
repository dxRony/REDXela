package com.redxela.model;

/**
 * Estados del ciclo de vida de un pedido en la simulación.
 */
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

    public String getLabel() {
        return label;
    }

    public String getHexColor() {
        return hexColor;
    }

    @Override
    public String toString() {
        return label;
    }
}
