package org.myhomelib.app;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.myhomelib.db.Database;
import org.myhomelib.model.Book;
import org.myhomelib.service.LibraryService;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

public final class MainFXApp extends Application {
    private Database database;
    private LibraryService libraryService;

    private TreeView<String> navigationTree;
    private TableView<Book> booksTable;
    private Label titleDetailsLabel;
    private TextArea annotationDetailsArea;
    private Label statusLabel;
    private TextField searchField;

    private final ObservableList<Book> masterBookList = FXCollections.observableArrayList();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        Path dbPath = Path.of("myhomelib-java.db");
        database = new Database(dbPath);
        libraryService = new LibraryService(database);

        primaryStage.setTitle("MyHomeLib - JavaFX Edition");

        // --- ВЕРХНЯ ЧАСТИНА: TOOLBAR ---
        ToolBar toolBar = new ToolBar();
        Button importInpxBtn = new Button("Імпорт .inpx");
        Button settingsBtn = new Button("Налаштування");
        Button statsBtn = new Button("Статистика");
        searchField = new TextField();
        searchField.setPromptText("Швидкий пошук книги...");
        searchField.setPrefWidth(250);

        toolBar.getItems().addAll(importInpxBtn, settingsBtn, statsBtn, new Separator(), searchField);

        // --- ЛІВА ЧАСТИНА: НАВІГАЦІЙНЕ ДЕРЕВО (Автори, Серії, Жанри) ---
        TreeItem<String> rootNode = new TreeItem<>("Бібліотека");
        rootNode.setExpanded(true);

        TreeItem<String> authorsNode = new TreeItem<>("Автори");
        TreeItem<String> seriesNode = new TreeItem<>("Серії");
        TreeItem<String> genresNode = new TreeItem<>("Жанри");
        rootNode.getChildren().addAll(authorsNode, seriesNode, genresNode);

        navigationTree = new TreeView<>(rootNode);
        navigationTree.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && newValue.isLeaf()) {
                handleTreeNavigation(newValue.getParent().getValue(), newValue.getValue());
            }
        });

        // --- ЦЕНТРАЛЬНА ЧАСТИНА: ТАБЛИЦЯ КНИГ ---
        booksTable = new TableView<>();
        booksTable.setItems(masterBookList);

        TableColumn<Book, String> authorCol = new TableColumn<>("Автор");
        authorCol.setCellValueFactory(new PropertyValueFactory<>("authorsText"));
        authorCol.setPrefWidth(150);

        TableColumn<Book, String> seriesCol = new TableColumn<>("Серія");
        seriesCol.setCellValueFactory(new PropertyValueFactory<>("series"));
        seriesCol.setPrefWidth(120);

        TableColumn<Book, Integer> seqCol = new TableColumn<>("№");
        seqCol.setCellValueFactory(new PropertyValueFactory<>("sequenceNumber"));
        seqCol.setPrefWidth(40);

        TableColumn<Book, String> titleCol = new TableColumn<>("Назва");
        titleCol.setCellValueFactory(new PropertyValueFactory<>("title"));
        titleCol.setPrefWidth(220);

        TableColumn<Book, Long> sizeCol = new TableColumn<>("Розмір");
        sizeCol.setCellValueFactory(new PropertyValueFactory<>("fileSize"));
        sizeCol.setPrefWidth(80);

        TableColumn<Book, String> fileCol = new TableColumn<>("Файл");
        fileCol.setCellValueFactory(new PropertyValueFactory<>("fileName"));
        fileCol.setPrefWidth(120);

        booksTable.getColumns().addAll(authorCol, seriesCol, seqCol, titleCol, sizeCol, fileCol);
        booksTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                updateDetailsPanel(newSelection);
            }
        });

        // --- НИЖНЯ/ПРАВА ЧАСТИНА: ДЕТАЛІ КНИГИ ---
        VBox detailsPanel = new VBox(10);
        detailsPanel.setPadding(new Insets(10));
        detailsPanel.setStyle("-color-base: #f4f1ea; -fx-background-color: #f4f1ea;");

        titleDetailsLabel = new Label("Назва книги не обрана");
        titleDetailsLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        annotationDetailsArea = new TextArea();
        annotationDetailsArea.setEditable(false);
        annotationDetailsArea.setWrapText(true);
        annotationDetailsArea.setPromptText("Анотація відсутня");
        VBox.setVgrow(annotationDetailsArea, Priority.ALWAYS);

        detailsPanel.getChildren().addAll(titleDetailsLabel, new Label("Анотація:"), annotationDetailsArea);

        // --- РОЗПОДІЛ ПАНЕЛЕЙ (SPLITPANES) ЯК НА МАКЕТІ ---
        SplitPane horizontalSplit = new SplitPane();
        horizontalSplit.setOrientation(Orientation.HORIZONTAL);

        SplitPane verticalCenterSplit = new SplitPane();
        verticalCenterSplit.setOrientation(Orientation.VERTICAL);
        verticalCenterSplit.getItems().addAll(booksTable, detailsPanel);
        verticalCenterSplit.setDividerPositions(0.65);

        horizontalSplit.getItems().addAll(navigationTree, verticalCenterSplit);
        horizontalSplit.setDividerPositions(0.25);

        // --- НИЖНІЙ СТАТУС БАР ---
        HBox statusBar = new HBox();
        statusBar.setPadding(new Insets(5));
        statusBar.setStyle("-fx-background-color: #e8e8e8;");
        statusLabel = new Label("Програма готова до роботи.");
        statusBar.getChildren().add(statusLabel);

        // --- ГОЛОВНИЙ ЛЕЯУТ ---
        BorderPane root = new BorderPane();
        root.setTop(toolBar);
        root.setCenter(horizontalSplit);
        root.setBottom(statusBar);

        // --- ЛОГІКА КНОПОК ТА ДІЙ ---
        importInpxBtn.setOnAction(e -> handleInpxImport(primaryStage));
        searchField.textProperty().addListener((observable, oldValue, newValue) -> handleLiveSearch(newValue));
        statsBtn.setOnAction(e -> showStatisticsDialog());

        // Первинне завантаження дерева навігації
        refreshNavigationTree();
        loadAllBooks();

        Scene scene = new Scene(root, 1050, 750);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void loadAllBooks() {
        statusLabel.setText("Завантаження книг...");
        List<Book> allBooks = database.searchBooks("");
        masterBookList.setAll(allBooks);
        statusLabel.setText("Завантажено книг: " + masterBookList.size());
    }

    private void handleLiveSearch(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            loadAllBooks();
        } else {
            List<Book> found = database.searchBooks(keyword);
            masterBookList.setAll(found);
            statusLabel.setText("Знайдено книг за запитом: " + masterBookList.size());
        }
    }

    private void handleTreeNavigation(String category, String value) {
        statusLabel.setText("Фільтрація: " + category + " -> " + value);
        // Динамічна побудова критеріїв залежно від вибору в дереві
        org.myhomelib.model.SearchCriteria criteria = switch (category) {
            case "Автори" -> new org.myhomelib.model.SearchCriteria(null, value, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
            case "Серії" -> new org.myhomelib.model.SearchCriteria(null, null, null, value, null, null, null, null, null, null, null, null, null, null, null, null);
            case "Жанри" -> new org.myhomelib.model.SearchCriteria(null, null, value, null, null, null, null, null, null, null, null, null, null, null, null, null);
            default -> org.myhomelib.model.SearchCriteria.empty();
        };
        List<Book> filtered = database.searchAdvanced(criteria);
        masterBookList.setAll(filtered);
    }

    private void handleInpxImport(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Оберіть файл колекції INPX");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Файли індексів (*.inpx)", "*.inpx"));
        File selectedFile = fileChooser.showOpenDialog(stage);

        if (selectedFile != null) {
            statusLabel.setText("Запущено фоновий імпорт з файлу: " + selectedFile.getName());
            Thread importThread = new Thread(() -> {
                try {
                    libraryService.importInpx(selectedFile.toPath(), status -> Platform.runLater(() -> statusLabel.setText(status)));
                    Platform.runLater(() -> {
                        statusLabel.setText("Імпорт успішно завершено!");
                        refreshNavigationTree();
                        loadAllBooks();
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> statusLabel.setText("Помилка імпорту: " + ex.getMessage()));
                }
            });
            importThread.setDaemon(true);
            importThread.start();
        }
    }

    private void refreshNavigationTree() {
        TreeItem<String> root = navigationTree.getRoot();

        // Оновлення авторів
        TreeItem<String> authorsNode = root.getChildren().get(0);
        authorsNode.getChildren().clear();
        for (String author : database.listAuthors()) {
            authorsNode.getChildren().add(new TreeItem<>(author));
        }

        // Оновлення серій
        TreeItem<String> seriesNode = root.getChildren().get(1);
        seriesNode.getChildren().clear();
        for (String series : database.listSeries()) {
            seriesNode.getChildren().add(new TreeItem<>(series));
        }

        // Оновлення жанрів
        TreeItem<String> genresNode = root.getChildren().get(2);
        genresNode.getChildren().clear();
        for (String genre : database.listGenres()) {
            genresNode.getChildren().add(new TreeItem<>(genre));
        }
    }

    private void updateDetailsPanel(Book book) {
        titleDetailsLabel.setText(book.title() + " (" + book.authorsText() + ")");
        if (book.annotation() != null && !book.annotation().isBlank()) {
            annotationDetailsArea.setText(book.annotation());
        } else {
            annotationDetailsArea.setText("Анотація відсутня для цієї книги.");
        }
    }

    private void showStatisticsDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Статистика колекції");
        alert.setHeaderText("Поточні показники бази даних MyHomeLib");

        StringBuilder sb = new StringBuilder();
        database.statistics().forEach((k, v) -> sb.append(k).append(": ").append(v).append("\n"));

        alert.setContentText(sb.toString());
        alert.showAndWait();
    }

    @Override
    public void stop() {
        if (database != null) {
            database.close();
        }
    }
}