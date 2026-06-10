package org.myhomelib.ui;

import org.myhomelib.model.Author;
import org.myhomelib.model.Book;
import org.myhomelib.model.BookEdit;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Arrays;
import java.util.List;

final class BookEditDialog extends JDialog {
    private final JTextField titleField = new JTextField();
    private final JTextField authorsField = new JTextField();
    private final JTextField genresField = new JTextField();
    private final JTextField seriesField = new JTextField();
    private final JTextField sequenceField = new JTextField();
    private final JTextField languageField = new JTextField();
    private final JTextField keywordsField = new JTextField();
    private final JSpinner rateSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 5, 1));
    private final JSpinner progressSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 100, 1));
    private final JTextArea annotationArea = new JTextArea();
    private BookEdit result;

    BookEditDialog(Frame owner, Book book) {
        super(owner, "Edit book", true);
        setMinimumSize(new Dimension(620, 560));
        setLocationRelativeTo(owner);
        build(book);
    }

    BookEdit result() {
        return result;
    }

    private void build(Book book) {
        titleField.setText(book.title());
        authorsField.setText(book.authorsText());
        genresField.setText(book.genresText());
        seriesField.setText(book.series() == null ? "" : book.series());
        sequenceField.setText(book.sequenceNumber() == null ? "" : book.sequenceNumber().toString());
        languageField.setText(book.language() == null ? "" : book.language());
        keywordsField.setText(book.keywords() == null ? "" : book.keywords());
        rateSpinner.setValue(book.rate());
        progressSpinner.setValue(book.progress());
        annotationArea.setText(book.annotation() == null ? "" : book.annotation());
        annotationArea.setLineWrap(true);
        annotationArea.setWrapStyleWord(true);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        addRow(form, 0, "Title", titleField);
        addRow(form, 1, "Authors", authorsField);
        addRow(form, 2, "Genres", genresField);
        addRow(form, 3, "Series", seriesField);
        addRow(form, 4, "Number", sequenceField);
        addRow(form, 5, "Language", languageField);
        addRow(form, 6, "Keywords", keywordsField);
        addRow(form, 7, "Rate", rateSpinner);
        addRow(form, 8, "Progress", progressSpinner);

        GridBagConstraints label = constraints(0, 9);
        label.anchor = GridBagConstraints.NORTHWEST;
        form.add(new JLabel("Annotation"), label);
        GridBagConstraints value = constraints(1, 9);
        value.weighty = 1;
        value.fill = GridBagConstraints.BOTH;
        form.add(new JScrollPane(annotationArea), value);

        JPanel buttons = new JPanel();
        JButton save = new JButton("Save");
        JButton cancel = new JButton("Cancel");
        save.addActionListener(event -> save());
        cancel.addActionListener(event -> dispose());
        buttons.add(save);
        buttons.add(cancel);

        setLayout(new BorderLayout());
        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
    }

    private void addRow(JPanel form, int row, String title, java.awt.Component field) {
        GridBagConstraints label = constraints(0, row);
        label.weightx = 0;
        label.fill = GridBagConstraints.NONE;
        label.anchor = GridBagConstraints.WEST;
        form.add(new JLabel(title), label);

        GridBagConstraints value = constraints(1, row);
        value.weightx = 1;
        value.fill = GridBagConstraints.HORIZONTAL;
        form.add(field, value);
    }

    private GridBagConstraints constraints(int x, int y) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        c.insets = new Insets(4, 4, 4, 4);
        c.weightx = x == 1 ? 1 : 0;
        return c;
    }

    private void save() {
        Integer sequence = null;
        if (!sequenceField.getText().isBlank()) {
            try {
                sequence = Integer.parseInt(sequenceField.getText().trim());
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "Number must be an integer.", "Invalid value", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
        result = new BookEdit(
                titleField.getText().trim(),
                parseAuthors(authorsField.getText()),
                splitList(genresField.getText()),
                seriesField.getText().trim(),
                sequence,
                languageField.getText().trim(),
                keywordsField.getText().trim(),
                annotationArea.getText().trim(),
                (Integer) rateSpinner.getValue(),
                (Integer) progressSpinner.getValue()
        );
        dispose();
    }

    private static List<Author> parseAuthors(String text) {
        List<Author> authors = splitList(text).stream()
                .map(BookEditDialog::parseAuthor)
                .toList();
        if (authors.isEmpty()) {
            return List.of(new Author(0, "", "", "Unknown"));
        }
        return authors;
    }

    private static Author parseAuthor(String text) {
        String[] parts = text.trim().split("\\s+");
        if (parts.length == 0) {
            return new Author(0, "", "", "Unknown");
        }
        if (parts.length == 1) {
            return new Author(0, "", "", parts[0]);
        }
        if (parts.length == 2) {
            return new Author(0, parts[1], "", parts[0]);
        }
        String middle = String.join(" ", Arrays.copyOfRange(parts, 2, parts.length));
        return new Author(0, parts[1], middle, parts[0]);
    }

    private static List<String> splitList(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }
}
