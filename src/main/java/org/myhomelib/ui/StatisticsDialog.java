package org.myhomelib.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.Map;

public final class StatisticsDialog extends JDialog {

    private final Map<String, Integer> stats;

    public StatisticsDialog(JFrame owner, Map<String, Integer> stats) {
        super(owner, "Collection Statistics", true);
        this.stats = stats;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(400, 350);
        setMinimumSize(new Dimension(300, 250));
        setLocationRelativeTo(owner);

        buildUi();
    }

    private void buildUi() {
        setLayout(new BorderLayout(8, 8));

        String[] columns = {"Metric", "Value"};
        DefaultTableModel tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        // Заповнюємо таблицю даними з твоєї Map
        stats.forEach((key, value) -> tableModel.addRow(new Object[]{key, value}));

        JTable statsTable = new JTable(tableModel);
        statsTable.setRowHeight(24);
        statsTable.getTableHeader().setReorderingAllowed(false);

        JScrollPane scrollPane = new JScrollPane(statsTable);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        buttonPanel.add(closeButton);

        add(buttonPanel, BorderLayout.SOUTH);
    }
}