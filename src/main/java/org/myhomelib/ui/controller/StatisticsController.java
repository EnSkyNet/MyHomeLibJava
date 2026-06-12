package org.myhomelib.ui.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.stage.Stage;

import java.util.Map;

public class StatisticsController {

    @FXML private PieChart languagePieChart;
    @FXML private BarChart<String, Number> authorsBarChart;
    @FXML private Button btnClose;

    /**
     * Завантаження реальних аналітичних даних у графіки інтерфейсу.
     * Використовує оптимізовані структури Map з Java 21.
     */
    public void setStatisticsData(Map<String, Integer> languageData, Map<String, Integer> authorData) {
        // 1. Наповнення кругової діаграми мов
        if (languagePieChart != null && languageData != null) {
            ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList();
            languageData.forEach((lang, count) -> pieChartData.add(new PieChart.Data(lang + " (" + count + ")", count)));
            languagePieChart.setData(pieChartData);
        }

        // 2. Наповнення стовпчикової діаграми авторів
        if (authorsBarChart != null && authorData != null) {
            authorsBarChart.getData().clear();
            XYChart.Series<String, Number> series = new XYChart.Series<>();

            authorData.forEach((author, count) -> {
                series.getData().add(new XYChart.Data<>(author, count));
            });

            authorsBarChart.getData().add(series);
        }
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) btnClose.getScene().getWindow();
        stage.close();
    }
}