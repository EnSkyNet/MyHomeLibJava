package org.myhomelib.ui.viewmodel;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.myhomelib.db.repository.BookRepository;
import org.myhomelib.importer.Fb2Importer;
import org.myhomelib.model.Book;

import java.util.List;

public class LibraryViewModel {

    private final BookRepository bookRepository;
    private final Fb2Importer fb2Importer;
    private final ObservableList<Book> booksList = FXCollections.observableArrayList();

    /**
     * Конструктор чітко розділяє репозиторії даних та інструменти парсингу.
     */
    public LibraryViewModel(BookRepository bookRepository, Fb2Importer fb2Importer) {
        this.bookRepository = bookRepository;
        this.fb2Importer = fb2Importer;
    }

    /**
     * Повне завантаження всіх книг з репозиторію в Observable-список для JavaFX TableView.
     * Використовує оригінальний метод getBooks() з BookRepository.java.
     */
    public void loadAllBooks() {
        if (bookRepository == null) {
            System.err.println("[ПОМИЛКА] Спроба завантаження книг, але BookRepository є null");
            return;
        }
        try {
            // КЛЮЧОВЕ ВИПРАВЛЕННЯ: Викликаємо оригінальний метод вашого репозиторію getBooks()
            List<Book> allBooks = bookRepository.getBooks();
            if (allBooks != null) {
                booksList.setAll(allBooks);
            } else {
                booksList.clear();
            }
        } catch (Exception e) {
            System.err.println("Помилка синхронізації ViewModel з репозиторієм: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public ObservableList<Book> getBooksList() {
        return booksList;
    }

    public BookRepository getBookRepository() {
        return bookRepository;
    }

    public Fb2Importer getFb2Importer() {
        return fb2Importer;
    }
}