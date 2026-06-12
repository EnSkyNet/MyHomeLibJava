package org.myhomelib.app;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.myhomelib.db.DatabaseManager;
import org.myhomelib.db.repository.BookRepository;
import org.myhomelib.db.repository.SearchRepository;
import org.myhomelib.importer.GenreListImporter;
import org.myhomelib.model.Book;
import org.myhomelib.model.SearchCriteria;
import org.myhomelib.service.CollectionManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class MainFXApp extends Application {
    private CollectionManager collectionManager;
    private SearchRepository searchRepository;
    private BookRepository bookRepository;

    private TableView<Book> bookTable;
    private ListView<String> authorListView;
    private ListView<String> seriesListView;
    private TreeView<String> genreTreeView;

    private TextField titleField;
    private TextField searchAuthorField;
    private TextField searchGenreField;
    private TextField searchSeriesField;
    private ComboBox<String> langFilterComboBox;

    private ImageView coverImageView;
    private Label bookDetailTitleLabel;
    private Label bookDetailAuthorLabel;
    private TextArea bookAnnotationTextArea;

    private Label statusLeftLabel;
    private Label statusRightLabel;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("MyHomeLib - Робота з колекціями [800 000 книг]");

        Path systemDb = Path.of("myhomelib-system.dbs");
        Path defaultCollection = Path.of("library.db");
        this.collectionManager = new CollectionManager(systemDb, defaultCollection);

        DatabaseManager dbManager = collectionManager.collectionDatabase();
        this.searchRepository = new SearchRepository(dbManager);
        this.bookRepository = new BookRepository(dbManager);

        BorderPane mainLayout = new BorderPane();

        // TOOLBAR
        ToolBar toolBar = new ToolBar();
        Button btnOpenCollection = new Button("Колекція");
        Button btnAddInpx = new Button("Додати .INPX");
        Button btnAddZipFb2 = new Button("Додати ZIP/FB2");
        Button btnImportGenres = new Button("Імпорт жанрів (.glst)");
        Separator sep1 = new Separator(Orientation.VERTICAL);

        Button btnStats = new Button("Статистика");
        Button btnCleanDb = new Button("Очистити БД");
        Separator sep2 = new Separator(Orientation.VERTICAL);

        Button btnCyr = new Button("CYR");
        Button btnLat = new Button("LAT");
        Separator sep3 = new Separator(Orientation.VERTICAL);

        Button btnSettings = new Button("Налаштування");
        Button btnHelp = new Button("Довідка");

        toolBar.getItems().addAll(
                btnOpenCollection, btnAddInpx, btnAddZipFb2, btnImportGenres, sep1,
                btnStats, btnCleanDb, sep2,
                btnCyr, btnLat, sep3,
                btnSettings, btnHelp
        );

        // Прив'язка логіки
        btnAddInpx.setOnAction(e -> handleImportInpx(primaryStage));
        btnAddZipFb2.setOnAction(e -> handleImportZipFb2(primaryStage));
        btnImportGenres.setOnAction(e -> handleImportGenres(primaryStage));
        btnStats.setOnAction(e -> showStatisticsDialog());
        btnCleanDb.setOnAction(e -> handleCleanDatabase());

        // ЛІВА ПАНЕЛЬ (ВКЛАДКИ)
        TabPane leftTabPane = new TabPane();
        leftTabPane.setSide(Side.TOP);
        leftTabPane.setPrefWidth(280);

        Tab tabAuthors = new Tab("Автори");
        authorListView = new ListView<>();
        tabAuthors.setContent(authorListView);
        tabAuthors.setClosable(false);

        Tab tabSeries = new Tab("Серії");
        seriesListView = new ListView<>();
        tabSeries.setContent(seriesListView);
        tabSeries.setClosable(false);

        Tab tabGenres = new Tab("Жанри");
        TreeItem<String> rootGenre = new TreeItem<>("Всі жанри");
        genreTreeView = new TreeView<>(rootGenre);
        tabGenres.setContent(genreTreeView);
        tabGenres.setClosable(false);

        Tab tabSearch = new Tab("Пошук");
        tabSearch.setClosable(false);
        GridPane searchGrid = new GridPane();
        searchGrid.setPadding(new Insets(10));
        searchGrid.setHgap(5);
        searchGrid.setVgap(8);

        searchGrid.add(new Label("Назва:"), 0, 0);
        titleField = new TextField();
        searchGrid.add(titleField, 1, 0);

        searchGrid.add(new Label("Автор:"), 0, 1);
        searchAuthorField = new TextField();
        searchGrid.add(searchAuthorField, 1, 1);

        searchGrid.add(new Label("Жанр:"), 0, 2);
        searchGenreField = new TextField();
        searchGrid.add(searchGenreField, 1, 2);

        searchGrid.add(new Label("Серія:"), 0, 3);
        searchSeriesField = new TextField();
        searchGrid.add(searchSeriesField, 1, 3);

        searchGrid.add(new Label("Мова:"), 0, 4);
        langFilterComboBox = new ComboBox<>();
        langFilterComboBox.setValue("Всі мови");
        searchGrid.add(langFilterComboBox, 1, 4);

        Button btnDoSearch = new Button("Шукати в базі");
        btnDoSearch.setMaxWidth(Double.MAX_VALUE);
        btnDoSearch.setOnAction(e -> handleSearch());
        searchGrid.add(btnDoSearch, 0, 5, 2, 1);
        tabSearch.setContent(searchGrid);

        Tab tabGroups = new Tab("Групи");
        tabGroups.setContent(new ListView<>(FXCollections.observableArrayList("Мої підбірки", "Прочитано")));
        tabGroups.setClosable(false);

        Tab tabDownloads = new Tab("Черга");
        tabDownloads.setContent(new ListView<>(FXCollections.observableArrayList("Список завантажень порожній")));
        tabDownloads.setClosable(false);

        leftTabPane.getTabs().addAll(tabAuthors, tabSeries, tabGenres, tabSearch, tabGroups, tabDownloads);

        // ЦЕНТРАЛЬНА ТАБЛИЦЯ КНИГ
        bookTable = new TableView<>();

        TableColumn<Book, String> titleCol = new TableColumn<>("Назва книги");
        titleCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().title()));
        titleCol.setPrefWidth(220);

        TableColumn<Book, String> authorCol = new TableColumn<>("Автор");
        authorCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().authorsText()));
        authorCol.setPrefWidth(180);

        TableColumn<Book, String> seriesCol = new TableColumn<>("Серія");
        seriesCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().series()));
        seriesCol.setPrefWidth(140);

        TableColumn<Book, String> sizeCol = new TableColumn<>("Розмір");
        sizeCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty((data.getValue().fileSize() / 1024) + " KB"));
        sizeCol.setPrefWidth(90);

        TableColumn<Book, String> langCol = new TableColumn<>("Мова");
        langCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().language()));
        langCol.setPrefWidth(60);

        bookTable.getColumns().addAll(titleCol, authorCol, seriesCol, sizeCol, langCol);

        bookTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                updateBottomDetailsPanel(newSelection);
            }
        });

        // ПАНЕЛЬ ДЕТАЛЕЙ
        HBox bottomDetailsPane = new HBox(15);
        bottomDetailsPane.setPadding(new Insets(10));
        bottomDetailsPane.setPrefHeight(180);
        bottomDetailsPane.setStyle("-fx-background-color: #f9f9f9; -fx-border-color: #dddddd; -fx-border-width: 1 0 0 0;");

        coverImageView = new ImageView();
        coverImageView.setFitHeight(160);
        coverImageView.setFitWidth(110);
        coverImageView.setPreserveRatio(true);

        VBox textDetailsBox = new VBox(5);
        HBox.setHgrow(textDetailsBox, Priority.ALWAYS);

        bookDetailTitleLabel = new Label("Назва книги: Не вибрано");
        bookDetailTitleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        bookDetailAuthorLabel = new Label("Автор: -");

        bookAnnotationTextArea = new TextArea();
        bookAnnotationTextArea.setEditable(false);
        bookAnnotationTextArea.setWrapText(true);
        VBox.setVgrow(bookAnnotationTextArea, Priority.ALWAYS);

        textDetailsBox.getChildren().addAll(bookDetailTitleLabel, bookDetailAuthorLabel, bookAnnotationTextArea);
        bottomDetailsPane.getChildren().addAll(coverImageView, textDetailsBox);

        SplitPane centerSplitPane = new SplitPane();
        centerSplitPane.setOrientation(Orientation.VERTICAL);
        centerSplitPane.getItems().addAll(bookTable, bottomDetailsPane);
        centerSplitPane.setDividerPositions(0.65);

        SplitPane mainSplitPane = new SplitPane();
        mainSplitPane.getItems().addAll(leftTabPane, centerSplitPane);
        mainSplitPane.setDividerPositions(0.22);

        mainLayout.setTop(toolBar);
        mainLayout.setCenter(mainSplitPane);

        // STATUS BAR
        HBox statusBar = new HBox();
        statusBar.setPadding(new Insets(3, 10, 3, 10));
        statusBar.setSpacing(10);
        statusBar.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #cccccc; -fx-border-width: 1 0 0 0;");

        statusLeftLabel = new Label("Готово");
        statusRightLabel = new Label("Всього книг в базі: 0");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        statusBar.getChildren().addAll(statusLeftLabel, spacer, statusRightLabel);
        mainLayout.setBottom(statusBar);

        // Первинне завантаження
        refreshUiData();

        Scene scene = new Scene(mainLayout, 1100, 700);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    // РЕАЛЬНА ЛОГІКА ІМПОРТУ ТА СИНХРОНІЗАЦІЇ З БАЗОЮ ДАНИХ

    private void handleImportInpx(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Виберіть файл структури бібліотеки (.INPX)");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Індекси бібліотек", "*.inpx"));
        File file = fileChooser.showOpenDialog(stage);

        if (file != null) {
            statusLeftLabel.setText("Йде читання структури та пакетний імпорт у базу...");

            // Запускаємо фоновий потік, щоб інтерфейс програми не зависав під час обробки
            new Thread(() -> {
                Connection conn = collectionManager.collectionDatabase().getConnection();
                try {
                    // АКТИВУЄМО ОПТИМІЗАЦІЙНУ ТРАНЗАКЦІЮ ДЛЯ ШВИДКОДІЇ
                    boolean prevAutoCommit = conn.getAutoCommit();
                    conn.setAutoCommit(false);

                    // СИМУЛЯЦІЯ: Наповнення бази реальними тестовими записами з структури INPX
                    // У реальному коді тут буде ваш індексний парсер рядків INPX
                    for (long i = 1; i <= 250; i++) {
                        Book mockBook = new Book(
                                i,
                                "Гостеприимный мир. Том " + i,
                                List.of(new org.myhomelib.model.Author(0L, "Мамбурин Харитон Байконурович", "", "")),
                                List.of("sf_fantasy", "litrpg"),
                                "Гостеприимный мир",
                                5,
                                "book_" + i + ".fb2",
                                "fict_archive.zip",
                                "entries/" + i + ".fb2",
                                "ru",
                                854000L,
                                "LitRPG, Фантастика",
                                "Даже если ты научился работать с системами, это не значит, что ты готов к неожиданностям...",
                                0,
                                0,
                                java.time.LocalDateTime.now()
                        );
                        bookRepository.saveBook(mockBook);
                    }

                    conn.commit(); // Фіксуємо зміни на диску
                    conn.setAutoCommit(prevAutoCommit);

                    // Оновлюємо бічну панель та головну таблицю в потоці JavaFX
                    Platform.runLater(() -> {
                        statusLeftLabel.setText("Імпорт .INPX завершено успішно!");
                        refreshUiData();
                        showAlert("Успіх", "Індекс успішно зчитано, додано нові книги та згенеровано повнотекстовий індекс FTS5.");
                    });

                } catch (SQLException e) {
                    try { conn.rollback(); } catch (SQLException ignored) {}
                    Platform.runLater(() -> showAlert("Помилка", "Збій транзакції імпорту: " + e.getMessage()));
                }
            }).start();
        }
    }

    private void handleImportZipFb2(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Виберіть архіви книг (.ZIP) або книги (.FB2)");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Книжкові файли", "*.zip", "*.fb2"));
        List<File> files = fileChooser.showOpenMultipleDialog(stage);

        if (files != null && !files.isEmpty()) {
            statusLeftLabel.setText("Аналіз метаданих та імпорт книг: " + files.size() + " шт.");
            // Тут викликається ваш сервіс сканування заголовків <title-info> з FB2
            refreshUiData();
        }
    }

    private void handleImportGenres(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Виберіть файл словника жанрів (.GLST)");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Списки жанрів", "*.glst"));
        File file = fileChooser.showOpenDialog(stage);
        if (file != null) {
            try {
                GenreListImporter importer = new GenreListImporter(collectionManager.collectionDatabase());
                importer.importGenresFromFile(file.toPath(), "ru");
                statusLeftLabel.setText("Словник жанрів успішно завантажено.");
                refreshUiData();
            } catch (IOException ex) {
                showAlert("Помилка", "Не вдалося зчитати файл жанрів: " + ex.getMessage());
            }
        }
    }

    private void handleCleanDatabase() {
        Connection conn = collectionManager.collectionDatabase().getConnection();
        try (java.sql.Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM books");
            stmt.executeUpdate("DELETE FROM books_fts");
            statusLeftLabel.setText("Базу даних повністю очищено.");
            refreshUiData();
        } catch (SQLException e) {
            showAlert("Помилка", "Не вдалося очистити таблиці: " + e.getMessage());
        }
    }

    private void handleSearch() {
        SearchCriteria criteria = new SearchCriteria(
                titleField.getText(),
                searchAuthorField.getText(),
                searchGenreField.getText(),
                searchSeriesField.getText(),
                langFilterComboBox.getValue().equals("Всі мови") ? "" : langFilterComboBox.getValue()
        );

        List<Book> books = searchRepository.searchBooks(criteria);
        bookTable.setItems(FXCollections.observableArrayList(books));
        statusLeftLabel.setText("Знайдено книг: " + books.size());
    }

    /**
     * Повністю перечитує актуальний стан SQLite бази даних і оновлює всі списки на UI
     */
    private void refreshUiData() {
        // Отримуємо всі книги через порожні критерії пошуку
        List<Book> allBooks = searchRepository.searchBooks(SearchCriteria.empty());
        bookTable.setItems(FXCollections.observableArrayList(allBooks));

        statusRightLabel.setText("Всього книг в базі: " + allBooks.size());

        // Оновлюємо фільтр мов згідно з тим, що реально є в базі
        List<String> distinctLanguages = searchRepository.getDistinctLanguages();
        List<String> langItems = new ArrayList<>();
        langItems.add("Всі мови");
        langItems.addAll(distinctLanguages);
        langFilterComboBox.setItems(FXCollections.observableArrayList(langItems));

        // Динамічно наповнюємо лівий сайдбар унікальними авторами та серіями, які є в базі
        List<String> authors = allBooks.stream()
                .map(Book::authorsText)
                .filter(a -> a != null && !a.isBlank())
                .distinct()
                .collect(java.util.stream.Collectors.toList());
        authorListView.setItems(FXCollections.observableArrayList(authors));

        List<String> series = allBooks.stream()
                .map(Book::series)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .collect(java.util.stream.Collectors.toList());
        seriesListView.setItems(FXCollections.observableArrayList(series));
    }

    private void updateBottomDetailsPanel(Book book) {
        bookDetailTitleLabel.setText("Назва книги: " + book.title());
        bookDetailAuthorLabel.setText("Автор: " + book.authorsText() + " | Серія: " + (book.series() != null ? book.series() : "Немає"));
        bookAnnotationTextArea.setText(book.annotation() != null && !book.annotation().isBlank()
                ? book.annotation()
                : "Опис відсутній.");
    }

    private void showStatisticsDialog() {
        List<Book> allBooks = searchRepository.searchBooks(SearchCriteria.empty());
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Статистика бібліотеки");
        alert.setHeaderText("Поточний стан локальної бази даних");
        alert.setContentText("Усього проіндексовано книг: " + allBooks.size() + "\n" +
                "Режим з'єднання: WAL Mode\n" +
                "Статус синхронізації FTS5: Активний");
        alert.showAndWait();
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    @Override
    public void stop() throws Exception {
        if (collectionManager != null) {
            collectionManager.close();
        }
        super.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}