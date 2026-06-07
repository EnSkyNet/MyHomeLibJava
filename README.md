# MyHomeLib Java

Java-port of the Delphi/VCL MyHomeLib project found in `../MyHomeLib-2.5.0`.

This is a working Java/Swing implementation of the main library-management flow:

- create or open an SQLite collection;
- import `.fb2` books from a folder;
- store books, authors, genres, and series;
- browse all books and group by author, series, genre, or custom group;
- search by title, author, genre, group, language, filename, keywords, or annotation;
- view selected book details;
- open a selected book in the operating system's default reader;
- edit book metadata, rating, and reading progress;
- add books to Favorites or custom groups.

The original project is a full Delphi desktop application with custom VCL controls,
download managers, LibRusEc/Flibusta workflows, INPX/FBD import/export, user data,
scripts, device export, and custom SQLite functions/collations. Those advanced
features are represented here as a maintainable Java foundation rather than a
line-by-line automatic translation.

## Requirements

- JDK 21
- Maven 3.9+

## Run

```powershell
cd D:\Lessons\P2J\MyHomeLibJava
mvn exec:java
```

## Build Jar

```powershell
cd D:\Lessons\P2J\MyHomeLibJava
mvn package
java -jar target\myhomelib-java-0.1.0.jar
```

By default the app creates `myhomelib-java.db` in the project directory. Use
`File -> Open collection...` to choose another database file.
