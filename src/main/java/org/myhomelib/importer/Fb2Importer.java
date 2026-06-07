package org.myhomelib.importer;

import org.myhomelib.model.Author;
import org.myhomelib.model.Fb2Book;
import org.myhomelib.util.ZipFiles;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class Fb2Importer {
    public List<Fb2Book> importFolder(Path folder) {
        return importFolder(folder, ignored -> {
        });
    }

    public List<Fb2Book> importFolder(Path folder, Consumer<String> status) {
        return importFolder(folder, status, true, true);
    }

    public List<Fb2Book> importFolder(Path folder, Consumer<String> status, boolean readFb2, boolean readZip) {
        List<Fb2Book> books = new ArrayList<>();
        if (Files.isRegularFile(folder)) {
            scanFile(folder, books, status, readFb2, readZip);
            return books;
        }
        try (var paths = Files.walk(folder)) {
            paths.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .forEach(path -> scanFile(path, books, status, readFb2, readZip));
            return books;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot scan folder: " + folder, e);
        }
    }

    public Fb2Book importFile(Path file) {
        try {
            return parse(Files.readAllBytes(file), file, "", fallbackTitle(file), Files.size(file));
        } catch (Exception e) {
            return fallback(file, "", 0);
        }
    }

    private void scanFile(Path file, List<Fb2Book> books, Consumer<String> status, boolean readFb2, boolean readZip) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (readFb2 && (name.endsWith(".fb2") || name.endsWith(".fbd"))) {
            status.accept("Scanning: " + file.getFileName());
            books.add(importFile(file));
        } else if (readZip && name.endsWith(".zip")) {
            scanZip(file, books, status);
        }
    }

    private void scanZip(Path file, List<Fb2Book> books, Consumer<String> status) {
        status.accept("Scanning archive: " + file.getFileName());
        try (ZipFile zip = ZipFiles.open(file)) {
            List<? extends ZipEntry> entries = zip.stream()
                    .filter(entry -> !entry.isDirectory())
                    .filter(Fb2Importer::isBookEntry)
                    .sorted(Comparator.comparing(ZipEntry::getName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            if (entries.isEmpty()) {
                scanZipByContent(file, zip, books, status);
                return;
            }

            for (ZipEntry entry : entries) {
                try (InputStream input = zip.getInputStream(entry)) {
                    books.add(parse(input.readAllBytes(), file, entry.getName(), fallbackTitle(Path.of(entry.getName())), entry.getSize()));
                } catch (Exception e) {
                    status.accept("Cannot read archive entry: " + entry.getName());
                    books.add(fallback(file, entry.getName(), entry.getSize()));
                }
            }
        } catch (Exception e) {
            status.accept("Cannot read ZIP: " + file.getFileName());
        }
    }

    private void scanZipByContent(Path file, ZipFile zip, List<Fb2Book> books, Consumer<String> status) {
        List<? extends ZipEntry> entries = zip.stream()
                .filter(entry -> !entry.isDirectory())
                .sorted(Comparator.comparing(ZipEntry::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        for (ZipEntry entry : entries) {
            try (InputStream input = zip.getInputStream(entry)) {
                byte[] data = input.readAllBytes();
                if (!looksLikeFb2(data)) {
                    continue;
                }
                books.add(parse(data, file, entry.getName(), fallbackTitle(Path.of(entry.getName())), entry.getSize()));
            } catch (Exception e) {
                status.accept("Cannot inspect archive entry: " + entry.getName());
            }
        }
        if (entries.isEmpty()) {
            status.accept("ZIP archive is empty: " + file.getFileName());
        }
    }

    private static Fb2Book parse(byte[] data, Path source, String archiveEntry, String fallbackTitle, long fileSize) {
        Fb2Book book = new Fb2Book();
        book.sourcePath(source);
        book.archiveEntry(archiveEntry);
        book.fileSize(Math.max(fileSize, 0));

        Element titleInfo = readTitleInfo(data);
        if (titleInfo == null) {
            book.title(fallbackTitle);
            book.authors().add(new Author(0, "", "", "Unknown"));
            return book;
        }

        book.title(textOf(titleInfo, "book-title", fallbackTitle));
        book.language(textOf(titleInfo, "lang", ""));
        book.keywords(textOf(titleInfo, "keywords", ""));
        book.annotation(textOf(titleInfo, "annotation", ""));
        parseAuthors(titleInfo, book);
        parseGenres(titleInfo, book);
        parseSequence(titleInfo, book);
        if (book.authors().isEmpty()) {
            book.authors().add(new Author(0, "", "", "Unknown"));
        }
        return book;
    }

    private static Element readTitleInfo(byte[] data) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new SilentXmlErrorHandler());
            Document document = builder.parse(new InputSource(new ByteArrayInputStream(data)));
            NodeList nodes = elementsByName(document, "title-info");
            return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void parseAuthors(Element titleInfo, Fb2Book book) {
        NodeList nodes = elementsByName(titleInfo, "author");
        for (int i = 0; i < nodes.getLength(); i++) {
            if (!(nodes.item(i) instanceof Element author)) {
                continue;
            }
            String firstName = textOf(author, "first-name", "");
            String middleName = textOf(author, "middle-name", "");
            String lastName = textOf(author, "last-name", "");
            if (lastName.isBlank() && firstName.isBlank() && middleName.isBlank()) {
                lastName = textOf(author, "nickname", "");
            }
            if (!lastName.isBlank() || !firstName.isBlank() || !middleName.isBlank()) {
                book.authors().add(new Author(0, firstName, middleName, lastName));
            }
        }
    }

    private static void parseGenres(Element titleInfo, Fb2Book book) {
        NodeList nodes = elementsByName(titleInfo, "genre");
        for (int i = 0; i < nodes.getLength(); i++) {
            String genre = nodes.item(i).getTextContent();
            if (genre != null && !genre.isBlank()) {
                book.genres().add(genre.trim());
            }
        }
    }

    private static void parseSequence(Element titleInfo, Fb2Book book) {
        NodeList nodes = elementsByName(titleInfo, "sequence");
        if (nodes.getLength() == 0 || !(nodes.item(0) instanceof Element sequence)) {
            return;
        }
        book.series(sequence.getAttribute("name"));
        String number = sequence.getAttribute("number");
        if (!number.isBlank()) {
            try {
                book.sequenceNumber((int) Math.round(Double.parseDouble(number)));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private static String textOf(Element parent, String tag, String fallback) {
        NodeList nodes = elementsByName(parent, tag);
        if (nodes.getLength() == 0) {
            return fallback;
        }
        Node node = nodes.item(0);
        String text = node.getTextContent();
        return text == null || text.isBlank() ? fallback : text.trim().replaceAll("\\s+", " ");
    }

    private static NodeList elementsByName(Document document, String tagName) {
        NodeList nodes = document.getElementsByTagNameNS("*", tagName);
        return nodes.getLength() > 0 ? nodes : document.getElementsByTagName(tagName);
    }

    private static NodeList elementsByName(Element element, String tagName) {
        NodeList nodes = element.getElementsByTagNameNS("*", tagName);
        return nodes.getLength() > 0 ? nodes : element.getElementsByTagName(tagName);
    }

    private static boolean isBookEntry(ZipEntry entry) {
        String name = entry.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".fb2") || name.endsWith(".fbd");
    }

    private static boolean looksLikeFb2(byte[] data) {
        if (data == null || data.length == 0) {
            return false;
        }
        int max = Math.min(data.length, 8192);
        String head = new String(data, 0, max, java.nio.charset.StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        return head.contains("<fictionbook") || head.contains("<title-info");
    }

    private static Fb2Book fallback(Path source, String archiveEntry, long fileSize) {
        Fb2Book book = new Fb2Book();
        book.sourcePath(source);
        book.archiveEntry(archiveEntry);
        book.fileSize(Math.max(fileSize, 0));
        book.title(archiveEntry == null || archiveEntry.isBlank() ? fallbackTitle(source) : fallbackTitle(Path.of(archiveEntry)));
        book.authors().add(new Author(0, "", "", "Unknown"));
        return book;
    }

    private static String fallbackTitle(Path path) {
        String fileName = path.getFileName() == null ? "Unknown title" : path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot <= 0 ? fileName : fileName.substring(0, dot);
    }

    private static class SilentXmlErrorHandler implements ErrorHandler {
        @Override
        public void warning(SAXParseException exception) {
        }

        @Override
        public void error(SAXParseException exception) {
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXParseException {
            throw exception;
        }
    }
}
