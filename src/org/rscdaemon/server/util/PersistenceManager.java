package org.rscdaemon.server.util;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Properties;
import java.util.zip.GZIPInputStream;

/*
 * Loads the server's definition and location documents out of conf/.
 *
 * This was XStream, forced onto Sun14ReflectionProvider so it could allocate
 * the model classes without calling a constructor. It is XmlObjects now, which
 * reads the same documents and builds the same objects without reaching into
 * the JDK -- see that class for the shapes involved and for why the six classes
 * that lacked a no-args constructor now declare one.
 *
 * aliases.xml is unchanged and still read the same way, by java.util.Properties
 * from its XML form. It is a file an operator can edit, so its format was not
 * something to change while replacing the engine underneath it.
 *
 * The writer is gone. XStream's toXML had no callers anywhere in the server, so
 * reimplementing an XML serialiser would have been the largest part of this
 * change and would have supported nothing. If definitions ever need writing
 * again, that is a tool, and a tool can depend on whatever it likes.
 */
public class PersistenceManager {

   /* This process's reader, with its own alias table -- see XmlObjects. */
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
         Logger.error(ioe);
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
         Logger.error(ioe);
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
            Logger.error(e);
         }
      }
   }

   static {
      PersistenceManager.setupAliases();
   }
}
