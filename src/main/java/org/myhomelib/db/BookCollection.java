package org.myhomelib.db;

import org.myhomelib.model.Book;
import org.myhomelib.model.Fb2Book;
import org.myhomelib.model.BookEdit;
import org.myhomelib.model.SearchCriteria;
import org.myhomelib.model.SearchPreset;

import java.nio.file.Path;
import java.util.List;

public interface BookCollection {

    // --- Керування життєвим циклом підключення до БД ---
    void open(Path dbPath);
    void close();
    boolean isOpen();

    // --- Лічильники та аналітика ---
    int getBooksCount();
    int getAuthorsCount();
    int getGenresCount();

    // --- Базові операції з книгами та імпорт ---
    int importBooks(List<Fb2Book> books);
    void updateBookFields(long bookId, BookEdit editData);
    List<Book> findBooks(SearchCriteria criteria);

    // --- Додаткові методи пошуку та вибірок (існують у Database.java) ---
    List<Book> searchBooks(String keyword);
    List<Book> searchAdvanced(SearchCriteria criteria);
    List<String> listAuthors();
    List<String> listSeries();
    List<String> listGenres();
    String statistics();

    // --- Специфічні системні методи імпорту структур ---
    // Використовуємо Object для загальних внутрішніх типів або вказуємо прямий тип,
    // якщо GenreImport оголошений як static/вкладений клас
    void importGenreList(List<?> genres, String lang);

    // --- Керування збереженими пресетами пошуку (Синхронізовано з SearchPresetService) ---
    List<SearchPreset> loadSearchPresets();
    void saveSearchPreset(String name, SearchCriteria criteria);
    void deleteSearchPreset(long id);

    // --- Інші методи внутрішньої інфраструктури, що викликаються ззовні ---
    void executeRawSql(String sql);
    void beginTransaction();
    void commitTransaction();
    void rollbackTransaction();
}