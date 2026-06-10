package org.myhomelib.ui;

import org.myhomelib.model.SearchCriteria;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;

public final class AdvancedSearchDialog extends JDialog {

    private SearchCriteria result;

    private final JTextField titleField = new JTextField();
    private final JTextField authorField = new JTextField();
    private final JTextField genreField = new JTextField();
    private final JTextField seriesField = new JTextField();
    private final JTextField languageField = new JTextField();

    private final JTextField rateFromField = new JTextField();
    private final JTextField rateToField = new JTextField();

    private final JTextField progressFromField = new JTextField();
    private final JTextField progressToField = new JTextField();

    private final JTextField sizeFromField = new JTextField();
    private final JTextField sizeToField = new JTextField();

    private final JTextField keywordsField = new JTextField();
    private final JTextField annotationField = new JTextField();

    private final JTextField groupField = new JTextField();

    public AdvancedSearchDialog(Frame owner) {
        super(owner, "Advanced Search", true);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(700, 500);
        setLocationRelativeTo(owner);

        buildUi();
    }

    public SearchCriteria result() {
        return result;
    }

    private void buildUi() {

        JPanel fields = new JPanel(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();

        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        addRow(fields, gbc, row++, "Title", titleField);
        addRow(fields, gbc, row++, "Author", authorField);
        addRow(fields, gbc, row++, "Genre", genreField);
        addRow(fields, gbc, row++, "Series", seriesField);
        addRow(fields, gbc, row++, "Language", languageField);

        addRangeRow(
                fields,
                gbc,
                row++,
                "Rate",
                rateFromField,
                rateToField
        );

        addRangeRow(
                fields,
                gbc,
                row++,
                "Progress",
                progressFromField,
                progressToField
        );

        addRangeRow(
                fields,
                gbc,
                row++,
                "Size",
                sizeFromField,
                sizeToField
        );

        addRow(fields, gbc, row++, "Keywords", keywordsField);
        addRow(fields, gbc, row++, "Annotation", annotationField);
        addRow(fields, gbc, row++, "Group", groupField);

        JButton searchButton = new JButton("Search");
        JButton cancelButton = new JButton("Cancel");

        searchButton.addActionListener(e -> onSearch());

        cancelButton.addActionListener(e -> {
            result = null;
            dispose();
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        buttons.add(searchButton);
        buttons.add(cancelButton);

        getContentPane().setLayout(new BorderLayout());

        getContentPane().add(
                new JScrollPane(fields),
                BorderLayout.CENTER
        );

        getContentPane().add(
                buttons,
                BorderLayout.SOUTH
        );
    }

    private void addRow(
            JPanel panel,
            GridBagConstraints gbc,
            int row,
            String label,
            JComponent component
    ) {

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;

        panel.add(new JLabel(label + ":"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;

        panel.add(component, gbc);
    }

    private void addRangeRow(
            JPanel panel,
            GridBagConstraints gbc,
            int row,
            String label,
            JTextField from,
            JTextField to
    ) {

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;

        panel.add(new JLabel(label + ":"), gbc);

        JPanel rangePanel = new JPanel(
                new GridLayout(1, 4, 4, 0)
        );

        rangePanel.add(new JLabel("From"));
        rangePanel.add(from);
        rangePanel.add(new JLabel("To"));
        rangePanel.add(to);

        gbc.gridx = 1;
        gbc.weightx = 1.0;

        panel.add(rangePanel, gbc);
    }

    private void onSearch() {

        try {

            result = new SearchCriteria(

                    text(titleField),

                    text(authorField),

                    text(genreField),

                    text(seriesField),

                    text(languageField),

                    integer(rateFromField),
                    integer(rateToField),

                    integer(progressFromField),
                    integer(progressToField),

                    longValue(sizeFromField),
                    longValue(sizeToField),

                    null,
                    null,

                    text(keywordsField),

                    text(annotationField),

                    text(groupField)
            );

            dispose();

        } catch (Exception e) {

            JOptionPane.showMessageDialog(
                    this,
                    e.getMessage(),
                    "Invalid values",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private static String text(JTextField field) {

        String value = field.getText();

        if (value == null) {
            return null;
        }

        value = value.trim();

        return value.isEmpty() ? null : value;
    }

    private static Integer integer(JTextField field) {

        String value = text(field);

        if (value == null) {
            return null;
        }

        return Integer.parseInt(value);
    }

    private static Long longValue(JTextField field) {

        String value = text(field);

        if (value == null) {
            return null;
        }

        return Long.parseLong(value);
    }

    @SuppressWarnings("unused")
    private static LocalDateTime dateTime(JTextField field) {

        String value = text(field);

        if (value == null) {
            return null;
        }

        return LocalDateTime.parse(value);
    }
}