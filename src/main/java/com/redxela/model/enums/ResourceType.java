package com.redxela.model.enums;

import lombok.Getter;

/**
 * Recursos fisicos limitados del centro de distribucion
 */
@Getter
public enum ResourceType {
    PACKING_STATION("Estación de Empaque", 6, "Estación donde se empaqueta y sella la mercadería"),
    LOADING_DOCK("Área de Carga", 2, "Bahía de carga para atención y despacho a camiones"),
    WAREHOUSE_WORKER("Encargado de Bodega", 10, "Personal operativo para picking y movilización manual"),
    FORKLIFT("Montacargas", 3, "Equipo pesado para traslado de carga pesada y estibado"),
    QUALITY_STATION("Control de Calidad", 4, "Mesa de inspección visual, técnica y de daños"),
    BARCODE_SCANNER("Escáner de Códigos", 1, "Sistema compartido y crítico entre Recepción y Despacho");

    // Nombre legible del recurso para la interfaz grafica.
    private final String displayName;
    // Capacidad total de instancias disponibles en el centro.
    private final int totalCapacity;
    // Descripcion operativa de la funcion del recurso.
    private final String description;

    ResourceType(String displayName, int totalCapacity, String description) {
        this.displayName = displayName;
        this.totalCapacity = totalCapacity;
        this.description = description;
    }

    @Override
    public String toString() {
        return displayName + " (" + totalCapacity + " disp.)";
    }
}
