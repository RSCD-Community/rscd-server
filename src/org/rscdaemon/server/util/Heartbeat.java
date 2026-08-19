package org.rscdaemon.server.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.rscdaemon.server.model.World;

/**
 * Tells the community world list this world exists, once a minute.
 *
 * Off unless the config sets heartbeat = true. A server that does not opt in
 * never opens an outbound connection, which is the only sane default for
 * something people run on their own machines.
 *
 * What is sent is one JSON object with what a player picking a world needs to
 * see -- the name, the address, the world number, how many are on and how many
 * fit, where to get the cache, and where browsers reach the WebSocket bridge --
 * plus the server_key, which identifies the sender and is the only value here
 * that is not already public. Nothing about any player is sent: not a name, not
 * an address, not a count of anything but the total.
 *
 * The reply carries a nonce. The registry then dials this world's game port
 * and asks for it back (see packethandler/client/Challenge.java); a listing
 * only appears once that has worked, so nobody can list an address they do not
 * actually run.
 */
public class Heartbeat implements Runnable {
    /** The nonce the registry will ask for, or null if none is outstanding. */
    private static volatile byte[] nonce;

    private static Thread thread;

    private int interval = 60;

    /** Starts the sender if this world is configured for it. */
    public static synchronized void start() {
        if (thread != null) {
            return;
        }

        if (!Config.HEARTBEAT) {
            return;
        }

        if (Config.API_URL == null || Config.API_URL.length() == 0) {
            Logger.print("Heartbeat is on but api_url is not set; not registering.");
            return;
        }

        thread = new Thread(new Heartbeat(), "Heartbeat");
        thread.setDaemon(true);
        thread.start();
        Logger.print("Registering world " + Config.SERVER_NUM + " with " + Config.API_URL);
    }

    /** What the registry is currently entitled to ask for. */
    public static byte[] getNonce() {
        return nonce;
    }

    public void run() {
        while (true) {
            try {
                this.send();
            } catch (Exception e) {
                // A registry that is down must never take a game server with
                // it, so this is the whole of the error handling: say so once
                // and try again next interval.
                Logger.print("Heartbeat failed: " + e.getMessage());
            }

            try {
                Thread.sleep(this.interval * 1000L);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private void send() throws Exception {
        String payload = this.payload();

        HttpURLConnection connection = (HttpURLConnection) new URL(Config.API_URL).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(15000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");

        OutputStream out = connection.getOutputStream();
        try {
            out.write(payload.getBytes("UTF-8"));
        } finally {
            out.close();
        }

        int status = connection.getResponseCode();
        InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String body = read(stream);

        if (status >= 400) {
            // Worth printing in full: every rejection here is something the
            // operator has to change in the config, so the reason has to reach
            // them rather than being counted.
            Logger.print("World list refused this server: " + string(body, "error"));
            return;
        }

        String issued = string(body, "nonce");
        nonce = issued == null ? null : unhex(issued);

        int given = number(body, "interval");
        if (given >= 15 && given <= 3600) {
            this.interval = given;
        }

        String message = string(body, "message");
        if (message != null && message.length() > 0) {
            Logger.print("World list: " + message);
        }
    }

    private String payload() {
        int online = World.getWorld().getPlayers().size();

        StringBuilder json = new StringBuilder(512);
        json.append('{');
        field(json, "key", Config.SERVER_KEY).append(',');
        field(json, "name", Config.SERVER_NAME).append(',');
        // ADDRESS is what this world tells the outside world to reach it on;
        // SERVER_IP is only what MINA binds to, and on most boxes that is a
        // private address nobody else can use.
        field(json, "host", Config.ADDRESS).append(',');
        field(json, "cache_url", Config.CACHE_URL).append(',');
        // Sent as configured, without checking the scheme here. The registry
        // has to reject anything that is not wss:// anyway -- it cannot trust a
        // value an unknown server sent it -- so validating in both places would
        // just mean two rules to keep in step. A world that gets this wrong
        // hears about it: the refusal comes back in the reply, which is logged.
        field(json, "ws_url", Config.WS_URL).append(',');
        field(json, "welcome1", Config.WELCOME1).append(',');
        field(json, "welcome2", Config.WELCOME2).append(',');
        number(json, "port", Config.SERVER_PORT).append(',');
        number(json, "world", Config.SERVER_NUM).append(',');
        number(json, "online", online).append(',');
        number(json, "capacity", Config.MAX_PLAYERS).append(',');
        number(json, "protocol", Config.SERVER_VERSION);
        json.append('}');
        return json.toString();
    }

    private static StringBuilder field(StringBuilder json, String key, String value) {
        json.append('"').append(key).append("\":\"");
        escape(json, value == null ? "" : value);
        return json.append('"');
    }

    private static StringBuilder number(StringBuilder json, String key, int value) {
        return json.append('"').append(key).append("\":").append(value);
    }

    private static void escape(StringBuilder json, String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') {
                json.append('\\').append(c);
            } else if (c < 0x20) {
                json.append(' ');
            } else {
                json.append(c);
            }
        }
    }

    private static String read(InputStream stream) throws Exception {
        if (stream == null) {
            return "";
        }
        StringBuilder body = new StringBuilder();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null && body.length() < 16384) {
                body.append(line);
            }
        } finally {
            stream.close();
        }
        return body.toString();
    }

    /*
     * The reply is five flat values and this reads three of them. A parser
     * would be a fairer trade if the shape were ever going to grow, and if it
     * does, that is when to write one.
     */
    private static String string(String body, String key) {
        int at = valueStart(body, key);
        if (at < 0 || at >= body.length() || body.charAt(at) != '"') {
            return null;
        }
        StringBuilder value = new StringBuilder();
        for (int i = at + 1; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '\\' && i + 1 < body.length()) {
                value.append(body.charAt(++i));
            } else if (c == '"') {
                return value.toString();
            } else {
                value.append(c);
            }
        }
        return null;
    }

    private static int number(String body, String key) {
        int at = valueStart(body, key);
        if (at < 0) {
            return -1;
        }
        int end = at;
        while (end < body.length() && Character.isDigit(body.charAt(end))) {
            end++;
        }
        if (end == at) {
            return -1;
        }
        try {
            return Integer.parseInt(body.substring(at, end));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static int valueStart(String body, String key) {
        int at = body.indexOf('"' + key + '"');
        if (at < 0) {
            return -1;
        }
        at = body.indexOf(':', at);
        if (at < 0) {
            return -1;
        }
        at++;
        while (at < body.length() && body.charAt(at) == ' ') {
            at++;
        }
        return at;
    }

    private static byte[] unhex(String hex) {
        if (hex.length() % 2 != 0) {
            return null;
        }
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            int high = Character.digit(hex.charAt(i * 2), 16);
            int low = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) {
                return null;
            }
            bytes[i] = (byte) ((high << 4) | low);
        }
        return bytes;
    }
}
