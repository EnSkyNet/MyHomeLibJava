package org.myhomelib.importer;

import org.myhomelib.model.Author;
import org.myhomelib.model.Fb2Book;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class InpxImporter {

    public List<Fb2Book> parseInpFolder(InputStream inpFileStream, Path parentArchivePath) {
        List<Fb2Book> list = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inpFileStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;

                // Рядок опису книги в INPX зазвичай розділений символом \u0004 або pipe |
                String[] parts = line.split("[\u0004|]");
                if (parts.length < 8) continue;

                // Парсинг авторів (формат: Прізвище,Ім'я,По-батькові:Прізвище2,Ім'я2;)
                List<Author> authors = new ArrayList<>();
                String rawAuthors = parts[0];
                if (!rawAuthors.isBlank()) {
                    String[] authorTokens = rawAuthors.split(":");
                    for (String token : authorTokens) {
                        String[] nameParts = token.split(",");
                        String lastName = nameParts.length > 0 ? nameParts[0].trim() : "Невідомий";
                        String firstName = nameParts.length > 1 ? nameParts[1].trim() : "";
                        String middleName = nameParts.length > 2 ? nameParts[2].trim() : "";
                        authors.add(new Author(0, firstName, middleName, lastName));
                    }
                }

                // Жанри (розділені двокрапкою чи комою)
                List<String> genres = Arrays.asList(parts[1].split(":"));
                String title = parts[2].trim();
                String series = parts[3].trim();

                Integer sequenceNumber = 0;
                if (!parts[4].isBlank()) {
                    try { sequenceNumber = Integer.parseInt(parts[4].trim()); } catch (NumberFormatException ignored) {}
                }

                String archiveEntry = parts[5].trim() + ".fb2"; // внутрішнє ім'я файлу в zip

                long fileSize = 0;
                if (!parts[6].isBlank()) {
                    try { fileSize = Long.parseLong(parts[6].trim()); } catch (NumberFormatException ignored) {}
                }

                String language = parts.length > 11 ? parts[11].trim() : "ru";
                String keywords = parts.length > 12 ? parts[12].trim() : "";

                // Збирання об'єкта через канонічний конструктор
                Fb2Book parsed = new Fb2Book(
                        title, authors, genres, series, sequenceNumber, language,
                        parentArchivePath, archiveEntry, fileSize, keywords, ""
                );
                list.add(parsed);
            }
        } catch (Exception e) {
            System.err.println("Помилка обробки запису INPX: " + e.getMessage());
        }
        return list;
    }
}