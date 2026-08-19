package org.rscdaemon.server.util.sql;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;

/* Builds a packet body. Little-endian, matching the protocol. */
final class Writer {

   private final ByteArrayOutputStream out = new ByteArrayOutputStream();

   void int1(int v) {
      this.out.write(v & 0xFF);
   }

   void int2(int v) {
      int1(v);
      int1(v >>> 8);
   }

   void int4(int v) {
      int1(v);
      int1(v >>> 8);
      int1(v >>> 16);
      int1(v >>> 24);
   }

   void zero(int n) {
      for (int i = 0; i < n; i++) {
         this.out.write(0);
      }
   }

   void bytes(byte[] b) {
      this.out.write(b, 0, b.length);
   }

   void nulString(String s) {
      if (s != null) {
         try {
            byte[] raw = s.getBytes("UTF-8");
            this.out.write(raw, 0, raw.length);
         } catch (UnsupportedEncodingException e) {
            byte[] raw = s.getBytes();
            this.out.write(raw, 0, raw.length);
         }
      }

      this.out.write(0);
   }

   byte[] toByteArray() {
      return this.out.toByteArray();
   }
}
