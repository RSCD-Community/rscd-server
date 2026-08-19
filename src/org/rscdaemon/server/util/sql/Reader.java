package org.rscdaemon.server.util.sql;

import java.io.UnsupportedEncodingException;

/*
 * Sequential reader over a packet body. MySQL is little-endian throughout, and
 * strings are length-encoded rather than terminated, except where they are
 * NUL-terminated -- both forms occur and both are here.
 */
final class Reader {

   private final byte[] b;
   private int p;

   Reader(Packet packet) {
      this.b = packet.body();
   }

   int remaining() {
      return this.b.length - this.p;
   }

   int peek() {
      return this.p < this.b.length ? this.b[this.p] & 0xFF : -1;
   }

   int int1() {
      return this.b[this.p++] & 0xFF;
   }

   int int2() {
      return int1() | (int1() << 8);
   }

   int int3() {
      return int1() | (int1() << 8) | (int1() << 16);
   }

   int int4() {
      return int1() | (int1() << 8) | (int1() << 16) | (int1() << 24);
   }

   long int8() {
      long v = 0;
      for (int i = 0; i < 8; i++) {
         v |= ((long) int1()) << (8 * i);
      }

      return v;
   }

   byte[] fixed(int n) {
      int take = Math.min(n, remaining());
      byte[] out = new byte[take];
      System.arraycopy(this.b, this.p, out, 0, take);
      this.p += take;
      return out;
   }

   String fixedString(int n) {
      return string(fixed(n));
   }

   String nulString() {
      int start = this.p;
      while (this.p < this.b.length && this.b[this.p] != 0) {
         this.p++;
      }

      byte[] out = new byte[this.p - start];
      System.arraycopy(this.b, start, out, 0, out.length);
      if (this.p < this.b.length) {
         this.p++;   // consume the NUL
      }

      return string(out);
   }

   /**
    * Length-encoded integer. The first byte is either the value itself, or a
    * marker for how many bytes follow.
    */
   long lenencInt() {
      int first = int1();
      if (first < 0xFB) {
         return first;
      } else if (first == 0xFC) {
         return int2();
      } else if (first == 0xFD) {
         return int3();
      } else if (first == 0xFE) {
         return int8();
      } else {
         // 0xFB is NULL in a row context and has no integer meaning here.
         return -1;
      }
   }

   String lenencString() {
      long n = lenencInt();
      return n < 0 ? "" : string(fixed((int) n));
   }

   /** As lenencString, but 0xFB means SQL NULL and yields a Java null. */
   String lenencStringOrNull() {
      if (peek() == 0xFB) {
         this.p++;
         return null;
      }

      return lenencString();
   }

   String restAsString() {
      return string(fixed(remaining()));
   }

   private static String string(byte[] raw) {
      try {
         return new String(raw, "UTF-8");
      } catch (UnsupportedEncodingException e) {
         return new String(raw);
      }
   }
}
