package org.rscdaemon.server.util.net;

import java.util.List;

/*
 * Turns bytes into messages and back.
 *
 * decode() replaces MINA's CumulativeProtocolDecoder. That class did one useful
 * thing -- hold onto a partial packet until the rest arrives -- and the
 * transport does it here instead: decode() is called with everything received
 * so far, consumes as many whole packets as it can, and leaves the position at
 * the start of the first incomplete one. Whatever is left is carried into the
 * next call.
 *
 * That contract is the entire subtlety. Leaving the position anywhere other
 * than the start of the unconsumed remainder either loses packets or repeats
 * them -- repeating them is precisely the bug the MINA decoders had, because
 * they called rewind() (position = 0) instead of restoring the packet's own
 * start.
 */
public interface Codec {

   /**
    * Consume as many complete messages from {@code in} as are available,
    * appending each to {@code out}. On return the position must be at the first
    * byte not yet consumed.
    */
   void decode(Connection connection, Buffer in, List<Object> out) throws Exception;

   /** Serialise one message. Returning null writes nothing. */
   byte[] encode(Connection connection, Object message) throws Exception;
}
