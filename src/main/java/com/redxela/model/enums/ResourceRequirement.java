package com.redxela.model.enums;

// Registro de requerimiento individual de un recurso para una fase de procesamiento.
public record ResourceRequirement(ResourceType resourceType, int count, String phaseDescription) {}
