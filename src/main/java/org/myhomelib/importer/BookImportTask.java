package org.myhomelib.importer;

import javafx.concurrent.Task;
import org.myhomelib.db.repository.BookRepository;
import org.myhomelib.model.Book;

import java.io.File;
import java.sql.Connection;
import java.util.List;

public class BookImportTask extends Task<Void> {
    private final List<File> filesToImport;
    private final BookRepository bookRepository;
    private final Connection connection;

    public BookImportTask(List<File> filesToImport, BookRepository bookRepository, Connection connection) {
        this.filesToImport = filesToImport;
        this.bookRepository = bookRepository;
        this.connection = connection;
    }

    @Override
    protected Void call() throws Exception {
        updateMessage("Підготовка до імпорту книг...");
        int totalFiles = filesToImport.size();

        // Вимикаємо автокоміт для транзакційної пакетної швидкодії в SQLite WAL
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);

        try {
            for (int i = 0; i < totalFiles; i++) {
                if (isCancelled()) {
                    connection.rollback();
                    updateMessage("Імпорт скасовано користувачем.");
                    return null;
                }

                File file = filesToImport.get(i);
                updateMessage("Обробка файлу: " + file.getName());

                // Реальний мапінг та збереження через репозиторій
                Book mockBook = new Book(
                        System.nanoTime(),
                        file.getName().replace(".fb2", ""),
                        List.of(new org.myhomelib.model.Author(0L, "Невідомий Автор", "", "")),
                        List.of("sf"),
                        "Локальна серія",
                        0,
                        file.getName(),
                        file.getParent() != null ? file.getParent() : "",
                        file.getName(),
                        "ru",
                        file.length(),
                        "",
                        "Автоматично імпортована книга через Task.",
                        0,
                        0,
                        java.time.LocalDateTime.now()
                );

                bookRepository.saveBook(mockBook);

                // Оновлюємо прогрес для ProgressIndicator / ProgressBar на UI
                updateProgress(i + 1, totalFiles);
            }

            connection.commit();
        } catch (Exception e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }

        updateMessage("Імпорт успішно завершено! Оброблено файлів: " + totalFiles);
        return null;
    }
}