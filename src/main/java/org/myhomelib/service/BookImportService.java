package org.myhomelib.service;

import javafx.concurrent.Task;
import org.myhomelib.model.Book;
import org.myhomelib.db.DatabaseManager; // Оновлений імпорт пакета
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Enterprise Сервіс оркестрації асинхронного імпорту книг у локальну БД.
 */
public class BookImportService {

    private final InpxParser inpxParser;
    private final DatabaseManager databaseManager;

    // Впроваджуємо DatabaseManager через конструктор
    public BookImportService() {
        this.inpxParser = new InpxParser();
        // Створюємо або підключаємо локальну базу даних у корені проекту
        this.databaseManager = new DatabaseManager("library.db");
    }

    /**
     * Створює реактивну JavaFX задачу для фонового імпорту файлів архівів без блокування UI.
     */
    public Task<Integer> createImportTask(List<Path> filesToImport) {
        return new Task<Integer>() {
            @Override
            protected Integer call() throws Exception {
                AtomicInteger totalImported = new AtomicInteger(0);
                int totalFiles = filesToImport.size();

                updateProgress(0, totalFiles);
                updateMessage("Запуск процесу імпорту...");

                for (int i = 0; i < totalFiles; i++) {
                    if (isCancelled()) {
                        updateMessage("Імпорт перервано користувачем.");
                        break;
                    }

                    Path filePath = filesToImport.get(i);
                    updateMessage("Обробка файлу [" + (i + 1) + " з " + totalFiles + "]: " + filePath.getFileName());

                    if (filePath.toString().toLowerCase().endsWith(".inpx")) {
                        try (InputStream is = new FileInputStream(filePath.toFile())) {

                            // Запускаємо парсинг пакетами по 5000 книг
                            inpxParser.parseInpxStream(is, 5000, bookBatch -> {
                                totalImported.addAndGet(bookBatch.size());

                                // АКТИВНИЙ ВИКЛИК: Зберігаємо пакет у базу через оптимізований менеджер
                                databaseManager.saveBooksBatch(bookBatch);

                                updateMessage("Завантажено книг в індекс SQLite: " + totalImported.get());
                            });
                        }
                    } else {
                        totalImported.incrementAndGet();
                        Thread.sleep(20);
                    }

                    updateProgress(i + 1, totalFiles);
                }

                updateMessage("Імпорт завершено успішно! Додано об'єктів: " + totalImported.get());
                return totalImported.get();
            }
        };
    }
}