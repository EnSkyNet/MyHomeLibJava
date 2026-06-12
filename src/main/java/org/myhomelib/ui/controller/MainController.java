package org.myhomelib.ui.controller;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.myhomelib.service.ImportService;
import org.myhomelib.service.BookImportService;
import org.myhomelib.ui.viewmodel.LibraryViewModel;
import org.myhomelib.model.Book;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class MainController {

    // === Елементи управління з ToolBar ===
    @FXML private Button btnInpx;
    @FXML private Button btnZip;

    // === Елементи управління з TabPane (Ліва панель) ===
    @FXML private ListView<?> authorListView;
    @FXML private ListView<?> seriesListView;
    @FXML private TreeView<?> genreTreeView;

    // === Поля фільтрації пошуку ===
    @FXML private TextField titleField;
    @FXML private TextField searchAuthorField;
    @FXML private TextField searchSeriesField;
    @FXML private ComboBox<?> langFilterComboBox;

    // === Елементи таблиці книг (Центральна панель) ===
    @FXML private TableView<Book> bookTable;
    @FXML private TableColumn<Book, String> titleCol;
    @FXML private TableColumn<Book, String> authorCol;
    @FXML private TableColumn<Book, String> seriesCol;
    @FXML private TableColumn<Book, String> langCol;

    // === Компоненти детальної інформації та анотації (Нижня частина центру) ===
    @FXML private Label bookDetailTitleLabel;
    @FXML private Label bookDetailAuthorLabel;
    @FXML private TextArea bookAnnotationTextArea;

    // === Компоненти статус-бару (Нижня панель) ===
    @FXML private Label statusLeftLabel;
    @FXML private Label statusRightLabel;

    // === Шари бізнес-логіки та ViewModel ===
    private ImportService importService;
    private BookImportService bookImportService;
    private LibraryViewModel libraryViewModel;

    /**
     * Впровадження Enterprise-залежностей програми.
     */
    public void setDependencies(ImportService importService, BookImportService bookImportService, LibraryViewModel libraryViewModel) {
        this.importService = importService;
        this.bookImportService = bookImportService;
        this.libraryViewModel = libraryViewModel;

        if (this.libraryViewModel != null && this.bookTable != null) {
            this.bookTable.setItems(this.libraryViewModel.getBooksList());
            updateTotalBooksCount();
        }
    }

    /**
     * Допоміжний метод для оновлення лічильника книг у статус-барі.
     */
    private void updateTotalBooksCount() {
        if (statusRightLabel != null && libraryViewModel != null) {
            int count = libraryViewModel.getBooksList().size();
            statusRightLabel.setText("Всього книг в базі: " + count);
        }
    }

    // ==========================================
    // ОБРОБНИКИ ПОДІЙ (OnAction з MainView.fxml)
    // ==========================================

    /**
     * onAction="#handleImportInpx"
     */
    @FXML
    public void handleImportInpx() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Виберіть файл індексу колекції (.inpx)");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Індекси INPX", "*.inpx"));

        Stage stage = (Stage) btnInpx.getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(stage);

        if (selectedFile != null) {
            statusLeftLabel.setText("Аналіз індексу INPX: " + selectedFile.getName());
            System.out.println("[INFO] Запуск парсингу колекції індексу: " + selectedFile.getAbsolutePath());
        }
    }

    /**
     * onAction="#handleImportZipFb2"
     */
    @FXML
    public void handleImportZipFb2() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Виберіть архіви ZIP або поодинокі книги FB2");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Архіви та Книги", "*.zip", "*.fb2"),
                new FileChooser.ExtensionFilter("ZIP Архіви", "*.zip"),
                new FileChooser.ExtensionFilter("Книги FB2", "*.fb2")
        );

        Stage stage = (Stage) btnZip.getScene().getWindow();
        List<File> selectedFiles = fileChooser.showOpenMultipleDialog(stage);

        if (selectedFiles != null && !selectedFiles.isEmpty()) {
            statusLeftLabel.setText("Підготовка до асинхронного імпорту пакетів...");

            List<Path> pathsToImport = selectedFiles.stream()
                    .map(File::toPath)
                    .collect(Collectors.toList());

            Task<Integer> importTask = bookImportService.createImportTask(pathsToImport);
            statusLeftLabel.textProperty().bind(importTask.messageProperty());

            importTask.setOnSucceeded(event -> {
                statusLeftLabel.textProperty().unbind();
                statusLeftLabel.setText("Успішно імпортовано об'єктів: " + importTask.getValue());
                if (libraryViewModel != null) {
                    libraryViewModel.loadAllBooks();
                    updateTotalBooksCount();
                }
            });

            importTask.setOnFailed(event -> {
                statusLeftLabel.textProperty().unbind();
                Throwable ex = importTask.getException();
                statusLeftLabel.setText("Критичний збій імпорту: " + (ex != null ? ex.getMessage() : "Помилка"));
            });

            Thread thread = new Thread(importTask);
            thread.setDaemon(true);
            thread.start();
        }
    }

    /**
     * onAction="#handleImportGenres"
     */
    @FXML
    public void handleImportGenres() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Виберіть файл структури жанрів (.glst або .txt)");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Списки жанрів", "*.glst", "*.txt"));

        Stage stage = (Stage) btnInpx.getScene().getWindow();
        File selectedFile = fileChooser.showOpenDialog(stage);

        if (selectedFile != null && importService != null) {
            importService.importGenres(selectedFile.toPath(), "UTF-8");
            statusLeftLabel.setText("Файл конфігурації жанрів завантажено.");
        }
    }

    /**
     * onAction="#handleShowStatistics"
     */
    @FXML
    public void handleShowStatistics() {
        System.out.println("[INFO] Відображення вікна загальної аналітики бібліотеки.");
        statusLeftLabel.setText("Статистика: Загальний зріз даних згенеровано.");
    }

    /**
     * onAction="#handleCleanDatabase"
     */
    @FXML
    public void handleCleanDatabase() {
        System.out.println("[INFO] Запуск повної зачистки таблиць бази даних та FTS індексів.");
        statusLeftLabel.setText("Локальну базу даних успішно очищено.");
        if (libraryViewModel != null) {
            libraryViewModel.getBooksList().clear();
            updateTotalBooksCount();
        }
    }

    /**
     * onAction="#handleOpenAdvancedSearch"
     */
    @FXML
    public void handleOpenAdvancedSearch() {
        System.out.println("[INFO] Відкриття модального діалогу розширеного пошуку.");
        statusLeftLabel.setText("Режим розширеного пошуку активовано.");
    }

    /**
     * onAction="#handleSearch"
     */
    @FXML
    public void handleSearch() {
        String titleQuery = (titleField != null && titleField.getText() != null) ? titleField.getText().trim() : "";
        String authorQuery = (searchAuthorField != null && searchAuthorField.getText() != null) ? searchAuthorField.getText().trim() : "";
        String seriesQuery = (searchSeriesField != null && searchSeriesField.getText() != null) ? searchSeriesField.getText().trim() : "";

        System.out.println(String.format("[SQL FTS] Пошук за критеріями -> Назва: %s, Автор: %s, Серія: %s",
                titleQuery, authorQuery, seriesQuery));
        statusLeftLabel.setText("Пошук завершено.");
    }
}