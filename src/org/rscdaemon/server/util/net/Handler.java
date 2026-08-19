package org.rscdaemon.server.util.net;

/*
 * Connection lifecycle callbacks -- MINA's IoHandler, reduced to what the
 * server implements.
 *
 * Named for what happens rather than for a session, because there is no session
 * object any more; a Connection is the thing.
 */
public interface Handler {

   /** Before any data. Attach the Player or World here. */
   void connectionOpened(Connection connection);

   /** A fully decoded message -- an RSCPacket or LSPacket, never raw bytes. */
   void messageReceived(Connection connection, Object message);

   void messageSent(Connection connection, Object message);

   void connectionClosed(Connection connection);

   /** No traffic for the connection's idle time. */
   void connectionIdle(Connection connection);

   void exceptionCaught(Connection connection, Throwable cause);
}
