package org.myhomelib.service;

import org.myhomelib.db.DatabaseManager;
import org.myhomelib.db.repository.AuthorRepository;
import org.myhomelib.db.repository.BookRepository;
import org.myhomelib.db.repository.GenreRepository;
import org.myhomelib.model.Book;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BookImportWorker {
    private final DatabaseManager dbManager;
    private final BookRepository bookRepository;
    private final AuthorRepository authorRepository;
    private final GenreRepository genreRepository;

    private final Map<String, Long> authorCache = new ConcurrentHashMap<>();
    private final Map<String, Long> genreCache = new ConcurrentHashMap<>();

    public BookImportWorker(DatabaseManager dbManager,
                            BookRepository bookRepository,
                            AuthorRepository authorRepository,
                            GenreRepository genreRepository) {
        this.dbManager = dbManager;
        this.bookRepository = bookRepository;
        this.authorRepository = authorRepository;
        this.genreRepository = genreRepository;
    }

    public void importSingleBook(Book book) {
        // 1. Збереження книги в репозиторій (включаючи основну таблицю та повнотекстовий індекс FTS5)
        bookRepository.saveBook(book);

        // 2. Обробка автора з використанням кешу в оперативній пам'яті (authorCache)
        String rawAuthors = book.authorsText();
        if (rawAuthors != null && !rawAuthors.isBlank()) {
            String[] parts = rawAuthors.split(",");
            for (String part : parts) {
                String cleanAuthor = part.trim();
                if (!cleanAuthor.isEmpty()) {
                    authorCache.computeIfAbsent(cleanAuthor, authorName -> {
                        long authorId = getOrInsertAuthorFromDb(authorName);
                        authorRepository.insertAuthor(book.id(), authorName);
                        return authorId;
                    });
                }
            }
        }

        // 3. Обробка жанрів з використанням кешу в оперативній пам'яті (genreCache)
        String rawGenres = book.genresText();
        if (rawGenres != null && !rawGenres.isBlank()) {
            String[] parts = rawGenres.split(",");
            for (String part : parts) {
                String cleanGenre = part.trim().toLowerCase();
                if (!cleanGenre.isEmpty()) {
                    genreCache.computeIfAbsent(cleanGenre, genreCode -> {
                        return getOrInsertGenreFromDb(genreCode);
                    });
                }
            }
        }
    }

    private long getOrInsertAuthorFromDb(String authorName) {
        String selectSql = "SELECT rowid FROM book_authors WHERE author_name = ? LIMIT 1";
        Connection conn = dbManager.getConnection();

        try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
            selectStmt.setString(1, authorName);
            try (ResultSet rs = selectStmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Помилка роботи кеш-провайдера для автора: " + authorName, e);
        }
        return -1;
    }

    private long getOrInsertGenreFromDb(String genreCode) {
        String selectSql = "SELECT rowid FROM genres WHERE code = ? LIMIT 1";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
            selectStmt.setString(1, genreCode);
            try (ResultSet rs = selectStmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Помилка роботи кеш-провайдера для жанру: " + genreCode, e);
        }
        return -1;
    }

    public void clearWorkerCache() {
        this.authorCache.clear();
        this.genreCache.clear();
    }
}