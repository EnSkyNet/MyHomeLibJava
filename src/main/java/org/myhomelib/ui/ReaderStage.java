package org.myhomelib.ui;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.myhomelib.model.Book;
import org.myhomelib.service.LibraryService;

public final class ReaderStage extends Stage {
    private final Book book;
    private final LibraryService libraryService;

    private WebView webView;
    private ProgressIndicator progressIndicator;
    private VBox loadingOverlay;

    public ReaderStage(Book book, LibraryService libraryService) {
        this.book = book;
        this.libraryService = libraryService;

        initModality(Modality.NONE);
        setTitle("Читання книги — " + book.title());
        setMinWidth(700);
        setMinHeight(550);

        initializeUI();
        loadBookTextAsync();
    }

    private void initializeUI() {
        BorderPane root = new BorderPane();

        // Верхня інформаційна панель
        VBox topPanel = new VBox(4);
        topPanel.setPadding(new Insets(10, 15, 10, 15));
        topPanel.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #e9ecef; -fx-border-width: 0 0 1 0;");

        Label lblTitle = new Label(book.title());
        lblTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        Label lblAuthor = new Label("Автор: " + book.authorsText());
        lblAuthor.setStyle("-fx-font-size: 12px; -fx-text-fill: #7f8c8d;");

        topPanel.getChildren().addAll(lblTitle, lblAuthor);
        root.setTop(topPanel);

        // Веб-компонент для тексту книги
        webView = new WebView();
        webView.setContextMenuEnabled(false);

        // Індикатор завантаження великих файлів книг
        progressIndicator = new ProgressIndicator();
        Label lblLoading = new Label("Відбувається декомпресія та рендеринг FB2 тексту...");
        lblLoading.setStyle("-fx-font-weight: bold;");
        loadingOverlay = new VBox(10, progressIndicator, lblLoading);
        loadingOverlay.setAlignment(Pos.CENTER);
        loadingOverlay.setStyle("-fx-background-color: rgba(255, 255, 255, 0.85);");

        // Накладаємо лоадер поверх WebView за допомогою StackPane
        StackPane centerPane = new StackPane(webView, loadingOverlay);
        root.setCenter(centerPane);

        // Нижня панель дій
        VBox bottomPanel = new VBox();
        bottomPanel.setPadding(new Insets(10, 15, 10, 15));
        bottomPanel.setAlignment(Pos.CENTER_RIGHT);
        bottomPanel.setStyle("-fx-background-color: #f1f2f6;");

        Button btnClose = new Button("Закрити книгу");
        btnClose.setPrefWidth(120);
        btnClose.setOnAction(e -> close());
        bottomPanel.getChildren().add(btnClose);
        root.setBottom(bottomPanel);

        Scene scene = new Scene(root, 850, 700);
        setScene(scene);
    }

    private void loadBookTextAsync() {
        // Створюємо асинхронну задачу JavaFX Task з перевизначенням методу call()
        Task<String> loadTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                // Витягуємо книгу з бази/архіву та обертаємо в HTML код
                return libraryService.readBookHtml(book);
            }
        };

        // Коли задача успішно виконана
        loadTask.setOnSucceeded(event -> {
            String htmlContent = loadTask.getValue();
            webView.getEngine().loadContent(htmlContent);
            loadingOverlay.setVisible(false);
        });

        // У випадку виникнення помилок
        loadTask.setOnFailed(event -> {
            Throwable e = loadTask.getException();
            String errorHtml = "<html><body style='font-family:sans-serif; padding:30px; text-align:center;'> "
                    + "<h2 style='color:#e74c3c;'>Помилка завантаження книги</h2>"
                    + "<p style='color:#555;'>" + e.getMessage() + "</p>"
                    + "</body></html>";
            webView.getEngine().loadContent(errorHtml);
            loadingOverlay.setVisible(false);
        });

        Thread thread = new Thread(loadTask);
        thread.setDaemon(true);
        thread.start();
    }
}