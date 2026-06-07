package org.myhomelib.model;

import java.util.List;

public record BookEdit(
        String title,
        List<Author> authors,
        List<String> genres,
        String series,
        Integer sequenceNumber,
        String language,
        String keywords,
        String annotation,
        int rate,
        int progress
) {
}
