package org.myhomelib.reader;

import org.myhomelib.model.Book;
import org.myhomelib.util.ZipFiles;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class BookContentReader {
    public String readHtml(Book book) throws Exception {
        byte[] data = readBytes(book);
        Document document = parse(data);
        String title = textOfFirst(document, "book-title", book.title());
        List<String> paragraphs = bodyParagraphs(document);
        if (paragraphs.isEmpty()) {
            paragraphs.add("No readable body text found.");
        }

        StringBuilder html = new StringBuilder();
        html.append("""
                <html>
                <body style="font-family: Georgia, 'Times New Roman', serif; background: #f4f1ea; color: #1f2933; line-height: 1.58; padding: 34px; max-width: 900px; margin: 0 auto;">
                """);
        html.append("<h1 style=\"font-family: Segoe UI, Arial, sans-serif; font-size: 30px; line-height: 1.2;\">")
                .append(escape(title))
                .append("</h1>");
        html.append("<p style=\"font-family: Segoe UI, Arial, sans-serif; color: #5c6670;\"><b>")
                .append(escape(book.authorsText()))
                .append("</b></p>");
        for (String paragraph : paragraphs) {
            html.append("<p>").append(escape(paragraph)).append("</p>");
        }
        html.append("</body></html>");
        return html.toString();
    }

    public byte[] readBytes(Book book) throws IOException {
        if (!book.hasArchiveEntry()) {
            return Files.readAllBytes(book.filePath());
        }
        String entry = book.archiveEntry();
        if (entry.contains("#")) {
            throw new IOException("This catalog entry points to INPX metadata, not a local book file.");
        }
        try (ZipFile zip = ZipFiles.open(book.filePath())) {
            ZipEntry zipEntry = zip.getEntry(entry);
            if (zipEntry == null) {
                zipEntry = firstBookEntry(zip);
            }
            if (zipEntry == null) {
                throw new IOException("No FB2/FBD entry found in archive: " + book.filePath());
            }
            try (InputStream input = zip.getInputStream(zipEntry)) {
                return input.readAllBytes();
            }
        }
    }

    private static ZipEntry firstBookEntry(ZipFile zip) {
        return zip.stream()
                .filter(entry -> !entry.isDirectory())
                .filter(entry -> {
                    String name = entry.getName().toLowerCase(Locale.ROOT);
                    return name.endsWith(".fb2") || name.endsWith(".fbd");
                })
                .findFirst()
                .orElse(null);
    }

    private static Document parse(byte[] data) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new SilentXmlErrorHandler());
        return builder.parse(new InputSource(new ByteArrayInputStream(data)));
    }

    private static List<String> bodyParagraphs(Document document) {
        List<String> paragraphs = new ArrayList<>();
        NodeList bodies = elementsByName(document, "body");
        for (int i = 0; i < bodies.getLength(); i++) {
            Element body = (Element) bodies.item(i);
            NodeList nodes = elementsByName(body, "p");
            for (int j = 0; j < nodes.getLength(); j++) {
                String text = nodes.item(j).getTextContent();
                if (text != null && !text.isBlank()) {
                    paragraphs.add(text.replaceAll("\\s+", " ").trim());
                }
            }
        }
        return paragraphs;
    }

    private static String textOfFirst(Document document, String tagName, String fallback) {
        NodeList nodes = elementsByName(document, tagName);
        if (nodes.getLength() == 0) {
            return fallback;
        }
        String text = nodes.item(0).getTextContent();
        return text == null || text.isBlank() ? fallback : text.trim();
    }

    private static NodeList elementsByName(Document document, String tagName) {
        NodeList nodes = document.getElementsByTagNameNS("*", tagName);
        return nodes.getLength() > 0 ? nodes : document.getElementsByTagName(tagName);
    }

    private static NodeList elementsByName(Element element, String tagName) {
        NodeList nodes = element.getElementsByTagNameNS("*", tagName);
        return nodes.getLength() > 0 ? nodes : element.getElementsByTagName(tagName);
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
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
