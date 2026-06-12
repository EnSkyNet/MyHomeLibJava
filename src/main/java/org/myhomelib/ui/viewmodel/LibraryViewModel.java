package org.myhomelib.ui.viewmodel;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.myhomelib.db.repository.BookRepository;
import org.myhomelib.db.repository.SearchRepository;
import org.myhomelib.model.Book;
import org.myhomelib.model.SearchCriteria;
import org.myhomelib.service.*;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class LibraryViewModel {
    // Сервіси (Шар бізнес-логіки)
    private final SearchService searchService;
    private final ImportService importService;
    private final StatisticsService statisticsService;
    private final LibraryService libraryService;

    // Властивості Data Binding для UI компонентів
    private final ObservableList<Book> books = FXCollections.observableArrayList();
    private final ObservableList<String> authors = FXCollections.observableArrayList();
    private final ObservableList<String> series = FXCollections.observableArrayList();

    private final ObjectProperty<Book> selectedBook = new SimpleObjectProperty<>();

    private final StringProperty searchText = new SimpleStringProperty("");
    private final StringProperty searchAuthor = new SimpleStringProperty("");
    private final StringProperty searchSeries = new SimpleStringProperty("");
    private final StringProperty selectedLanguage = new SimpleStringProperty("Всі мови");

    public LibraryViewModel() {
        // Ініціалізація інфраструктури баз даних
        Path systemDb = Path.of("myhomelib-system.dbs");
        Path defaultCollection = Path.of("library.db");
        CollectionManager collectionManager = new CollectionManager(systemDb, defaultCollection);

        SearchRepository searchRepository = new SearchRepository(collectionManager.collectionDatabase());
        BookRepository bookRepository = new BookRepository(collectionManager.collectionDatabase());

        // Створення екземплярів сервісів (Впровадження залежностей)
        this.searchService = new SearchService(searchRepository);
        this.importService = new ImportService(bookRepository, collectionManager);
        this.statisticsService = new StatisticsService(searchRepository);
        this.libraryService = new LibraryService(collectionManager);

        refreshData();
    }

    public void refreshData() {
        // Оновлюємо дані, використовуючи виключно SearchService
        List<Book> allBooks = searchService.findBooks(SearchCriteria.empty());
        books.setAll(allBooks);

        authors.setAll(allBooks.stream()
                .map(Book::authorsText)
                .filter(a -> a != null && !a.isBlank())
                .distinct()
                .collect(Collectors.toList()));

        series.setAll(allBooks.stream()
                .map(Book::series)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .collect(Collectors.toList()));
    }

    public void executeSearch() {
        SearchCriteria criteria = new SearchCriteria(
                searchText.get(),
                searchAuthor.get(),
                "",
                searchSeries.get(),
                selectedLanguage.get()
        );
        // Запит йде через сервісний шар
        List<Book> results = searchService.findBooks(criteria);
        books.setAll(results);
    }

    // =========================================================================
    // ГЕТТЕРИ ДЛЯ СЕРВІСІВ (Необхідні для вирішення вашої помилки компіляції)
    // =========================================================================

    public SearchService getSearchService() {
        return searchService;
    }

    public ImportService getImportService() {
        return importService;
    }

    public StatisticsService getStatisticsService() {
        return statisticsService;
    }

    public LibraryService getLibraryService() {
        return libraryService;
    }

    // =========================================================================
    // ГЕТТЕРИ ВЛАСТИВОСТЕЙ DATA BINDING
    // =========================================================================

    public ObservableList<Book> getBooks() {
        return books;
    }

    public ObservableList<String> getAuthors() {
        return authors;
    }

    public ObservableList<String> getSeries() {
        return series;
    }

    public ObjectProperty<Book> selectedBookProperty() {
        return selectedBook;
    }

    public StringProperty searchTextProperty() {
        return searchText;
    }

    public StringProperty searchAuthorProperty() {
        return searchAuthor;
    }

    public StringProperty searchSeriesProperty() {
        return searchSeries;
    }

    public StringProperty selectedLanguageProperty() {
        return selectedLanguage;
    }
}