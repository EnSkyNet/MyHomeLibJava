package org.myhomelib.ui.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.stage.Stage;

public class SearchPresetsController {

    @FXML private ListView<String> presetsListView;
    @FXML private Button btnApply;
    @FXML private Button btnDelete;
    @FXML private Button btnCancel;

    private String selectedPreset = null;
    private boolean applyClicked = false;
    private ObservableList<String> presetsList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        presetsListView.setItems(presetsList);

        // Слухач вибору елемента в списку для активації кнопок дій
        presetsListView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            boolean hasSelection = (newValue != null);
            btnApply.setDisable(!hasSelection);
            btnDelete.setDisable(!hasSelection);
            if (hasSelection) {
                selectedPreset = newValue;
            }
        });
    }

    /**
     * Завантаження списку назв збережених пресетів з конфігурації користувача.
     */
    public void loadPresets(java.util.List<String> userPresets) {
        if (userPresets != null) {
            presetsList.setAll(userPresets);
        }
    }

    public String getSelectedPreset() {
        return selectedPreset;
    }

    public boolean isApplyClicked() {
        return applyClicked;
    }

    @FXML
    private void handleApplyPreset() {
        if (selectedPreset != null) {
            applyClicked = true;
            closeStage();
        }
    }

    @FXML
    private void handleDeletePreset() {
        if (selectedPreset != null) {
            presetsList.remove(selectedPreset);
            System.out.println("[INFO] Видалено пресет пошуку: " + selectedPreset);
            // Тут буде інтегровано виклик до конфіг-сервісу для збереження змін на диску
        }
    }

    @FXML
    private void handleCancel() {
        applyClicked = false;
        closeStage();
    }

    private void closeStage() {
        Stage stage = (Stage) btnCancel.getScene().getWindow();
        stage.close();
    }
}