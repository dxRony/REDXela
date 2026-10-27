package com.redxela.model.enums;

import lombok.Getter;
import java.util.Collections;
import java.util.List;

/**
 * Define los 5 tipos de mercancia con sus caracteristicas y combinaciones de recursos.
 */
@Getter
public enum MerchandiseType {
    ESTANDAR(
            "Estándar / Retail",
            "Artículos cotidianos ligeros (ropa, calzado, libros). Flujo normal de bodega a empaque.",
            List.of(
                    new ResourceRequirement(ResourceType.WAREHOUSE_WORKER, 1, "Picking y transporte manual"),
                    new ResourceRequirement(ResourceType.PACKING_STATION, 1, "Empaquetado regular")
            ),
            1.0 // Factor de tiempo base
    ),
    FRAGIL(
            "Frágil / Alta Precisión",
            "Cristalería, componentes electrónicos delicados. Pasa a Control de Calidad y luego pide Montacargas para traslado especial a Empaque.",
            List.of(
                    new ResourceRequirement(ResourceType.WAREHOUSE_WORKER, 1, "Picking inicial con cuidado"),
                    new ResourceRequirement(ResourceType.QUALITY_STATION, 1, "Inspección minuciosa de roturas"),
                    new ResourceRequirement(ResourceType.FORKLIFT, 1, "Traslado de pallets protegidos"),
                    new ResourceRequirement(ResourceType.PACKING_STATION, 1, "Empaque acolchado con burbuja")
            ),
            1.5
    ),
    PESADA(
            "Pesada / Voluminosa",
            "Maquinaria pesada, muebles o electrodomésticos. Requiere Montacargas primero, luego Control de Calidad en plataforma antes de Carga.",
            List.of(
                    new ResourceRequirement(ResourceType.WAREHOUSE_WORKER, 2, "Equipo de apoyo y sujeción"),
                    new ResourceRequirement(ResourceType.FORKLIFT, 1, "Carga y movilización mecánica"),
                    new ResourceRequirement(ResourceType.QUALITY_STATION, 1, "Verificación de anclaje y empaque"),
                    new ResourceRequirement(ResourceType.LOADING_DOCK, 1, "Bahía de carga para camiones")
            ),
            2.0
    ),
    ALTO_VALOR(
            "Alto Valor / Restringida",
            "Joyería, medicamentos controlados y electrónica de gama alta. Requiere auditoría con Escáner de Códigos antes de empaque.",
            List.of(
                    new ResourceRequirement(ResourceType.WAREHOUSE_WORKER, 1, "Custodia y retiro seguro"),
                    new ResourceRequirement(ResourceType.BARCODE_SCANNER, 1, "Escaneo unitario y trazabilidad serial"),
                    new ResourceRequirement(ResourceType.QUALITY_STATION, 1, "Verificación de sellos de seguridad"),
                    new ResourceRequirement(ResourceType.PACKING_STATION, 1, "Empaque de alta seguridad precintado")
            ),
            1.8
    ),
    PERECEDERA(
            "Perecedera / Cadena de Frío",
            "Alimentos y vacunas con tiempo crítico. Salida rápida con montacargas hacia camión refrigerado con escaneo rápido.",
            List.of(
                    new ResourceRequirement(ResourceType.WAREHOUSE_WORKER, 2, "Carga rápida en frío"),
                    new ResourceRequirement(ResourceType.FORKLIFT, 1, "Estibado hacia bahía"),
                    new ResourceRequirement(ResourceType.BARCODE_SCANNER, 1, "Escaneo de salida y lote"),
                    new ResourceRequirement(ResourceType.LOADING_DOCK, 1, "Acople a camión térmico")
            ),
            1.2
    );

    // Nombre descriptivo para la interfaz grafica.
    private final String displayName;
    // Descripcion operativa del tipo de mercancia.
    private final String description;
    // Lista ordenada de fases y requerimientos de recursos.
    private final List<ResourceRequirement> steps;
    // Factor multiplicador de duracion base.
    private final double timeMultiplier;

    MerchandiseType(String displayName, String description, List<ResourceRequirement> steps, double timeMultiplier) {
        this.displayName = displayName;
        this.description = description;
        this.steps = Collections.unmodifiableList(steps);
        this.timeMultiplier = timeMultiplier;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
