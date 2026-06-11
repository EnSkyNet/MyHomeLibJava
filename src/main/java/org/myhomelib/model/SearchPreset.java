package org.myhomelib.model;

public record SearchPreset(
        long presetId,
        String name,
        SearchCriteria criteria
) {
    public SearchPreset {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Назва пресету пошуку не може бути порожньою");
        }
        if (criteria == null) {
            criteria = SearchCriteria.empty();
        }
    }

    // Сумісність із кодом, який викликає p.id() замість p.presetId()
    public long id() {
        return presetId;
    }
}