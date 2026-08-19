package org.rscdaemon.server.codec;

import java.util.List;

import org.rscdaemon.server.net.LSPacket;
import org.rscdaemon.server.util.Logger;
import org.rscdaemon.server.util.net.Buffer;
import org.rscdaemon.server.util.net.Codec;
import org.rscdaemon.server.util.net.Connection;

/*
 * The game server's side of the link to the login server.
 *
 * Frame: 4-byte length, 1-byte opcode, 8-byte uid, then payload. The length
 * counts the opcode, the uid and the payload -- hence the 9 that keeps
 * appearing.
 *
 * Carries the same position-restore fix as the game codec: the MINA version
 * called rewind() on a short frame, which re-delivered frames already handled.
 * On this link that means a duplicated login, world registration or player
 * count.
 */
public final class LSCodec implements Codec {

   public void decode(Connection connection, Buffer in, List<Object> out) {
      while (true) {
         if (in.remaining() < 13) {
            return;
         }

         int start = in.position();
         int length = in.getInt();

         if (length < 9) {
            // A frame shorter than its own header: nothing after this point in
            // the stream can be trusted.
            Logger.error("Malformed LS packet length " + length + " from "
               + connection.getRemoteAddress() + "; closing connection");
            connection.close();
            return;
         }

         if (length > in.remaining()) {
            in.position(start);
            return;
         }

         byte[] payload = new byte[length - 9];
         short id = in.getUnsigned();
         long uid = in.getLong();
         in.get(payload);
         if (System.getProperty("rscd.lsdebug") != null) {
            Logger.print("[lsdebug] decoded id=" + id + " uid=" + uid
               + " payload=" + payload.length + " frameLength=" + length);
         }

         out.add(new LSPacket(connection, id, uid, payload));
      }
   }

   public byte[] encode(Connection connection, Object message) {
      if (!(message instanceof LSPacket)) {
         Logger.error(new Exception("Wrong packet type! " + message));
         return null;
      }

      LSPacket p = (LSPacket) message;
      byte[] data = p.getData();
      if (System.getProperty("rscd.lsdebug") != null) {
         Logger.print("[lsdebug] encoding id=" + p.getID() + " bare=" + p.isBare()
            + " payload=" + data.length);
      }

      Buffer buffer;

      if (!p.isBare()) {
         buffer = Buffer.allocate(data.length + 13);
         buffer.putInt(data.length + 9);
         buffer.put((byte) p.getID());
         buffer.putLong(p.getUID());
      } else {
         buffer = Buffer.allocate(data.length);
      }

      buffer.put(data, 0, data.length);
      buffer.flip();
      return buffer.toByteArray();
   }
}
