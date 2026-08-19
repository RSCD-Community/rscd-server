package org.rscdaemon.server.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/**
 * conf/server/Conf.xml, read and written back without destroying it.
 *
 * Config reads that file through java.util.Properties, which is fine for
 * reading and useless for writing: Properties.store() discards every comment,
 * reorders every key, and rewrites the file the operator has been editing by
 * hand into something they will not recognise. The config is almost entirely
 * comments explaining what each key does, and that is the useful part of it.
 *
 * So this keeps the file as the list of lines it actually is. Setting a value
 * rewrites the one line carrying that key and leaves everything else exactly
 * as it was; a key that is not there yet is appended.
 *
 * The client has its own copy of this idea for settings.ini. They are separate
 * programs shipped separately -- a shared helper would mean the game server
 * depending on the client jar to start.
 */
public class ConfigFile {
    private final File file;
    private final List<String> lines = new ArrayList<String>();

    private ConfigFile(File file) {
        this.file = file;
    }

    /** Reads the file if it is there; a missing file is an empty one. */
    public static ConfigFile load(File file) {
        ConfigFile config = new ConfigFile(file);
        if (file != null && file.isFile()) {
            try {
                FileInputStream stream = new FileInputStream(file);
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        config.lines.add(line);
                    }
                } finally {
                    stream.close();
                }
            } catch (IOException e) {
                System.err.println("Could not read " + file.getPath() + ": " + e.getMessage());
            }
        }
        return config;
    }

    public String get(String key, String fallback) {
        for (String line : this.lines) {
            String found = keyOf(line);
            if (found != null && found.equals(key)) {
                return valueOf(line);
            }
        }
        return fallback;
    }

    public void set(String key, String value) {
        String replacement = key + " = " + (value == null ? "" : value);
        for (int i = 0; i < this.lines.size(); i++) {
            String found = keyOf(this.lines.get(i));
            if (found != null && found.equals(key)) {
                this.lines.set(i, replacement);
                return;
            }
        }
        this.lines.add(replacement);
    }

    /** Writes the file, reporting whether it worked rather than throwing. */
    public boolean save() {
        if (this.file == null) {
            return false;
        }
        try {
            FileOutputStream stream = new FileOutputStream(this.file);
            try {
                Writer writer = new OutputStreamWriter(stream, "UTF-8");
                for (String line : this.lines) {
                    writer.write(line);
                    writer.write("\n");
                }
                writer.flush();
            } finally {
                stream.close();
            }
            return true;
        } catch (IOException e) {
            System.err.println("Could not write " + this.file.getPath() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * The key a line carries, or null for a blank line or a comment. Matches
     * what Properties accepts, since Properties still reads this same file.
     */
    private static String keyOf(String line) {
        String trimmed = line.trim();
        if (trimmed.length() == 0 || trimmed.charAt(0) == '#' || trimmed.charAt(0) == '!') {
            return null;
        }
        int split = separator(trimmed);
        return split < 0 ? null : trimmed.substring(0, split).trim();
    }

    private static String valueOf(String line) {
        String trimmed = line.trim();
        int split = separator(trimmed);
        return split < 0 ? "" : trimmed.substring(split + 1).trim();
    }

    private static int separator(String trimmed) {
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '=' || c == ':') {
                return i;
            }
        }
        return -1;
    }
}
