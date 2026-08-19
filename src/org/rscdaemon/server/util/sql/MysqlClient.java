package org.rscdaemon.server.util.sql;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/*
 * A MySQL client, written here rather than taken from a jar.
 *
 * WHY. The server carried Connector/J built 31 May 2006 -- JDBC 3.0, class file
 * version 46, older than the MySQL 5.7 it talks to by nine years. It is the last
 * third-party code in the login path, and it sits between the internet and the
 * players' credentials. A driver that age cannot negotiate a modern TLS suite,
 * so in practice the account table was being read over a cleartext connection.
 *
 * WHAT IT IMPLEMENTS. Only what this project asks of a database, which turned
 * out to be very little. Every query in the tree goes through
 * ls/net/DatabaseConnection, and every caller uses exactly five things: next(),
 * getInt(name), getLong(name), getString(name), close(). There is not one
 * PreparedStatement anywhere. So this speaks:
 *
 *   - protocol 10 handshake, CLIENT_PROTOCOL_41
 *   - mysql_native_password authentication (what the server actually offers --
 *     verified against it, not assumed)
 *   - optional TLS, by upgrading the socket after an SSLRequest packet
 *   - COM_QUERY, and the TEXT protocol result set
 *   - OK, ERR and EOF packets
 *
 * The text protocol matters: every value arrives as a length-encoded string, so
 * getInt is a parse rather than a type negotiation. That is also why this needs
 * no equivalent of Connector/J's tinyInt1isBit workaround -- there is no
 * TINYINT(1)-to-Boolean mapping to defeat, because there is no type mapping at
 * all.
 *
 * WHAT IT DOES NOT DO. Prepared statements, transactions, batching, stored
 * procedures, multi-statement, LOCAL INFILE, compression, caching_sha2_password.
 * None of them are used here, and a database client that implements what it is
 * asked for and nothing else is the point of the exercise. If any of those is
 * ever needed, add it deliberately.
 */
public final class MysqlClient {

   /* Capability flags. Only what is needed is requested. */
   private static final int CLIENT_LONG_PASSWORD = 0x00000001;
   private static final int CLIENT_CONNECT_WITH_DB = 0x00000008;
   private static final int CLIENT_PROTOCOL_41 = 0x00000200;
   private static final int CLIENT_SSL = 0x00000800;
   private static final int CLIENT_TRANSACTIONS = 0x00002000;
   private static final int CLIENT_SECURE_CONNECTION = 0x00008000;
   private static final int CLIENT_PLUGIN_AUTH = 0x00080000;

   /*
    * CLIENT_DEPRECATE_EOF is deliberately NOT requested. With it, the server
    * replaces EOF packets with OK packets and the reader has to disambiguate
    * 0xFE by packet length. Leaving it off keeps result-set parsing to the
    * classic, unambiguous shape.
    */

   private static final int MAX_PACKET = 16777215;

   /** No TLS. What Connector/J was effectively doing: credentials in clear. */
   public static final int TLS_OFF = 0;
   /**
    * TLS, with the server's certificate verified against the JVM trust store or
    * a CA file supplied via {@link #setTrustStore}. The only mode that resists
    * an active attacker.
    */
   public static final int TLS_VERIFY = 1;
   /**
    * TLS, accepting whatever certificate the server presents.
    *
    * This exists because MySQL generates a SELF-SIGNED certificate on first
    * start, and that is what a stock 5.7 install serves -- no public CA will
    * ever vouch for it, so TLS_VERIFY fails against an out-of-the-box server
    * with "unable to find valid certification path".
    *
    * Be clear about what this buys: it encrypts the connection, so a passive
    * eavesdropper on the network no longer reads the password and the account
    * table. It does NOT authenticate the server, so it does not stop an active
    * machine-in-the-middle. It is strictly better than TLS_OFF and strictly
    * worse than TLS_VERIFY. The real fix is to copy the server's ca.pem and
    * point setTrustStore at it.
    */
   public static final int TLS_TRUST_ANY = 2;

   private final String host;
   private final int port;
   private final String database;
   private final String user;
   private final String password;
   private final int tlsMode;
   private String trustStorePath;
   private String trustStorePassword;

   private Socket socket;
   private DataInputStream in;
   private OutputStream out;
   private int sequence;
   private String serverVersion = "";
   private boolean tlsActive;

   public MysqlClient(String host, int port, String database, String user, String password, int tlsMode) {
      this.host = host;
      this.port = port;
      this.database = database;
      this.user = user;
      this.password = password;
      this.tlsMode = tlsMode;
   }

   /** A JKS or PKCS12 trust store holding the server's CA, for TLS_VERIFY. */
   public void setTrustStore(String path, String password) {
      this.trustStorePath = path;
      this.trustStorePassword = password;
   }

   public String serverVersion() {
      return this.serverVersion;
   }

   public boolean isTls() {
      return this.tlsActive;
   }

   public boolean isOpen() {
      return this.socket != null && this.socket.isConnected() && !this.socket.isClosed();
   }

   /*
    * ---- connect ----
    */
   public void connect(int timeoutMillis) throws MysqlException {
      try {
         this.socket = new Socket();
         this.socket.connect(new InetSocketAddress(this.host, this.port), timeoutMillis);
         this.socket.setTcpNoDelay(true);
         this.socket.setSoTimeout(timeoutMillis);
         this.in = new DataInputStream(new BufferedInputStream(this.socket.getInputStream()));
         this.out = this.socket.getOutputStream();

         Packet greeting = read();
         Handshake hs = parseHandshake(greeting);
         this.serverVersion = hs.serverVersion;

         int caps = CLIENT_LONG_PASSWORD | CLIENT_PROTOCOL_41 | CLIENT_SECURE_CONNECTION
            | CLIENT_TRANSACTIONS | CLIENT_PLUGIN_AUTH;
         if (this.database != null && this.database.length() > 0) {
            caps |= CLIENT_CONNECT_WITH_DB;
         }

         if (this.tlsMode != TLS_OFF) {
            if ((hs.capabilities & CLIENT_SSL) == 0) {
               throw new MysqlException("TLS requested but the server does not offer CLIENT_SSL");
            }

            caps |= CLIENT_SSL;
            // The SSLRequest packet is the handshake response truncated right
            // after the reserved block; everything after it goes encrypted.
            Writer ssl = new Writer();
            ssl.int4(caps);
            ssl.int4(MAX_PACKET);
            ssl.int1(hs.charset);
            ssl.zero(23);
            write(ssl.toByteArray());

            SSLSocket tls = (SSLSocket) socketFactory()
               .createSocket(this.socket, this.host, this.port, true);
            tls.setUseClientMode(true);
            tls.startHandshake();
            this.socket = tls;
            this.in = new DataInputStream(new BufferedInputStream(tls.getInputStream()));
            this.out = tls.getOutputStream();
            this.tlsActive = true;
         }

         if (!"mysql_native_password".equals(hs.authPlugin) && hs.authPlugin.length() > 0) {
            throw new MysqlException("unsupported auth plugin '" + hs.authPlugin
               + "'; this client implements mysql_native_password only");
         }

         Writer w = new Writer();
         w.int4(caps);
         w.int4(MAX_PACKET);
         w.int1(hs.charset);
         w.zero(23);
         w.nulString(this.user);

         byte[] token = nativePassword(this.password, hs.salt);
         w.int1(token.length);
         w.bytes(token);

         if ((caps & CLIENT_CONNECT_WITH_DB) != 0) {
            w.nulString(this.database);
         }

         w.nulString("mysql_native_password");
         write(w.toByteArray());

         Packet response = read();
         if (response.isErr()) {
            throw error(response);
         }
         if (response.isAuthSwitch()) {
            throw new MysqlException("server asked to switch auth plugin; only "
               + "mysql_native_password is implemented");
         }
      } catch (IOException e) {
         closeQuietly();
         throw new MysqlException("connect to " + this.host + ":" + this.port + " failed: " + e, e);
      } catch (MysqlException e) {
         closeQuietly();
         throw e;
      } catch (Exception e) {
         closeQuietly();
         throw new MysqlException("connect failed: " + e, e);
      }
   }

   private SSLSocketFactory socketFactory() throws Exception {
      if (this.tlsMode == TLS_TRUST_ANY) {
         javax.net.ssl.SSLContext ctx = javax.net.ssl.SSLContext.getInstance("TLS");
         ctx.init(null, new javax.net.ssl.TrustManager[] { new javax.net.ssl.X509TrustManager() {
            public void checkClientTrusted(java.security.cert.X509Certificate[] c, String t) {
            }

            public void checkServerTrusted(java.security.cert.X509Certificate[] c, String t) {
               // Deliberately unchecked -- see TLS_TRUST_ANY.
            }

            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
               return new java.security.cert.X509Certificate[0];
            }
         } }, new java.security.SecureRandom());
         return ctx.getSocketFactory();
      }

      if (this.trustStorePath != null) {
         java.security.KeyStore ks = java.security.KeyStore.getInstance(
            this.trustStorePath.toLowerCase().endsWith(".p12") ? "PKCS12" : "JKS");
         java.io.InputStream ksIn = new java.io.FileInputStream(this.trustStorePath);
         try {
            ks.load(ksIn, this.trustStorePassword == null ? null : this.trustStorePassword.toCharArray());
         } finally {
            ksIn.close();
         }

         javax.net.ssl.TrustManagerFactory tmf = javax.net.ssl.TrustManagerFactory
            .getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
         tmf.init(ks);
         javax.net.ssl.SSLContext ctx = javax.net.ssl.SSLContext.getInstance("TLS");
         ctx.init(null, tmf.getTrustManagers(), new java.security.SecureRandom());
         return ctx.getSocketFactory();
      }

      return (SSLSocketFactory) SSLSocketFactory.getDefault();
   }

   /**
    * mysql_native_password:
    *   SHA1(password) XOR SHA1( salt + SHA1(SHA1(password)) )
    * The password itself never crosses the wire, but note this is a challenge
    * over the connection -- it is not a substitute for TLS.
    */
   private static byte[] nativePassword(String password, byte[] salt) throws Exception {
      if (password == null || password.length() == 0) {
         return new byte[0];
      }

      MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
      byte[] stage1 = sha1.digest(password.getBytes("UTF-8"));
      sha1.reset();
      byte[] stage2 = sha1.digest(stage1);
      sha1.reset();
      sha1.update(salt);
      sha1.update(stage2);
      byte[] scrambled = sha1.digest();

      byte[] token = new byte[stage1.length];
      for (int i = 0; i < stage1.length; i++) {
         token[i] = (byte) (stage1[i] ^ scrambled[i]);
      }

      return token;
   }

   /*
    * ---- queries ----
    */

   /** COM_QUERY returning rows. */
   public synchronized Rows query(String sql) throws MysqlException {
      Packet first = command(sql);

      if (first.isErr()) {
         throw error(first);
      }
      if (first.isOk()) {
         // A statement that returned no result set where one was expected.
         return new Rows(new String[0], new ArrayList<String[]>());
      }

      Reader r = new Reader(first);
      int columns = (int) r.lenencInt();

      String[] names = new String[columns];
      for (int i = 0; i < columns; i++) {
         Reader def = new Reader(read());
         def.lenencString();   // catalog
         def.lenencString();   // schema
         def.lenencString();   // table
         def.lenencString();   // org_table
         names[i] = def.lenencString();   // name -- the only field callers use
      }

      Packet afterColumns = read();
      if (!afterColumns.isEof()) {
         // Without CLIENT_DEPRECATE_EOF this must be EOF; anything else means
         // the stream is out of step and continuing would silently misread.
         throw new MysqlException("expected EOF after column definitions, got 0x"
            + Integer.toHexString(afterColumns.type()));
      }

      List<String[]> rows = new ArrayList<String[]>();
      for (;;) {
         Packet p = read();
         if (p.isEof()) {
            break;
         }
         if (p.isErr()) {
            throw error(p);
         }

         Reader row = new Reader(p);
         String[] values = new String[columns];
         for (int i = 0; i < columns; i++) {
            values[i] = row.lenencStringOrNull();
         }
         rows.add(values);
      }

      return new Rows(names, rows);
   }

   /** COM_QUERY returning an affected-row count. */
   public synchronized int update(String sql) throws MysqlException {
      Packet p = command(sql);

      if (p.isErr()) {
         throw error(p);
      }
      if (!p.isOk()) {
         // A SELECT sent through update(): drain it so the connection stays
         // usable rather than leaving half a result set in the socket.
         drainResultSet(p);
         return 0;
      }

      Reader r = new Reader(p);
      r.int1();                       // 0x00
      return (int) r.lenencInt();     // affected rows
   }

   private void drainResultSet(Packet first) throws MysqlException {
      Reader r = new Reader(first);
      int columns = (int) r.lenencInt();
      for (int i = 0; i < columns; i++) {
         read();
      }
      read();                          // EOF after columns
      for (;;) {
         Packet p = read();
         if (p.isEof() || p.isErr()) {
            return;
         }
      }
   }

   private Packet command(String sql) throws MysqlException {
      if (!isOpen()) {
         throw new MysqlException("not connected");
      }

      try {
         byte[] body = sql.getBytes("UTF-8");
         byte[] packet = new byte[body.length + 1];
         packet[0] = 0x03;             // COM_QUERY
         System.arraycopy(body, 0, packet, 1, body.length);
         /*
          * A command starts a fresh sequence at 0, and write() pre-increments,
          * so this has to be -1. Getting it wrong costs you "Got packets out of
          * order" (error 1156) followed by the server hanging up -- and only on
          * queries, because the handshake response genuinely is sequence 1 and
          * authenticates perfectly with the same off-by-one.
          */
         this.sequence = -1;
         write(packet);
         return read();
      } catch (IOException e) {
         throw new MysqlException("query failed: " + e, e);
      }
   }

   public void close() {
      try {
         if (isOpen()) {
            this.sequence = -1;           // COM_QUIT is also sequence 0
            write(new byte[] { 0x01 });
         }
      } catch (Exception e) {
         // going away regardless
      } finally {
         closeQuietly();
      }
   }

   private void closeQuietly() {
      try {
         if (this.socket != null) {
            this.socket.close();
         }
      } catch (IOException e) {
         // nothing useful to do
      }

      this.socket = null;
      this.in = null;
      this.out = null;
   }

   /*
    * ---- packet framing ----
    *
    * Every packet is a 3-byte little-endian length, a 1-byte sequence number,
    * then the body. A body of exactly 0xFFFFFF means the payload continues in
    * the next packet.
    */
   private Packet read() throws MysqlException {
      try {
         byte[] body = readOne();
         if (body.length < MAX_PACKET) {
            return new Packet(body);
         }

         // Continuation: keep reading until a short packet ends the payload.
         java.io.ByteArrayOutputStream all = new java.io.ByteArrayOutputStream();
         all.write(body);
         while (body.length == MAX_PACKET) {
            body = readOne();
            all.write(body);
         }

         return new Packet(all.toByteArray());
      } catch (IOException e) {
         throw new MysqlException("read failed: " + e, e);
      }
   }

   private byte[] readOne() throws IOException {
      int b0 = this.in.read();
      if (b0 < 0) {
         throw new IOException("connection closed by server");
      }

      int length = b0 | (this.in.read() << 8) | (this.in.read() << 16);
      this.sequence = this.in.read();
      byte[] body = new byte[length];
      this.in.readFully(body);
      return body;
   }

   private void write(byte[] body) throws IOException {
      int offset = 0;
      do {
         int chunk = Math.min(MAX_PACKET, body.length - offset);
         this.out.write(chunk & 0xFF);
         this.out.write((chunk >>> 8) & 0xFF);
         this.out.write((chunk >>> 16) & 0xFF);
         this.out.write(++this.sequence & 0xFF);
         this.out.write(body, offset, chunk);
         offset += chunk;
      } while (offset < body.length);

      this.out.flush();
   }

   private static MysqlException error(Packet p) {
      Reader r = new Reader(p);
      r.int1();                        // 0xFF
      int code = r.int2();
      String state = "";
      if (r.peek() == '#') {
         r.int1();
         state = r.fixedString(5);
      }

      return new MysqlException("MySQL error " + code
         + (state.length() > 0 ? " [" + state + "]" : "") + ": " + r.restAsString());
   }

   /*
    * ---- handshake ----
    */
   private static final class Handshake {
      String serverVersion = "";
      byte[] salt = new byte[0];
      int capabilities;
      int charset = 33;   // utf8_general_ci
      String authPlugin = "";
   }

   private static Handshake parseHandshake(Packet p) throws MysqlException {
      Reader r = new Reader(p);
      Handshake h = new Handshake();

      int protocol = r.int1();
      if (protocol == 0xFF) {
         throw error(p);
      }
      if (protocol != 10) {
         throw new MysqlException("unsupported protocol version " + protocol + " (expected 10)");
      }

      h.serverVersion = r.nulString();
      r.int4();                        // connection id
      byte[] saltPart1 = r.fixed(8);
      r.int1();                        // filler
      int capLow = r.int2();
      h.charset = r.int1();
      r.int2();                        // status flags
      int capHigh = r.int2();
      h.capabilities = capLow | (capHigh << 16);
      int saltLen = r.int1();
      r.fixed(10);                     // reserved

      int part2 = Math.max(13, saltLen - 8);
      byte[] saltPart2 = r.fixed(part2);

      // part 2 carries a trailing NUL that is not part of the salt
      int keep = Math.max(0, Math.min(saltPart2.length - 1, 20 - saltPart1.length));
      h.salt = new byte[saltPart1.length + keep];
      System.arraycopy(saltPart1, 0, h.salt, 0, saltPart1.length);
      System.arraycopy(saltPart2, 0, h.salt, saltPart1.length, keep);

      if (r.remaining() > 0) {
         h.authPlugin = r.nulString();
      }

      return h;
   }
}
