package org.rscdaemon.ls.net;

import org.rscdaemon.ls.Server;
import org.rscdaemon.ls.util.Config;
import org.rscdaemon.server.util.sql.MysqlClient;
import org.rscdaemon.server.util.sql.MysqlException;
import org.rscdaemon.server.util.sql.Rows;

/**
 * MySQL access for the login server.
 *
 * This used JDBC and Connector/J -- a driver built in 2006, JDBC 3.0, the last
 * third-party code in the login path and the only thing between the internet
 * and the account table. It now uses server/util/sql/MysqlClient, written for
 * this project; see that class for the protocol it speaks and what it
 * deliberately does not implement.
 *
 * The API here is unchanged: getQuery returns fully-read rows, updateQuery
 * returns an affected count, both are synchronized, and a dropped connection is
 * reconnected once and the statement retried.
 *
 * The original implementation kept a single Statement alive for the whole
 * process and handed the live ResultSet straight back to callers. Neither was
 * ever closed, which caused two failures:
 *
 *   1. Connector/J registered every ResultSet a Statement created and released
 *      them only when the Statement closed. With one immortal Statement the
 *      heap grew with every query the server ever ran.
 *   2. A Statement may have only one open ResultSet. getQuery() was
 *      synchronized, but callers read the result after the lock was released,
 *      so a second player's query closed the first player's rows mid-iteration.
 *      Invisible with one player, fatal under load.
 *
 * Both stay fixed, now by construction rather than by workaround: the client
 * reads every row into a detached Rows before returning, while the lock is
 * still held. There is no live cursor to invalidate and no driver-side resource
 * to leak.
 *
 * Gone with the driver: the tinyInt1isBit=false connection flag. Connector/J
 * mapped TINYINT(1) to Boolean, which broke getInt() on the 17 such columns in
 * rscd_players once results were detached. The text protocol has no type
 * mapping to defeat -- a "0" is a "0".
 */
public class DatabaseConnection {

    private MysqlClient client;
    private String lastQuery;

    public DatabaseConnection() {
        if (!this.createConnection()) {
            /* Fatal by design. The login server authenticates against MySQL and
               does nothing else, so starting without it would only mean failing
               every login instead. Nobody is connected yet, so nobody is
               dropped -- which is what separates this from the runtime errors
               that go through Server.error(). */
            Server.fatal("unable to connect to MySQL at " + Config.MYSQL_HOST + ":" + Config.MYSQL_PORT
                + "/" + Config.MYSQL_DB + " as " + Config.MYSQL_USER, null);
        }
    }

    public synchronized boolean createConnection() {
        this.closeQuietly();

        try {
            this.client = new MysqlClient(Config.MYSQL_HOST, Config.MYSQL_PORT, Config.MYSQL_DB,
                Config.MYSQL_USER, Config.MYSQL_PASS, Config.MYSQL_TLS);
            if (Config.MYSQL_TRUSTSTORE != null && Config.MYSQL_TRUSTSTORE.length() > 0) {
                this.client.setTrustStore(Config.MYSQL_TRUSTSTORE, Config.MYSQL_TRUSTSTORE_PASS);
            }

            this.client.connect(10000);
            // Worth one line at startup: whether the account table is crossing
            // the network in clear is not something to have to guess at.
            System.out.println("MySQL " + this.client.serverVersion()
                + (this.client.isTls() ? " over TLS" : " UNENCRYPTED"));
            return true;
        } catch (MysqlException e) {
            Server.error(e.getMessage());
            this.client = null;
            return false;
        }
    }

    public synchronized boolean isConnected() {
        if (this.client == null || !this.client.isOpen()) {
            return false;
        }

        try {
            this.client.query("SELECT CURRENT_DATE");
            return true;
        } catch (MysqlException e) {
            return false;
        }
    }

    /**
     * Runs a query and returns its rows detached from the database. The
     * returned object owns no connection resources, so callers need not -- and
     * cannot usefully -- close it.
     */
    public synchronized Rows getQuery(String q) throws MysqlException {
        this.lastQuery = q;

        try {
            return this.client.query(q);
        } catch (MysqlException e) {
            if (!this.isConnected() && this.createConnection()) {
                return this.client.query(q);
            }

            throw new MysqlException(e.getMessage() + ": '" + this.lastQuery + "'", e);
        }
    }

    public synchronized int updateQuery(String q) throws MysqlException {
        this.lastQuery = q;

        try {
            return this.client.update(q);
        } catch (MysqlException e) {
            if (!this.isConnected() && this.createConnection()) {
                return this.client.update(q);
            }

            throw new MysqlException(e.getMessage() + ": '" + this.lastQuery + "'", e);
        }
    }

    public synchronized void close() {
        if (this.client != null) {
            this.client.close();
            this.client = null;
        }
    }

    private void closeQuietly() {
        try {
            this.close();
        } catch (Exception e) {
            // discarding a connection we are about to replace
        }
    }
}
