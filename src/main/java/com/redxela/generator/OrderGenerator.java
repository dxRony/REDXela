package com.redxela.generator;

import com.redxela.model.*;
import com.redxela.model.enums.*;
import com.redxela.scheduler.OrderScheduler;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generador automatico y manual de pedidos para la simulacion.
 * Simula la llegada periodica de clientes con diferentes niveles de SLA
 * y permite forzar escenarios de interbloqueo controlado (deadlock).
 */
public class OrderGenerator {

    // Planificador de pedidos donde se encolan los pedidos generados.
    private final OrderScheduler scheduler;

    // Contador atomico para asignar IDs numericos secuenciales y unicos a cada
    // pedido.
    private final AtomicInteger orderCounter = new AtomicInteger(1000);

    // Estado booleano atomico que indica si la generacion automatica esta activa.
    private final AtomicBoolean isGenerating = new AtomicBoolean(false);

    // Servicio de ejecucion programada para despachar pedidos en intervalos de
    // tiempo regulares.
    private ScheduledExecutorService executorService;

    // Nombres representativos para simular cuentas de clientes de la region de
    // Quetzaltenango.
    private static final String[] CLIENT_NAMES = {
            "Pablo Rojas (CUNOC)", "Rony Rojas (Xelapan)", "José Cardona (HRO Quetzaltenango)",
            "Lionel Messi (Los Altos)", "Cristiano Ronaldo (Altiplano)", "Yairi Enriquez (Minerva)",
            "Vinicius Jr (La Democracia)", "Lamine Yamal (Quetzaltenango)", "Carlo Natareno (Trigales)"
    };

    // Generador de numeros aleatorios para la simulacion.
    private final Random random = new Random();

    /**
     * Construye el generador asociandolo al planificador del centro.
     *
     * @param scheduler Planificador donde se insertaran los pedidos.
     */
    public OrderGenerator(OrderScheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * Inicia el hilo generador de pedidos automaticos con el intervalo
     * especificado.
     *
     * @param intervalSeconds Frecuencia en segundos entre la creacion de cada
     *                        pedido.
     */
    public synchronized void start(int intervalSeconds) {
        if (isGenerating.get())
            return;
        isGenerating.set(true);

        executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleWithFixedDelay(
                this::generateRandomOrder,
                1,
                Math.max(1, intervalSeconds),
                TimeUnit.SECONDS);
    }

    /**
     * Detiene la generacion automatica de pedidos.
     */
    public synchronized void stop() {
        if (!isGenerating.get())
            return;
        isGenerating.set(false);
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    /**
     * Consulta si el generador automatico esta corriendo.
     *
     * @return true si esta generando pedidos. false si esta en
     *         pausa.
     */
    public boolean isRunning() {
        return isGenerating.get();
    }

    /**
     * Genera un pedido individual aleatorio con parametros validos y lo ingresa al
     * planificador.
     *
     * @return El pedido recien creado e insertado en la cola.
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
     * Provoca de manera determinista el escenario de Deadlock por Contencion
     * Cruzada Inversa:
     * 
     * - Pedido A (Mercancia Fragil): Requiere Control de Calidad y luego
     * Montacargas.
     * - Pedido B (Mercancia Pesada): Requiere Montacargas y luego Control de
     * Calidad.
     * 
     * Ambos se marcan con forceDeadlockTest = true y se ingresan
     * simultaneamente al planificador.
     */
    public void triggerDeadlockScenario() {
        String id1 = "DLK-FRG-" + orderCounter.incrementAndGet();
        String id2 = "DLK-PES-" + orderCounter.incrementAndGet();

        Product prodFragil = Catalog.getByMerchandiseType(MerchandiseType.FRAGIL).get(0);
        Product prodPesado = Catalog.getByMerchandiseType(MerchandiseType.PESADA).get(0);

        Map<Product, Integer> items1 = Map.of(prodFragil, 1);
        Map<Product, Integer> items2 = Map.of(prodPesado, 1);

        Order orderFragil = new Order(id1, "Test Deadlock A (Frágil)", ServiceLevel.EXPRES, MerchandiseType.FRAGIL,
                items1);
        orderFragil.setForceDeadlockTest(true);

        Order orderPesado = new Order(id2, "Test Deadlock B (Pesado)", ServiceLevel.EXPRES, MerchandiseType.PESADA,
                items2);
        orderPesado.setForceDeadlockTest(true);

        // los anade simultaneamente al planificador para forzar la competencia
        scheduler.addOrder(orderFragil);
        scheduler.addOrder(orderPesado);
    }
}
