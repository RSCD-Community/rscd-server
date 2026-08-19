package org.rscdaemon.server.util.sql;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * A fully-read result set, detached from the connection.
 *
 * This deliberately mirrors the shape callers already used, because
 * DatabaseConnection had converted every query into a CachedRowSet for reasons
 * worth preserving: with a live result set, a second player's query would close
 * the first player's rows mid-iteration, and Connector/J never released them
 * until the Statement closed, so the heap grew with every query the server ever
 * ran. Reading the rows up front while the connection lock is held avoids both,
 * and it is why this owns no resources and its close() has nothing to do.
 *
 * Every value is a String because the MySQL text protocol sends them that way.
 * The accessors parse on demand, which is also what removes the need for
 * Connector/J's tinyInt1isBit=false workaround -- there is no TINYINT(1) to
 * Boolean mapping here to defeat, and getInt on a "0"/"1" column just works.
 */
public final class Rows {

   private final String[] names;
   private final Map<String, Integer> index;
   private final List<String[]> rows;
   private int cursor = -1;

   Rows(String[] names, List<String[]> rows) {
      this.names = names;
      this.rows = rows;
      this.index = new HashMap<String, Integer>();
      for (int i = 0; i < names.length; i++) {
         // Case-insensitive, as SQL column references are.
         this.index.put(names[i].toLowerCase(), Integer.valueOf(i));
      }
   }

   public boolean next() {
      if (this.cursor + 1 >= this.rows.size()) {
         this.cursor = this.rows.size();
         return false;
      }

      this.cursor++;
      return true;
   }

   public int size() {
      return this.rows.size();
   }

   public String[] columnNames() {
      return this.names;
   }

   /** Present so callers written against ResultSet read naturally. A no-op. */
   public void close() {
   }

   public String getString(String column) throws MysqlException {
      return value(column);
   }

   public int getInt(String column) throws MysqlException {
      String v = value(column);
      if (v == null || v.length() == 0) {
         return 0;
      }

      try {
         return Integer.parseInt(v.trim());
      } catch (NumberFormatException e) {
         /* MySQL renders DECIMAL and friends with a fractional part; a caller
            asking for an int wants it truncated, not an exception. */
         try {
            return (int) Double.parseDouble(v.trim());
         } catch (NumberFormatException e2) {
            throw new MysqlException("column '" + column + "' is not a number: '" + v + "'");
         }
      }
   }

   public long getLong(String column) throws MysqlException {
      String v = value(column);
      if (v == null || v.length() == 0) {
         return 0L;
      }

      try {
         return Long.parseLong(v.trim());
      } catch (NumberFormatException e) {
         try {
            return (long) Double.parseDouble(v.trim());
         } catch (NumberFormatException e2) {
            throw new MysqlException("column '" + column + "' is not a number: '" + v + "'");
         }
      }
   }

   public boolean isNull(String column) throws MysqlException {
      return value(column) == null;
   }

   private String value(String column) throws MysqlException {
      if (this.cursor < 0 || this.cursor >= this.rows.size()) {
         throw new MysqlException("no current row -- call next() first");
      }

      Integer i = this.index.get(column.toLowerCase());
      if (i == null) {
         throw new MysqlException("no such column '" + column + "' in " + java.util.Arrays.toString(this.names));
      }

      return this.rows.get(this.cursor)[i.intValue()];
   }
}
