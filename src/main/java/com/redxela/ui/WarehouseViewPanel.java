package com.redxela.ui;

import com.redxela.metrics.SimulationMetrics;
import com.redxela.model.Catalog;
import com.redxela.model.Product;
import com.redxela.model.WarehouseSlot;
import com.redxela.model.enums.ServiceLevel;
import com.redxela.storage.WarehouseStorageManager;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Panel visual que representa la matriz de almacenamiento no contiguo del almacén
 * junto con la tabla de inventario y el monitoreo de cumplimiento de SLA.
 * Diseñado con Dirty-Checking y ScrollMode seguro para evitar artefactos visuales de repintado.
 */
public class WarehouseViewPanel extends JPanel {

    // Gestor físico del almacén.
    private final WarehouseStorageManager storageManager;

    // Recolector de métricas para tiempos de SLA.
    private final SimulationMetrics metrics;

    // Modelo de datos para la tabla de inventario por producto.
    private final DefaultTableModel stockModel;

    // Modelo de datos para la tabla de cumplimiento de SLA.
    private final DefaultTableModel slaModel;

    // Etiqueta de resumen de capacidad general.
    private final JLabel capacitySummaryLabel;

    // Componentes de la interfaz de cada slot guardados para actualización en caliente.
    private final List<SlotUI> slotUIList = new ArrayList<>();

    // Colores reutilizables para el renderizado (Verde = Libre, Azul = Ocupado)
    private static final Color COLOR_FREE_BG = new Color(25, 45, 30);
    private static final Color COLOR_FREE_BORDER = new Color(46, 204, 113, 160);
    private static final Color COLOR_FREE_TEXT = new Color(46, 204, 113);
    private static final Color COLOR_OCCUPIED_BG = new Color(30, 65, 95);
    private static final Color COLOR_OCCUPIED_BORDER = new Color(52, 152, 219);
    private static final Color COLOR_OCCUPIED_ID = new Color(133, 193, 233);

    // Bordes estáticos preconstruidos para evitar revalidaciones innecesarias
    private static final Border BORDER_FREE = BorderFactory.createLineBorder(COLOR_FREE_BORDER, 1);
    private static final Border BORDER_OCCUPIED = BorderFactory.createLineBorder(COLOR_OCCUPIED_BORDER, 2);

    // Clase auxiliar para retener referencias directas y evitar repintados si el estado no cambió.
    private static class SlotUI {
        final WarehouseSlot slot;
        final JPanel panel;
        final JLabel idLbl;
        final JLabel contentLbl;

        private boolean lastAvailable = false;
        private String lastProductId = "";
        private String lastBatchId = "";
        private int lastStoredUnits = -1;
        private boolean initialized = false;

        SlotUI(WarehouseSlot slot, JPanel panel, JLabel idLbl, JLabel contentLbl) {
            this.slot = slot;
            this.panel = panel;
            this.idLbl = idLbl;
            this.contentLbl = contentLbl;
        }

        void update() {
            boolean isAvail = slot.isAvailable();
            String prodId = slot.getCurrentProductId();
            String batchId = slot.getCurrentBatchId();
            int units = slot.getStoredUnits();

            // DIRTY-CHECKING: Si el estado no ha cambiado, no tocar componentes para evitar
            // interferir con ToolTipManager o disparar ciclos de repintado.
            if (initialized &&
                    isAvail == lastAvailable &&
                    Objects.equals(prodId, lastProductId) &&
                    Objects.equals(batchId, lastBatchId) &&
                    units == lastStoredUnits) {
                return;
            }

            lastAvailable = isAvail;
            lastProductId = prodId;
            lastBatchId = batchId;
            lastStoredUnits = units;
            initialized = true;

            if (isAvail) {
                panel.setBackground(COLOR_FREE_BG);
                panel.setBorder(BORDER_FREE);
                contentLbl.setText("LIBRE");
                contentLbl.setForeground(COLOR_FREE_TEXT);
                idLbl.setForeground(Color.GRAY);
            } else {
                panel.setBackground(COLOR_OCCUPIED_BG);
                panel.setBorder(BORDER_OCCUPIED);
                contentLbl.setText(prodId + " (" + units + "u)");
                contentLbl.setForeground(Color.WHITE);
                idLbl.setForeground(COLOR_OCCUPIED_ID);
            }
        }
    }

    /**
     * Construye el panel visual de almacenamiento y métricas de SLA.
     *
     * @param storageManager Gestor de almacenamiento físico.
     * @param metrics Gestor de métricas de simulación.
     */
    public WarehouseViewPanel(WarehouseStorageManager storageManager, SimulationMetrics metrics) {
        this.storageManager = storageManager;
        this.metrics = metrics;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 1. Panel Superior con Estadísticas de Capacidad
        JPanel topPanel = new JPanel(new BorderLayout());
        capacitySummaryLabel = new JLabel("Capacidad Almacén: 0 / 0 slots ocupados (0%)");
        capacitySummaryLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        topPanel.add(capacitySummaryLabel, BorderLayout.WEST);
        add(topPanel, BorderLayout.NORTH);

        // 2. Panel Central: Matriz de Slots Físicos
        JPanel gridPanel = new JPanel(new GridLayout(6, 8, 5, 5));
        gridPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(70, 70, 70)),
                "MAPA FÍSICO DE RANURAS / SLOTS (PAGINACIÓN NO CONTIGUA)",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 12)
        ));

        for (WarehouseSlot slot : storageManager.getAllSlots()) {
            // El panel sobreescribe getToolTipText para renderizar el contenido on-demand
            // sin necesidad de llamar a setToolTipText continuamente cada tick.
            JPanel slotBox = new JPanel(new BorderLayout()) {
                @Override
                public String getToolTipText(MouseEvent event) {
                    if (slot.isAvailable()) {
                        return String.format("Slot: %s (Disponible)", slot.getSlotId());
                    } else {
                        return String.format(
                                "<html><b>Ubicación:</b> %s<br><b>Lote:</b> %s<br><b>Producto:</b> %s<br><b>Unidades:</b> %d / %d</html>",
                                slot.getSlotId(), slot.getCurrentBatchId(), slot.getCurrentProductId(), slot.getStoredUnits(), slot.getCapacity()
                        );
                    }
                }
            };
            slotBox.setPreferredSize(new Dimension(95, 55));
            // Registrar una sola vez en el ToolTipManager
            slotBox.setToolTipText("");

            JLabel idLbl = new JLabel(slot.getSlotId(), SwingConstants.CENTER);
            idLbl.setFont(new Font("Segoe UI", Font.BOLD, 10));
            idLbl.setToolTipText(null);

            JLabel contentLbl = new JLabel("LIBRE", SwingConstants.CENTER);
            contentLbl.setFont(new Font("Segoe UI", Font.BOLD, 10));
            contentLbl.setToolTipText(null);

            slotBox.add(idLbl, BorderLayout.NORTH);
            slotBox.add(contentLbl, BorderLayout.CENTER);
            gridPanel.add(slotBox);

            SlotUI ui = new SlotUI(slot, slotBox, idLbl, contentLbl);
            slotUIList.add(ui);
        }

        JScrollPane scrollGrid = new JScrollPane(gridPanel);
        // SIMPLE_SCROLL_MODE desactiva copyArea (blit) que causa artefactos con tooltips flotantes
        scrollGrid.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        add(scrollGrid, BorderLayout.CENTER);

        // 3. Panel Lateral Derecho: Tablas de Inventario y SLA
        JPanel rightPanel = new JPanel(new GridLayout(2, 1, 8, 8));
        rightPanel.setPreferredSize(new Dimension(450, 0));

        // Tabla de Stock
        String[] stockCols = {"Código", "Producto", "Tipo Mercancía", "Stock Total"};
        stockModel = new DefaultTableModel(stockCols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        // Inicializar filas fijas del catálogo
        for (Product p : Catalog.getAllProducts()) {
            stockModel.addRow(new Object[]{
                    p.getId(),
                    p.getName(),
                    p.getMerchandiseType().getDisplayName(),
                    storageManager.getAvailableStock(p) + " unidades"
            });
        }

        JTable stockTable = new JTable(stockModel);
        stockTable.setRowHeight(22);
        JPanel stockWrapper = new JPanel(new BorderLayout());
        stockWrapper.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(70, 70, 70)),
                "EXISTENCIAS ACTUALES DE INVENTARIO",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 11)
        ));
        JScrollPane stockScroll = new JScrollPane(stockTable);
        stockScroll.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        stockWrapper.add(stockScroll, BorderLayout.CENTER);
        rightPanel.add(stockWrapper);

        // Tabla de SLA
        String[] slaCols = {"Nivel de Servicio", "Tiempo Objetivo", "Tiempo Real Prom.", "Cumplimiento"};
        slaModel = new DefaultTableModel(slaCols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        // Inicializar filas fijas de SLA
        for (ServiceLevel sl : ServiceLevel.values()) {
            slaModel.addRow(new Object[]{
                    sl.getDisplayName(),
                    String.format("%s (Meta: %ds)", sl.getDescription(), sl.getMaxWaitSecondsSimulated()),
                    "0.0 seg",
                    "Sin datos"
            });
        }

        JTable slaTable = new JTable(slaModel);
        slaTable.setRowHeight(22);
        JPanel slaWrapper = new JPanel(new BorderLayout());
        slaWrapper.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(70, 70, 70)),
                "DESEMPEÑO Y CUMPLIMIENTO DE SLA",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 11)
        ));
        JScrollPane slaScroll = new JScrollPane(slaTable);
        slaScroll.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        slaWrapper.add(slaScroll, BorderLayout.CENTER);
        rightPanel.add(slaWrapper);

        add(rightPanel, BorderLayout.EAST);

        refreshData();
    }

    /**
     * Refresca los estados de los slots y los registros de las dos tablas en su lugar (in-place).
     */
    public void refreshData() {
        // Actualizar resumen de capacidad
        int total = storageManager.getTotalSlots();
        int occupied = storageManager.getOccupiedSlotsCount();
        double pct = total > 0 ? (occupied / (double) total) * 100.0 : 0.0;
        capacitySummaryLabel.setText(String.format("Capacidad Almacén: %d / %d slots ocupados (%.1f%%)", occupied, total, pct));

        // Actualizar slots (con dirty-checking interno)
        for (SlotUI ui : slotUIList) {
            ui.update();
        }

        // Actualizar tabla de existencias in-place (sin setRowCount(0))
        List<Product> products = Catalog.getAllProducts();
        for (int i = 0; i < products.size(); i++) {
            Product p = products.get(i);
            String stockStr = storageManager.getAvailableStock(p) + " unidades";
            if (!stockStr.equals(stockModel.getValueAt(i, 3))) {
                stockModel.setValueAt(stockStr, i, 3);
            }
        }

        // Actualizar tabla de SLA in-place
        Map<ServiceLevel, Double> waitMap = metrics.getAverageWaitTimeByServiceLevel();
        ServiceLevel[] levels = ServiceLevel.values();
        for (int i = 0; i < levels.length; i++) {
            ServiceLevel sl = levels[i];
            double actualSecs = waitMap.getOrDefault(sl, 0.0);
            String timeStr = String.format("%.1f seg", actualSecs);
            String status = actualSecs == 0.0 ? "Sin datos" :
                    (actualSecs <= sl.getMaxWaitSecondsSimulated() ? "✓ Dentro de SLA" : "⚠️ Excedido");

            if (!timeStr.equals(slaModel.getValueAt(i, 2))) {
                slaModel.setValueAt(timeStr, i, 2);
            }
            if (!status.equals(slaModel.getValueAt(i, 3))) {
                slaModel.setValueAt(status, i, 3);
            }
        }
    }
}
