package com.redxela.scheduler;

import com.redxela.model.Order;
import java.util.Comparator;

/**
 * Politica de prioridad dinamica con envejecimiento (Aging).
 * Incrementa gradualmente la prioridad de los pedidos que esperan en la cola
 * para evitar la inanicion de pedidos de baja prioridad.
 */
public class AgingPriorityPolicy implements SchedulingPolicy {

    @Override
    public String getName() {
        return "Prioridad por SLA con Aging";
    }

    @Override
    public String getDescription() {
        return "Combina el SLA con el tiempo acumulado en espera. Evita la inanición de pedidos económicos elevando su prioridad.";
    }

    /**
     * Retorna el comparador que evalua en tiempo real el puntaje de aging de ambos
     * pedidos,
     * ordenandolos de mayor a menor puntaje (orden descendente).
     */
    @Override
    public Comparator<Order> getComparator() {
        return (o1, o2) -> {
            long now = System.currentTimeMillis();
            double p1 = o1.calculateAgingPriority(now);
            double p2 = o2.calculateAgingPriority(now);
            // Mayor puntaje de prioridad va primero (orden descendente)
            int cmp = Double.compare(p2, p1);
            if (cmp != 0)
                return cmp;
            return Long.compare(o1.getCreatedAt(), o2.getCreatedAt());
        };
    }

    @Override
    public String toString() {
        return getName();
    }
}
