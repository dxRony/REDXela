package com.redxela.ui;

import com.redxela.concurrency.DeadlockDetector;
import com.redxela.concurrency.ReceptionProcess;
import com.redxela.concurrency.ResourceManager;
import com.redxela.generator.OrderGenerator;
import com.redxela.metrics.SimulationMetrics;
import com.redxela.scheduler.*;
import com.redxela.storage.WarehouseStorageManager;

import javax.swing.*;
import java.awt.*;

/**
 * Ventana principal de la aplicacion REDXela.
 * Integra los controles de simulacion, la visualizacion de colas y recursos,
 * la matriz de almacenamiento fisico y las metricas operativas.
 */
public class MainFrame extends JFrame {

    // Planificador de pedidos.
    private final OrderScheduler scheduler;

    // Generador automatico de pedidos.
    private final OrderGenerator orderGenerator;

    // Proceso de recepcion para inyectar stock de camiones.
    private final ReceptionProcess receptionProcess;

    // Panel de monitoreo de semaforos y recursos compartidos.
    private final ResourcePanel resourcePanel;

    // Panel de tablas sincronizadas de pedidos activos y en espera.
    private final OrdersTablePanel ordersTablePanel;

    // Panel de visualizacion de slots del almacen no contiguo.
    private final WarehouseViewPanel warehouseViewPanel;

    // Panel de KPIs y metricas de desempeno del sistema.
    private final StatsPanel statsPanel;

    // Timer de Swing para actualizar la interfaz periodicamente cada 300 ms en el hilo EDT.
    private final Timer refreshTimer;

    /**
     * Construye la ventana principal, ensambla los paneles e inicia los escuchadores de eventos.
     *
     * @param resourceManager Gestor de semaforos.
     * @param storageManager Gestor de almacenamiento fisico.
     * @param scheduler Planificador central.
     * @param deadlockDetector Detector de ciclos RAG.
     * @param orderGenerator Generador de trafico.
     * @param receptionProcess Productor de recepcion.
     * @param metrics Recolector de estadisticas.
     */
    public MainFrame(ResourceManager resourceManager,
                     WarehouseStorageManager storageManager,
                     OrderScheduler scheduler,
                     DeadlockDetector deadlockDetector,
                     OrderGenerator orderGenerator,
                     ReceptionProcess receptionProcess,
                     SimulationMetrics metrics) {
        super("REDXela - Centro de Distribución de Comercio Electrónico (Sistemas Operativos 2)");
        this.scheduler = scheduler;
        this.orderGenerator = orderGenerator;
        this.receptionProcess = receptionProcess;

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1380, 860);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(5, 5));

        // 1. Barra de Herramientas Superior
        JPanel toolbar = createToolbar();
        add(toolbar, BorderLayout.NORTH);

        // 2. Paneles de Pestanas
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 13));

        // Pestana 1: Operaciones y Recursos
        JPanel opsPanel = new JPanel(new BorderLayout(5, 8));
        opsPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        this.resourcePanel = new ResourcePanel(resourceManager);
        this.ordersTablePanel = new OrdersTablePanel(scheduler);
        opsPanel.add(resourcePanel, BorderLayout.NORTH);
        opsPanel.add(ordersTablePanel, BorderLayout.CENTER);
        tabbedPane.addTab("📦 Monitoreo de Operaciones y Recursos", opsPanel);

        // Pestana 2: Almacen Fisico No Contiguo
        this.warehouseViewPanel = new WarehouseViewPanel(storageManager);
        tabbedPane.addTab("🏢 Almacén Físico (Asignación No Contigua)", warehouseViewPanel);

        // Pestana 3: Estadisticas y Metricas
        this.statsPanel = new StatsPanel(metrics);
        tabbedPane.addTab("📊 Estadísticas y Políticas de Planificación", statsPanel);

        add(tabbedPane, BorderLayout.CENTER);

        // 3. Listener de Deadlocks: abre el dialogo inmediatamente en el EDT
        deadlockDetector.addListener(new DeadlockDetector.DeadlockListener() {
            @Override
            public void onDeadlockDetected(DeadlockDetector.DeadlockInfo info) {
                SwingUtilities.invokeLater(() -> {
                    DeadlockDialog dlg = new DeadlockDialog(MainFrame.this, info, deadlockDetector, scheduler);
                    dlg.setVisible(true);
                });
            }

            @Override
            public void onDeadlockResolved(DeadlockDetector.DeadlockInfo info) {
                SwingUtilities.invokeLater(() -> refreshAllPanels());
            }
        });

        // 4. Timer de refresco periodico de la interfaz (cada 300 ms en el EDT)
        refreshTimer = new Timer(300, e -> refreshAllPanels());
        refreshTimer.start();
    }

    /**
     * Construye la barra de herramientas superior con controles de simulacion.
     */
    private JPanel createToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(60, 60, 60)));

        // Selector de Politica de Planificacion
        bar.add(new JLabel("Política de Atención:"));
        SchedulingPolicy[] policies = {
                new StrictPriorityPolicy(),
                new AgingPriorityPolicy(),
                new SjfPolicy()
        };
        JComboBox<SchedulingPolicy> cmbPolicy = new JComboBox<>(policies);
        cmbPolicy.setSelectedIndex(0);
        cmbPolicy.addActionListener(e -> {
            SchedulingPolicy p = (SchedulingPolicy) cmbPolicy.getSelectedItem();
            if (p != null) {
                scheduler.setPolicy(p);
            }
        });
        bar.add(cmbPolicy);

        bar.add(new JSeparator(SwingConstants.VERTICAL));

        // Controles de Generador Automatico
        JButton btnToggleGen = new JButton("Iniciar Generador Automático");
        btnToggleGen.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnToggleGen.setBackground(new Color(41, 128, 185));
        btnToggleGen.setForeground(Color.WHITE);
        btnToggleGen.addActionListener(e -> {
            if (orderGenerator.isRunning()) {
                orderGenerator.stop();
                btnToggleGen.setText("Iniciar Generador Automático");
                btnToggleGen.setBackground(new Color(41, 128, 185));
            } else {
                orderGenerator.start(3); // Cada 3 segundos
                btnToggleGen.setText("⏸ Detener Generador");
                btnToggleGen.setBackground(new Color(192, 57, 43));
            }
        });
        bar.add(btnToggleGen);

        bar.add(new JSeparator(SwingConstants.VERTICAL));

        // Boton Pedido Manual
        JButton btnManualOrder = new JButton("+ Pedido Manual");
        btnManualOrder.addActionListener(e -> {
            ManualOrderDialog dlg = new ManualOrderDialog(this, scheduler);
            dlg.setVisible(true);
        });
        bar.add(btnManualOrder);

        // Boton Recepcion de Mercaderia Manual
        JButton btnGoods = new JButton("+ Recepción Camión (Stock)");
        btnGoods.addActionListener(e -> {
            ManualGoodsDialog dlg = new ManualGoodsDialog(this, receptionProcess);
            dlg.setVisible(true);
        });
        bar.add(btnGoods);

        bar.add(new JSeparator(SwingConstants.VERTICAL));

        // Boton Provocar Deadlock
        JButton btnDeadlock = new JButton("⚠️ PROVOCAR DEADLOCK");
        btnDeadlock.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnDeadlock.setBackground(new Color(192, 57, 43));
        btnDeadlock.setForeground(Color.WHITE);
        btnDeadlock.setToolTipText("Inyecta simultáneamente Pedido Frágil y Pedido Pesado solicitando recursos en orden inverso");
        btnDeadlock.addActionListener(e -> {
            orderGenerator.triggerDeadlockScenario();
            JOptionPane.showMessageDialog(this,
                    "Se han inyectado dos pedidos en conflicto inverso de recursos:\n"
                            + "1. Pedido Frágil (Control Calidad -> Montacargas)\n"
                            + "2. Pedido Pesado (Montacargas -> Control Calidad)\n"
                            + "En breve el detector RAG reportará el interbloqueo.",
                    "Escenario de Deadlock Activado",
                    JOptionPane.WARNING_MESSAGE);
        });
        bar.add(btnDeadlock);

        return bar;
    }

    /**
     * Refresca concurrentemente todos los paneles visuales leyendo los datos del modelo.
     */
    private void refreshAllPanels() {
        resourcePanel.refreshData();
        ordersTablePanel.refreshData();
        warehouseViewPanel.refreshData();
        statsPanel.refreshData();
    }
}
