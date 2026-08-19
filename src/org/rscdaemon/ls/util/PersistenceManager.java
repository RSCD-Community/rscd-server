package org.rscdaemon.ls.util;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Properties;
import java.util.zip.GZIPInputStream;
import org.rscdaemon.ls.Server;
import org.rscdaemon.server.util.XmlObjects;

/*
 * The login server's loader. Same job as
 * org.rscdaemon.server.util.PersistenceManager, against conf/ls instead of
 * conf/server, and reporting through Server.error rather than Logger.
 *
 * This was XStream on Sun14ReflectionProvider; it is XmlObjects now, so nothing
 * here reflects into the JDK any more. The reader itself is shared with the game
 * server -- both packages ship in the same jar -- but each side builds its own
 * instance, because conf/ls/aliases.xml and conf/server/aliases.xml both define
 * "PacketHandler" and they mean different classes. Two processes today, but a
 * shared alias table would make that a requirement instead of a fact.
 */
public class PersistenceManager {

   private static final XmlObjects xml = new XmlObjects();

   public static void setupAliases() {
      FileInputStream fis = null;

      try {
         Properties aliases = new Properties();
         fis = new FileInputStream(new File(Config.CONF_DIR, "aliases.xml"));
         aliases.loadFromXML(fis);
         Enumeration<?> e = aliases.propertyNames();

         while (e.hasMoreElements()) {
            String alias = (String)e.nextElement();
            xml.alias(alias, Class.forName((String)aliases.get(alias)));
         }
      } catch (Exception ioe) {
         Server.error(ioe);
      } finally {
         closeQuietly(fis);
      }
   }

   public static Object load(String filename) {
      InputStream is = null;

      try {
         is = new FileInputStream(new File(Config.CONF_DIR, filename));
         if (filename.endsWith(".gz")) {
            is = new GZIPInputStream(is);
         }

         ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 16);
         byte[] buffer = new byte[8192];

         int count;
         while ((count = is.read(buffer)) > 0) {
            out.write(buffer, 0, count);
         }

         return xml.read(new String(out.toByteArray(), "UTF-8"));
      } catch (IOException ioe) {
         Server.error(ioe);
         return null;
      } finally {
         closeQuietly(is);
      }
   }

   private static void closeQuietly(Closeable c) {
      if (c != null) {
         try {
            c.close();
         } catch (IOException e) {
            Server.error(e);
         }
      }
   }

   static {
      PersistenceManager.setupAliases();
   }
}
