package org.myhomelib.importer;

import org.myhomelib.db.Database;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class GenreListImporter {
    public List<Database.GenreImport> importFile(Path file) throws IOException {
        String text = readText(file);
        List<Database.GenreImport> genres = new ArrayList<>();
        for (String rawLine : text.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            Database.GenreImport genre = parseLine(line);
            if (genre != null) {
                genres.add(genre);
            }
        }
        return genres;
    }

    private static Database.GenreImport parseLine(String line) {
        int firstSpace = firstWhitespace(line);
        if (firstSpace <= 0 || firstSpace == line.length() - 1) {
            return null;
        }
        String code = line.substring(0, firstSpace).trim();
        String rest = line.substring(firstSpace + 1).trim();
        String fb2Code = "";
        String alias = rest;
        int delimiter = rest.indexOf(';');
        if (delimiter >= 0) {
            fb2Code = rest.substring(0, delimiter).trim();
            alias = rest.substring(delimiter + 1).trim();
        }
        if (alias.isBlank()) {
            return null;
        }
        return new Database.GenreImport(code, parentCode(code), fb2Code, alias);
    }

    private static String readText(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        try {
            return stripBom(decodeStrict(bytes, StandardCharsets.UTF_8));
        } catch (CharacterCodingException ignored) {
            return stripBom(new String(bytes, Charset.forName("windows-1251")));
        }
    }

    private static String decodeStrict(byte[] bytes, Charset charset) throws CharacterCodingException {
        return charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes))
                .toString();
    }

    private static String parentCode(String code) {
        int dot = code.lastIndexOf('.');
        return dot <= 0 ? "" : code.substring(0, dot);
    }

    private static int firstWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static String stripBom(String value) {
        if (value != null && !value.isEmpty() && value.charAt(0) == '\uFEFF') {
            return value.substring(1);
        }
        return value;
    }
}
