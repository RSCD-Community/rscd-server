package org.rscdaemon.server.util.net;

import java.net.SocketAddress;

/*
 * One network connection.
 *
 * This is the shape MINA's IoSession was actually used in. A survey of every
 * call site across the server found nine methods in use out of IoSession's
 * several dozen -- getAttachment (51 calls), write (21), getRemoteAddress (7),
 * isClosing (4), close (4), setAttachment (3), isConnected, setIdleTime and
 * setWriteTimeout. Eighty-four of the 103 files that imported MINA imported it
 * for nothing but this type, and used it purely as an opaque handle to pass
 * around.
 *
 * So the framework was never really woven through the server; one interface was.
 */
public interface Connection {

   /** Queues a message for the codec to encode and the writer to send. */
   void write(Object message);

   /**
    * How many messages are queued but not yet on the wire.
    *
    * write() hands off to the writer thread, so a caller that has to know its
    * packets really left -- the shutdown path, which is racing the JVM -- has
    * no other way to find out. Nothing in normal operation should need this.
    */
   int pendingWrites();

   void close();

   /** True once close() has been called, whether or not the socket has gone. */
   boolean isClosing();

   boolean isConnected();

   SocketAddress getRemoteAddress();

   /** The Player or World this connection belongs to. */
   Object getAttachment();

   void setAttachment(Object attachment);

   /**
    * Seconds of inactivity after which the handler is told the connection is
    * idle. Zero disables it.
    */
   void setIdleTime(int seconds);

   /** Seconds a queued write may wait before the connection is considered dead. */
   void setWriteTimeout(int seconds);
}
