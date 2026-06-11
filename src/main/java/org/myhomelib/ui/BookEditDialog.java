package org.myhomelib.ui;

import org.myhomelib.model.Book;
import org.myhomelib.model.BookEdit;
import javax.swing.*;
import java.awt.*;

public final class BookEditDialog extends JDialog {
    private final Book book;
    private BookEdit result;

    private JTextField titleField;
    private JTextField sequenceNumField;
    private JTextField langField;
    private JTextField keywordsField;
    private JTextArea annotationArea;
    private JSlider rateSlider;
    private JSlider progressSlider;

    public BookEditDialog(Frame owner, Book book) {
        super(owner, "Редагування картки книги", true);
        this.book = book;
        initializeUI();
    }

    private void initializeUI() {
        setLayout(new BorderLayout(10, 10));
        setSize(500, 600);
        setLocationRelativeTo(getOwner());

        JPanel formPanel = new JPanel(new GridLayout(8, 2, 5, 5));
        formPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        formPanel.add(new JLabel("Назва книги:"));
        titleField = new JTextField(book.title());
        formPanel.add(titleField);

        formPanel.add(new JLabel("Автори (тільки перегляд):"));
        // Використовуємо наш виправлений сумісний метод
        formPanel.add(new JLabel(book.authorsText()));

        formPanel.add(new JLabel("Жанри (тільки перегляд):"));
        formPanel.add(new JLabel(book.genresText()));

        formPanel.add(new JLabel("Номер у серії:"));
        sequenceNumField = new JTextField(book.sequenceNumber() != null ? book.sequenceNumber().toString() : "0");
        formPanel.add(sequenceNumField);

        formPanel.add(new JLabel("Мова:"));
        langField = new JTextField(book.language());
        formPanel.add(langField);

        formPanel.add(new JLabel("Ключові слова:"));
        keywordsField = new JTextField(book.keywords());
        formPanel.add(keywordsField);

        formPanel.add(new JLabel("Оцінка книги (0-5):"));
        rateSlider = new JSlider(0, 5, book.rate());
        rateSlider.setMajorTickSpacing(1);
        rateSlider.setPaintTicks(true);
        rateSlider.setPaintLabels(true);
        formPanel.add(rateSlider);

        formPanel.add(new JLabel("Прогрес читання (%):"));
        progressSlider = new JSlider(0, 100, book.progress());
        progressSlider.setMajorTickSpacing(25);
        progressSlider.setPaintTicks(true);
        progressSlider.setPaintLabels(true);
        formPanel.add(progressSlider);

        annotationArea = new JTextArea(book.annotation());
        annotationArea.setLineWrap(true);
        annotationArea.setWrapStyleWord(true);
        JScrollPane annotationScroll = new JScrollPane(annotationArea);
        annotationScroll.setBorder(BorderFactory.createTitledBorder("Анотація"));

        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveButton = new JButton("Зберегти");
        JButton cancelButton = new JButton("Скасувати");
        buttonsPanel.add(saveButton);
        buttonsPanel.add(cancelButton);

        add(formPanel, BorderLayout.NORTH);
        add(annotationScroll, BorderLayout.CENTER);
        add(buttonsPanel, BorderLayout.SOUTH);

        cancelButton.addActionListener(e -> dispose());
        saveButton.addActionListener(e -> {
            Integer seqNum = 0;
            try {
                seqNum = Integer.parseInt(sequenceNumField.getText().trim());
            } catch (NumberFormatException ignored) {}

            // Виклик виправленого конструктора рекорду BookEdit (всього 7 параметрів)
            result = new BookEdit(
                    titleField.getText().trim(),
                    seqNum,
                    langField.getText().trim(),
                    keywordsField.getText().trim(),
                    annotationArea.getText().trim(),
                    rateSlider.getValue(),
                    progressSlider.getValue()
            );
            dispose();
        });
    }

    public BookEdit getResult() {
        return result;
    }
}