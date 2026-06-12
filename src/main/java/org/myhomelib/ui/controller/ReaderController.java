package org.myhomelib.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;

public class ReaderController {
    @FXML private TextArea readerTextArea;
    @FXML private Slider fontSizeSlider;

    @FXML
    public void initialize() {
        readerTextArea.setText("Завантаження вмісту книги з ZIP/FB2 архіву...\nПочаток розділу 1.");

        // Динамічна зміна розміру шрифту читання в рантаймі
        fontSizeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            readerTextArea.setStyle("-fx-font-family: 'Georgia'; -fx-font-size: " + newVal.intValue() + "px;");
        });
    }

    @FXML private void handlePrevPage() {}
    @FXML private void handleNextPage() {}
}