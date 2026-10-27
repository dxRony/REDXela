package com.redxela.ui;

import com.redxela.model.*;
import com.redxela.model.enums.*;
import com.redxela.scheduler.OrderScheduler;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Diálogo modal para la creación manual de pedidos personalizados.
 * Permite definir el ID único, cliente, nivel de servicio SLA, tipo de mercancía,
 * producto solicitado y cantidad de unidades.
 */
public class ManualOrderDialog extends JDialog {

    // Secuenciador para IDs de pedidos manuales si se deja vacío.
    private static final AtomicInteger manualCounter = new AtomicInteger(1000);

    /**
     * Construye y despliega el formulario modal de ingreso manual.
     *
     * @param parent Ventana principal padre.
     * @param scheduler Planificador donde se insertará el pedido.
     */
    public ManualOrderDialog(JFrame parent, OrderScheduler scheduler) {
        super(parent, "Ingreso Manual de Pedido", true);
        setSize(520, 420);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(10, 10));

        JPanel form = new JPanel(new GridLayout(6, 2, 10, 12));
        form.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // 1. ID del Pedido
        form.add(new JLabel("ID del Pedido:"));
        JTextField txtId = new JTextField("MAN-999");
        form.add(txtId);

        // 2. Nombre del Cliente
        form.add(new JLabel("Nombre del Cliente:"));
        JTextField txtClient = new JTextField("Catedrático SO2");
        form.add(txtClient);

        // 3. Nivel de Servicio (SLA)
        form.add(new JLabel("Nivel de Servicio (SLA):"));
        JComboBox<ServiceLevel> cmbSla = new JComboBox<>(ServiceLevel.values());
        cmbSla.setSelectedItem(ServiceLevel.EXPRES);
        form.add(cmbSla);

        // 4. Tipo de Mercancía
        form.add(new JLabel("Tipo de Mercancía:"));
        JComboBox<MerchandiseType> cmbType = new JComboBox<>(MerchandiseType.values());
        cmbType.setSelectedItem(MerchandiseType.PERECEDERA);
        form.add(cmbType);

        // 5. Producto
        form.add(new JLabel("Producto:"));
        JComboBox<Product> cmbProduct = new JComboBox<>();
        form.add(cmbProduct);

        // Método auxiliar para actualizar los productos según el tipo de mercancía seleccionado
        Runnable updateProducts = () -> {
            MerchandiseType selectedType = (MerchandiseType) cmbType.getSelectedItem();
            cmbProduct.removeAllItems();
            if (selectedType != null) {
                List<Product> products = Catalog.getByMerchandiseType(selectedType);
                for (Product p : products) {
                    cmbProduct.addItem(p);
                }
            }
        };

        cmbType.addActionListener(e -> updateProducts.run());
        updateProducts.run(); // Carga inicial

        // 6. Cantidad de Unidades
        form.add(new JLabel("Cantidad de Unidades:"));
        JSpinner spinQty = new JSpinner(new SpinnerNumberModel(2, 1, 50, 1));
        form.add(spinQty);

        add(form, BorderLayout.CENTER);

        // Botones de acción
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnCancel = new JButton("Cancelar");
        btnCancel.addActionListener(e -> dispose());

        JButton btnSubmit = new JButton("Crear Pedido");
        btnSubmit.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnSubmit.setBackground(new Color(46, 204, 113));
        btnSubmit.setForeground(Color.BLACK);
        btnSubmit.addActionListener(e -> {
            Product prod = (Product) cmbProduct.getSelectedItem();
            if (prod == null) {
                JOptionPane.showMessageDialog(this, "Debe seleccionar un producto válido.", "Aviso", JOptionPane.WARNING_MESSAGE);
                return;
            }

            String orderId = txtId.getText().trim();
            if (orderId.isEmpty()) {
                orderId = "MAN-" + manualCounter.incrementAndGet();
            }

            String client = txtClient.getText().trim();
            if (client.isEmpty()) {
                client = "Cliente Anónimo";
            }

            int qty = (int) spinQty.getValue();
            Map<Product, Integer> items = new HashMap<>();
            items.put(prod, qty);

            MerchandiseType selectedType = (MerchandiseType) cmbType.getSelectedItem();
            if (selectedType == null) {
                selectedType = prod.getMerchandiseType();
            }

            Order order = new Order(
                    orderId,
                    client,
                    (ServiceLevel) cmbSla.getSelectedItem(),
                    selectedType,
                    items
            );

            scheduler.addOrder(order);
            JOptionPane.showMessageDialog(this,
                    "Pedido #" + order.getId() + " (" + order.getMerchandiseType().getDisplayName() + ") creado con éxito.",
                    "Pedido Registrado",
                    JOptionPane.INFORMATION_MESSAGE);
            dispose();
        });

        buttons.add(btnCancel);
        buttons.add(btnSubmit);
        add(buttons, BorderLayout.SOUTH);
    }
}
