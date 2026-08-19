package org.rscdaemon.server.util.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/*
 * One connection, two threads: a reader and a writer.
 *
 * WHY NOT A SELECTOR. MINA multiplexed every connection onto a small NIO pool.
 * That is the right design for tens of thousands of connections and overkill
 * for this: Config.MAX_PLAYERS defaults to 75, and the largest RSC server that
 * ever existed would not trouble a thread pair per player. Blocking sockets are
 * dramatically easier to get right, and "easier to get right" is worth more
 * here than a scalability ceiling nobody will reach. If that assumption ever
 * breaks, this class is the only thing that has to change.
 *
 * WHY A WRITER THREAD. Writes come from the game loop. A blocking write to one
 * slow client would stall every player, which is the failure MINA's write queue
 * existed to prevent -- so the queue is kept. write() hands the message over and
 * returns; the writer thread encodes and sends. A queue that stays full past the
 * write timeout means the peer has stopped reading, and the connection is
 * dropped rather than allowed to consume memory forever.
 *
 * IDLE is driven by the read timeout rather than a scheduler: the socket wakes
 * every idle period, and if nothing has been sent OR received since the last
 * check the handler is told. Tracking writes too matches MINA's BOTH_IDLE,
 * which is what the one caller asked for.
 */
public final class SocketConnection implements Connection {

   /* Enough to absorb a burst without letting a dead peer eat the heap. */
   private static final int WRITE_QUEUE_DEPTH = 512;
   private static final int READ_CHUNK = 4096;

   private final Socket socket;
   private final Handler handler;
   private final Codec codec;
   private final BlockingQueue<Object> outbound = new ArrayBlockingQueue<Object>(WRITE_QUEUE_DEPTH);
   private final AtomicBoolean closing = new AtomicBoolean(false);
   private final AtomicBoolean closedFired = new AtomicBoolean(false);

   private volatile Object attachment;
   private volatile int idleSeconds;
   private volatile int writeTimeoutSeconds = 30;
   private volatile long lastActivity = System.currentTimeMillis();

   private Thread reader;
   private Thread writer;

   /*
    * Told once, when this connection is finished with. The Acceptor uses it to
    * forget the connection; without it the accepted set only ever grew, and
    * because every game connection has a Player attached the leak was a Player
    * per connection for the lifetime of the process.
    */
   private volatile Runnable closeListener;

   public SocketConnection(Socket socket, Handler handler, Codec codec) throws IOException {
      this.socket = socket;
      this.handler = handler;
      this.codec = codec;
      socket.setTcpNoDelay(true);
   }

   void setCloseListener(Runnable listener) {
      this.closeListener = listener;
   }

   void start(String name) {
      this.reader = new Thread(new Runnable() {
         public void run() {
            readLoop();
         }
      }, name + "-reader");

      this.writer = new Thread(new Runnable() {
         public void run() {
            writeLoop();
         }
      }, name + "-writer");

      this.reader.setDaemon(true);
      this.writer.setDaemon(true);

      try {
         this.handler.connectionOpened(this);
      } catch (Throwable t) {
         fireException(t);
      }

      this.reader.start();
      this.writer.start();
   }

   /*
    * ---- Connection ----
    */

   public void write(Object message) {
      if (message == null || this.closing.get()) {
         return;
      }

      if (!this.outbound.offer(message)) {
         /* The queue is full: this peer is not reading. Dropping the
            connection is the only honest option -- silently discarding
            packets would desync the client, and blocking here would stall
            the game loop for everyone. */
         Logger("write queue full for " + getRemoteAddress() + "; dropping connection");
         close();
      }
   }

   public int pendingWrites() {
      return this.outbound.size();
   }

   public void close() {
      if (this.closing.compareAndSet(false, true)) {
         // Wake the writer so it notices and drains.
         this.outbound.offer(Boolean.FALSE);
      }
   }

   public boolean isClosing() {
      return this.closing.get();
   }

   public boolean isConnected() {
      return !this.closing.get() && this.socket.isConnected() && !this.socket.isClosed();
   }

   public SocketAddress getRemoteAddress() {
      return this.socket.getRemoteSocketAddress();
   }

   public Object getAttachment() {
      return this.attachment;
   }

   public void setAttachment(Object attachment) {
      this.attachment = attachment;
   }

   public void setIdleTime(int seconds) {
      this.idleSeconds = seconds;

      try {
         // The read timeout IS the idle tick; 0 means block forever.
         this.socket.setSoTimeout(seconds * 1000);
      } catch (IOException e) {
         fireException(e);
      }
   }

   public void setWriteTimeout(int seconds) {
      this.writeTimeoutSeconds = seconds;
   }

   /*
    * ---- reader ----
    */
   private void readLoop() {
      Buffer cumulative = Buffer.allocate(READ_CHUNK);
      byte[] chunk = new byte[READ_CHUNK];
      List<Object> decoded = new ArrayList<Object>();

      try {
         InputStream in = this.socket.getInputStream();

         while (!this.closing.get()) {
            int read;
            try {
               read = in.read(chunk);
            } catch (SocketTimeoutException e) {
               // No bytes for one idle period. Only actually idle if nothing
               // has been written either.
               if (this.idleSeconds > 0
                     && System.currentTimeMillis() - this.lastActivity >= this.idleSeconds * 1000L) {
                  this.handler.connectionIdle(this);
               }

               continue;
            }

            if (read < 0) {
               break;   // peer closed
            }
            if (read == 0) {
               continue;
            }

            this.lastActivity = System.currentTimeMillis();
            cumulative.append(chunk, read);

            decoded.clear();
            this.codec.decode(this, cumulative, decoded);
            /* Everything the codec did not consume stays for the next read.
               The codec contract is that position is left at the first
               unconsumed byte -- see Codec. */
            cumulative.compact();

            for (int i = 0; i < decoded.size(); i++) {
               try {
                  this.handler.messageReceived(this, decoded.get(i));
               } catch (Throwable t) {
                  fireException(t);
               }
            }
         }
      } catch (Throwable t) {
         if (!this.closing.get()) {
            fireException(t);
         }
      } finally {
         close();
         shutdown();
      }
   }

   /*
    * ---- writer ----
    */
   private void writeLoop() {
      try {
         OutputStream out = this.socket.getOutputStream();

         while (true) {
            Object message = this.outbound.poll(this.writeTimeoutSeconds > 0
               ? this.writeTimeoutSeconds : 30, TimeUnit.SECONDS);

            if (message == null) {
               if (this.closing.get()) {
                  break;
               }

               continue;   // nothing to send; not an error
            }

            if (message == Boolean.FALSE) {
               break;      // the close() wake-up
            }

            byte[] bytes;
            try {
               bytes = this.codec.encode(this, message);
            } catch (Throwable t) {
               fireException(t);
               continue;   // a bad message must not kill the connection
            }

            if (bytes != null && bytes.length > 0) {
               out.write(bytes);
               out.flush();
               this.lastActivity = System.currentTimeMillis();
            }

            try {
               this.handler.messageSent(this, message);
            } catch (Throwable t) {
               fireException(t);
            }
         }
      } catch (Throwable t) {
         if (!this.closing.get()) {
            fireException(t);
         }
      } finally {
         close();
         shutdown();
      }
   }

   /*
    * ---- teardown ----
    *
    * Either thread may get here first; connectionClosed fires exactly once.
    */
   private void shutdown() {
      try {
         this.socket.close();
      } catch (IOException e) {
         // already gone
      }

      if (this.closedFired.compareAndSet(false, true)) {
         /* Before the handler, and outside its try: the bookkeeping must
            happen even if connectionClosed throws, or a handler that fails
            once reintroduces the leak it is here to prevent. */
         Runnable listener = this.closeListener;
         if (listener != null) {
            try {
               listener.run();
            } catch (Throwable ignored) {
               // nothing useful to do; the connection is going away
            }
         }

         try {
            this.handler.connectionClosed(this);
         } catch (Throwable t) {
            fireException(t);
         }
      }
   }

   private void fireException(Throwable t) {
      try {
         this.handler.exceptionCaught(this, t);
      } catch (Throwable ignored) {
         // A handler that throws from its own error path gets nothing further.
      }
   }

   /* Kept local so this package does not depend on either server's Logger. */
   private static void Logger(String message) {
      System.err.println("[net] " + message);
   }
}
