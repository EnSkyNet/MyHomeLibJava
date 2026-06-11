package org.myhomelib.importer;

import org.myhomelib.db.BookCollection;
import org.myhomelib.db.Database;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class GenreListImporter {
    private final BookCollection database;

    public GenreListImporter(BookCollection database) {
        this.database = database;
    }

    public void importGenresFromFile(Path filePath, String language) throws IOException {
        List<Database.GenreImport> genresToImport = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.strip().isEmpty() || line.startsWith("#")) {
                    continue; // Пропускаємо порожні рядки та коментарі
                }

                String[] parts = line.split(";");
                if (parts.length >= 2) {
                    String code = parts[0].strip();
                    String name = parts[1].strip();
                    genresToImport.add(new Database.GenreImport(code, name));
                }
            }
        }

        // Передача типізованого колекційного масиву в шар доступу до даних (DAO)
        database.importGenreList(genresToImport, language);
    }
}