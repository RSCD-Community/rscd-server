package org.rscdaemon.server.util;

import java.io.Console;
import java.io.File;
import java.security.SecureRandom;

/**
 * The boot gate for the community registry key.
 *
 * server_key is how the registry knows one server from another. It is never
 * sent to a player and never appears in the world list -- only its SHA-256
 * reaches the registry's table. Two installs sharing a key are one listing,
 * overwriting each other's name, address and player count, which is why the
 * server will not start on the value it ships with.
 *
 * What it does NOT do is make the operator invent one. Everyone running one of
 * these has better things to do than read a paragraph about entropy, so if
 * the config file is writable the key is generated and written for them, printed
 * once, and the server asks them to confirm before it carries on. If the file
 * cannot be written -- read-only mount, wrong owner, running from inside a jar
 * -- it prints the generated value and the one line to paste, and stops.
 *
 * Changing a key that already works is the expensive mistake, so that case is
 * spelled out rather than merely warned about.
 */
public final class ServerKey {
    /** What the distribution ships. Also rejected by the registry itself. */
    public static final String DEFAULT_KEY = "change-me-before-first-boot";

    private static final int KEY_BYTES = 16;

    private ServerKey() {
    }

    /**
     * Called from Server.main() before anything binds a port.
     *
     * @return the key to run with, or null if the server must not start.
     */
    public static String ensure(File configFile) {
        String current = Config.SERVER_KEY == null ? "" : Config.SERVER_KEY.trim();

        if (current.length() >= 16 && !current.equals(DEFAULT_KEY)) {
            return current;
        }

        String generated = generate();

        // Not "is the file missing" -- a file can exist and still be unwritable,
        // and a missing file means the directory has to take the new one.
        boolean writable = configFile != null
                && (configFile.isFile() ? configFile.canWrite()
                                        : configFile.getParentFile() != null && configFile.getParentFile().canWrite());

        if (!writable) {
            printBanner(generated, configFile, false);
            return null;
        }

        ConfigFile file = ConfigFile.load(configFile);
        file.set("server_key", generated);
        if (!file.save()) {
            printBanner(generated, configFile, false);
            return null;
        }

        printBanner(generated, configFile, true);

        /*
         * Confirmation. With a terminal, ask; a keypress is cheaper than a
         * restart and proves someone read the banner. Under a service manager
         * there is no terminal to ask on, so the key is written and the server
         * exits -- the restart the operator performs IS the confirmation, and
         * an init system that restarts it automatically starts cleanly on the
         * second attempt with the key now in place.
         */
        Console console = System.console();
        if (console == null) {
            System.out.println("  No terminal to confirm on. The key above has been saved --");
            System.out.println("  start the server again and it will boot with it.");
            System.out.println();
            return null;
        }

        String answer = console.readLine("  Type yes to start the server with this key: ");
        if (answer != null && answer.trim().equalsIgnoreCase("yes")) {
            System.out.println();
            Config.SERVER_KEY = generated;
            return generated;
        }

        System.out.println();
        System.out.println("  Not confirmed. The key is saved in the config either way -- edit it");
        System.out.println("  there if you want a different one, then start the server again.");
        System.out.println();
        return null;
    }

    /** 32 hex characters from SecureRandom. */
    public static String generate() {
        byte[] bytes = new byte[KEY_BYTES];
        new SecureRandom().nextBytes(bytes);
        StringBuilder key = new StringBuilder(KEY_BYTES * 2);
        for (byte b : bytes) {
            key.append(Character.forDigit((b >> 4) & 0xf, 16));
            key.append(Character.forDigit(b & 0xf, 16));
        }
        return key.toString();
    }

    private static void printBanner(String key, File configFile, boolean saved) {
        String path = configFile == null ? "conf/server/Conf.xml" : configFile.getPath();

        System.out.println();
        System.out.println("  ---------------------------------------------------------------");
        System.out.println("   This server has no server_key of its own.");
        System.out.println();
        System.out.println("   The key identifies your server to the community world list. It");
        System.out.println("   is never shown to players and never leaves your machine except");
        System.out.println("   as a hash. Every install ships with the same placeholder, so a");
        System.out.println("   server running on it would share one listing with every other.");
        System.out.println();

        if (saved) {
            System.out.println("   One has been generated and written to");
            System.out.println("     " + path);
        } else {
            System.out.println("   " + path + " could not be written, so add this line to it");
            System.out.println("   yourself:");
            System.out.println();
            System.out.println("     server_key = " + key);
        }

        System.out.println();
        System.out.println("   Your key:  " + key);
        System.out.println();
        System.out.println("   Keep it. Back it up with your database. If you ever replace a");
        System.out.println("   key that has been in use, the registry treats the result as a");
        System.out.println("   different server, and three things happen:");
        System.out.println();
        System.out.println("     - your old listing stops updating and drops off the list");
        System.out.println("       once it goes stale");
        System.out.println("     - every player who favourited you loses the favourite");
        System.out.println("     - every player re-downloads your cache on next login");
        System.out.println();
        System.out.println("   None of that happens if you keep this file.");
        System.out.println("  ---------------------------------------------------------------");
        System.out.println();
    }
}
