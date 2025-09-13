package raj.cbm.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/** Tiny CSV append helper (UTF-8) compatible with Java 8. */
public final class CsvUtils {
    private CsvUtils(){}

    public static synchronized void appendLine(File f, String line) throws Exception {
        File parent = f.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        // Java 8 safe: FileOutputStream(append=true) + OutputStreamWriter(UTF-8)
        try (BufferedWriter bw = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(f, true), StandardCharsets.UTF_8))) {
            bw.write(line);
            bw.newLine();
        }
    }
}
