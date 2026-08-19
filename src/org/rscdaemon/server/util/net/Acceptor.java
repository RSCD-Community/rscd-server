package org.rscdaemon.server.util.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/*
 * Listens on a port and hands each accepted socket to a Handler.
 *
 * MINA's SocketAcceptor, minus the parts nobody used. bind() was called in
 * exactly two places -- Server and LoginConnector -- and unbind() in one, from
 * the in-game admin command that shuts the world down.
 */
public final class Acceptor {

   private final Handler handler;
   private final Codec codec;
   private final Set<Connection> connections =
      Collections.synchronizedSet(new HashSet<Connection>());

   private ServerSocket serverSocket;
   private Thread acceptThread;
   private volatile boolean running;

   public Acceptor(Handler handler, Codec codec) {
      this.handler = handler;
      this.codec = codec;
   }

   public void bind(SocketAddress address) throws IOException {
      this.serverSocket = new ServerSocket();
      this.serverSocket.setReuseAddress(true);
      this.serverSocket.bind(address);
      this.running = true;

      final String label = describe(address);
      this.acceptThread = new Thread(new Runnable() {
         public void run() {
            acceptLoop(label);
         }
      }, "acceptor-" + label);
      this.acceptThread.setDaemon(true);
      this.acceptThread.start();
   }

   private void acceptLoop(String label) {
      while (this.running) {
         Socket socket;
         try {
            socket = this.serverSocket.accept();
         } catch (IOException e) {
            if (this.running) {
               this.handler.exceptionCaught(null, e);
            }

            /* An accept failure is usually the socket being closed by
               unbind(). If it is not, spinning on a broken listener would
               burn a core, so stop either way. */
            return;
         }

         try {
            final SocketConnection connection = new SocketConnection(socket, this.handler, this.codec);
            /* Registered before start() so that a connection which dies during
               its own opening still takes itself back out again. */
            connection.setCloseListener(new Runnable() {
               public void run() {
                  Acceptor.this.connections.remove(connection);
               }
            });
            this.connections.add(connection);

            try {
               connection.start(label + "-" + socket.getPort());
            } catch (RuntimeException e) {
               /* start() is not declared to throw, but it does start threads
                  and call into the handler. If it fails the reader and writer
                  may never run, so nothing would ever fire the close listener
                  and the entry would sit in the set forever. Take it out here,
                  drop this one socket, and keep listening -- one bad accept
                  must not take the whole acceptor down. */
               this.connections.remove(connection);
               this.handler.exceptionCaught(null, e);
               connection.close();

               try {
                  socket.close();
               } catch (IOException ignored) {
                  // nothing useful to do
               }
            }
         } catch (IOException e) {
            this.handler.exceptionCaught(null, e);
            try {
               socket.close();
            } catch (IOException ignored) {
               // nothing useful to do
            }
         }
      }
   }

   /**
    * Stops listening and drops every live connection -- MINA's
    * setDisconnectOnUnbind(true), which is what this server configured.
    */
   public void unbind() {
      this.running = false;

      try {
         if (this.serverSocket != null) {
            this.serverSocket.close();
         }
      } catch (IOException e) {
         // going away regardless
      }

      Connection[] live;
      synchronized (this.connections) {
         live = this.connections.toArray(new Connection[this.connections.size()]);
      }

      for (int i = 0; i < live.length; i++) {
         live[i].close();
      }

      this.connections.clear();
   }

   public int connectionCount() {
      return this.connections.size();
   }

   private static String describe(SocketAddress address) {
      if (address instanceof InetSocketAddress) {
         return String.valueOf(((InetSocketAddress) address).getPort());
      }

      return String.valueOf(address);
   }
}
