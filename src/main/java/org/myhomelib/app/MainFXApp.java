package org.myhomelib.app;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.myhomelib.db.BookCollection;
import org.myhomelib.db.Database;
import org.myhomelib.model.Book;
import org.myhomelib.model.SearchCriteria;
import org.myhomelib.service.LibraryService;
import org.myhomelib.ui.ReaderStage;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;

public final class MainFXApp extends Application {
    private BookCollection database;
    private LibraryService libraryService;

    private TableView<Book> bookTable;
    private TextField titleField;
    private TextField authorField;
    private TextField genreField;
    private TextField seriesField;

    private Label totalBooksLabel;
    private Label totalAuthorsLabel;
    private Label totalGenresLabel;
    private TextArea logArea;

    @Override
    public void start(Stage primaryStage) {
        // Явне створення конкретної реалізації з відкриттям підключення до файлу SQLite
        Database db = new Database(Paths.get("library.db"));
        db.open(Paths.get("library.db"));

        // Зберігаємо посилання через інтерфейс BookCollection
        this.database = db;
        this.libraryService = new LibraryService(this.database);

        primaryStage.setTitle("MyHomeLibJava — Сучасна Бібліотека JavaFX");

        // 1. ВЕРХНЯ ПАНЕЛЬ: ФІЛЬТРАЦІЯ / ПОШУК
        GridPane searchGrid = new GridPane();
        searchGrid.setHgap(10);
        searchGrid.setVgap(8);
        searchGrid.setPadding(new Insets(12));
        searchGrid.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #e9ecef; -fx-border-width: 0 0 1 0;");

        searchGrid.add(new Label("Назва книги:"), 0, 0);
        titleField = new TextField();
        titleField.setPromptText("Введіть назву...");
        searchGrid.add(titleField, 1, 0);

        searchGrid.add(new Label("Автор:"), 2, 0);
        authorField = new TextField();
        authorField.setPromptText("Ім'я автора...");
        searchGrid.add(authorField, 3, 0);

        searchGrid.add(new Label("Жанр:"), 0, 1);
        genreField = new TextField();
        genreField.setPromptText("Код або назва жанру...");
        searchGrid.add(genreField, 1, 1);

        searchGrid.add(new Label("Серія:"), 2, 1);
        seriesField = new TextField();
        seriesField.setPromptText("Назва серії...");
        searchGrid.add(seriesField, 3, 1);

        Button btnSearch = new Button("Шукати в базі");
        btnSearch.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white; -fx-font-weight: bold;");
        btnSearch.setOnAction(e -> handleSearch());
        searchGrid.add(btnSearch, 4, 0);

        Button btnReset = new Button("Скинути фільтр");
        btnReset.setOnAction(e -> {
            titleField.clear(); authorField.clear(); genreField.clear(); seriesField.clear();
            handleSearch();
        });
        searchGrid.add(btnReset, 4, 1);

        // 2. ЦЕНТРАЛЬНА ЧАСТИНА: ТАБЛИЦЯ КНИГ
        bookTable = new TableView<>();
        bookTable.setPlaceholder(new Label("Немає даних для відображення. Змініть критерії пошуку або виконайте імпорт."));

        TableColumn<Book, Long> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        idCol.setPrefWidth(70);

        TableColumn<Book, String> titleCol = new TableColumn<>("Назва");
        titleCol.setCellValueFactory(new PropertyValueFactory<>("title"));
        titleCol.setPrefWidth(300);

        TableColumn<Book, String> authorCol = new TableColumn<>("Автори");
        authorCol.setCellValueFactory(new PropertyValueFactory<>("authorsText"));
        authorCol.setPrefWidth(220);

        TableColumn<Book, String> genreCol = new TableColumn<>("Жанри");
        genreCol.setCellValueFactory(new PropertyValueFactory<>("genresText"));
        genreCol.setPrefWidth(180);

        TableColumn<Book, String> langCol = new TableColumn<>("Мова");
        langCol.setCellValueFactory(new PropertyValueFactory<>("language"));
        langCol.setPrefWidth(60);

        bookTable.getColumns().addAll(idCol, titleCol, authorCol, genreCol, langCol);

        // Подвійний клік на рядок таблиці відкриває книгу в читалці
        bookTable.setRowFactory(tv -> {
            TableRow<Book> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    openBookInReader(row.getItem());
                }
            });
            return row;
        });

        // 3. ЛІВА БІКОВА ПАНЕЛЬ: АНАЛІТИКА ТА ДІЇ
        VBox leftPanel = new VBox(15);
        leftPanel.setPadding(new Insets(12));
        leftPanel.setPrefWidth(260);
        leftPanel.setStyle("-fx-background-color: #f1f2f6; -fx-border-color: #dcdde1; -fx-border-width: 0 1 0 0;");

        // Панель лічильників
        VBox statsBox = new VBox(8);
        statsBox.setStyle("-fx-background-color: white; -fx-border-color: #ced4da; -fx-padding: 10; -fx-border-radius: 4;");
        Label statsTitle = new Label("Статистика бібліотеки:");
        statsTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        totalBooksLabel = new Label("Книг: 0");
        totalAuthorsLabel = new Label("Авторів: 0");
        totalGenresLabel = new Label("Жанрів: 0");
        statsBox.getChildren().addAll(statsTitle, totalBooksLabel, totalAuthorsLabel, totalGenresLabel);

        // Панель журналу
        VBox logBox = new VBox(5);
        Label logTitle = new Label("Журнал операцій імпорту:");
        logTitle.setStyle("-fx-font-weight: bold;");
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(250);
        logArea.setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: 11px;");
        logBox.getChildren().addAll(logTitle, logArea);

        // Кнопки імпорту
        Button btnImportFolder = new Button("Імпорт папки з FB2/ZIP");
        btnImportFolder.setMaxWidth(Double.MAX_VALUE);
        btnImportFolder.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-font-weight: bold;");
        btnImportFolder.setOnAction(e -> handleFolderImport(primaryStage));

        Button btnImportInpx = new Button("Імпорт метаданих INPX");
        btnImportInpx.setMaxWidth(Double.MAX_VALUE);
        btnImportInpx.setStyle("-fx-background-color: #e67e22; -fx-text-fill: white; -fx-font-weight: bold;");
        btnImportInpx.setOnAction(e -> handleInpxImport(primaryStage));

        leftPanel.getChildren().addAll(statsBox, logBox, new Separator(), btnImportFolder, btnImportInpx);

        // 4. НИЖНЯ ПАНЕЛЬ: КЕРУВАННЯ ВИДІЛЕНУЮ КНИГОЮ
        HBox bottomActions = new HBox(15);
        bottomActions.setPadding(new Insets(10));
        bottomActions.setAlignment(Pos.CENTER_RIGHT);
        bottomActions.setStyle("-fx-background-color: #e9ecef;");

        Button btnRead = new Button("Читати виділену книгу");
        btnRead.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px;");
        btnRead.setOnAction(e -> {
            Book selected = bookTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                openBookInReader(selected);
            } else {
                showWarning("Попередження", "Будь ласка, виберіть книгу зі списку для читання.");
            }
        });
        bottomActions.getChildren().add(btnRead);

        // Головна збірка структури BorderPane
        BorderPane root = new BorderPane();
        root.setTop(searchGrid);
        root.setCenter(bookTable);
        root.setLeft(leftPanel);
        root.setBottom(bottomActions);

        Scene scene = new Scene(root, 1200, 800);
        primaryStage.setScene(scene);
        primaryStage.show();

        // Первинне завантаження лічильників та записів
        refreshMetadataCounters();
        handleSearch();
    }

    private void handleSearch() {
        SearchCriteria criteria = new SearchCriteria(
                titleField.getText(),
                authorField.getText(),
                genreField.getText(),
                seriesField.getText(),
                "", null, null, null, null, null, null, "", "", "", "", ""
        );
        List<Book> books = database.findBooks(criteria);
        bookTable.setItems(FXCollections.observableArrayList(books));
    }

    private void refreshMetadataCounters() {
        totalBooksLabel.setText("Книг у базі: " + database.getBooksCount());
        totalAuthorsLabel.setText("Авторів у базі: " + database.getAuthorsCount());
        totalGenresLabel.setText("Жанрів у базі: " + database.getGenresCount());
    }

    private void handleFolderImport(Stage stage) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Виберіть папку для пакетного імпорту книг");
        File selectedFolder = chooser.showDialog(stage);
        if (selectedFolder != null) {
            logArea.clear();
            logArea.appendText("Запуск сканування папки... Будь ласка, зачекайте.\n");

            new Thread(() -> {
                libraryService.importFolder(selectedFolder.toPath(), log -> Platform.runLater(() -> logArea.appendText(log + "\n")), true, true);
                Platform.runLater(() -> {
                    refreshMetadataCounters();
                    handleSearch();
                });
            }).start();
        }
    }

    private void handleInpxImport(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Виберіть файл колекції індексів INPX");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Файли метаданих INPX (*.inpx)", "*.inpx"));
        File selectedFile = chooser.showOpenDialog(stage);
        if (selectedFile != null) {
            logArea.clear();
            logArea.appendText("Запуск декомпресії та транзакційного парсингу індексів INPX...\n");

            new Thread(() -> {
                libraryService.importInpx(selectedFile.toPath(), log -> Platform.runLater(() -> logArea.appendText(log + "\n")));
                Platform.runLater(() -> {
                    refreshMetadataCounters();
                    handleSearch();
                });
            }).start();
        }
    }

    private void openBookInReader(Book book) {
        ReaderStage readerStage = new ReaderStage(book, libraryService);
        readerStage.show();
    }

    private void showWarning(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}