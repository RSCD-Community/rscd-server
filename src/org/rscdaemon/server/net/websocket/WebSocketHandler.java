package org.rscdaemon.server.net.websocket;

import org.rscdaemon.server.util.net.Connection;
import org.rscdaemon.server.util.net.Handler;

/*
 * Delegates every lifecycle event to the real game handler, and does one job of
 * its own: when a connection dies, the codec's per-connection state dies with
 * it. The codec cannot see closes -- Codec has no lifecycle methods, by design
 * -- so the cleanup has to ride the Handler, and this wrapper is what keeps
 * that wiring an implementation detail of the websocket package instead of a
 * chore for whoever assembles the acceptor.
 *
 * Order matters in connectionClosed: the game handler runs first, because it
 * may still want the connection's identity for its own teardown; the codec
 * state is dropped after, and unconditionally, even if the game handler threw.
 */
public final class WebSocketHandler implements Handler {

   private final Handler game;
   private final WebSocketCodec codec;

   public WebSocketHandler(Handler game, WebSocketCodec codec) {
      this.game = game;
      this.codec = codec;
   }

   public void connectionOpened(Connection connection) {
      this.game.connectionOpened(connection);
   }

   public void messageReceived(Connection connection, Object message) {
      this.game.messageReceived(connection, message);
   }

   public void messageSent(Connection connection, Object message) {
      this.game.messageSent(connection, message);
   }

   public void connectionClosed(Connection connection) {
      try {
         this.game.connectionClosed(connection);
      } finally {
         this.codec.forget(connection);
      }
   }

   public void connectionIdle(Connection connection) {
      this.game.connectionIdle(connection);
   }

   public void exceptionCaught(Connection connection, Throwable cause) {
      this.game.exceptionCaught(connection, cause);
   }
}
