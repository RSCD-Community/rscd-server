package org.rscdaemon.server.util.sql;

/*
 * Anything that goes wrong talking to MySQL. Checked, because every caller in
 * this project already handled java.sql.SQLException and losing that would turn
 * a database outage into an unhandled crash.
 */
public class MysqlException extends Exception {

   private static final long serialVersionUID = 1L;

   public MysqlException(String message) {
      super(message);
   }

   public MysqlException(String message, Throwable cause) {
      super(message, cause);
   }
}
