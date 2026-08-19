package org.rscdaemon.ls.codec;

import java.util.List;

import org.rscdaemon.ls.Server;
import org.rscdaemon.ls.net.LSPacket;
import org.rscdaemon.server.util.net.Buffer;
import org.rscdaemon.server.util.net.Codec;
import org.rscdaemon.server.util.net.Connection;

/*
 * The login server's side of the link to game worlds -- the same wire format as
 * org.rscdaemon.server.codec.LSCodec, read from the other end.
 *
 * The two are near-duplicates and always were, because the packet classes they
 * build differ by package. Left as two files rather than unified through a
 * shared base, since the alternative is a generic that exists only to save
 * thirty lines and makes both ends harder to read.
 *
 * Same position-restore fix: rewind() on a short frame re-delivered frames
 * already handled, which here means a duplicated login or world registration.
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
            Server.error("Malformed LS packet length " + length + " from "
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
         out.add(new LSPacket(connection, id, uid, payload));
      }
   }

   public byte[] encode(Connection connection, Object message) {
      if (!(message instanceof LSPacket)) {
         Server.error(new Exception("Wrong packet type! " + message));
         return null;
      }

      LSPacket p = (LSPacket) message;
      byte[] data = p.getData();
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
