package org.myhomelib.ui.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import java.time.LocalDate;

public class AdvancedSearchController {

    @FXML private TextField titleField;
    @FXML private TextField authorField;
    @FXML private TextField seriesField;
    @FXML private TextField genreField;
    @FXML private ComboBox<String> languageBox;
    @FXML private Slider minRateSlider;
    @FXML private DatePicker dateFromPicker;
    @FXML private Button btnSearch;

    private boolean searchTriggered = false;

    @FXML
    public void initialize() {
        languageBox.setItems(FXCollections.observableArrayList("Усі", "uk", "en", "ru", "pl", "de"));
        languageBox.setValue("Усі");
    }

    public boolean isSearchTriggered() {
        return searchTriggered;
    }

    @FXML
    private void handleSearch() {
        searchTriggered = true;
        closeStage();
    }

    @FXML
    private void handleReset() {
        titleField.clear();
        authorField.clear();
        seriesField.clear();
        genreField.clear();
        languageBox.setValue("Усі");
        minRateSlider.setValue(0);
        dateFromPicker.setValue(null);
    }

    private void closeStage() {
        Stage stage = (Stage) btnSearch.getScene().getWindow();
        stage.close();
    }

    // Метод для отримання критеріїв у форматі SQL-Ready об'єкта
    public SearchParams getSearchParams() {
        return new SearchParams(
                titleField.getText().trim(),
                authorField.getText().trim(),
                seriesField.getText().trim(),
                genreField.getText().trim(),
                languageBox.getValue().equals("Усі") ? "" : languageBox.getValue(),
                (int) minRateSlider.getValue(),
                dateFromPicker.getValue()
        );
    }

    public record SearchParams(
            String title, String author, String series, String genre,
            String lang, int minRate, LocalDate dateFrom
    ) {}
}