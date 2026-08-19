/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.util;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Random;
import org.rscdaemon.server.model.Point;
import org.rscdaemon.server.net.RSCPacket;
import org.rscdaemon.server.util.Logger;

/**
 * The protocol's number- and text-mangling, collected in one place: chat
 * compression, the base-37 username hash, position deltas, the login RSA
 * step, and the dice every skill rolls.
 *
 * Run standalone it is also a tool: `main encode <name>` / `main decode
 * <hash>` converts between usernames and the base-37 hashes the friends
 * list and the players table store.
 */
public final class DataConversions {
    private static SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss dd-MM-yy");
    private static MessageDigest md;
    private static Random rand;
    private static char[] characters;
    private static final BigInteger key;
    private static final BigInteger modulus;

    public static void main(String[] argv) throws Exception {
        if (argv[0].equals("encode")) {
            System.out.println(DataConversions.usernameToHash(argv[1]));
        }
        if (argv[0].equals("decode")) {
            System.out.println(DataConversions.hashToUsername(Long.parseLong(argv[1])));
        }
    }

    public static final ByteBuffer streamToBuffer(BufferedInputStream in) throws IOException {
        byte[] buffer = new byte[in.available()];
        in.read(buffer, 0, buffer.length);
        return ByteBuffer.wrap(buffer);
    }

    public static String timeSince(long time) {
        int seconds = (int)((System.currentTimeMillis() - time) / 1000L);
        int minutes = seconds / 60;
        int hours = minutes / 60;
        int days = hours / 24;
        return days + " days " + hours % 24 + " hours " + minutes % 60 + " mins";
    }

    public static String timeFormat(long l) {
        return formatter.format(l);
    }

    public static int roundUp(double val) {
        return (int)Math.round(val + 0.5);
    }

    public static double round(double value, int decimalPlace) {
        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(decimalPlace, 4);
        return bd.doubleValue();
    }

    public static byte[] getObjectPositionOffsets(Point p1, Point p2) {
        byte[] rv = new byte[]{DataConversions.getObjectCoordOffset(p1.getX(), p2.getX()), DataConversions.getObjectCoordOffset(p1.getY(), p2.getY())};
        return rv;
    }

    private static byte getObjectCoordOffset(int coord1, int coord2) {
        return (byte)(coord1 - coord2);
    }

    public static byte[] getMobPositionOffsets(Point p1, Point p2) {
        byte[] rv = new byte[]{DataConversions.getMobCoordOffset(p1.getX(), p2.getX()), DataConversions.getMobCoordOffset(p1.getY(), p2.getY())};
        return rv;
    }

    /* Mob deltas travel as 5-bit fields in the position-update packets, so a
       negative offset wraps into 0..31 (5-bit two's complement) rather than
       using a sign bit the wire format does not have. */
    private static byte getMobCoordOffset(int coord1, int coord2) {
        byte offset = (byte)(coord1 - coord2);
        if (offset < 0) {
            offset = (byte)(offset + 32);
        }
        return offset;
    }

    /* Undoes the client's RSA on the login block (the session keys and
       password travel inside it). The exponent/modulus pair in the static
       initialiser is the server-side half baked into this build's client;
       a foreign client encrypting against different keys just produces
       garbage here and fails login. */
    public static RSCPacket decryptRSA(byte[] pData, int plainLength) {
        try {
            BigInteger bigInteger = new BigInteger(pData);
            pData = toFixedLength(bigInteger.modPow(key, modulus).toByteArray(), plainLength);
            return new RSCPacket(null, 0, pData, true);
        }
        catch (Exception e) {
            return null;
        }
    }

    /*
     * Right-aligns a BigInteger's bytes into a field of known width, which is
     * the only correct way to read one back as a fixed-layout record.
     *
     * BigInteger is a number, not a byte string, and toByteArray() gives the
     * SHORTEST two's-complement form of that number. It therefore does not
     * return what went in: a plaintext whose first byte is 0x00 comes back one
     * byte shorter, because a leading zero does not change a value. Every field
     * after it then reads one byte early, and the login block is read as
     * nonsense -- the session keys do not match, and PlayerLogin answers 5,
     * "server rejected session".
     *
     * This was live and frequent rather than theoretical. The client's first
     * session-rotation key is (int)(Math.random() * 9.9999999E7), so it is
     * below 2^23 -- top byte zero, second byte under 0x80, one byte lost --
     * for 8.39% of logins. Measured over 200,000 simulated logins against the
     * real key pair: 8.34% failed, and every one of them was this. That is the
     * "server refused, click again and it works" a player sees; the retry
     * simply draws a different random number.
     *
     * The mirror case is handled too: a value whose top byte has the high bit
     * set comes back one byte LONGER, with a 0x00 sign byte in front. The
     * client's key can never do that today, but the rule "read a number back
     * into the width you wrote it at" is what makes that not matter.
     *
     * Fixed here rather than in the client on purpose. The bug is in reading,
     * not writing, so the server can cure it for every client already out in
     * the world -- including old ones, and including anything that ever spoke
     * this protocol correctly. A client-side change would fix only clients that
     * had been updated, and would leave the server still misreading the rest.
     */
    private static byte[] toFixedLength(byte[] bytes, int length) {
        if (bytes.length == length) {
            return bytes;
        }
        byte[] fixed = new byte[length];
        if (bytes.length > length) {
            // Longer: the extra is leading sign padding, so keep the tail.
            System.arraycopy(bytes, bytes.length - length, fixed, 0, length);
        } else {
            // Shorter: the missing leading bytes were zeros. Put them back.
            System.arraycopy(bytes, 0, fixed, length - bytes.length, bytes.length);
        }
        return fixed;
    }

    public static int average(int[] values) {
        int total = 0;
        for (int value : values) {
            total += value;
        }
        return total / values.length;
    }

    private static int getCharCode(char c) {
        for (int x = 0; x < characters.length; ++x) {
            if (c != characters[x]) continue;
            return x;
        }
        return 0;
    }

    /*
     * Chat compression, both directions. The `characters` table in the
     * static initialiser is ordered by letter frequency; the 13 most common
     * characters fit a 4-bit code, everything rarer takes 8 bits (its code
     * + 195, split across two nibbles). Ordinary sentences pack close to two
     * characters per byte. byteToString also applies the original display
     * rules on the way out: sentences are capitalised after . ! and :, and
     * @ / % are blanked past position 4 so chat cannot forge the @col@
     * colour codes the client honours.
     */
    public static byte[] stringToByteArray(String message) {
        byte[] buffer = new byte[100];
        if (message.length() > 80) {
            message = message.substring(0, 80);
        }
        message = message.toLowerCase();
        int length = 0;
        int j = -1;
        for (int k = 0; k < message.length(); ++k) {
            int code = DataConversions.getCharCode(message.charAt(k));
            if (code > 12) {
                code += 195;
            }
            if (j == -1) {
                if (code < 13) {
                    j = code;
                    continue;
                }
                buffer[length++] = (byte)code;
                continue;
            }
            if (code < 13) {
                buffer[length++] = (byte)((j << 4) + code);
                j = -1;
                continue;
            }
            buffer[length++] = (byte)((j << 4) + (code >> 4));
            j = code & 0xF;
        }
        if (j != -1) {
            buffer[length++] = (byte)(j << 4);
        }
        byte[] string = new byte[length];
        System.arraycopy(buffer, 0, string, 0, length);
        return string;
    }

    public static String byteToString(byte[] data, int offset, int length) {
        char[] buffer = new char[100];
        try {
            int k = 0;
            int l = -1;
            for (int i1 = 0; i1 < length; ++i1) {
                int j1 = data[offset++] & 0xFF;
                int k1 = j1 >> 4 & 0xF;
                if (l == -1) {
                    if (k1 < 13) {
                        buffer[k++] = characters[k1];
                    } else {
                        l = k1;
                    }
                } else {
                    buffer[k++] = characters[(l << 4) + k1 - 195];
                    l = -1;
                }
                k1 = j1 & 0xF;
                if (l == -1) {
                    if (k1 < 13) {
                        buffer[k++] = characters[k1];
                        continue;
                    }
                    l = k1;
                    continue;
                }
                buffer[k++] = characters[(l << 4) + k1 - 195];
                l = -1;
            }
            boolean flag = true;
            for (int l1 = 0; l1 < k; ++l1) {
                char c = buffer[l1];
                if (l1 > 4 && c == '@') {
                    buffer[l1] = 32;
                }
                if (c == '%') {
                    buffer[l1] = 32;
                }
                if (flag && c >= 'a' && c <= 'z') {
                    int n = l1;
                    buffer[n] = (char)(buffer[n] + 65504);
                    flag = false;
                }
                if (c != '.' && c != '!' && c != ':') continue;
                flag = true;
            }
            return new String(buffer, 0, k);
        }
        catch (Exception e) {
            return ".";
        }
    }

    public static boolean percentChance(int percent) {
        return DataConversions.random(1, 100) <= percent;
    }

    public static boolean inArray(int[] haystack, int needle) {
        for (int option : haystack) {
            if (needle != option) continue;
            return true;
        }
        return false;
    }

    public static int max(int i1, int i2) {
        return i1 > i2 ? i1 : i2;
    }

    public static Random getRandom() {
        return rand;
    }

    public static int random(int low, int high) {
        return low + rand.nextInt(high - low + 1);
    }

    /* A shaped roll: every value 0..max starts at weight 100, the weight
       falls by 3 per step outside [dip, peak] and rises by 3 per step inside
       it -- a cheap way to make mid-range outcomes common and the extremes
       rare without a real distribution. */
    public static int randomWeighted(int low, int dip, int peak, int max) {
        int total = 0;
        int probability = 100;
        int[] probArray = new int[max + 1];
        for (int x = 0; x < probArray.length; ++x) {
            probArray[x] = probability;
            total += probArray[x];
            if (x < dip || x > peak) {
                probability -= 3;
                continue;
            }
            probability += 3;
        }
        int hit = DataConversions.random(0, total);
        total = 0;
        for (int x = 0; x < probArray.length; ++x) {
            if (hit >= total && hit < total + probArray[x]) {
                return x;
            }
            total += probArray[x];
        }
        return 0;
    }

    /* The base-37 username hash (space=0, a-z=1..26, 0-9=27..36), at most 12
       characters, which is how the wire, the friends list and the players
       table all identify a character -- names round-trip through
       hashToUsername, they are never stored as text on the wire. */
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

    public static long dateToHash(String s) {
        s = s.toLowerCase();
        s = s.replaceAll("/", "g");
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

    public static String hashToDate(long l) {
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
        s = s.replaceAll("g", "/");
        return s;
    }

    /*
     * Was HexString.bufferToHex() out of hex-string.jar -- a whole jar for six
     * lines, and one that cannot be redistributed. This is the same six lines:
     * uppercase, zero-padded, no separators, which is the format the login
     * server compares against, so the wire bytes are unchanged.
     */
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    public static String md5(String s) {
        md.reset();
        md.update(s.getBytes());
        byte[] digest = md.digest();
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (int i = 0; i < digest.length; ++i) {
            hex.append(HEX[(digest[i] & 0xF0) >> 4]);
            hex.append(HEX[digest[i] & 0xF]);
        }
        return hex.toString();
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

    static {
        rand = new Random();
        characters = new char[]{' ', 'e', 't', 'a', 'o', 'i', 'h', 'n', 's', 'r', 'd', 'l', 'u', 'm', 'w', 'c', 'y', 'f', 'g', 'p', 'b', 'v', 'k', 'x', 'j', 'q', 'z', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', ' ', '!', '?', '.', ',', ':', ';', '(', ')', '-', '&', '*', '\\', '\'', '@', '#', '+', '=', '\u00a3', '$', '%', '\"', '[', ']'};
        key = new BigInteger("730546719878348732291497161314617369560443701473303681965331739205703475535302276087891130348991033265134162275669215460061940182844329219743687403068279");
        modulus = new BigInteger("1549611057746979844352781944553705273443228154042066840514290174539588436243191882510185738846985723357723362764835928526260868977814405651690121789896823");
        try {
            md = MessageDigest.getInstance("MD5");
        }
        catch (Exception e) {
            Logger.error(e);
        }
    }
}

