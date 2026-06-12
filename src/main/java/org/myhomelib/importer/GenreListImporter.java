package org.myhomelib.importer;

import org.myhomelib.db.DatabaseManager;
import org.myhomelib.db.repository.GenreRepository;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public final class GenreListImporter {
    private final DatabaseManager dbManager;
    private final GenreRepository genreRepository;

    public GenreListImporter(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        this.genreRepository = new GenreRepository(dbManager);
    }

    /**
     * Імпортує переклади та коди жанрів з текстового файлу конфігурації (.glst) у базу даних.
     * Використовує механізм пакетного вставлення JDBC Batch для максимальної продуктивності.
     */
    public void importGenresFromFile(Path filePath, String language) throws IOException {
        String sql = "INSERT OR REPLACE INTO genres (code, name, lang) VALUES (?, ?, ?)";
        Connection conn = dbManager.getConnection();

        // Активуємо транзакційну групу, щоб не робити коміт на кожен рядок файлу
        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             BufferedReader reader = Files.newBufferedReader(filePath)) {

            // Вимикаємо автокоміт для пакетної обробки
            boolean previousAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            String line;
            int count = 0;

            while ((line = reader.readLine()) != null) {
                if (line.strip().isEmpty() || line.startsWith("#")) {
                    continue; // Пропускаємо порожні рядки та коментарі
                }

                String[] parts = line.split(";");
                if (parts.length >= 2) {
                    String code = parts[0].strip();
                    String name = parts[1].strip();

                    pstmt.setString(1, code);
                    pstmt.setString(2, name);
                    pstmt.setString(3, language != null ? language.strip().toLowerCase() : "ru");

                    pstmt.addBatch();
                    count++;

                    // Виконуємо проміжне скидання пакету кожні 1000 записів для економії пам'яті
                    if (count % 1000 == 0) {
                        pstmt.executeBatch();
                    }
                }
            }

            // Виконуємо фінальний запис залишку даних та фіксуємо транзакцію
            if (count % 1000 != 0) {
                pstmt.executeBatch();
            }

            conn.commit();
            conn.setAutoCommit(previousAutoCommit); // Повертаємо попередній стан підключення

            System.out.println("[Імпорт жанрів] Успішно завантажено " + count + " кодів для мови: " + language);

        } catch (SQLException e) {
            try {
                if (!conn.isClosed()) {
                    conn.rollback(); // Відкат у разі критичної помилки запису на диск
                }
            } catch (SQLException ignored) {}
            throw new RuntimeException("Помилка пакетного імпорту словника жанрів з файлу: " + filePath.getFileName(), e);
        }
    }
}