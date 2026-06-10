package org.myhomelib.importer;

import org.myhomelib.model.Author;
import org.myhomelib.model.Fb2Book;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class InpxImporter {
    private static final char FIELD_DELIMITER = 4;
    private static final char ITEM_DELIMITER = ':';
    private static final char SUBITEM_DELIMITER = ',';
    private static final String STRUCTURE_INFO = "structure.info";
    private static final String DEFAULT_STRUCTURE =
            "AUTHOR;GENRE;TITLE;SERIES;SERNO;FILE;SIZE;LIBID;DEL;EXT;DATE;LANG;LIBRATE;KEYWORDS";

    // Тепер метод повертає void і приймає onBookParsed для миттєвої обробки кожної книги
    public void importFile(Path inpxFile, Consumer<Fb2Book> onBookParsed, Consumer<String> status) throws IOException {
        int totalParsed = 0;

        try (ZipFile zip = new ZipFile(inpxFile.toFile())) {
            List<Field> fields = fields(readStructure(zip));
            List<? extends ZipEntry> entries = zip.stream()
                    .filter(entry -> !entry.isDirectory())
                    .filter(entry -> entry.getName().toLowerCase(Locale.ROOT).endsWith(".inp"))
                    .sorted(Comparator.comparing(ZipEntry::getName, String.CASE_INSENSITIVE_ORDER))
                    .toList();

            for (ZipEntry entry : entries) {
                status.accept("Importing INPX: " + entry.getName() + " (Total parsed: " + totalParsed + ")");

                try (InputStream is = zip.getInputStream(entry);
                     InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
                     BufferedReader reader = new BufferedReader(isr)) {

                    String line;
                    int lineNumber = 0;
                    boolean firstLine = true;

                    while ((line = reader.readLine()) != null) {
                        if (firstLine) {
                            line = stripBom(line);
                            firstLine = false;
                        }
                        if (!line.isBlank()) {
                            Fb2Book book = parseLine(line, fields, inpxFile, entry.getName(), lineNumber);

                            onBookParsed.accept(book);
                            totalParsed++;

                            // Оновлюємо статус рідше — синхронно з новим розміром батчу
                            if (totalParsed % 20000 == 0) {
                                status.accept("Importing INPX: " + entry.getName() + " (Total parsed: " + totalParsed + ")");
                            }
                        }
                        lineNumber++;
                    }
                }
            }
            status.accept("Import completed! Total books processed: " + totalParsed);
        }
    }

    private static String readStructure(ZipFile zip) throws IOException {
        ZipEntry structure = zip.getEntry(STRUCTURE_INFO);
        if (structure == null) {
            return DEFAULT_STRUCTURE;
        }
        try (InputStream is = zip.getInputStream(structure)) {
            return stripBom(new String(is.readAllBytes(), StandardCharsets.UTF_8)).trim();
        }
    }

    private static List<Field> fields(String structure) {
        List<Field> result = new ArrayList<>();
        for (String token : structure.split(";")) {
            result.add(Field.fromCode(stripBom(token).trim()));
        }
        return result;
    }

    private static Fb2Book parseLine(String line, List<Field> fields, Path inpxFile, String inpEntry, int lineNumber) {
        String[] values = split(line, FIELD_DELIMITER);
        Fb2Book book = new Fb2Book();
        book.sourcePath(inpxFile);
        book.archiveEntry(inpEntry + "#" + (lineNumber + 1));

        String fileName = "";
        String ext = "";
        int max = Math.min(values.length, fields.size());
        for (int i = 0; i < max; i++) {
            String value = values[i].trim();
            switch (fields.get(i)) {
                case AUTHOR -> book.authors().addAll(authors(value));
                case TITLE -> book.title(value);
                case SERIES -> book.series(value);
                case SERNO -> book.sequenceNumber(parseInteger(value));
                case GENRE -> book.genres().addAll(genres(value));
                case FILE -> fileName = cleanFilePart(value);
                case EXT -> ext = normalizeExtension(value);
                case SIZE -> book.fileSize(parseLong(value));
                case LANG -> book.language(value);
                case KEYWORDS -> book.keywords(value);
                default -> {
                }
            }
        }

        if (book.title().isBlank()) {
            book.title(fileName.isBlank() ? "Unknown title" : fileName + ext);
        }
        if (book.authors().isEmpty()) {
            book.authors().add(new Author(0, "", "", "Unknown"));
        }
        return book;
    }

    private static List<Author> authors(String value) {
        List<Author> result = new ArrayList<>();
        for (String item : split(value, ITEM_DELIMITER)) {
            if (item.isBlank()) {
                continue;
            }
            String[] parts = split(item, SUBITEM_DELIMITER);
            String lastName = parts.length > 0 ? parts[0].trim() : "";
            String firstName = parts.length > 1 ? parts[1].trim() : "";
            String middleName = parts.length > 2 ? parts[2].trim() : "";
            result.add(new Author(0, firstName, middleName, lastName));
        }
        return result;
    }

    private static List<String> genres(String value) {
        List<String> result = new ArrayList<>();
        for (String item : split(value, ITEM_DELIMITER)) {
            if (!item.isBlank()) {
                result.add(item.trim());
            }
        }
        return result;
    }

    private static String[] split(String value, char delimiter) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == delimiter) {
                parts.add(value.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(value.substring(start));
        return parts.toArray(String[]::new);
    }

    private static String cleanFilePart(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        char[] chars = value.toCharArray();
        boolean changed = false;
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c == '<' || c == '>' || c == ':' || c == '"' || c == '/'
                    || c == '\\' || c == '|' || c == '*' || c == '?') {
                chars[i] = ' ';
                changed = true;
            }
        }
        return changed ? new String(chars).trim() : value.trim();
    }

    private static String normalizeExtension(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String ext = value.trim();
        return ext.startsWith(".") ? ext : "." + ext;
    }

    private static Integer parseInteger(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static String stripBom(String value) {
        if (value != null && !value.isEmpty() && value.charAt(0) == '\uFEFF') {
            return value.substring(1);
        }
        return value;
    }

    private enum Field {
        NONE, AUTHOR, TITLE, SERIES, SERNO, GENRE, LIBID, INSNO, FILE, FOLDER, EXT, SIZE, LANG, DATE, CODE, DEL, RATE, URI, LIBRATE, KEYWORDS;

        static Field fromCode(String code) {
            return switch (code.toUpperCase(Locale.ROOT)) {
                case "AUTHOR" -> AUTHOR;
                case "TITLE" -> TITLE;
                case "SERIES" -> SERIES;
                case "SERNO" -> SERNO;
                case "GENRE" -> GENRE;
                case "LIBID" -> LIBID;
                case "INSNO" -> INSNO;
                case "FILE" -> FILE;
                case "FOLDER" -> FOLDER;
                case "EXT" -> EXT;
                case "SIZE" -> SIZE;
                case "LANG" -> LANG;
                case "DATE" -> DATE;
                case "CODE" -> CODE;
                case "DEL" -> DEL;
                case "RATE" -> RATE;
                case "URI", "URL" -> URI;
                case "LIBRATE" -> LIBRATE;
                case "KEYWORDS" -> KEYWORDS;
                default -> NONE;
            };
        }
    }
}