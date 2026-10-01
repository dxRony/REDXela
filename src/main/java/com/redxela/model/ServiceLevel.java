package com.redxela.model;

/**
 * Niveles de servicio y acuerdos de nivel de servicio (SLA) para los pedidos.
 * Nivel 1 tiene la máxima prioridad y menor tiempo objetivo de espera.
 */
public enum ServiceLevel {
    EXPRES(1, "Exprés", "Procesamiento casi instantáneo", 5),          // 5 seg / simulación
    PRIORITARIO(2, "Prioritario", "Máximo 10 min en espera", 20),      // 20 seg
    ESTANDAR(3, "Estándar", "Máximo 30 min en espera", 45),            // 45 seg
    PROGRAMADO(4, "Programado", "Máximo 60 min en espera", 90),        // 90 seg
    ECONOMICO(5, "Económico", "Máximo 120 min en espera", 150);        // 150 seg

    private final int level;
    private final String displayName;
    private final String description;
    private final int maxWaitSecondsSimulated;

    ServiceLevel(int level, String displayName, String description, int maxWaitSecondsSimulated) {
        this.level = level;
        this.displayName = displayName;
        this.description = description;
        this.maxWaitSecondsSimulated = maxWaitSecondsSimulated;
    }

    public int getLevel() {
        return level;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public int getMaxWaitSecondsSimulated() {
        return maxWaitSecondsSimulated;
    }

    @Override
    public String toString() {
        return "Nivel " + level + " - " + displayName;
    }
}
