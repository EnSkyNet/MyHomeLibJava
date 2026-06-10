package org.myhomelib.util;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

public final class ZipFiles {
    private static final List<Charset> ZIP_NAME_CHARSETS = List.of(
            StandardCharsets.UTF_8,
            Charset.forName("CP866"),
            Charset.forName("windows-1251"),
            StandardCharsets.ISO_8859_1
    );

    private ZipFiles() {
    }

    public static ZipFile open(Path file) throws IOException {
        IOException last = null;
        for (Charset charset : ZIP_NAME_CHARSETS) {
            try {
                return new ZipFile(file.toFile(), charset);
            } catch (IOException e) {
                last = e;
            }
        }
        throw last == null ? new IOException("Cannot open ZIP: " + file) : last;
    }

    public static List<Charset> nameCharsets() {
        return ZIP_NAME_CHARSETS;
    }
}
