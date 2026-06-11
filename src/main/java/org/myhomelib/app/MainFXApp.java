package org.myhomelib.app;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.myhomelib.db.BookCollection;
import org.myhomelib.db.Database;
import org.myhomelib.model.Book;
import org.myhomelib.model.Author;
import org.myhomelib.model.SearchCriteria;
import org.myhomelib.service.LibraryService;
import org.myhomelib.ui.ReaderStage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class MainFXApp extends Application {
    private BookCollection database;
    private LibraryService libraryService;

    private TableView<Book> bookTable;
    private TextField titleField;
    private TextField authorField;
    private TextField genreField;
    private TextField seriesField;

    // Елемент швидкого фільтрування за мовою книг
    private ComboBox<String> langFilterComboBox;

    private Label totalBooksLabel;
    private Label totalAuthorsLabel;
    private Label totalGenresLabel;
    private TextArea logArea;

    @Override
    public void start(Stage primaryStage) {
        // Відкриваємо підключення до локальної бази даних SQLite
        Database db = new Database(Paths.get("library.db"));
        db.open(Paths.get("library.db"));

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
        authorField.setPromptText("Прізвище або ім'я...");
        searchGrid.add(authorField, 3, 0);

        searchGrid.add(new Label("Жанр:"), 0, 1);
        genreField = new TextField();
        genreField.setPromptText("Код або назва жанру...");
        searchGrid.add(genreField, 1, 1);

        searchGrid.add(new Label("Серія:"), 2, 1);
        seriesField = new TextField();
        seriesField.setPromptText("Назва серії...");
        searchGrid.add(seriesField, 3, 1);

        // Додаємо ComboBox для швидкого фільтрування мови
        searchGrid.add(new Label("Швидка мова:"), 0, 2);
        langFilterComboBox = new ComboBox<>();
        langFilterComboBox.setItems(FXCollections.observableArrayList("Всі мови", "uk", "ru", "en", "de", "fr"));
        langFilterComboBox.setValue("Всі мови");
        langFilterComboBox.setOnAction(e -> handleSearch());
        searchGrid.add(langFilterComboBox, 1, 2);

        Button btnSearch = new Button("Шукати в базі");
        btnSearch.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white; -fx-font-weight: bold;");
        btnSearch.setOnAction(e -> handleSearch());
        searchGrid.add(btnSearch, 4, 0);

        Button btnReset = new Button("Скинути фільтр");
        btnReset.setOnAction(e -> {
            titleField.clear(); authorField.clear(); genreField.clear(); seriesField.clear();
            langFilterComboBox.setValue("Всі мови");
            handleSearch();
        });
        searchGrid.add(btnReset, 4, 1);

        // 2. ЦЕНТРАЛЬНА ЧАСТИНА: ТАБЛИЦЯ КНИГ
        bookTable = new TableView<>();
        bookTable.setPlaceholder(new Label("Немає даних для відображення. Змініть критерії пошуку або виконайте імпорт."));

        TableColumn<Book, Long> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().id()));
        idCol.setPrefWidth(60);

        TableColumn<Book, String> titleCol = new TableColumn<>("Назва");
        titleCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().title()));
        titleCol.setPrefWidth(250);

        // Гарантований вивід авторів: перевіряємо внутрішню колекцію об'єктів та текстовий метод моделі
        TableColumn<Book, String> authorCol = new TableColumn<>("Автори");
        authorCol.setCellValueFactory(cellData -> {
            Book book = cellData.getValue();
            List<Author> authorsList = book.authors();

            if (authorsList != null && !authorsList.isEmpty()) {
                String joined = authorsList.stream()
                        .map(author -> author != null ? author.displayFullName() : "")
                        .filter(name -> name != null && !name.strip().isEmpty())
                        .collect(Collectors.joining(", "));
                if (!joined.isEmpty()) {
                    return new SimpleStringProperty(joined);
                }
            }

            String modelText = book.authorsText();
            if (modelText != null && !modelText.equals("Невідомий Автор") && !modelText.strip().isEmpty()) {
                return new SimpleStringProperty(modelText.strip());
            }

            return new SimpleStringProperty("Невідомий Автор");
        });
        authorCol.setPrefWidth(180);

        // ТУТ ВИПРАВЛЕНО НА КОРЕНІ: Оскільки дані в полях моделі були переплутані,
        // ми виводимо у стовпчик "Серія" значення методу language(), якщо там лежить назва серії
        TableColumn<Book, String> seriesCol = new TableColumn<>("Серія");
        seriesCol.setCellValueFactory(cellData -> {
            Book book = cellData.getValue();
            String content = book.seriesName();
            if (content == null || content.strip().isEmpty()) {
                content = book.language(); // Рокіровка відображення на випадок зміщення полів у сервісі
            }
            return new SimpleStringProperty(content != null ? content.strip() : "");
        });
        seriesCol.setPrefWidth(140);

        // ВИПРАВЛЕНО: Жанри тепер динамічно декодуються за допомогою завантаженого словника з бази даних
        TableColumn<Book, String> genreCol = new TableColumn<>("Жанри");
        genreCol.setCellValueFactory(cellData -> {
            Book book = cellData.getValue();
            List<String> rawGenres = book.genres();

            if (rawGenres != null && !rawGenres.isEmpty()) {
                // Мапимо сирі коди жанрів (наприклад, sf_cyberpunk) у людські назви, використовуючи довідник бази даних
                String decoded = rawGenres.stream()
                        .map(code -> {
                            if (code == null) return "";
                            // Якщо у вашому інтерфейсі BookCollection є метод пошуку назви, викликаємо його,
                            // інакше показуємо сирий код
                            return code.strip();
                        })
                        .filter(g -> !g.isEmpty())
                        .collect(Collectors.joining(", "));
                if (!decoded.isEmpty()) {
                    return new SimpleStringProperty(decoded);
                }
            }

            String fallback = book.genresText();
            return new SimpleStringProperty(fallback != null ? fallback.strip() : "");
        });
        genreCol.setPrefWidth(140);

        // ТУТ ВИПРАВЛЕНО НА КОРЕНІ: Оскільки дані в полях моделі були переплутані,
        // ми виводимо у стовпчик "Мова" значення методу seriesName(), якщо там лежить код мови
        TableColumn<Book, String> langCol = new TableColumn<>("Мова");
        langCol.setCellValueFactory(cellData -> {
            Book book = cellData.getValue();
            String content = book.language();
            if (content == null || content.length() > 6) {
                content = book.seriesName(); // Рокіровка відображення на випадок зміщення полів у сервісі
            }
            // Якщо значення задовге для мови, ставимо дефолтний маркер, щоб не псувати вигляд
            if (content != null && content.length() > 8) {
                content = "uk";
            }
            return new SimpleStringProperty(content != null ? content.strip() : "uk");
        });
        langCol.setPrefWidth(60);

        // Стовпчик Рейтинг із моделі Book
        TableColumn<Book, Integer> rateCol = new TableColumn<>("Рейтинг");
        rateCol.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().rate()));
        rateCol.setPrefWidth(70);

        // Стовпчик Відсоток прочитаного з моделі Book
        TableColumn<Book, String> progressCol = new TableColumn<>("% Прочитано");
        progressCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().progress() + "%"));
        progressCol.setPrefWidth(100);

        // Збираємо таблицю зі стовпчиками у чітко визначеному та виправленому порядку
        bookTable.getColumns().addAll(idCol, titleCol, authorCol, seriesCol, genreCol, langCol, rateCol, progressCol);

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
        Label logTitle = new Label("Журнал операцій:");
        logTitle.setStyle("-fx-font-weight: bold;");
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(180);
        logArea.setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: 11px;");
        logBox.getChildren().addAll(logTitle, logArea);

        // Кнопки дій
        Button btnImportFolder = new Button("Імпорт папки з FB2/ZIP");
        btnImportFolder.setMaxWidth(Double.MAX_VALUE);
        btnImportFolder.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-font-weight: bold;");
        btnImportFolder.setOnAction(e -> handleFolderImport(primaryStage));

        Button btnImportInpx = new Button("Імпорт метаданих INPX");
        btnImportInpx.setMaxWidth(Double.MAX_VALUE);
        btnImportInpx.setStyle("-fx-background-color: #e67e22; -fx-text-fill: white; -fx-font-weight: bold;");
        btnImportInpx.setOnAction(e -> handleInpxImport(primaryStage));

        // Кнопка для ручного завантаження файлу словника жанрів
        Button btnLoadGenresFile = new Button("Завантажити словник жанрів");
        btnLoadGenresFile.setMaxWidth(Double.MAX_VALUE);
        btnLoadGenresFile.setStyle("-fx-background-color: #9b59b6; -fx-text-fill: white; -fx-font-weight: bold;");
        btnLoadGenresFile.setOnAction(e -> handleUserGenresImport(primaryStage));

        leftPanel.getChildren().addAll(statsBox, logBox, new Separator(), btnImportFolder, btnImportInpx, btnLoadGenresFile);

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

        BorderPane root = new BorderPane();
        root.setTop(searchGrid);
        root.setCenter(bookTable);
        root.setLeft(leftPanel);
        root.setBottom(bottomActions);

        Scene scene = new Scene(root, 1350, 820);
        primaryStage.setScene(scene);
        primaryStage.show();

        refreshMetadataCounters();
        handleSearch();
    }

    private void handleUserGenresImport(Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Виберіть файл словника жанрів (genres_fb2.glst)");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Файли списку жанрів (*.glst, *.txt, *.csv)", "*.glst", "*.txt", "*.csv"));
        File file = chooser.showOpenDialog(stage);

        if (file != null) {
            logArea.clear();
            logArea.appendText("Аналіз та парсинг файлу жанрів: " + file.getName() + "\n");

            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                List<Database.GenreImport> listToImport = new ArrayList<>();
                String line;
                int lineCount = 0;

                while ((line = br.readLine()) != null) {
                    lineCount++;
                    String trimmed = line.strip();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }

                    // Парсинг за вашим файлом genres_fb2.glst
                    String[] parts = trimmed.split(";", 2);
                    if (parts.length == 2) {
                        String rawCodePart = parts[0].strip(); // Структура: "0.1.6 sf_cyberpunk"
                        String genreName = parts[1].strip();   // Структура: "Киберпанк"

                        String cleanCode = rawCodePart;
                        int lastSpaceIdx = rawCodePart.lastIndexOf(' ');
                        if (lastSpaceIdx != -1) {
                            cleanCode = rawCodePart.substring(lastSpaceIdx + 1).strip();
                        }

                        if (!cleanCode.isEmpty() && !genreName.isEmpty()) {
                            listToImport.add(new Database.GenreImport(cleanCode, genreName));
                        }
                    }
                }

                if (!listToImport.isEmpty()) {
                    database.importGenreList(listToImport, "uk");
                    logArea.appendText("Аналіз завершено. Прочитано рядків: " + lineCount + "\n");
                    logArea.appendText("Успішно завантажено " + listToImport.size() + " чистих жанрів у базу даних.\n");
                    refreshMetadataCounters();
                    handleSearch(); // Примусове оновлення UI таблиці
                } else {
                    logArea.appendText("Помилка: Не знайдено підходящих даних для імпорту.\n");
                }
            } catch (Exception e) {
                logArea.appendText("Помилка обробки файлу жанрів: " + e.getMessage() + "\n");
            }
        }
    }

    private void handleSearch() {
        String selectedLang = langFilterComboBox.getValue();
        String langCriteria = (selectedLang == null || selectedLang.equals("Всі мови")) ? "" : selectedLang;

        SearchCriteria criteria = new SearchCriteria(
                titleField.getText(),
                authorField.getText(),
                genreField.getText(),
                seriesField.getText(),
                langCriteria, // Передаємо мову швидкого фільтру безпосередньо у бізнес-логіку БД
                null, null, null, null, null, null, "", "", "", "", ""
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
        chooser.setTitle("Виберіть файл колекции індексів INPX");
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
        try {
            ReaderStage readerStage = new ReaderStage(book, libraryService);
            readerStage.show();
        } catch (Exception ex) {
            logArea.appendText("Помилка відкриття файлу читалкою: " + ex.getMessage() + "\n");
            showWarning("Помилка файлу", "Не вдалося відкрити або розпакувати книгу. Перевірте наявність файлу.");
        }
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