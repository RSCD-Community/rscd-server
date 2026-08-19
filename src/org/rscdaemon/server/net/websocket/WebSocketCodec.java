package org.rscdaemon.server.net.websocket;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.rscdaemon.server.util.net.Buffer;
import org.rscdaemon.server.util.net.Codec;
import org.rscdaemon.server.util.net.Connection;

/*
 * WebSocket (RFC 6455) in front of the game protocol.
 *
 * A browser cannot open a TCP socket, so the web client connects here instead:
 * one HTTP upgrade handshake, then binary frames whose payloads are exactly the
 * bytes the fat client would have written to its socket. This codec peels the
 * WebSocket layer off and hands the naked byte stream to the wrapped codec --
 * RSCCodec in practice -- so a WebSocket player and a TCP player are the same
 * thing to every class above this one.
 *
 * Sits entirely inside the transport's Codec seam. decode() honours the same
 * contract as every other codec (consume whole units, leave position at the
 * first unconsumed byte); the difference is that a "unit" here is a WebSocket
 * frame, and the game packets are found one level further down, in this codec's
 * own carry-over buffer rather than the transport's.
 *
 * The handshake response is a write that originates inside decode(), which the
 * transport does not otherwise allow for: writes normally enter through
 * Connection.write() carrying game messages. The escape hatch is a private
 * sentinel type -- decode() queues a Raw, and encode() passes its bytes through
 * untouched. Ping and close replies use the same hatch.
 *
 * Per-connection state lives in an IdentityHashMap guarded by this codec, not
 * in the Connection attachment: the attachment belongs to the game (it holds
 * the Player), and this layer must not be another claimant on it. Entries are
 * removed by WebSocketHandler.connectionClosed -- forgetting that call would
 * leak one State per connection, which is exactly the class of leak the
 * transport rewrite went to such lengths to bury.
 */
public final class WebSocketCodec implements Codec {

   /* A handshake request larger than this is not a browser, it is a problem. */
   private static final int MAX_HANDSHAKE = 8192;

   /*
    * Largest frame payload accepted. The game protocol's own ceiling is a
    * two-byte length (~16K in practice, far less in reality), so a well-behaved
    * client never approaches this; a frame claiming more is hostile and the
    * connection is dropped before any allocation happens.
    */
   private static final long MAX_FRAME = 65536;

   private static final String ACCEPT_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

   private final Codec inner;
   private final Map<Connection, State> states = new IdentityHashMap<Connection, State>();

   public WebSocketCodec(Codec inner) {
      this.inner = inner;
   }

   /* ---- sentinels: writes that bypass the game codec ---- */

   private static final class Raw {
      final byte[] bytes;

      Raw(byte[] bytes) {
         this.bytes = bytes;
      }
   }

   /*
    * Deferred close. Calling connection.close() from inside decode() loses a
    * race: decode runs on the reader thread, whose loop exits the moment
    * closing is set and shuts the socket -- racing the writer thread that is
    * still draining the close frame those same bytes were meant to precede.
    * Queued AFTER the final bytes, this sentinel reaches encode() on the
    * writer thread only once they are flushed, and closes there.
    */
   private static final Object CLOSE_AFTER = new Object();

   private static final class State {
      boolean open;          // handshake completed
      boolean closeSent;     // close frame already queued; drop further output
      byte[] carry = new byte[0];   // unwrapped game bytes the inner codec has not consumed yet
   }

   private State state(Connection connection) {
      synchronized (this.states) {
         State s = this.states.get(connection);
         if (s == null) {
            s = new State();
            this.states.put(connection, s);
         }

         return s;
      }
   }

   /** Called by WebSocketHandler when the connection goes away. */
   void forget(Connection connection) {
      synchronized (this.states) {
         this.states.remove(connection);
      }
   }

   /** Visible for the handler's own bookkeeping and for tests. */
   int stateCount() {
      synchronized (this.states) {
         return this.states.size();
      }
   }

   public void decode(Connection connection, Buffer in, List<Object> out) throws Exception {
      State s = state(connection);

      if (!s.open) {
         if (!handshake(connection, s, in)) {
            return;   // incomplete or rejected; either way nothing more to do
         }
      }

      /* Frame loop: consume as many complete frames as arrived. */
      while (true) {
         int start = in.position();

         if (in.remaining() < 2) {
            return;
         }

         int b0 = in.getUnsigned();
         int b1 = in.getUnsigned();

         int opcode = b0 & 0x0F;
         boolean masked = (b1 & 0x80) != 0;
         long length = b1 & 0x7F;

         if (length == 126) {
            if (in.remaining() < 2) {
               in.position(start);
               return;
            }

            length = (in.getUnsigned() << 8) | in.getUnsigned();
         } else if (length == 127) {
            if (in.remaining() < 8) {
               in.position(start);
               return;
            }

            length = in.getLong();
         }

         if (length < 0 || length > MAX_FRAME) {
            close(connection, s, 1009 /* too big */);
            return;
         }

         /*
          * RFC 6455 5.1: a client MUST mask every frame, and a server MUST
          * close on an unmasked one. Not optional -- unmasked client frames
          * are how cache-poisoning through dumb proxies was done.
          */
         if (!masked) {
            close(connection, s, 1002 /* protocol error */);
            return;
         }

         if (in.remaining() < 4 + length) {
            in.position(start);
            return;
         }

         byte[] mask = new byte[4];
         in.get(mask);

         byte[] payload = new byte[(int) length];
         in.get(payload);
         for (int i = 0; i < payload.length; i++) {
            payload[i] ^= mask[i & 3];
         }

         switch (opcode) {
            case 0x0:   // continuation
            case 0x1:   // text -- tolerated and treated as bytes
            case 0x2:   // binary
               /*
                * Fragmentation (FIN unset) needs no special handling: the game
                * bytes are a stream, not per-frame messages, so fragments
                * simply arrive as smaller appends. FIN is ignored on purpose.
                */
               gameBytes(connection, s, payload, out);
               break;

            case 0x8:   // close
               close(connection, s, -1);   // echo, no payload of our own
               return;

            case 0x9:   // ping
               if (!s.closeSent) {
                  connection.write(new Raw(frame(0xA, payload)));
               }
               break;

            case 0xA:   // pong -- unsolicited pongs are legal and ignored
               break;

            default:
               close(connection, s, 1002);
               return;
         }
      }
   }

   /*
    * Append unmasked payload to the carry-over and let the game codec take
    * what it can. The carry-over exists because a game packet is free to span
    * WebSocket frames; whatever the inner codec leaves is kept for the next
    * frame. (Buffer.append/compact do this job for the transport, but they are
    * package-private to util.net -- deliberately -- so this layer keeps its own
    * remainder and re-wraps. The copy is a few hundred bytes at 20 packets a
    * second, which is nothing.)
    */
   private void gameBytes(Connection connection, State s, byte[] payload, List<Object> out) throws Exception {
      byte[] combined;
      if (s.carry.length == 0) {
         combined = payload;
      } else {
         combined = new byte[s.carry.length + payload.length];
         System.arraycopy(s.carry, 0, combined, 0, s.carry.length);
         System.arraycopy(payload, 0, combined, s.carry.length, payload.length);
      }

      Buffer game = Buffer.wrap(combined, combined.length);
      this.inner.decode(connection, game, out);
      s.carry = game.hasRemaining() ? game.toByteArray() : new byte[0];
   }

   /*
    * ---- handshake ----
    *
    * Returns true once the connection is upgraded and frames may follow in the
    * same buffer. Returns false when the request is incomplete (wait for more)
    * or rejected (connection is closing; the position is parked at the limit so
    * nothing further is parsed).
    */
   private boolean handshake(Connection connection, State s, Buffer in) {
      int start = in.position();

      byte[] have = in.toByteArray();   // copy of the unconsumed bytes; position untouched
      int end = indexOfHeaderEnd(have);

      if (end < 0) {
         if (have.length > MAX_HANDSHAKE) {
            reject(connection, s, in, "400 Bad Request", "handshake too large");
         }

         in.position(start);
         return false;
      }

      String request = new String(have, 0, end, StandardCharsets.ISO_8859_1);
      String key = null;
      boolean upgrade = false;
      boolean version13 = false;
      boolean get = request.startsWith("GET ");

      String[] lines = request.split("\r\n");
      for (int i = 1; i < lines.length; i++) {
         int colon = lines[i].indexOf(':');
         if (colon < 0) {
            continue;
         }

         String name = lines[i].substring(0, colon).trim().toLowerCase();
         String value = lines[i].substring(colon + 1).trim();

         if (name.equals("upgrade") && value.equalsIgnoreCase("websocket")) {
            upgrade = true;
         } else if (name.equals("sec-websocket-key")) {
            key = value;
         } else if (name.equals("sec-websocket-version")) {
            version13 = value.equals("13");
         }
      }

      if (!get || !upgrade || key == null || !version13) {
         reject(connection, s, in, "400 Bad Request", "not a websocket upgrade");
         return false;
      }

      String response = "HTTP/1.1 101 Switching Protocols\r\n"
         + "Upgrade: websocket\r\n"
         + "Connection: Upgrade\r\n"
         + "Sec-WebSocket-Accept: " + accept(key) + "\r\n"
         + "\r\n";

      connection.write(new Raw(response.getBytes(StandardCharsets.ISO_8859_1)));
      s.open = true;

      /* Consume exactly the request; frames may already sit behind it. */
      in.position(start + end);
      return true;
   }

   private void reject(Connection connection, State s, Buffer in, String status, String reason) {
      String response = "HTTP/1.1 " + status + "\r\n"
         + "Connection: close\r\n"
         + "Content-Length: " + (reason.length() + 1) + "\r\n"
         + "\r\n"
         + reason + "\n";

      s.closeSent = true;
      connection.write(new Raw(response.getBytes(StandardCharsets.ISO_8859_1)));
      connection.write(CLOSE_AFTER);

      /* Park the position at the limit: nothing else in this buffer matters. */
      in.position(in.position() + in.remaining());
   }

   private void close(Connection connection, State s, int code) {
      if (s.closeSent || connection.isClosing()) {
         return;
      }

      s.closeSent = true;

      byte[] payload;
      if (code > 0) {
         payload = new byte[] { (byte) (code >> 8), (byte) code };
      } else {
         payload = new byte[0];
      }

      connection.write(new Raw(frame(0x8, payload)));
      connection.write(CLOSE_AFTER);
   }

   private static int indexOfHeaderEnd(byte[] bytes) {
      for (int i = 3; i < bytes.length; i++) {
         if (bytes[i] == '\n' && bytes[i - 1] == '\r' && bytes[i - 2] == '\n' && bytes[i - 3] == '\r') {
            return i + 1;
         }
      }

      return -1;
   }

   static String accept(String key) {
      try {
         MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
         byte[] digest = sha1.digest((key + ACCEPT_GUID).getBytes(StandardCharsets.ISO_8859_1));
         return Base64.getEncoder().encodeToString(digest);
      } catch (NoSuchAlgorithmException e) {
         // SHA-1 is mandatory in every JRE; this cannot happen.
         throw new IllegalStateException(e);
      }
   }

   /* ---- encode: game packets go out as single unmasked binary frames ---- */

   public byte[] encode(Connection connection, Object message) throws Exception {
      if (message == CLOSE_AFTER) {
         /* The writer thread has flushed everything queued before this;
            closing here is what makes the close frame actually arrive. */
         connection.close();
         return null;
      }

      if (message instanceof Raw) {
         return ((Raw) message).bytes;
      }

      State s = state(connection);
      if (s.closeSent) {
         return null;   // nothing sails after a close frame
      }

      byte[] bytes = this.inner.encode(connection, message);
      if (bytes == null || bytes.length == 0) {
         return null;
      }

      return frame(0x2, bytes);
   }

   /** Build one FIN + opcode frame, unmasked (server-to-client is never masked). */
   private static byte[] frame(int opcode, byte[] payload) {
      int length = payload.length;
      byte[] out;
      int offset;

      if (length < 126) {
         out = new byte[2 + length];
         out[1] = (byte) length;
         offset = 2;
      } else if (length <= 0xFFFF) {
         out = new byte[4 + length];
         out[1] = 126;
         out[2] = (byte) (length >> 8);
         out[3] = (byte) length;
         offset = 4;
      } else {
         out = new byte[10 + length];
         out[1] = 127;
         for (int i = 0; i < 8; i++) {
            out[9 - i] = (byte) (length >>> (8 * i));
         }

         offset = 10;
      }

      out[0] = (byte) (0x80 | opcode);
      System.arraycopy(payload, 0, out, offset, length);
      return out;
   }
}
