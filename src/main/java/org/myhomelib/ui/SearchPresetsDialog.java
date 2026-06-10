package org.myhomelib.ui;

import org.myhomelib.model.SearchPreset;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;

public final class SearchPresetsDialog extends JDialog {

    private final DefaultListModel<SearchPreset> model =
            new DefaultListModel<>();

    private final JList<SearchPreset> presetList =
            new JList<>(model);

    private SearchPreset selectedPreset;

    public SearchPresetsDialog(
            JFrame owner,
            List<SearchPreset> presets
    ) {

        super(owner, "Search Presets", true);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(500, 400);
        setMinimumSize(new Dimension(400, 300));
        setLocationRelativeTo(owner);

        for (SearchPreset preset : presets) {
            model.addElement(preset);
        }

        buildUi();
    }

    public SearchPreset selectedPreset() {
        return selectedPreset;
    }

    private void buildUi() {

        setLayout(new BorderLayout(8, 8));

        JPanel centerPanel =
                new JPanel(new BorderLayout());

        centerPanel.setBorder(
                BorderFactory.createEmptyBorder(
                        8,
                        8,
                        8,
                        8
                )
        );

        presetList.setSelectionMode(
                ListSelectionModel.SINGLE_SELECTION
        );

        presetList.setCellRenderer(
                (list,
                 value,
                 index,
                 isSelected,
                 cellHasFocus) -> {

                    javax.swing.JLabel label =
                            new javax.swing.JLabel(
                                    value.name()
                            );

                    if (isSelected) {
                        label.setOpaque(true);
                        label.setBackground(
                                list.getSelectionBackground()
                        );
                        label.setForeground(
                                list.getSelectionForeground()
                        );
                    }

                    return label;
                }
        );

        centerPanel.add(
                new JScrollPane(presetList),
                BorderLayout.CENTER
        );

        add(centerPanel, BorderLayout.CENTER);

        JPanel buttons =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.RIGHT
                        )
                );

        JButton openButton =
                new JButton("Open");

        JButton cancelButton =
                new JButton("Cancel");

        openButton.addActionListener(
                e -> openPreset()
        );

        cancelButton.addActionListener(
                e -> {
                    selectedPreset = null;
                    dispose();
                }
        );

        buttons.add(openButton);
        buttons.add(cancelButton);

        add(buttons, BorderLayout.SOUTH);
    }

    private void openPreset() {

        SearchPreset preset =
                presetList.getSelectedValue();

        if (preset == null) {

            JOptionPane.showMessageDialog(
                    this,
                    "Select preset",
                    "Search Presets",
                    JOptionPane.WARNING_MESSAGE
            );

            return;
        }

        selectedPreset = preset;

        dispose();
    }
}