package org.myhomelib.model;

public record BookEdit(
        String title,
        Integer sequenceNumber,
        String language,
        String keywords,
        String annotation,
        int rate,
        int progress
) {
    public BookEdit {
        title = title != null ? title.trim() : "";
        language = language != null ? language.trim() : "";
        keywords = keywords != null ? keywords.trim() : "";
        annotation = annotation != null ? annotation.trim() : "";
    }
}