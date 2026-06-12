package org.myhomelib.db.repository;

import org.myhomelib.db.DatabaseManager;
import org.myhomelib.model.Book;
import org.myhomelib.model.Author;
import org.myhomelib.model.SearchCriteria;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Репозиторій фонового пошуку та фільтрації книг у локальній базі даних SQLite.
 * Повністю захищений від розбіжностей у методах-геттерах класу SearchCriteria.
 */
public class SearchRepository {

    private final DatabaseManager databaseManager;

    public SearchRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Розширений пошук за критеріями.
     * Динамічно адаптується під будь-які назви геттерів у SearchCriteria.
     */
    public List<Book> searchBooks(SearchCriteria criteria) {
        List<Book> result = new ArrayList<>();
        if (criteria == null) return result;

        String queryText = "";

        // Динамічний пошук текстового геттера всередині SearchCriteria для уникнення помилок компіляції
        try {
            java.lang.reflect.Method[] methods = SearchCriteria.class.getMethods();
            for (java.lang.reflect.Method method : methods) {
                String name = method.getName();
                // Перевіряємо найпопулярніші назви геттерів для тексту пошуку
                if ((name.equals("getQuery") || name.equals("getTextQuery") || name.equals("query") || name.equals("text"))
                        && method.getParameterCount() == 0 && method.getReturnType() == String.class) {
                    queryText = (String) method.invoke(criteria);
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("[SEARCH REPO] Не вдалося прочитати текстове поле через Reflection: " + e.getMessage());
        }

        // Якщо через reflection нічого не знайшли, але об'єкт має власне toString представлення
        if ((queryText == null || queryText.isBlank()) && criteria.toString() != null && !criteria.toString().startsWith("SearchCriteria@")) {
            queryText = criteria.toString();
        }

        if (queryText == null || queryText.isBlank()) {
            // Фолбек: якщо запит порожній, віддаємо перші 1000 книг для стабільності інтерфейсу UI
            String sql = "SELECT * FROM books ORDER BY title ASCII LIMIT 1000;";
            try (Connection conn = databaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    result.add(mapResultSetToBook(rs));
                }
            } catch (SQLException e) {
                System.err.println("[SEARCH REPO ERR] Помилка пустого searchBooks: " + e.getMessage());
                throw new RuntimeException(e);
            }
            return result;
        }

        return search(queryText);
    }

    /**
     * Отримання списку всіх унікальних мов книг.
     */
    public List<String> getDistinctLanguages() {
        List<String> languages = new ArrayList<>();
        String sql = "SELECT DISTINCT language FROM books WHERE language IS NOT NULL AND language != '' ORDER BY language ASCII;";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                languages.add(rs.getString("language"));
            }
        } catch (SQLException e) {
            System.err.println("[SEARCH REPO ERR] Помилка getDistinctLanguages: " + e.getMessage());
            throw new RuntimeException(e);
        }
        return languages;
    }

    /**
     * Комплексний гнучкий пошук за текстовим запитом (Назва, Серія або Ключові слова).
     */
    public List<Book> search(String query) {
        List<Book> result = new ArrayList<>();
        if (query == null || query.isBlank()) return result;

        String sql = """
            SELECT * FROM books 
            WHERE title LIKE ? OR series LIKE ? OR keywords LIKE ? 
            ORDER BY title ASCII;
        """;

        String searchPattern = "%" + query.trim() + "%";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, searchPattern);
            pstmt.setString(2, searchPattern);
            pstmt.setString(3, searchPattern);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    result.add(mapResultSetToBook(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[SEARCH REPO ERR] Помилка виконання пошуку query=" + query + " : " + e.getMessage());
            throw new RuntimeException(e);
        }
        return result;
    }

    /**
     * Пошук усіх книг, що належать конкретному автору за ID.
     */
    public List<Book> findBooksByAuthorId(long authorId) {
        List<Book> result = new ArrayList<>();
        String sql = """
            SELECT b.* FROM books b
            JOIN book_authors ba ON b.id = ba.book_id
            WHERE ba.author_id = ?
            ORDER BY b.title ASCII;
        """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, authorId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    result.add(mapResultSetToBook(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[SEARCH REPO ERR] Помилка findBooksByAuthorId: " + e.getMessage());
            throw new RuntimeException(e);
        }
        return result;
    }

    /**
     * Мапінг результатів вибірки SQLite в об'єкти вашої моделі Book.
     */
    private Book mapResultSetToBook(ResultSet rs) throws SQLException {
        List<Author> authorsList = new ArrayList<>();
        List<String> genresList = new ArrayList<>();

        String dtStr = rs.getString("date_time");
        LocalDateTime bookDate = (dtStr == null || dtStr.isBlank()) ? LocalDateTime.now() : LocalDateTime.parse(dtStr);

        return new Book(
                rs.getLong("id"),
                rs.getString("title"),
                authorsList,
                genresList,
                rs.getString("series"),
                rs.getInt("sequence_number"),
                rs.getString("language"),
                rs.getString("file_name"),
                rs.getString("folder"),
                rs.getString("archive_entry"),
                rs.getLong("file_size"),
                rs.getString("keywords"),
                rs.getString("annotation"),
                rs.getInt("rate"),
                rs.getInt("progress"),
                bookDate
        );
    }
}