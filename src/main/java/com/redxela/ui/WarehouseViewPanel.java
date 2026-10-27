package com.redxela.ui;

import com.redxela.metrics.SimulationMetrics;
import com.redxela.model.Catalog;
import com.redxela.model.Product;
import com.redxela.model.WarehouseSlot;
import com.redxela.model.enums.ServiceLevel;
import com.redxela.storage.WarehouseStorageManager;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Panel visual que representa la matriz de almacenamiento no contiguo del almacen
 * junto con la tabla de inventario y el monitoreo de cumplimiento de SLA.
 */
public class WarehouseViewPanel extends JPanel {

    // Gestor fisico del almacen.
    private final WarehouseStorageManager storageManager;

    // Recolector de metricas para tiempos de SLA.
    private final SimulationMetrics metrics;

    // Modelo de datos para la tabla de inventario por producto.
    private final DefaultTableModel stockModel;

    // Modelo de datos para la tabla de cumplimiento de SLA.
    private final DefaultTableModel slaModel;

    // Etiqueta de resumen de capacidad general.
    private final JLabel capacitySummaryLabel;

    // Componentes de la interfaz de cada slot guardados para actualizacion en caliente.
    private final List<SlotUI> slotUIList = new ArrayList<>();

    // Colores reutilizables para el renderizado
    private static final Color COLOR_FREE_BG = new Color(40, 44, 52);
    private static final Color COLOR_FREE_BORDER = new Color(70, 80, 95);
    private static final Color COLOR_FREE_TEXT = new Color(120, 140, 160);
    private static final Color COLOR_OCCUPIED_BG = new Color(30, 65, 95);
    private static final Color COLOR_OCCUPIED_BORDER = new Color(52, 152, 219);
    private static final Color COLOR_OCCUPIED_ID = new Color(133, 193, 233);

    // Clase auxiliar para retener referencias directas a los componentes de cada slot.
    private static class SlotUI {
        final WarehouseSlot slot;
        final JPanel panel;
        final JLabel idLbl;
        final JLabel contentLbl;

        SlotUI(WarehouseSlot slot, JPanel panel, JLabel idLbl, JLabel contentLbl) {
            this.slot = slot;
            this.panel = panel;
            this.idLbl = idLbl;
            this.contentLbl = contentLbl;
        }

        void update() {
            if (slot.isAvailable()) {
                panel.setBackground(COLOR_FREE_BG);
                panel.setBorder(BorderFactory.createLineBorder(COLOR_FREE_BORDER));
                contentLbl.setText("LIBRE");
                contentLbl.setForeground(COLOR_FREE_TEXT);
                idLbl.setForeground(Color.GRAY);
                panel.setToolTipText(String.format("Slot: %s (Disponible)", slot.getSlotId()));
            } else {
                panel.setBackground(COLOR_OCCUPIED_BG);
                panel.setBorder(BorderFactory.createLineBorder(COLOR_OCCUPIED_BORDER, 2));
                contentLbl.setText(slot.getCurrentProductId() + " (" + slot.getStoredUnits() + "u)");
                contentLbl.setForeground(Color.WHITE);
                idLbl.setForeground(COLOR_OCCUPIED_ID);
                panel.setToolTipText(String.format(
                        "<html><b>Ubicación:</b> %s<br><b>Lote:</b> %s<br><b>Producto:</b> %s<br><b>Unidades:</b> %d / %d</html>",
                        slot.getSlotId(), slot.getCurrentBatchId(), slot.getCurrentProductId(), slot.getStoredUnits(), slot.getCapacity()
                ));
            }
        }
    }

    /**
     * Construye el panel visual de almacenamiento y métricas de SLA.
     *
     * @param storageManager Gestor de almacenamiento fisico.
     * @param metrics Gestor de metricas de simulacion.
     */
    public WarehouseViewPanel(WarehouseStorageManager storageManager, SimulationMetrics metrics) {
        this.storageManager = storageManager;
        this.metrics = metrics;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 1. Panel Superior con Estadisticas de Capacidad
        JPanel topPanel = new JPanel(new BorderLayout());
        capacitySummaryLabel = new JLabel("Capacidad Almacén: 0 / 0 slots ocupados (0%)");
        capacitySummaryLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        topPanel.add(capacitySummaryLabel, BorderLayout.WEST);
        add(topPanel, BorderLayout.NORTH);

        // 2. Panel Central: Matriz de Slots Fisicos (Construida una sola vez)
        JPanel gridPanel = new JPanel(new GridLayout(6, 8, 5, 5));
        gridPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(70, 70, 70)),
                "MAPA FÍSICO DE RANURAS / SLOTS (PAGINACIÓN NO CONTIGUA)",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 12)
        ));

        for (WarehouseSlot slot : storageManager.getAllSlots()) {
            JPanel slotBox = new JPanel(new BorderLayout());
            slotBox.setPreferredSize(new Dimension(95, 55));

            JLabel idLbl = new JLabel(slot.getSlotId(), SwingConstants.CENTER);
            idLbl.setFont(new Font("Segoe UI", Font.BOLD, 10));

            JLabel contentLbl = new JLabel("LIBRE", SwingConstants.CENTER);
            contentLbl.setFont(new Font("Segoe UI", Font.BOLD, 10));

            slotBox.add(idLbl, BorderLayout.NORTH);
            slotBox.add(contentLbl, BorderLayout.CENTER);
            gridPanel.add(slotBox);

            SlotUI ui = new SlotUI(slot, slotBox, idLbl, contentLbl);
            slotUIList.add(ui);
        }

        JScrollPane scrollGrid = new JScrollPane(gridPanel);
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
        stockWrapper.add(new JScrollPane(stockTable), BorderLayout.CENTER);
        rightPanel.add(stockWrapper);

        // Tabla de SLA
        String[] slaCols = {"Nivel de Servicio", "Tiempo Objetivo", "Tiempo Real Prom.", "Cumplimiento"};
        slaModel = new DefaultTableModel(slaCols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
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
        slaWrapper.add(new JScrollPane(slaTable), BorderLayout.CENTER);
        rightPanel.add(slaWrapper);

        add(rightPanel, BorderLayout.EAST);

        refreshData();
    }

    /**
     * Refresca los estados de los slots y los registros de las dos tablas.
     */
    public void refreshData() {
        // Actualizar resumen de capacidad
        int total = storageManager.getTotalSlots();
        int occupied = storageManager.getOccupiedSlotsCount();
        double pct = total > 0 ? (occupied / (double) total) * 100.0 : 0.0;
        capacitySummaryLabel.setText(String.format("Capacidad Almacén: %d / %d slots ocupados (%.1f%%)", occupied, total, pct));

        // Actualizar slots en su lugar (sin destruir ni recrear componentes)
        for (SlotUI ui : slotUIList) {
            ui.update();
        }

        // Actualizar tabla de existencias
        stockModel.setRowCount(0);
        for (Product p : Catalog.getAllProducts()) {
            int stock = storageManager.getAvailableStock(p);
            stockModel.addRow(new Object[]{
                    p.getId(),
                    p.getName(),
                    p.getMerchandiseType().getDisplayName(),
                    stock + " unidades"
            });
        }

        // Actualizar tabla de SLA
        slaModel.setRowCount(0);
        Map<ServiceLevel, Double> waitMap = metrics.getAverageWaitTimeByServiceLevel();
        for (ServiceLevel sl : ServiceLevel.values()) {
            double actualSecs = waitMap.getOrDefault(sl, 0.0);
            String status = actualSecs == 0.0 ? "Sin datos" :
                    (actualSecs <= sl.getMaxWaitSecondsSimulated() ? "✓ Dentro de SLA" : "⚠️ Excedido");
            slaModel.addRow(new Object[]{
                    sl.getDisplayName(),
                    sl.getDescription(),
                    String.format("%.1f seg", actualSecs),
                    status
            });
        }
    }
}
