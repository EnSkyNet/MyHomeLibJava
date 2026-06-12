package org.myhomelib.importer;

import org.myhomelib.model.Author;
import org.myhomelib.model.Fb2Book;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

/**
 * Високопродуктивний Enterprise-парсер FB2 метаданих на базі архітектури StAX.
 */
public final class Fb2Importer {

    public Fb2Book parseFb2(InputStream inputStream, Path sourcePath, String archiveEntry, long fileSize) {
        String title = "Без назви";
        List<Author> authors = new ArrayList<>();
        List<String> genres = new ArrayList<>();
        String series = "";
        Integer sequenceNumber = 0;
        String language = "ru";
        String keywords = "";
        StringBuilder annotationBuilder = new StringBuilder();

        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            // Комплексний захист від XXE (XML External Entity) та збоїв пам'яті (Entity Expansion Injection)
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);

            XMLStreamReader reader = factory.createXMLStreamReader(inputStream);

            String currentElement = "";
            String fName = "";
            String mName = "";
            String lName = "";

            boolean inTitleInfo = false;
            boolean inAnnotation = false;

            while (reader.hasNext()) {
                int event = reader.next();

                switch (event) {
                    case XMLStreamConstants.START_ELEMENT:
                        currentElement = reader.getLocalName();

                        if ("title-info".equals(currentElement)) {
                            inTitleInfo = true;
                        } else if ("annotation".equals(currentElement) && inTitleInfo) {
                            inAnnotation = true;
                        } else if ("author".equals(currentElement) && inTitleInfo) {
                            fName = "";
                            mName = "";
                            lName = "";
                        } else if ("sequence".equals(currentElement) && inTitleInfo) {
                            String nameAttr = reader.getAttributeValue(null, "name");
                            if (nameAttr != null && !nameAttr.isBlank()) {
                                series = nameAttr.trim();
                            }
                            String numberAttr = reader.getAttributeValue(null, "number");
                            if (numberAttr != null) {
                                try {
                                    sequenceNumber = Integer.parseInt(numberAttr.trim());
                                } catch (NumberFormatException e) {
                                    sequenceNumber = 0;
                                }
                            }
                        }
                        break;

                    case XMLStreamConstants.CHARACTERS:
                        String text = reader.getText();
                        if (text == null || text.isEmpty()) {
                            break;
                        }

                        if (inTitleInfo) {
                            if (inAnnotation) {
                                // Збираємо анотацію, ігноруючи внутрішні теги форматування <p>, <v>, <strong>
                                annotationBuilder.append(text);
                            } else {
                                String trimmedText = text.trim();
                                if (trimmedText.isEmpty()) {
                                    break;
                                }

                                switch (currentElement) {
                                    case "book-title":
                                        title = trimmedText;
                                        break;
                                    case "first-name":
                                        fName = trimmedText;
                                        break;
                                    case "middle-name":
                                        mName = trimmedText;
                                        break;
                                    case "last-name":
                                        lName = trimmedText;
                                        break;
                                    case "genre":
                                        if (!genres.contains(trimmedText)) {
                                            genres.add(trimmedText);
                                        }
                                        break;
                                    case "lang":
                                        language = trimmedText.toLowerCase();
                                        break;
                                    case "keywords":
                                        keywords = trimmedText;
                                        break;
                                }
                            }
                        }
                        break;

                    case XMLStreamConstants.END_ELEMENT:
                        String endElement = reader.getLocalName();

                        if ("title-info".equals(endElement)) {
                            inTitleInfo = false;
                            // Оскільки секція title-info закінчилася, ми зібрали всі ключові метадані.
                            // Для прискорення обробки 800к книг, робимо достроковий вихід з циклу (Early Exit).
                            // Нам не потрібно читати мегабайти самого тексту книги, що лежить нижче в тегу <body>.
                            break;
                        } else if ("annotation".equals(endElement)) {
                            inAnnotation = false;
                        } else if ("author".equals(endElement) && inTitleInfo) {
                            if (!lName.isEmpty() || !fName.isEmpty()) {
                                authors.add(new Author(0, fName, mName, lName));
                            }
                        }

                        // Скидаємо поточний елемент лише якщо ми не всередині анотації,
                        // щоб не втратити контекст текстових блоків
                        if (!inAnnotation) {
                            currentElement = "";
                        }
                        break;
                }

                // Перериваємо парсинг документа, якщо блок заголовків уже прочитано
                if (!inTitleInfo && event == XMLStreamConstants.END_ELEMENT && "title-info".equals(reader.getLocalName())) {
                    break;
                }
            }
            reader.close();
        } catch (Exception e) {
            // Фолбек: якщо структура XML пошкоджена, використовуємо ім'я файлу або запису з архіву
            if (archiveEntry != null && !archiveEntry.isEmpty()) {
                title = archiveEntry;
                if (title.contains("/")) {
                    title = title.substring(title.lastIndexOf('/') + 1);
                }
            } else if (sourcePath != null) {
                title = sourcePath.getFileName().toString();
            }
            title = title.replace(".fb2", "");
        }

        // Гарантована фінальна перевірка, щоб уникнути порожніх полів у базі
        if (title == null || title.isBlank()) {
            title = (archiveEntry != null && !archiveEntry.isEmpty()) ? archiveEntry : sourcePath.getFileName().toString();
        }

        // Повертаємо новий об'єкт через повний конструктор рекорду
        return new Fb2Book(
                title,
                authors,
                genres,
                series,
                sequenceNumber,
                language,
                sourcePath,
                archiveEntry,
                fileSize,
                keywords,
                annotationBuilder.toString().replaceAll("\\s+", " ").trim()
        );
    }
}