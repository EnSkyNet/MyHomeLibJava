package org.myhomelib.ui.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.myhomelib.importer.BookImportTask;
import org.myhomelib.model.Book;

// ТОЧНИЙ ІМПОРТ, ЯКИЙ ВИПРАВЛЯЄ ПОМИЛКУ КОМПІЛЯЦІЇ:
import org.myhomelib.ui.viewmodel.LibraryViewModel;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class MainController {
    @FXML private TableView<Book> bookTable;
    @FXML private TableColumn<Book, String> titleCol;
    @FXML private TableColumn<Book, String> authorCol;
    @FXML private TableColumn<Book, String> seriesCol;
    @FXML private TableColumn<Book, String> langCol;

    @FXML private ListView<String> authorListView;
    @FXML private ListView<String> seriesListView;
    @FXML private TreeView<String> genreTreeView;

    @FXML private TextField titleField;
    @FXML private TextField searchAuthorField;
    @FXML private TextField searchSeriesField;
    @FXML private ComboBox<String> langFilterComboBox;

    @FXML private Label bookDetailTitleLabel;
    @FXML private Label bookDetailAuthorLabel;
    @FXML private TextArea bookAnnotationTextArea;

    @FXML private Label statusLeftLabel;
    @FXML private Label statusRightLabel;

    private LibraryViewModel viewModel;

    @FXML
    public void initialize() {
        // Ініціалізація шару ViewModel, який всередині себе створює сервіси
        this.viewModel = new LibraryViewModel();

        // Двонаправлене зв'язування даних (Data Binding) між UI та ViewModel
        titleField.textProperty().bindBidirectional(viewModel.searchTextProperty());
        searchAuthorField.textProperty().bindBidirectional(viewModel.searchAuthorProperty());
        searchSeriesField.textProperty().bindBidirectional(viewModel.searchSeriesProperty());
        langFilterComboBox.valueProperty().bindBidirectional(viewModel.selectedLanguageProperty());

        // Налаштування фабрик відображення осередків колонок таблиці книг
        titleCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().title()));
        authorCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().authorsText()));
        seriesCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().series()));
        langCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().language()));

        // Зв'язування колекцій відображення з ViewModel екрану
        bookTable.setItems(viewModel.getBooks());
        authorListView.setItems(viewModel.getAuthors());
        seriesListView.setItems(viewModel.getSeries());

        // Слухач події виділення рядка у таблиці для детального перегляду
        bookTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            viewModel.selectedBookProperty().set(newVal);
            if (newVal != null) {
                bookDetailTitleLabel.setText("Назва книги: " + newVal.title());
                bookDetailAuthorLabel.setText("Автор: " + newVal.authorsText());
                bookAnnotationTextArea.setText(newVal.annotation());
            } else {
                bookDetailTitleLabel.setText("Назва книги: Не вибрано");
                bookDetailAuthorLabel.setText("Автор: -");
                bookAnnotationTextArea.setText("");
            }
        });

        // Автоматичне динамічне оновлення лічильника кількості книг у статус-барі
        statusRightLabel.textProperty().bind(
                javafx.beans.binding.Bindings.size(viewModel.getBooks()).asString("Книг у вибірці: %d")
        );

        loadLanguages();
    }

    private void loadLanguages() {
        langFilterComboBox.getItems().clear();
        langFilterComboBox.getItems().add("Всі мови");
        // Звернення до Service Layer для отримання списку мов з бази
        langFilterComboBox.getItems().addAll(viewModel.getSearchService().getAvailableLanguages());
        langFilterComboBox.getSelectionModel().selectFirst();
    }

    @FXML
    private void handleSearch() {
        // Виконання пошуку через ViewModel (яка делегує його SearchService)
        viewModel.executeSearch();
        statusLeftLabel.setText("Пошук завершено.");
    }

    @FXML
    private void handleImportInpx() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Виберіть файл індексу (.INPX)");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Файли індексів", "*.inpx"));
        File file = chooser.showOpenDialog(bookTable.getScene().getWindow());
        if (file != null) {
            statusLeftLabel.setText("Імпорт структури з " + file.getName());
        }
    }

    @FXML
    private void handleImportZipFb2() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Виберіть файли книг для імпорту");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Книги", "*.fb2", "*.zip"));
        List<File> files = chooser.showOpenMultipleDialog(bookTable.getScene().getWindow());

        if (files != null && !files.isEmpty()) {
            statusLeftLabel.setText("Запуск фонового процесу імпорту...");

            // Створення асинхронної задачі інкапсульовано всередині ImportService
            BookImportTask task = viewModel.getImportService().createImportTask(files);

            // Прив'язуємо прогрес-повідомлення задачі до статус-бару UI
            statusLeftLabel.textProperty().bind(task.messageProperty());

            task.setOnSucceeded(e -> {
                statusLeftLabel.textProperty().unbind();
                statusLeftLabel.setText("Імпорт успішно виконано.");
                viewModel.refreshData();
                loadLanguages();
            });

            task.setOnFailed(e -> {
                statusLeftLabel.textProperty().unbind();
                statusLeftLabel.setText("Помилка під час імпорту книг.");
                if (task.getException() != null) {
                    task.getException().printStackTrace();
                }
            });

            // Запуск задачі у фоновому потоці, щоб інтерфейс не зависав
            Thread thread = new Thread(task);
            thread.setDaemon(true);
            thread.start();
        }
    }

    @FXML
    private void handleImportGenres() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Виберіть файл жанрів (.glst)");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Списки жанрів", "*.glst", "*.txt"));
        File file = chooser.showOpenDialog(bookTable.getScene().getWindow());
        if (file != null) {
            try {
                // Виклик логіки оновлення через ImportService
                viewModel.getImportService().importGenres(file.toPath(), "ru");
                statusLeftLabel.setText("Жанри успішно оновлено з файлу: " + file.getName());
            } catch (IOException e) {
                statusLeftLabel.setText("Помилка імпорту жанрів.");
                e.printStackTrace();
            }
        }
    }

    @FXML
    private void handleCleanDatabase() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Підтвердження очищення");
        alert.setHeaderText("Ви збираєтеся повністю очистити бібліотеку!");
        alert.setContentText("Це видалить усі 800 000 книг та очистить FTS5 індекси. Продовжити?");

        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                // Виклик каскадного видалення через LibraryService
                viewModel.getLibraryService().clearEntireLibrary();
                viewModel.refreshData();
                loadLanguages();
                statusLeftLabel.setText("Базу даних повністю очищено.");
            } catch (Exception e) {
                statusLeftLabel.setText("Помилка під час очищення баз даних.");
                e.printStackTrace();
            }
        }
    }

    @FXML
    private void handleShowStatistics() {
        openModalWindow("StatisticsView.fxml", "Статистика бібліотеки");
    }

    @FXML
    private void handleOpenAdvancedSearch() {
        openModalWindow("AdvancedSearchView.fxml", "Розширений повнотекстовий MATCH-пошук");
    }

    private void openModalWindow(String fxmlFile, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/org/myhomelib/ui/view/" + fxmlFile));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle(title);
            stage.initOwner(bookTable.getScene().getWindow());
            stage.initModality(javafx.stage.Modality.WINDOW_MODAL);
            stage.setScene(new Scene(root));
            stage.show();
        } catch (IOException e) {
            System.err.println("[ERROR] Не вдалося відкрити вікно: " + fxmlFile);
            e.printStackTrace();
        }
    }
}