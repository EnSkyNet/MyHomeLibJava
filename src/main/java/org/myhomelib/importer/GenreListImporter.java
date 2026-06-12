package org.myhomelib.importer;

import org.myhomelib.db.DatabaseManager;
import org.myhomelib.db.repository.GenreRepository;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Імпортер словників та списків жанрів бібліотеки у SQLite базу даних.
 * Повністю захищений від виключень SQLException для безпечної роботи в фонових тасках.
 */
public class GenreListImporter {

    private final DatabaseManager databaseManager;
    private final GenreRepository genreRepository;

    public GenreListImporter(DatabaseManager databaseManager, GenreRepository genreRepository) {
        this.databaseManager = databaseManager;
        this.genreRepository = genreRepository;
    }

    /**
     * Імпортувати жанри з текстового файлу списку (де кожен жанр на новому рядку).
     */
    public void importGenresFromFile(Path filePath) {
        if (filePath == null || !Files.exists(filePath)) {
            System.err.println("[GENRE IMPORTER ERR] Файл списку жанрів не знайдено.");
            return;
        }

        String sql = "INSERT OR IGNORE INTO genres (name) VALUES (?);";

        // Огортаємо роботу з ресурсами та JDBC у try-with-resources та try-catch для перехоплення SQLException
        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8);
             Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            conn.setAutoCommit(false); // Вимикаємо авто-комміт для швидкісного пакетного запису
            String line;
            int count = 0;

            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;

                pstmt.setString(1, line.trim());
                pstmt.addBatch();
                count++;

                // Коммітимо пакетами по 1000 записів для економії оперативної пам'яті
                if (count % 1000 == 0) {
                    pstmt.executeBatch();
                }
            }

            pstmt.executeBatch(); // Скидаємо залишок записів у базу даних
            conn.commit();        // Фіксуємо транзакцію

            System.out.println("[GENRE IMPORTER] Успішно імпортовано жанрів: " + count);

        } catch (IOException e) {
            System.err.println("[GENRE IMPORTER ERR] Помилка читання файлу жанрів: " + e.getMessage());
            throw new RuntimeException(e);
        } catch (SQLException e) {
            System.err.println("[GENRE IMPORTER ERR] Критична помилка бази даних при імпорті: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}