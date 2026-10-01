package com.redxela.ui;

import com.redxela.concurrency.ReceptionProcess;
import com.redxela.model.Catalog;
import com.redxela.model.Product;

import javax.swing.*;
import java.awt.*;

/**
 * Diálogo para registrar la llegada manual de mercancía desde un camión al Área de Recepción.
 */
public class ManualGoodsDialog extends JDialog {
    public ManualGoodsDialog(JFrame parent, ReceptionProcess receptionProcess) {
        super(parent, "Recepción de Mercadería (Llegada de Camión)", true);
        setSize(480, 260);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(10, 10));

        JPanel form = new JPanel(new GridLayout(3, 2, 10, 12));
        form.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        form.add(new JLabel("Producto Recibido:"));
        JComboBox<Product> cmbProduct = new JComboBox<>(Catalog.getAllProducts().toArray(new Product[0]));
        form.add(cmbProduct);

        form.add(new JLabel("Cantidad de Unidades:"));
        JSpinner spinQty = new JSpinner(new SpinnerNumberModel(20, 1, 200, 5));
        form.add(spinQty);

        form.add(new JLabel("Destino Inicial:"));
        JLabel lblDest = new JLabel("Buffer de Recepción -> Almacén No Contiguo");
        lblDest.setForeground(new Color(46, 204, 113));
        form.add(lblDest);

        add(form, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnCancel = new JButton("Cancelar");
        btnCancel.addActionListener(e -> dispose());

        JButton btnSubmit = new JButton("Registrar Llegada de Camión");
        btnSubmit.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnSubmit.setBackground(new Color(52, 152, 219));
        btnSubmit.setForeground(Color.BLACK);
        btnSubmit.addActionListener(e -> {
            Product prod = (Product) cmbProduct.getSelectedItem();
            if (prod == null) return;
            int qty = (int) spinQty.getValue();

            new Thread(() -> {
                try {
                    receptionProcess.registerArrival(prod, qty);
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this,
                                "Se registraron " + qty + " unidades de " + prod.getName() + " en el Buffer de Recepción.\n"
                                        + "El área de Inventario las almacenará de forma no contigua.",
                                "Llegada Procesada",
                                JOptionPane.INFORMATION_MESSAGE);
                        dispose();
                    });
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        });

        buttons.add(btnCancel);
        buttons.add(btnSubmit);
        add(buttons, BorderLayout.SOUTH);
    }
}
