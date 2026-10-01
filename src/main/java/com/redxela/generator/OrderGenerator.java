package com.redxela.generator;

import com.redxela.model.*;
import com.redxela.scheduler.OrderScheduler;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generador automático de pedidos continuos para simular el tráfico en tiempo real de REDXela.
 */
public class OrderGenerator {
    private final OrderScheduler scheduler;
    private final AtomicInteger orderCounter = new AtomicInteger(1000);
    private final AtomicBoolean isGenerating = new AtomicBoolean(false);
    private ScheduledExecutorService executorService;

    private static final String[] CLIENT_NAMES = {
            "Carlos López (CUNOC)", "María Morales (Xelapán)", "José Gómez (Tecún)",
            "Ana Castillo (Los Altos)", "Fernando Ruiz (Altiplano)", "Sofía Mendoza (Minerva)",
            "David Herrera (La Democracia)", "Lucía Juárez (Quetzaltenango)", "Pedro Alvarado (Trigales)"
    };

    private final Random random = new Random();

    public OrderGenerator(OrderScheduler scheduler) {
        this.scheduler = scheduler;
    }

    public synchronized void start(int intervalSeconds) {
        if (isGenerating.get()) return;
        isGenerating.set(true);

        executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleWithFixedDelay(
                this::generateRandomOrder,
                1,
                Math.max(1, intervalSeconds),
                TimeUnit.SECONDS
        );
    }

    public synchronized void stop() {
        if (!isGenerating.get()) return;
        isGenerating.set(false);
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    public boolean isRunning() {
        return isGenerating.get();
    }

    /**
     * Genera un pedido individual aleatorio y lo ingresa al planificador.
     */
    public Order generateRandomOrder() {
        String orderId = String.valueOf(orderCounter.incrementAndGet());
        String client = CLIENT_NAMES[random.nextInt(CLIENT_NAMES.length)];
        ServiceLevel level = ServiceLevel.values()[random.nextInt(ServiceLevel.values().length)];

        List<Product> catalog = Catalog.getAllProducts();
        Product selectedProduct = catalog.get(random.nextInt(catalog.size()));
        int quantity = 1 + random.nextInt(4); // 1 a 4 unidades

        Map<Product, Integer> items = new HashMap<>();
        items.put(selectedProduct, quantity);

        Order order = new Order(orderId, client, level, selectedProduct.getMerchandiseType(), items);
        scheduler.addOrder(order);
        return order;
    }

    /**
     * Provoca deliberadamente el escenario de Deadlock de recursos cruzados entre:
     * - Pedido 1: Mercancía Frágil (Control de Calidad -> Montacargas)
     * - Pedido 2: Mercancía Pesada (Montacargas -> Control de Calidad)
     */
    public void triggerDeadlockScenario() {
        String id1 = "DLK-FRG-" + orderCounter.incrementAndGet();
        String id2 = "DLK-PES-" + orderCounter.incrementAndGet();

        Product prodFragil = Catalog.getByMerchandiseType(MerchandiseType.FRAGIL).get(0);
        Product prodPesado = Catalog.getByMerchandiseType(MerchandiseType.PESADA).get(0);

        Map<Product, Integer> items1 = Map.of(prodFragil, 1);
        Map<Product, Integer> items2 = Map.of(prodPesado, 1);

        Order orderFragil = new Order(id1, "Test Deadlock A (Frágil)", ServiceLevel.EXPRES, MerchandiseType.FRAGIL, items1);
        orderFragil.setForceDeadlockTest(true);

        Order orderPesado = new Order(id2, "Test Deadlock B (Pesado)", ServiceLevel.EXPRES, MerchandiseType.PESADA, items2);
        orderPesado.setForceDeadlockTest(true);

        // Añadirlos simultáneamente al planificador
        scheduler.addOrder(orderFragil);
        scheduler.addOrder(orderPesado);
    }
}
