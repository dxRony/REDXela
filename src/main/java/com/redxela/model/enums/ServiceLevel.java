package com.redxela.model.enums;

import lombok.Getter;

/**
 * Niveles de servicio y acuerdos de nivel de servicio para los pedidos
 */
@Getter
public enum ServiceLevel {
    EXPRES(1, "Exprés", "Procesamiento casi instantáneo", 5),          // 5 seg / simulacion
    PRIORITARIO(2, "Prioritario", "Máximo 10 min en espera", 20),      // 20 seg
    ESTANDAR(3, "Estándar", "Máximo 30 min en espera", 45),            // 45 seg
    PROGRAMADO(4, "Programado", "Máximo 60 min en espera", 90),        // 90 seg
    ECONOMICO(5, "Económico", "Máximo 120 min en espera", 150);        // 150 seg

    // Nivel numerico de prioridad
    private final int level;
    // Nombre legible del nivel de servicio.
    private final String displayName;
    // Descripcion contractual del SLA.
    private final String description;
    // Tiempo maximo objetivo en segundos simulados.
    private final int maxWaitSecondsSimulated;

    ServiceLevel(int level, String displayName, String description, int maxWaitSecondsSimulated) {
        this.level = level;
        this.displayName = displayName;
        this.description = description;
        this.maxWaitSecondsSimulated = maxWaitSecondsSimulated;
    }

    @Override
    public String toString() {
        return "Nivel " + level + " - " + displayName;
    }
}
