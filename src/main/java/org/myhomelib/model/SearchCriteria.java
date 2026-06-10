package org.myhomelib.model;

import java.time.LocalDateTime;

public record SearchCriteria(
        String title,
        String author,
        String genre,
        String series,
        String language,

        Integer rateFrom,
        Integer rateTo,

        Integer progressFrom,
        Integer progressTo,

        Long sizeFrom,
        Long sizeTo,

        LocalDateTime updatedAfter,
        LocalDateTime updatedBefore,

        String keywords,
        String annotation,

        String group
) {
    public static SearchCriteria empty() {
        return new SearchCriteria(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}