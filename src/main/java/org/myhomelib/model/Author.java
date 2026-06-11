package org.myhomelib.model;

import java.util.Objects;

public final class Author {
    private final long id;
    private final String firstName;
    private final String middleName;
    private final String lastName;

    public Author(long id, String firstName, String middleName, String lastName) {
        this.id = id;
        this.firstName = firstName != null ? firstName.trim() : "";
        this.middleName = middleName != null ? middleName.trim() : "";
        this.lastName = lastName != null ? lastName.trim() : "Невідомий Автор";
    }

    public long id() { return id; }
    public String firstName() { return firstName; }
    public String middleName() { return middleName; }
    public String lastName() { return lastName; }

    public String displayFullName() {
        StringBuilder sb = new StringBuilder(lastName);
        if (!firstName.isEmpty()) {
            sb.append(" ").append(firstName);
        }
        if (!middleName.isEmpty()) {
            sb.append(" ").append(middleName);
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Author author = (Author) o;
        return Objects.equals(firstName.toLowerCase(), author.firstName.toLowerCase()) &&
                Objects.equals(middleName.toLowerCase(), author.middleName.toLowerCase()) &&
                Objects.equals(lastName.toLowerCase(), author.lastName.toLowerCase());
    }

    @Override
    public int hashCode() {
        return Objects.hash(firstName.toLowerCase(), middleName.toLowerCase(), lastName.toLowerCase());
    }

    @Override
    public String toString() {
        return displayFullName();
    }
}