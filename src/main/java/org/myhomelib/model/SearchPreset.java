package org.myhomelib.model;

public record SearchPreset(
        long id,
        String name,
        SearchCriteria criteria
) {
}