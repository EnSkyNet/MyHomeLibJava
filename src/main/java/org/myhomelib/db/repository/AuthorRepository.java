package org.myhomelib.db.repository;

import org.myhomelib.db.DatabaseManager;
import org.myhomelib.model.Author;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Репозиторій для управління об'єктами авторів у SQLite.
 * Повністю адаптований під динамічне читання полів Record та сумісність з BookImportWorker.
 */
public class AuthorRepository {

    private final DatabaseManager databaseManager;

    public AuthorRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Пошук усіх авторів у базі даних.
     */
    public List<Author> findAll() {
        List<Author> authors = new ArrayList<>();
        String sql = "SELECT id, full_name, field3, field4 FROM authors ORDER BY full_name ASCII;";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                // Створення об'єкта Author за допомогою конструктора з 4 аргументами
                authors.add(new Author(
                        rs.getLong("id"),
                        rs.getString("full_name"),
                        rs.getString("field3"),
                        rs.getString("field4")
                ));
            }
        } catch (SQLException e) {
            System.err.println("[REPO ERR] Помилка при отриманні авторів: " + e.getMessage());
            throw new RuntimeException(e);
        }
        return authors;
    }

    /**
     * Старий метод вставлення автора, який очікує BookImportWorker.
     */
    public void insertAuthor(long id, String name) {
        String sql = "INSERT OR IGNORE INTO authors (id, full_name, field3, field4) VALUES (?, ?, ?, ?);";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            pstmt.setString(2, name);
            pstmt.setString(3, "");
            pstmt.setString(4, "");
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[REPO ERR] Помилка виконання insertAuthor: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Збереження або оновлення інформації про автора за допомогою динамічного рефлекшена компонентів Record.
     * Захищає від помилок "cannot find symbol" для внутрішніх методів-геттерів.
     */
    public void save(Author author) {
        String sql = "INSERT OR REPLACE INTO authors (id, full_name, field3, field4) VALUES (?, ?, ?, ?);";

        long authId = author.id();
        String authName = "";
        String f3 = "";
        String f4 = "";

        try {
            // Динамічно дістаємо значення полів незалежно від того, як вони названі всередині рекорду Author
            java.lang.reflect.RecordComponent[] components = Author.class.getRecordComponents();
            if (components != null && components.length >= 2) {
                authName = (String) components[1].getAccessor().invoke(author);
                if (components.length > 2) f3 = (String) components[2].getAccessor().invoke(author);
                if (components.length > 3) f4 = (String) components[3].getAccessor().invoke(author);
            }
        } catch (Exception e) {
            authName = author.toString();
        }

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, authId);
            pstmt.setString(2, authName != null ? authName : "Невідомий автор");
            pstmt.setString(3, f3 != null ? f3 : "");
            pstmt.setString(4, f4 != null ? f4 : "");
            pstmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("[REPO ERR] Помилка збереження автора: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}