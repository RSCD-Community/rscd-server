/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Config {
    public static String SERVER_IP;
    public static String SERVER_NAME;
    public static String RSCD_HOME;
    public static String CONF_DIR;
    public static String SERVER_LOCATION;
    public static String LS_IP;
    public static String ADDRESS;
    public static int SERVER_PORT;
    /** Same game protocol as SERVER_PORT, wrapped in WebSocket framing for
        browser clients. 0 disables the listener entirely. */
    public static int WS_PORT;
    public static int SERVER_VERSION;
    public static int MAX_PLAYERS;
    public static int LS_PORT;
    public static int SERVER_NUM;
    public static int EXP_MULT;
    public static int SUBSCRIBER_EXP_MULT;
    /** When true, Player.isSubscriber() is true for everyone, subscribed or not. */
    public static boolean EVERYONE_SUBSCRIBER;

    /**
     * The ObjectBuilder and NPCBuilder Swing windows, which collect the XML for
     * whatever a moderator spawns with ::npc and ::object. World-building
     * tools, and the original build force-opened both on every boot -- which
     * makes the game server require an X display and throw HeadlessException
     * on exactly the kind of host a real server runs on. Off unless asked for.
     */
    public static boolean BUILDER_WINDOWS;

    public static long START_TIME;

    /*
     * Community world list. All optional: a server that sets none of these
     * never contacts anything and is exactly the server it was before.
     */
    public static String SERVER_KEY;
    public static String API_URL;
    public static String CACHE_URL;
    /**
     * Where browsers reach this world's WebSocket bridge, e.g.
     * "wss://example.org/ws". Advertised to the world list so a browser client
     * on any site can dial this world directly.
     *
     * Deliberately not derived from WS_PORT. WS_PORT is what the bridge binds
     * to on loopback; what a browser can actually reach is whatever the
     * operator's reverse proxy publishes, which is a different host, a
     * different port, and a different scheme. Only the operator knows it, so
     * only the operator can set it. Empty means "no bridge", and a world that
     * leaves it empty is simply not playable in a browser -- which is the
     * correct outcome, not a failure.
     */
    public static String WS_URL;
    public static String WELCOME1;
    public static String WELCOME2;
    public static boolean HEARTBEAT;

    /**
     * The file initConfig actually read, once it has been resolved against
     * CONF_DIR. ServerKey writes back to it, so the path the operator gave has
     * to survive that lookup rather than being resolved twice.
     */
    public static File CONFIG_FILE;

    /**
     * As with the login server, the original build ignored this argument and
     * hardcoded everything, so no config file survives in the archive. The
     * defaults below are the recovered original values, except that the two
     * host fields now default to loopback: the original build hardcoded
     * SERVER_NAME "Ignis Isle" and both SERVER_IP and ADDRESS
     * "ignisisle.no-ip.org", and since the acceptor binds to SERVER_IP directly, a
     * hostname that no longer resolves throws UnresolvedAddressException
     * before the server can start. A file of the given name overrides all of
     * these if present.
     *
     * EXP_MULT and SUBSCRIBER_EXP_MULT were both 15 and a hardcoded +10
     * respectively -- neither authentic, and the subscriber bonus was not
     * even a setting, just a number added inline wherever experience was
     * granted. Authentic rates were restored 2026-08-02: an authenticity
     * decision, not a default-value cleanup.
     *
     * The subscriber figure is an ADDEND, not a factor -- experience is
     * granted as (EXP_MULT + SUBSCRIBER_EXP_MULT), so its "no bonus" value is
     * 0 and not 1. Setting both to 1 on 2026-08-02 therefore shipped 2x to
     * every account rather than the intended 1x, silently: everyone_subscriber
     * makes every player a subscriber, and the login banner only announces a
     * rate when EXP_MULT itself exceeds 1, which it did not. It went unnoticed
     * until a player ran ::myrate and it answered 2x.
     */
    public static void initConfig(String file) throws IOException {
        START_TIME = System.currentTimeMillis();
        SERVER_VERSION = 1200;
        SERVER_NAME = "RSCD Community";
        SERVER_IP = "127.0.0.1";
        SERVER_PORT = 43594;
        WS_PORT = 43595;
        SERVER_LOCATION = "US";
        ADDRESS = "127.0.0.1";
        MAX_PLAYERS = 75;
        LS_IP = "localhost";
        LS_PORT = 34522;
        SERVER_NUM = 1;
        EXP_MULT = 1;
        SUBSCRIBER_EXP_MULT = 0;
        EVERYONE_SUBSCRIBER = true;
        BUILDER_WINDOWS = false;
        SERVER_KEY = "";
        API_URL = "";
        CACHE_URL = "";
        WS_URL = "";
        WELCOME1 = "";
        WELCOME2 = "";
        HEARTBEAT = false;

        Properties p = loadProperties(file);
        if (p == null) {
            return;
        }
        SERVER_VERSION = getInt(p, "server_version", SERVER_VERSION);
        SERVER_NAME = p.getProperty("server_name", SERVER_NAME);
        SERVER_IP = p.getProperty("server_ip", SERVER_IP);
        SERVER_PORT = getInt(p, "server_port", SERVER_PORT);
        WS_PORT = getInt(p, "ws_port", WS_PORT);
        SERVER_LOCATION = p.getProperty("server_location", SERVER_LOCATION);
        ADDRESS = p.getProperty("address", ADDRESS);
        MAX_PLAYERS = getInt(p, "max_players", MAX_PLAYERS);
        LS_IP = p.getProperty("ls_ip", LS_IP);
        LS_PORT = getInt(p, "ls_port", LS_PORT);
        SERVER_NUM = getInt(p, "server_num", SERVER_NUM);
        EXP_MULT = getInt(p, "exp_mult", EXP_MULT);
        SUBSCRIBER_EXP_MULT = getInt(p, "subscriber_exp_mult", SUBSCRIBER_EXP_MULT);
        EVERYONE_SUBSCRIBER = getBoolean(p, "everyone_subscriber", EVERYONE_SUBSCRIBER);
        BUILDER_WINDOWS = getBoolean(p, "builder_windows", BUILDER_WINDOWS);

        SERVER_KEY = p.getProperty("server_key", SERVER_KEY).trim();
        API_URL = p.getProperty("api_url", API_URL).trim();
        CACHE_URL = p.getProperty("cache_url", CACHE_URL).trim();
        WS_URL = p.getProperty("ws_url", WS_URL).trim();
        WELCOME1 = p.getProperty("welcome1", WELCOME1);
        WELCOME2 = p.getProperty("welcome2", WELCOME2);
        HEARTBEAT = getBoolean(p, "heartbeat", HEARTBEAT);
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
            // Remembered anyway: a first boot with no config file still has to be
            // able to write one for the generated server_key.
            CONFIG_FILE = new File(CONF_DIR, new File(file).getName());
            return null;
        }
        CONFIG_FILE = f;
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

    static boolean getBoolean(Properties p, String key, boolean fallback) {
        String v = p.getProperty(key);
        if (v == null) {
            return fallback;
        }
        v = v.trim();
        if (v.equalsIgnoreCase("true") || v.equals("1") || v.equalsIgnoreCase("yes") || v.equalsIgnoreCase("on")) {
            return true;
        }
        if (v.equalsIgnoreCase("false") || v.equals("0") || v.equalsIgnoreCase("no") || v.equalsIgnoreCase("off")) {
            return false;
        }
        return fallback;
    }

    private static void loadEnv() {
        String home = System.getenv("RSCD_HOME");
        if (home == null) {
            home = ".";
        }
        CONF_DIR = home + File.separator + "conf" + File.separator + "server";
        RSCD_HOME = home;
    }

    static {
        Config.loadEnv();
    }
}

