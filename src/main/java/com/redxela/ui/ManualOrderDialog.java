package com.redxela.ui;

import com.redxela.model.*;
import com.redxela.scheduler.OrderScheduler;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Diálogo para el ingreso manual de un pedido específico.
 */
public class ManualOrderDialog extends JDialog {
    private static final AtomicInteger manualCounter = new AtomicInteger(5000);

    public ManualOrderDialog(JFrame parent, OrderScheduler scheduler) {
        super(parent, "Ingreso Manual de Pedido", true);
        setSize(480, 360);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(10, 10));

        JPanel form = new JPanel(new GridLayout(5, 2, 10, 12));
        form.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Campos
        form.add(new JLabel("Nombre del Cliente:"));
        JTextField txtClient = new JTextField("Cliente Manual #" + manualCounter.incrementAndGet());
        form.add(txtClient);

        form.add(new JLabel("Nivel de Servicio (SLA):"));
        JComboBox<ServiceLevel> cmbSla = new JComboBox<>(ServiceLevel.values());
        cmbSla.setSelectedItem(ServiceLevel.PRIORITARIO);
        form.add(cmbSla);

        form.add(new JLabel("Producto:"));
        JComboBox<Product> cmbProduct = new JComboBox<>(Catalog.getAllProducts().toArray(new Product[0]));
        form.add(cmbProduct);

        form.add(new JLabel("Cantidad de Unidades:"));
        JSpinner spinQty = new JSpinner(new SpinnerNumberModel(2, 1, 20, 1));
        form.add(spinQty);

        form.add(new JLabel("Tipo de Mercancía:"));
        JLabel lblType = new JLabel(Catalog.getAllProducts().get(0).getMerchandiseType().getDisplayName());
        lblType.setForeground(new Color(52, 152, 219));
        form.add(lblType);

        cmbProduct.addActionListener(e -> {
            Product selected = (Product) cmbProduct.getSelectedItem();
            if (selected != null) {
                lblType.setText(selected.getMerchandiseType().getDisplayName());
            }
        });

        add(form, BorderLayout.CENTER);

        // Botones de acción
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnCancel = new JButton("Cancelar");
        btnCancel.addActionListener(e -> dispose());

        JButton btnSubmit = new JButton("Ingresar Pedido");
        btnSubmit.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnSubmit.setBackground(new Color(46, 204, 113));
        btnSubmit.setForeground(Color.BLACK);
        btnSubmit.addActionListener(e -> {
            Product prod = (Product) cmbProduct.getSelectedItem();
            if (prod == null) return;

            int qty = (int) spinQty.getValue();
            Map<Product, Integer> items = new HashMap<>();
            items.put(prod, qty);

            Order order = new Order(
                    String.valueOf(manualCounter.get()),
                    txtClient.getText().trim(),
                    (ServiceLevel) cmbSla.getSelectedItem(),
                    prod.getMerchandiseType(),
                    items
            );

            scheduler.addOrder(order);
            JOptionPane.showMessageDialog(this,
                    "Pedido #" + order.getId() + " ingresado con éxito al sistema.",
                    "Pedido Registrado",
                    JOptionPane.INFORMATION_MESSAGE);
            dispose();
        });

        buttons.add(btnCancel);
        buttons.add(btnSubmit);
        add(buttons, BorderLayout.SOUTH);
    }
}
