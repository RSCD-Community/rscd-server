package org.rscdaemon.ls.codec;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.List;

import org.rscdaemon.ls.Server;
import org.rscdaemon.ls.net.FPacket;
import org.rscdaemon.server.util.net.Buffer;
import org.rscdaemon.server.util.net.Codec;
import org.rscdaemon.server.util.net.Connection;

/*
 * The status/query endpoint's protocol: a line of text.
 *
 *   "<id> <param> <param> ..."
 *
 * with each parameter URL-encoded, so a value may contain spaces. Not framed at
 * all -- whatever arrived in one read is treated as one request. That was true
 * of the MINA version too and is preserved rather than improved: this endpoint
 * answers short one-shot queries, and inventing framing here would change a
 * protocol that something outside this repository may be speaking.
 */
public final class FCodec implements Codec {

   public void decode(Connection connection, Buffer in, List<Object> out) {
      if (in.remaining() <= 0) {
         return;
      }

      byte[] raw = new byte[in.remaining()];
      in.get(raw);

      try {
         String s = new String(raw, "UTF-8").trim();
         if (s.length() == 0) {
            return;
         }

         int id;
         String[] params;
         int delim = s.indexOf(" ");
         if (delim > -1) {
            id = Integer.parseInt(s.substring(0, delim));
            params = s.substring(delim + 1).split(" ");
         } else {
            id = Integer.parseInt(s);
            params = new String[0];
         }

         for (int i = 0; i < params.length; i++) {
            params[i] = URLDecoder.decode(params[i], "UTF-8");
         }

         out.add(new FPacket(connection, id, params));
      } catch (Exception e) {
         /* A malformed request is the caller's problem, not a server fault.
            The bytes are already consumed, so the connection stays in step. */
         Server.error(e);
      }
   }

   public byte[] encode(Connection connection, Object message) {
      if (!(message instanceof FPacket)) {
         Server.error(new Exception("Wrong packet type! " + message));
         return null;
      }

      FPacket p = (FPacket) message;

      try {
         StringBuilder s = new StringBuilder(String.valueOf(p.getID()));
         if (p.countParameters() > 0) {
            String[] params = p.getParameters();
            for (int i = 0; i < params.length; i++) {
               s.append(' ').append(URLEncoder.encode(params[i], "UTF-8"));
            }
         }

         return s.toString().getBytes("UTF-8");
      } catch (UnsupportedEncodingException e) {
         Server.error(e);
         return null;
      }
   }
}
