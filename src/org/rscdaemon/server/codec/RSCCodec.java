package org.rscdaemon.server.codec;

import java.util.List;

import org.rscdaemon.server.net.RSCPacket;
import org.rscdaemon.server.util.Logger;
import org.rscdaemon.server.util.net.Buffer;
import org.rscdaemon.server.util.net.Codec;
import org.rscdaemon.server.util.net.Connection;

/*
 * The game protocol: client bytes in, RSCPackets out.
 *
 * Replaces RSCCodecFactory + RSCProtocolDecoder + RSCProtocolEncoder, which
 * were three files to hold one format. The framing is Jagex's and is carried
 * over unchanged:
 *
 *   a first byte under 160  -> that is the whole length
 *   160 or above            -> two-byte length, ((first - 160) * 256) + second
 *
 * and a short packet carries its LAST payload byte immediately after the
 * length, before the opcode. That is not a transcription error, it is the wire
 * format.
 *
 * Two bugs found in the MINA version were fixed on 2026-08-02 and stay fixed
 * here; both are described where they occur below.
 */
public final class RSCCodec implements Codec {

   public void decode(Connection connection, Buffer in, List<Object> out) {
      while (true) {
         if (in.remaining() < 2) {
            return;
         }

         /*
          * BUG 1 -- duplicated packets.
          *
          * The MINA decoder called in.rewind() on an incomplete packet, which
          * sets the position to ZERO rather than back to this packet's start.
          * Unconsumed data is retained from the current position, so rewinding
          * re-retained every packet already decoded out of the same read: one
          * TCP read of [complete][partial] re-delivered the complete one when
          * the rest arrived. In a game server a replayed packet is a replayed
          * action.
          */
         int start = in.position();

         /*
          * BUG 2 -- every inbound packet of 128 bytes or more threw.
          *
          * The length was read with get(), which returns a SIGNED byte, so
          * 128..255 arrived as -128..-1: the >= 160 test never fired and
          * new byte[length - 1] went negative. getUnsigned() is what the opcode
          * read below always used.
          */
         int length = in.getUnsigned();
         if (length >= 160) {
            length = (length - 160) * 256 + in.getUnsigned();
         }

         if (length < 1) {
            /* Unrecoverable: a zero length consumes nothing, so returning would
               stall this connection forever on a frame that can never be
               satisfied while the receive buffer grew without bound. */
            Logger.error("Malformed packet length " + length + " from "
               + connection.getRemoteAddress() + "; closing connection");
            connection.close();
            return;
         }

         if (length > in.remaining()) {
            in.position(start);
            return;
         }

         short id;
         byte[] payload = new byte[length - 1];
         if (length < 160) {
            if (length > 1) {
               payload[length - 2] = in.get();
               id = in.getUnsigned();
               if (length - 2 > 0) {
                  in.get(payload, 0, length - 2);
               }
            } else {
               id = in.getUnsigned();
            }
         } else {
            id = in.getUnsigned();
            in.get(payload);
         }

         out.add(new RSCPacket(connection, id, payload));
      }
   }

   public byte[] encode(Connection connection, Object message) {
      if (!(message instanceof RSCPacket)) {
         Logger.error(new Exception("Wrong packet type! " + message));
         return null;
      }

      RSCPacket p = (RSCPacket) message;
      byte[] data = p.getData();
      int dataLength = data.length;
      int packetLength = data.length;
      Buffer buffer;

      if (!p.isBare()) {
         buffer = Buffer.allocate(dataLength + 3);
         packetLength++;
         /*
          * BUG 3, found by FramingTest on 2026-08-02 and inherited from the
          * original RSCProtocolEncoder.
          *
          * This tested `data.length >= 160` while writing `packetLength`, which
          * is data.length + 1. At exactly 159 bytes of payload the two disagree:
          * packetLength is 160, the single-byte branch is taken, and 160 goes
          * out as the length byte -- which every decoder, ours and the client's
          * alike, reads as the marker for the TWO-byte form. The receiver then
          * takes the next payload byte as the low half of a length and every
          * following packet on that connection is misframed.
          *
          * The test must be on the value actually written.
          */
         if (packetLength >= 160) {
            buffer.put((byte) (160 + packetLength / 256));
            buffer.put((byte) (packetLength & 0xFF));
         } else {
            buffer.put((byte) packetLength);
            if (dataLength > 0) {
               buffer.put(data[--dataLength]);
            }
         }

         buffer.put((byte) p.getID());
      } else {
         buffer = Buffer.allocate(dataLength);
      }

      buffer.put(data, 0, dataLength);
      buffer.flip();
      return buffer.toByteArray();
   }
}
