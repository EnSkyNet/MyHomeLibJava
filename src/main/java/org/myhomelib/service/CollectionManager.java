package org.myhomelib.service;

import org.myhomelib.db.DatabaseManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

/**
 * Сервіс управління життєвим циклом підключення до файлів колекцій бібліотек.
 * Додано метод collectionDatabase() для повної сумісності з LibraryService.
 */
public class CollectionManager {

    private DatabaseManager collectionDatabase;
    private Path currentCollectionPath;

    /**
     * Ініціалізація та відкриття нової бази даних колекції.
     */
    public void openCollection(Path path) {
        try {
            if (collectionDatabase != null) {
                closeCollection();
            }

            this.currentCollectionPath = path;
            this.collectionDatabase = new DatabaseManager(path);
            this.collectionDatabase.open();

            System.out.println("[COLLECTION] Успішно підключено робочий простір: " + path.getFileName());
        } catch (SQLException e) {
            System.err.println("[COLLECTION ERR] Не вдалося відкрити базу даних: " + e.getMessage());
        }
    }

    /**
     * Закриття поточного активного підключення.
     */
    public void closeCollection() {
        try {
            if (collectionDatabase != null) {
                collectionDatabase.close();
                collectionDatabase = null;
                currentCollectionPath = null;
                System.out.println("[COLLECTION] Поточну колекцію успішно вивантажено з пам'яті.");
            }
        } catch (SQLException e) {
            System.err.println("[COLLECTION ERR] Помилка при закритті підключення: " + e.getMessage());
        }
    }

    /**
     * Створення нової порожньої колекції на диску.
     */
    public void createNewCollection(Path path) {
        try {
            if (Files.exists(path)) {
                Files.delete(path);
            }

            openCollection(path);
            System.out.println("[COLLECTION] Створено новий чистий файл бази даних за шляхом: " + path);
        } catch (IOException e) {
            System.err.println("[COLLECTION ERR] Помилка створення файлу структури: " + e.getMessage());
        }
    }

    /**
     * Геттер за класичною Java-конвенцією.
     */
    public DatabaseManager getCollectionDatabase() {
        return collectionDatabase;
    }

    /**
     * Метод-геттер без префікса 'get' суворо для вирішення помилки в LibraryService.
     */
    public DatabaseManager collectionDatabase() {
        return collectionDatabase;
    }

    public Path getCurrentCollectionPath() {
        return currentCollectionPath;
    }
}