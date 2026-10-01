package com.redxela.scheduler;

import com.redxela.model.Order;

import java.util.Comparator;

/**
 * Política con Envejecimiento (Aging):
 * Da prioridad al nivel de servicio, pero incrementa gradualmente la prioridad
 * de los pedidos conforme acumulan tiempo de espera, evitando la inanición (starvation)
 * de pedidos económicos (Nivel 5).
 */
public class AgingPriorityPolicy implements SchedulingPolicy {
    @Override
    public String getName() {
        return "Prioridad Dinámica con Envejecimiento (Aging)";
    }

    @Override
    public String getDescription() {
        return "Combina el SLA con el tiempo acumulado en espera. Evita la inanición de pedidos económicos elevando su prioridad.";
    }

    @Override
    public Comparator<Order> getComparator() {
        return (o1, o2) -> {
            long now = System.currentTimeMillis();
            double p1 = o1.calculateAgingPriority(now);
            double p2 = o2.calculateAgingPriority(now);
            // Mayor puntaje de prioridad va primero (orden descendente)
            int cmp = Double.compare(p2, p1);
            if (cmp != 0) return cmp;
            return Long.compare(o1.getCreatedAt(), o2.getCreatedAt());
        };
    }

    @Override
    public String toString() {
        return getName();
    }
}
