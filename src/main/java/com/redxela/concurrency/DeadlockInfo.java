package com.redxela.concurrency;

import com.redxela.model.Order;
import com.redxela.model.enums.ResourceType;

// Registro inmutable que almacena el diagnostico completo de un Deadlock.
public record DeadlockInfo(
        Order orderA,
        Order orderB,
        ResourceType resourceA,
        ResourceType resourceB,
        String diagnosticMessage,
        long detectedAt
) {}
