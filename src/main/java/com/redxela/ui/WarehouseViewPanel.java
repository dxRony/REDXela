package com.redxela.ui;

import com.redxela.model.Catalog;
import com.redxela.model.Product;
import com.redxela.model.WarehouseSlot;
import com.redxela.storage.WarehouseStorageManager;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * Panel visual que representa la matriz de almacenamiento no contiguo del almacen.
 * Muestra el estado de cada slot (pasillo, rack, nivel) y la tabla de existencias actual.
 */
public class WarehouseViewPanel extends JPanel {

    // Gestor fisico del almacen.
    private final WarehouseStorageManager storageManager;

    // Panel contenedor de la cuadricula tridimensional de ranuras.
    private final JPanel gridPanel;

    // Modelo de datos para la tabla de inventario por producto.
    private final DefaultTableModel stockModel;

    // Etiqueta de resumen que muestra la ocupacion total en porcentaje y unidades.
    private final JLabel capacitySummaryLabel;

    /**
     * Construye el panel visual de almacenamiento.
     *
     * @param storageManager Gestor de almacenamiento fisico.
     */
    public WarehouseViewPanel(WarehouseStorageManager storageManager) {
        this.storageManager = storageManager;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 1. Panel Superior con Estadisticas de Capacidad
        JPanel topPanel = new JPanel(new BorderLayout());
        capacitySummaryLabel = new JLabel("Capacidad Almacén: 0 / 0 slots ocupados (0%)");
        capacitySummaryLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        topPanel.add(capacitySummaryLabel, BorderLayout.WEST);
        add(topPanel, BorderLayout.NORTH);

        // 2. Panel Central: Matriz de Slots Fisicos
        gridPanel = new JPanel(new GridLayout(6, 8, 6, 6)); // Cuadricula de slots
        gridPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(70, 70, 70)),
                "MAPA FÍSICO DE RANURAS / SLOTS (ASIGNACIÓN NO CONTIGUA)",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 12)
        ));
        JScrollPane scrollGrid = new JScrollPane(gridPanel);
        add(scrollGrid, BorderLayout.CENTER);

        // 3. Panel Lateral Derecho: Tabla de Stock por Producto
        String[] cols = {"Código", "Producto", "Tipo", "Stock Total"};
        stockModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        JTable stockTable = new JTable(stockModel);
        stockTable.setRowHeight(22);
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setPreferredSize(new Dimension(380, 0));
        rightPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(70, 70, 70)),
                "EXISTENCIAS ACTUALES DE INVENTARIO",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 12)
        ));
        rightPanel.add(new JScrollPane(stockTable), BorderLayout.CENTER);
        add(rightPanel, BorderLayout.EAST);

        refreshData();
    }

    /**
     * Reconstruye la cuadricula grafica de slots y actualiza la tabla de existencias de productos.
     */
    public void refreshData() {
        // Actualizar resumen
        int total = storageManager.getTotalSlots();
        int occupied = storageManager.getOccupiedSlotsCount();
        double pct = total > 0 ? (occupied / (double) total) * 100.0 : 0.0;
        capacitySummaryLabel.setText(String.format("Capacidad Almacén: %d / %d slots ocupados (%.1f%%)", occupied, total, pct));

        // Reconstruir cuadricula de slots
        gridPanel.removeAll();
        List<WarehouseSlot> slots = storageManager.getAllSlots();
        for (WarehouseSlot slot : slots) {
            JPanel slotBox = new JPanel(new BorderLayout());
            slotBox.setPreferredSize(new Dimension(100, 60));

            JLabel idLbl = new JLabel(slot.getSlotId(), SwingConstants.CENTER);
            idLbl.setFont(new Font("Segoe UI", Font.BOLD, 11));

            JLabel contentLbl;
            if (slot.isAvailable()) {
                slotBox.setBackground(new Color(40, 44, 52));
                slotBox.setBorder(BorderFactory.createLineBorder(new Color(70, 80, 95)));
                contentLbl = new JLabel("LIBRE", SwingConstants.CENTER);
                contentLbl.setForeground(new Color(120, 140, 160));
                idLbl.setForeground(Color.GRAY);
            } else {
                slotBox.setBackground(new Color(30, 65, 95));
                slotBox.setBorder(BorderFactory.createLineBorder(new Color(52, 152, 219), 2));
                contentLbl = new JLabel(slot.getCurrentProductId() + " (" + slot.getStoredUnits() + "u)", SwingConstants.CENTER);
                contentLbl.setForeground(Color.WHITE);
                contentLbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
                idLbl.setForeground(new Color(133, 193, 233));

                slotBox.setToolTipText(String.format(
                        "<html><b>Ubicación:</b> %s<br><b>Lote:</b> %s<br><b>Producto:</b> %s<br><b>Unidades:</b> %d / %d</html>",
                        slot.getSlotId(), slot.getCurrentBatchId(), slot.getCurrentProductId(), slot.getStoredUnits(), slot.getCapacity()
                ));
            }

            slotBox.add(idLbl, BorderLayout.NORTH);
            slotBox.add(contentLbl, BorderLayout.CENTER);
            gridPanel.add(slotBox);
        }
        gridPanel.revalidate();
        gridPanel.repaint();

        // Actualizar tabla de existencias por producto
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
    }
}
