package com.redxela;

import com.formdev.flatlaf.FlatDarkLaf;
import com.redxela.concurrency.*;
import com.redxela.generator.OrderGenerator;
import com.redxela.metrics.SimulationMetrics;
import com.redxela.model.Catalog;
import com.redxela.model.Product;
import com.redxela.scheduler.OrderScheduler;
import com.redxela.storage.WarehouseStorageManager;
import com.redxela.ui.MainFrame;
import javax.swing.*;

/**
 * Clase principal que inicializa los subsistemas del simulador (recursos,
 * almacen,
 * bufer de recepcion, planificador e interfaz grafica) y arranca la aplicacion.
 */
public class Main {

    /**
     * Metodo que arranca la aplicacion
     *
     * @param args Argumentos de la linea de comandos (no requeridos).
     */
    public static void main(String[] args) {
        // Configurando FlatLaf para interfaz moderna tipo Dark Mode
        FlatDarkLaf.setup();

        // iniciando subsistemas de concurrencia y almacenamiento
        ResourceManager resourceManager = new ResourceManager();
        WarehouseStorageManager storageManager = new WarehouseStorageManager(6, 4, 2, 15); // 48 ranuras
        ReceptionBuffer receptionBuffer = new ReceptionBuffer(15);
        DeadlockDetector deadlockDetector = new DeadlockDetector(resourceManager);

        // Pre-cargar stock inicial en el almacen de forma no contigua para demostracion
        // inmediata
        int batchSeed = 100;
        for (Product p : Catalog.getAllProducts()) {
            storageManager.allocateGoods("LOTE-INIT-" + (++batchSeed), p, 20);
        }

        // iniciando el hilo consumidor de Inventario (Procesamiento de lotes del
        // ReceptionBuffer)
        InventoryProcess inventoryProcess = new InventoryProcess(receptionBuffer, storageManager);
        Thread inventoryThread = new Thread(inventoryProcess, "Thread-Inventory-Consumer");
        inventoryThread.setDaemon(true);
        inventoryThread.start();

        // iniciando el Productor de Recepcion de Mercaderia
        ReceptionProcess receptionProcess = new ReceptionProcess(receptionBuffer);

        // iniciando el Planificador central de pedidos
        OrderScheduler scheduler = new OrderScheduler(resourceManager, storageManager, deadlockDetector);
        scheduler.start();

        // generador de pedidos automaticos
        OrderGenerator orderGenerator = new OrderGenerator(scheduler);

        // metricas y estadisticas
        SimulationMetrics metrics = new SimulationMetrics(scheduler, resourceManager);

        // lanzando la Interfaz Grafica en el hilo de despacho de eventos de Swing
        // (EDT)
        SwingUtilities.invokeLater(() -> {
            MainFrame mainFrame = new MainFrame(
                    resourceManager,
                    storageManager,
                    scheduler,
                    deadlockDetector,
                    orderGenerator,
                    receptionProcess,
                    metrics);
            mainFrame.setVisible(true);
        });
    }
}
