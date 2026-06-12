package org.myhomelib.service;

import org.myhomelib.importer.Fb2Importer;
import org.myhomelib.db.repository.BookRepository;
import org.myhomelib.model.Book;
import org.myhomelib.model.Fb2Book;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

public class ImportService {

    private final Fb2Importer fb2Importer;
    private final BookRepository bookRepository;

    public ImportService(Fb2Importer fb2Importer, BookRepository bookRepository) {
        this.fb2Importer = fb2Importer;
        this.bookRepository = bookRepository;
    }

    /**
     * Імпорт книги безпосередньо з локального одиночного файлу FB2.
     */
    public Book importBookFromPath(Path filePath) {
        if (filePath == null || !Files.exists(filePath)) {
            throw new IllegalArgumentException("Файл для імпорту не існує або шлях порожній");
        }

        try (InputStream inputStream = Files.newInputStream(filePath)) {
            long fileSize = Files.size(filePath);
            return importBook(inputStream, filePath, "", fileSize);
        } catch (Exception e) {
            throw new RuntimeException("Помилка під час читання файлу для імпорту: " + filePath.getFileName(), e);
        }
    }

    /**
     * Центральний Enterprise-метод імпорту, що приймає InputStream.
     */
    public Book importBook(InputStream inputStream, Path sourcePath, String archiveEntry, long fileSize) {
        if (inputStream == null) {
            throw new IllegalArgumentException("Потік даних InputStream не може бути null");
        }

        Fb2Book fb2Book = fb2Importer.parseFb2(inputStream, sourcePath, archiveEntry, fileSize);
        long generatedId = System.nanoTime();

        Book book = new Book(
                generatedId,
                fb2Book.title(),
                fb2Book.authors(),
                fb2Book.genres(),
                fb2Book.series(),
                fb2Book.sequenceNumber(),
                (fb2Book.archiveEntry() != null && !fb2Book.archiveEntry().isEmpty()) ? fb2Book.archiveEntry() : fb2Book.sourcePath().getFileName().toString(),
                fb2Book.sourcePath().getParent() != null ? fb2Book.sourcePath().getParent().toString() : "",
                fb2Book.archiveEntry() != null ? fb2Book.archiveEntry() : "",
                fb2Book.language(),
                fb2Book.fileSize(),
                fb2Book.keywords(),
                fb2Book.annotation(),
                0,
                0,
                LocalDateTime.now()
        );

        bookRepository.saveBook(book);
        return book;
    }

    /**
     * Імпорт жанрів з INPX структури (Тимчасова Enterprise заглушка для успішної компіляції Фази 1).
     */
    public void importGenres(Path path, String encoding) {
        // Логіка буде розширена під час рефакторингу шару жанрів (GenreRepository)
        System.out.println("[INFO] Запуск імпорту жанрів з файлу: " + path + " (Кодування: " + encoding + ")");
    }
}