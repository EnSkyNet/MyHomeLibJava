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
            // Захист від XXE вразливостей
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);

            XMLStreamReader reader = factory.createXMLStreamReader(inputStream);

            String currentElement = "";
            String fName = "", mName = "", lName = "";
            boolean inTitleInfo = false;
            boolean inAnnotation = false;

            while (reader.hasNext()) {
                int event = reader.next();
                switch (event) {
                    case XMLStreamConstants.START_ELEMENT:
                        currentElement = reader.getLocalName();
                        if ("title-info".equals(currentElement)) {
                            inTitleInfo = true;
                        } else if ("annotation".equals(currentElement)) {
                            inAnnotation = true;
                        } else if ("author".equals(currentElement) && inTitleInfo) {
                            fName = ""; mName = ""; lName = "";
                        } else if ("sequence".equals(currentElement) && inTitleInfo) {
                            String nameAttr = reader.getAttributeValue(null, "name");
                            if (nameAttr != null) series = nameAttr;
                            String numberAttr = reader.getAttributeValue(null, "number");
                            if (numberAttr != null) {
                                try {
                                    sequenceNumber = Integer.parseInt(numberAttr);
                                } catch (NumberFormatException e) {
                                    sequenceNumber = 0;
                                }
                            }
                        }
                        break;

                    case XMLStreamConstants.CHARACTERS:
                        String text = reader.getText().trim();
                        if (text.isEmpty()) break;

                        if (inTitleInfo) {
                            if (inAnnotation) {
                                annotationBuilder.append(text).append(" ");
                            } else if ("book-title".equals(currentElement)) {
                                title = text;
                            } else if ("first-name".equals(currentElement)) {
                                fName = text;
                            } else if ("middle-name".equals(currentElement)) {
                                mName = text;
                            } else if ("last-name".equals(currentElement)) {
                                lName = text;
                            } else if ("genre".equals(currentElement)) {
                                genres.add(text);
                            } else if ("lang".equals(currentElement)) {
                                language = text;
                            } else if ("keywords".equals(currentElement)) {
                                keywords = text;
                            }
                        }
                        break;

                    case XMLStreamConstants.END_ELEMENT:
                        String endElement = reader.getLocalName();
                        if ("title-info".equals(endElement)) {
                            inTitleInfo = false;
                        } else if ("annotation".equals(endElement)) {
                            inAnnotation = false;
                        } else if ("author".equals(endElement) && inTitleInfo) {
                            authors.add(new Author(0, fName, mName, lName));
                        }
                        currentElement = "";
                        break;
                }
            }
            reader.close();
        } catch (Exception e) {
            // Якщо парсинг впав, створюємо об'єкт із назвою файлу/архівного запису
            title = (archiveEntry != null && !archiveEntry.isEmpty()) ? archiveEntry : sourcePath.getFileName().toString();
        }

        // Повертаємо новий об'єкт через повний конструктор рекорду
        return new Fb2Book(
                title, authors, genres, series, sequenceNumber, language,
                sourcePath, archiveEntry, fileSize, keywords, annotationBuilder.toString().trim()
        );
    }
}