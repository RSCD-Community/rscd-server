package org.rscdaemon.server.net.websocket;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.rscdaemon.server.util.net.Acceptor;
import org.rscdaemon.server.util.net.Codec;
import org.rscdaemon.server.util.net.Handler;

/*
 * Assembles the websocket listener: the same game handler and game codec the
 * TCP port uses, with the websocket layer wrapped around them. Everything a
 * caller needs is one line in the server bootstrap, right next to the TCP
 * acceptor it mirrors:
 *
 *    this.wsAcceptor = WebSocketBridge.bind(
 *       new RSCConnectionHandler(this.engine), new RSCCodec(),
 *       Config.SERVER_IP, 43595);
 *
 * A player arriving here is indistinguishable from one arriving over TCP by
 * the time the game sees them; the difference lives and dies inside this
 * package.
 *
 * The bridge speaks plain ws://. Browsers on an https page require wss://,
 * which is TLS's job, not the game server's -- terminate it in the same
 * reverse proxy that already fronts rscd-www and forward the raw stream here.
 */
public final class WebSocketBridge {

   private WebSocketBridge() {
      // static assembly only
   }

   /**
    * Binds a websocket listener and returns its acceptor, so the caller can
    * unbind it in the same breath as the TCP one on shutdown.
    */
   public static Acceptor bind(Handler gameHandler, Codec gameCodec, String ip, int port)
         throws IOException {
      WebSocketCodec codec = new WebSocketCodec(gameCodec);
      Acceptor acceptor = new Acceptor(new WebSocketHandler(gameHandler, codec), codec);
      acceptor.bind(new InetSocketAddress(ip, port));
      return acceptor;
   }
}
