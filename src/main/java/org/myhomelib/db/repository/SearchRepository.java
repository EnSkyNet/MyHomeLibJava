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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class SearchRepository {
    private final DatabaseManager dbManager;
    private final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public SearchRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public List<Book> searchBooks(SearchCriteria criteria) {
        List<Book> results = new ArrayList<>();
        List<String> ftsTokens = new ArrayList<>();

        if (criteria.title() != null && !criteria.title().isBlank()) {
            ftsTokens.add("title:\"" + criteria.title().replace("\"", "\"\"") + "*\"");
        }
        if (criteria.author() != null && !criteria.author().isBlank()) {
            ftsTokens.add("authors:\"" + criteria.author().replace("\"", "\"\"") + "*\"");
        }
        if (criteria.genre() != null && !criteria.genre().isBlank()) {
            ftsTokens.add("genres:\"" + criteria.genre().replace("\"", "\"\"") + "*\"");
        }
        if (criteria.keywords() != null && !criteria.keywords().isBlank()) {
            ftsTokens.add("keywords:\"" + criteria.keywords().replace("\"", "\"\"") + "*\"");
        }
        if (criteria.annotation() != null && !criteria.annotation().isBlank()) {
            ftsTokens.add("annotation:\"" + criteria.annotation().replace("\"", "\"\"") + "*\"");
        }

        StringBuilder sql = new StringBuilder();
        List<Object> queryParams = new ArrayList<>();

        if (!ftsTokens.isEmpty()) {
            String matchExpression = String.join(" AND ", ftsTokens);
            sql.append("SELECT b.* FROM books b ")
                    .append("JOIN books_fts f ON b.id = f.rowid ")
                    .append("WHERE books_fts MATCH ?");
            queryParams.add(matchExpression);
        } else {
            sql.append("SELECT b.* FROM books b WHERE 1=1");
        }

        if (criteria.series() != null && !criteria.series().isBlank()) {
            sql.append(" AND b.series LIKE ?");
            queryParams.add("%" + criteria.series() + "%");
        }
        if (criteria.language() != null && !criteria.language().isBlank()) {
            sql.append(" AND b.language = ?");
            queryParams.add(criteria.language().toLowerCase());
        }
        if (criteria.fileFolder() != null && !criteria.fileFolder().isBlank()) {
            sql.append(" AND b.folder LIKE ?");
            queryParams.add("%" + criteria.fileFolder() + "%");
        }
        if (criteria.fileArchive() != null && !criteria.fileArchive().isBlank()) {
            sql.append(" AND b.archive_entry LIKE ?");
            queryParams.add("%" + criteria.fileArchive() + "%");
        }

        if (criteria.rateFrom() != null) {
            sql.append(" AND b.rate >= ?");
            queryParams.add(criteria.rateFrom());
        }
        if (criteria.rateTo() != null) {
            sql.append(" AND b.rate <= ?");
            queryParams.add(criteria.rateTo());
        }

        if (criteria.progressFrom() != null) {
            sql.append(" AND b.progress >= ?");
            queryParams.add(criteria.progressFrom());
        }
        if (criteria.progressTo() != null) {
            sql.append(" AND b.progress <= ?");
            queryParams.add(criteria.progressTo());
        }

        if (criteria.sizeFrom() != null) {
            sql.append(" AND b.file_size >= ?");
            queryParams.add(criteria.sizeFrom());
        }
        if (criteria.sizeTo() != null) {
            sql.append(" AND b.file_size <= ?");
            queryParams.add(criteria.sizeTo());
        }

        sql.append(" ORDER BY b.id DESC LIMIT 1000");

        Connection conn = dbManager.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < queryParams.size(); i++) {
                pstmt.setObject(i + 1, queryParams.get(i));
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String dateStr = rs.getString("update_date");
                    LocalDateTime ldt = (dateStr != null && !dateStr.isBlank()) ? LocalDateTime.parse(dateStr, formatter) : LocalDateTime.now();

                    List<Author> authorsList = new ArrayList<>();
                    String rawAuthors = rs.getString("authors");
                    if (rawAuthors != null && !rawAuthors.isBlank()) {
                        for (String name : rawAuthors.split(",")) {
                            authorsList.add(new Author(0L, name.strip(), "", ""));
                        }
                    }

                    List<String> genresList = new ArrayList<>();
                    String rawGenres = rs.getString("genre");
                    if (rawGenres != null && !rawGenres.isBlank()) {
                        for (String g : rawGenres.split(",")) {
                            genresList.add(g.strip());
                        }
                    }

                    results.add(new Book(
                            rs.getLong("id"),
                            rs.getString("title"),
                            authorsList,
                            genresList,
                            rs.getString("series"),
                            rs.getInt("rate"),
                            rs.getString("file_name"),
                            rs.getString("folder"),
                            rs.getString("archive_entry"),
                            rs.getString("language"),
                            rs.getLong("file_size"),
                            rs.getString("keywords"),
                            rs.getString("annotation"),
                            rs.getInt("progress"),
                            0,
                            ldt
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Критична помилка виконання FTS5 пошуку з фільтрацією діапазонів", e);
        }
        return results;
    }

    public List<String> getDistinctLanguages() {
        List<String> languages = new ArrayList<>();
        String sql = "SELECT DISTINCT language FROM books WHERE language IS NOT NULL AND language != '' ORDER BY language ASC";
        Connection conn = dbManager.getConnection();
        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                languages.add(rs.getString("language"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Помилка отримання списку унікальних мов", e);
        }
        return languages;
    }
}