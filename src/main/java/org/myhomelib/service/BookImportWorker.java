package org.myhomelib.service;

import org.myhomelib.db.DatabaseManager;
import org.myhomelib.db.repository.AuthorRepository;
import org.myhomelib.db.repository.BookRepository;
import org.myhomelib.db.repository.GenreRepository;
import org.myhomelib.model.Author;
import org.myhomelib.model.Book;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;

/**
 * Фоновий воркер для високопродуктивного пакетного збереження книг та їх метаданих.
 * Повністю ізольований від виключень java.sql.SQLException.
 */
public class BookImportWorker implements Runnable {

    private final BlockingQueue<List<Book>> queue;
    private final DatabaseManager databaseManager;
    private final BookRepository bookRepository;
    private final AuthorRepository authorRepository;
    private final GenreRepository genreRepository;
    private final CountDownLatch latch;
    private volatile boolean running = true;

    public BookImportWorker(BlockingQueue<List<Book>> queue,
                            DatabaseManager databaseManager,
                            BookRepository bookRepository,
                            AuthorRepository authorRepository,
                            GenreRepository genreRepository,
                            CountDownLatch latch) {
        this.queue = queue;
        this.databaseManager = databaseManager;
        this.bookRepository = bookRepository;
        this.authorRepository = authorRepository;
        this.genreRepository = genreRepository;
        this.latch = latch;
    }

    public void stop() {
        this.running = false;
    }

    @Override
    public void run() {
        try {
            while (running || !queue.isEmpty()) {
                List<Book> batch = queue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (batch != null && !batch.isEmpty()) {
                    processBatchWithTransaction(batch);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[WORKER] Потік імпорту перервано.");
        } finally {
            latch.countDown();
            System.out.println("[WORKER] Фоновий воркер успішно завершив роботу.");
        }
    }

    /**
     * Пакетне збереження масиву книг у межах єдиної ACID транзакції SQLite.
     * Захищає потік від падінь при виникненні SQLException на будь-якому етапі.
     */
    private void processBatchWithTransaction(List<Book> batch) {
        Connection conn = null;
        try {
            // Отримуємо з'єднання безпечно
            conn = databaseManager.getConnection();
            conn.setAutoCommit(false); // Початок транзакції

            for (Book book : batch) {
                // Зберігаємо основну інформацію про книгу
                bookRepository.save(book);

                // Зберігаємо зв'язаних авторів
                if (book.authors() != null) {
                    for (Author author : book.authors()) {
                        authorRepository.save(author);
                        // Якщо у вашій структурі є таблиця зв'язку book_authors,
                        // тут можна викликати метод збереження мапінгу
                    }
                }

                // Зберігаємо жанри книги
                if (book.genres() != null) {
                    for (String genre : book.genres()) {
                        genreRepository.save(genre);
                    }
                }
            }

            conn.commit(); // Фіксуємо транзакцію на диску

        } catch (SQLException e) {
            System.err.println("[WORKER ERR] Критична помилка транзакції пакету: " + e.getMessage());
            if (conn != null) {
                try {
                    conn.rollback(); // Відкат при збої
                    System.err.println("[WORKER] Транзакцію успішно скасовано.");
                } catch (SQLException ex) {
                    System.err.println("[WORKER ERR] Не вдалося виконати rollback: " + ex.getMessage());
                }
            }
        } finally {
            if (conn != null) {
                try {
                    conn.close(); // Обов'язкове повернення з'єднання в пул / закриття
                } catch (SQLException e) {
                    System.err.println("[WORKER ERR] Помилка закриття з'єднання: " + e.getMessage());
                }
            }
        }
    }
}