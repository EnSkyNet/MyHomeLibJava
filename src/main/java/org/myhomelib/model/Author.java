package org.myhomelib.model;

public record Author(long id, String firstName, String middleName, String lastName) {
    public String displayName() {
        StringBuilder text = new StringBuilder();
        append(text, lastName);
        append(text, firstName);
        append(text, middleName);
        return text.toString();
    }

    private static void append(StringBuilder text, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!text.isEmpty()) {
            text.append(' ');
        }
        text.append(value.trim());
    }
}
