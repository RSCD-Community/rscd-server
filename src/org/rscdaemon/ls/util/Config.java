/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.ls.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Config {
    public static String RSCDLS_HOME;
    public static String CONF_DIR;
    public static String LOG_DIR;
    public static String MYSQL_HOST;
    public static String MYSQL_DB;
    public static String MYSQL_USER;
    public static String MYSQL_PASS;
    public static String LS_IP;
    public static String QUERY_IP;
    public static int LS_PORT;
    public static int QUERY_PORT;
    public static int MYSQL_PORT;
    /*
     * How the database connection is protected. 0 off, 1 TLS with the server's
     * certificate verified, 2 TLS accepting whatever certificate is presented.
     *
     * Default is 2 rather than 0: MySQL ships a self-signed certificate that no
     * public CA will vouch for, so 1 fails against a stock server, and 0 sends
     * the password and the whole account table in clear. 2 stops a passive
     * eavesdropper, which is the realistic threat on a shared host, and does
     * not stop an active one -- for that, set mysql_truststore to the server's
     * ca.pem converted to a keystore and use 1.
     *
     * The old Connector/J build could not negotiate a modern TLS suite at all,
     * so this connection was effectively always cleartext.
     */
    public static int MYSQL_TLS;
    public static String MYSQL_TRUSTSTORE;
    public static String MYSQL_TRUSTSTORE_PASS;
    public static long START_TIME;

    /**
     * Reads conf/ls/Conf.xml.
     *
     * NOTHING SECRET HAS A DEFAULT. The original build hardcoded every value
     * including the MySQL password, and for a while this kept those literals as
     * fallbacks so behaviour was unchanged when no file was present. That is
     * exactly the pattern that puts a credential in a source repository, and it
     * is gone: mysql_user and mysql_pass now come from the file or the server
     * does not start.
     *
     * Everything that is NOT a secret still has a default -- ports, bind
     * addresses, database name -- because making an operator restate
     * ls_port = 34522 to run the standard configuration is friction with no
     * security benefit.
     *
     * Refusing to boot is deliberate, and the same rule ServerKey applies to its
     * own default: a server that silently starts with the wrong credentials
     * fails every login instead, which is far harder to diagnose than one clear
     * message at startup.
     */
    public static void initConfig(String file) throws IOException {
        START_TIME = System.currentTimeMillis();

        // Non-secret defaults only.
        MYSQL_HOST = "127.0.0.1";
        MYSQL_DB = "rscd";
        MYSQL_PORT = 3306;
        MYSQL_TLS = 2;
        MYSQL_TRUSTSTORE = "";
        MYSQL_TRUSTSTORE_PASS = "";
        LS_IP = "localhost";
        LS_PORT = 34522;
        QUERY_IP = "localhost";
        QUERY_PORT = 8181;

        // No defaults. Absence is a startup failure, not a fallback.
        MYSQL_USER = null;
        MYSQL_PASS = null;

        Properties p = loadProperties(file);
        if (p == null) {
            /* Only mention the CONF_DIR fallback when it was actually tried.
               For an absolute path it is not, and printing
               ".\conf\ls\C:\somewhere\ls.conf" reads as a bug in the server. */
            String looked = "  Looked for: " + file;
            if (file != null && !new File(file).isAbsolute()) {
                looked += "\n         and: " + new File(CONF_DIR, file).getPath();
            }

            fail("No login server configuration found.\n\n"
                + looked + "\n\n"
                + "Copy conf/ls/Conf.xml.example to conf/ls/Conf.xml and fill in\n"
                + "mysql_user and mysql_pass.\n"
                + "The login server is the only process that talks to the database, so it\n"
                + "cannot start without them.");
        }

        MYSQL_HOST = p.getProperty("mysql_host", MYSQL_HOST);
        MYSQL_DB = p.getProperty("mysql_db", MYSQL_DB);
        MYSQL_USER = trimmed(p, "mysql_user");
        MYSQL_PASS = p.getProperty("mysql_pass");
        MYSQL_PORT = getInt(p, "mysql_port", MYSQL_PORT);
        MYSQL_TLS = getInt(p, "mysql_tls", MYSQL_TLS);
        MYSQL_TRUSTSTORE = p.getProperty("mysql_truststore", MYSQL_TRUSTSTORE);
        MYSQL_TRUSTSTORE_PASS = p.getProperty("mysql_truststore_pass", MYSQL_TRUSTSTORE_PASS);
        LS_IP = p.getProperty("ls_ip", LS_IP);
        LS_PORT = getInt(p, "ls_port", LS_PORT);
        QUERY_IP = p.getProperty("query_ip", QUERY_IP);
        QUERY_PORT = getInt(p, "query_port", QUERY_PORT);

        require("mysql_user", MYSQL_USER, file);
        require("mysql_pass", MYSQL_PASS, file);

        /* The template ships a placeholder so the file is obviously incomplete
           rather than subtly wrong. Refuse it by name, the way ServerKey
           refuses its own shipped default. */
        if ("CHANGEME".equals(MYSQL_PASS)) {
            fail("mysql_pass in " + file + " is still the placeholder 'CHANGEME'.\n"
                + "Set it to the real database password.");
        }
    }

    private static String trimmed(Properties p, String key) {
        String v = p.getProperty(key);
        return v == null ? null : v.trim();
    }

    private static void require(String key, String value, String file) {
        if (value == null || value.length() == 0) {
            fail("Required setting '" + key + "' is missing from " + file + ".\n"
                + "See conf/ls/Conf.xml.example for the full list of keys.");
        }
    }

    /*
     * Straight to stderr and out, rather than through Server.fatal: this runs
     * before the server object exists, and a misconfiguration should not be
     * reported as a crash.
     */
    private static void fail(String message) {
        System.err.println();
        System.err.println("RSCD login server cannot start.");
        System.err.println();
        System.err.println(message);
        System.err.println();
        System.exit(1);
    }

    /** Looks for the file as given, then inside CONF_DIR. Returns null if absent. */
    static Properties loadProperties(String file) throws IOException {
        if (file == null) {
            return null;
        }
        File f = new File(file);
        if (!f.isFile()) {
            f = new File(CONF_DIR, file);
        }
        if (!f.isFile()) {
            return null;
        }
        Properties p = new Properties();
        InputStream in = new FileInputStream(f);
        try {
            p.load(in);
        }
        finally {
            in.close();
        }
        return p;
    }

    static int getInt(Properties p, String key, int fallback) {
        String v = p.getProperty(key);
        if (v == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(v.trim());
        }
        catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static void loadEnv() {
        String home = System.getenv("RSCDLS_HOME");
        if (home == null) {
            home = ".";
        }
        CONF_DIR = home + File.separator + "conf" + File.separator + "ls";
        LOG_DIR = home + File.separator + "logs";
        RSCDLS_HOME = home;
    }

    static {
        Config.loadEnv();
    }
}

