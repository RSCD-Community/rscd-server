package org.rscdaemon.server.util.net;

/*
 * A byte buffer, big-endian, serving both halves of a codec.
 *
 * Deliberately exposes the same method names MINA's ByteBuffer did -- get,
 * getUnsigned, getInt, getLong, put, putInt, putLong, remaining, position,
 * flip -- because the four codecs were written against those and the framing
 * logic in them is Jagex's, worth carrying over unchanged rather than
 * retyping. Only eleven of MINA's buffer operations were ever used.
 *
 * Two roles:
 *
 *   reading   the cumulative receive buffer. Bytes are appended as they arrive
 *             and consumed as whole packets are recognised; anything left over
 *             is kept for the next read.
 *   writing   allocate(n), put things, flip(), hand the bytes to the socket.
 *
 * Not thread-safe, and does not need to be: a receive buffer belongs to one
 * reader thread, and a send buffer is a local in encode().
 */
public final class Buffer {

   private byte[] data;
   private int position;
   private int limit;

   private Buffer(byte[] data, int limit) {
      this.data = data;
      this.limit = limit;
   }

   public static Buffer allocate(int capacity) {
      return new Buffer(new byte[Math.max(capacity, 16)], 0);
   }

   public static Buffer wrap(byte[] bytes, int length) {
      return new Buffer(bytes, length);
   }

   public int position() {
      return this.position;
   }

   public void position(int p) {
      this.position = p;
   }

   public int limit() {
      return this.limit;
   }

   public int remaining() {
      return this.limit - this.position;
   }

   public boolean hasRemaining() {
      return this.position < this.limit;
   }

   /** Switch from writing to reading: limit becomes what was written. */
   public Buffer flip() {
      this.limit = this.position;
      this.position = 0;
      return this;
   }

   public void clear() {
      this.position = 0;
      this.limit = 0;
   }

   public byte[] toByteArray() {
      byte[] out = new byte[remaining()];
      System.arraycopy(this.data, this.position, out, 0, out.length);
      return out;
   }

   /*
    * ---- reading ----
    */

   public byte get() {
      return this.data[this.position++];
   }

   /** Unsigned, which is what a length or an opcode byte always is. */
   public short getUnsigned() {
      return (short) (this.data[this.position++] & 0xFF);
   }

   public void get(byte[] dst) {
      get(dst, 0, dst.length);
   }

   public void get(byte[] dst, int offset, int length) {
      System.arraycopy(this.data, this.position, dst, offset, length);
      this.position += length;
   }

   public int getInt() {
      return ((get() & 0xFF) << 24) | ((get() & 0xFF) << 16) | ((get() & 0xFF) << 8) | (get() & 0xFF);
   }

   public long getLong() {
      long v = 0L;
      for (int i = 0; i < 8; i++) {
         v = (v << 8) | (get() & 0xFF);
      }

      return v;
   }

   /*
    * ---- writing ----
    */

   public Buffer put(byte b) {
      ensure(1);
      this.data[this.position++] = b;
      return this;
   }

   public Buffer put(byte[] src) {
      return put(src, 0, src.length);
   }

   public Buffer put(byte[] src, int offset, int length) {
      ensure(length);
      System.arraycopy(src, offset, this.data, this.position, length);
      this.position += length;
      return this;
   }

   public Buffer putInt(int v) {
      ensure(4);
      put((byte) (v >>> 24));
      put((byte) (v >>> 16));
      put((byte) (v >>> 8));
      put((byte) v);
      return this;
   }

   public Buffer putLong(long v) {
      ensure(8);
      for (int i = 7; i >= 0; i--) {
         put((byte) (v >>> (8 * i)));
      }

      return this;
   }

   /*
    * ---- cumulative receive ----
    */

   /** Appends freshly-read bytes at the end, growing if needed. */
   void append(byte[] src, int length) {
      int end = this.limit;
      if (end + length > this.data.length) {
         grow(end + length);
      }

      System.arraycopy(src, 0, this.data, end, length);
      this.limit = end + length;
   }

   /**
    * Drops everything already consumed, moving the remainder to the front.
    * This is what keeps a half-received packet across reads, and it is why the
    * decoders must leave the position at the start of the incomplete packet
    * rather than rewinding to zero -- rewinding would re-deliver packets that
    * were already handed to the handler.
    */
   void compact() {
      int left = remaining();
      if (this.position > 0) {
         System.arraycopy(this.data, this.position, this.data, 0, left);
      }

      this.position = 0;
      this.limit = left;
   }

   private void ensure(int extra) {
      if (this.position + extra > this.data.length) {
         grow(this.position + extra);
      }

      if (this.position + extra > this.limit) {
         this.limit = this.position + extra;
      }
   }

   private void grow(int needed) {
      int size = Math.max(this.data.length * 2, needed);
      byte[] bigger = new byte[size];
      System.arraycopy(this.data, 0, bigger, 0, this.limit);
      this.data = bigger;
   }
}
