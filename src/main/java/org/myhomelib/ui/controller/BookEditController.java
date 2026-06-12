package org.myhomelib.ui.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.myhomelib.model.Book;

import java.time.LocalDateTime;

public class BookEditController {

    @FXML private TextField titleField;
    @FXML private TextField seriesField;
    @FXML private TextField sequenceNumberField;
    @FXML private ComboBox<String> languageBox;
    @FXML private Spinner<Integer> rateSpinner;
    @FXML private Slider progressSlider;
    @FXML private Button btnSave;
    @FXML private Button btnCancel;

    private Book book;
    private boolean saveClicked = false;

    @FXML
    public void initialize() {
        // Ініціалізація випадаючого списку популярних мов локальної бібліотеки
        languageBox.setItems(FXCollections.observableArrayList("uk", "en", "ru", "pl", "de"));

        // Налаштування фабрики значень для Спінера оцінок від 0 до 5
        SpinnerValueFactory<Integer> valueFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 5, 0);
        rateSpinner.setValueFactory(valueFactory);
    }

    /**
     * Передача об'єкта книги для наповнення полів UI перед відображенням вікна.
     */
    public void setBook(Book book) {
        this.book = book;
        if (book != null) {
            titleField.setText(book.title());
            seriesField.setText(book.series());
            sequenceNumberField.setText(String.valueOf(book.sequenceNumber()));
            languageBox.setValue(book.language());
            rateSpinner.getValueFactory().setValue(book.rate());
            progressSlider.setValue(book.progress());
        }
    }

    public boolean isSaveClicked() {
        return saveClicked;
    }

    /**
     * Повертає оновлений об'єкт Book із новими даними з текстових полів.
     */
    public Book getUpdatedBook() {
        if (book == null) {
            return null;
        }

        int seqNum = 0;
        if (sequenceNumberField.getText() != null && !sequenceNumberField.getText().isBlank()) {
            try {
                seqNum = Integer.parseInt(sequenceNumberField.getText().trim());
            } catch (NumberFormatException ignored) {}
        }

        return new Book(
                book.id(),
                titleField.getText() != null ? titleField.getText().trim() : "",
                book.authors(),
                book.genres(),
                seriesField.getText() != null ? seriesField.getText().trim() : "",
                seqNum,
                book.fileName(),
                book.folder(),
                book.archiveEntry(),
                languageBox.getValue() != null ? languageBox.getValue() : book.language(),
                book.fileSize(),
                book.keywords(),
                book.annotation(),
                rateSpinner.getValue(),
                (int) progressSlider.getValue(),
                LocalDateTime.now()
        );
    }

    @FXML
    private void handleSaveAction() {
        if (isInputValid()) {
            saveClicked = true;
            closeStage();
        }
    }

    @FXML
    private void handleCancelAction() {
        saveClicked = false;
        closeStage();
    }

    private void closeStage() {
        Stage stage = (Stage) btnCancel.getScene().getWindow();
        stage.close();
    }

    /**
     * Валідація введення для захисту цілісності даних у базі SQLite з використанням .isBlank()
     */
    private boolean isInputValid() {
        String errorMessage = "";

        if (titleField.getText() == null || titleField.getText().isBlank()) {
            errorMessage += "Поле 'Назва книги' не може бути порожнім!\n";
        }

        if (sequenceNumberField.getText() != null && !sequenceNumberField.getText().isBlank()) {
            try {
                Integer.parseInt(sequenceNumberField.getText().trim());
            } catch (NumberFormatException e) {
                errorMessage += "Номер у серії повинен бути цілим числом!\n";
            }
        }

        if (errorMessage.isEmpty()) {
            return true;
        } else {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Помилка валідації полів");
            alert.setHeaderText("Будь ласка, виправте некоректні дані:");
            alert.setContentText(errorMessage);
            alert.showAndWait();
            return false;
        }
    }
}