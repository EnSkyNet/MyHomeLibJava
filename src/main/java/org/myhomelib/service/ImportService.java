package org.myhomelib.service;

import org.myhomelib.db.repository.BookRepository;
import org.myhomelib.importer.BookImportTask;
import org.myhomelib.importer.GenreListImporter;
import org.myhomelib.service.CollectionManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class ImportService {
    private final BookRepository bookRepository;
    private final CollectionManager collectionManager;

    public ImportService(BookRepository bookRepository, CollectionManager collectionManager) {
        this.bookRepository = bookRepository;
        this.collectionManager = collectionManager;
    }

    public BookImportTask createImportTask(List<File> files) {
        return new BookImportTask(
                files,
                bookRepository,
                collectionManager.collectionDatabase().getConnection()
        );
    }

    public void importGenres(Path filePath, String defaultLang) throws IOException {
        GenreListImporter importer = new GenreListImporter(collectionManager.collectionDatabase());
        importer.importGenresFromFile(filePath, defaultLang);
    }
}