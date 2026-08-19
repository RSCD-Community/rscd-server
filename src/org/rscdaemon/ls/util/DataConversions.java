/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.ls.util;

public class DataConversions {
    public static long usernameToHash(String s) {
        s = s.toLowerCase();
        String s1 = "";
        for (int i = 0; i < s.length(); ++i) {
            char c = s.charAt(i);
            s1 = c >= 'a' && c <= 'z' ? s1 + c : (c >= '0' && c <= '9' ? s1 + c : s1 + ' ');
        }
        if ((s1 = s1.trim()).length() > 12) {
            s1 = s1.substring(0, 12);
        }
        long l = 0L;
        for (int j = 0; j < s1.length(); ++j) {
            char c1 = s1.charAt(j);
            l *= 37L;
            if (c1 >= 'a' && c1 <= 'z') {
                l += (long)('\u0001' + c1 - 97);
                continue;
            }
            if (c1 < '0' || c1 > '9') continue;
            l += (long)(27 + c1 - 48);
        }
        return l;
    }

    public static String hashToUsername(long l) {
        if (l < 0L) {
            return "invalid_name";
        }
        String s = "";
        while (l != 0L) {
            int i = (int)(l % 37L);
            l /= 37L;
            if (i == 0) {
                s = " " + s;
                continue;
            }
            if (i < 27) {
                if (l % 37L == 0L) {
                    s = (char)(i + 65 - 1) + s;
                    continue;
                }
                s = (char)(i + 97 - 1) + s;
                continue;
            }
            s = (char)(i + 48 - 27) + s;
        }
        return s;
    }

    public static String IPToString(long ip) {
        String result = "0.0.0.0";
        for (int x = 0; x < 4; ++x) {
            int octet = (int)((double)ip / Math.pow(256.0, 3 - x));
            ip = (long)((double)ip - (double)octet * Math.pow(256.0, 3 - x));
            result = x == 0 ? String.valueOf(octet) : result + "." + octet;
        }
        return result;
    }

    public static long IPToLong(String ip) {
        String[] octets = ip.split("\\.");
        long result = 0L;
        for (int x = 0; x < 4; ++x) {
            result = (long)((double)result + (double)Integer.parseInt(octets[x]) * Math.pow(256.0, 3 - x));
        }
        return result;
    }
}

