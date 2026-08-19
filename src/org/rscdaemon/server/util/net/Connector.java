package org.rscdaemon.server.util.net;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketAddress;

/*
 * The outbound half: MINA's SocketConnector and ConnectFuture.
 *
 * Used once, by the game server's LoginConnector dialling the login server. The
 * future is gone with it -- that call already blocked on
 * future.join()/isConnected(), so connect() simply returns the Connection or
 * throws, which is what the caller wanted in the first place.
 */
public final class Connector {

   private final Handler handler;
   private final Codec codec;

   public Connector(Handler handler, Codec codec) {
      this.handler = handler;
      this.codec = codec;
   }

   public Connection connect(SocketAddress address, int timeoutMillis) throws IOException {
      Socket socket = new Socket();
      socket.connect(address, timeoutMillis);

      SocketConnection connection = new SocketConnection(socket, this.handler, this.codec);
      connection.start("ls-link");
      return connection;
   }
}
