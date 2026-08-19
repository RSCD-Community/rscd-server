/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.ls.packethandler.loginserver;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;

import org.rscdaemon.ls.Server;
import org.rscdaemon.ls.net.Packet;
import org.rscdaemon.ls.packethandler.PacketHandler;
import org.rscdaemon.ls.util.Config;
import org.rscdaemon.server.util.net.Connection;

/*
 * The login server's action log: every mod action, error and event a world
 * reports gets a dated line under logs/.
 *
 * IT HAD NEVER WRITTEN ANYTHING. The three PrintWriters were declared and never
 * assigned -- not here, not anywhere -- so handlePacket threw on the first log
 * packet it ever received:
 *
 *     Exception with p[32]: java.lang.NullPointerException: Cannot invoke
 *     "java.io.PrintWriter.println(String)" because "...LogHandler.error" is null
 *
 * It stayed hidden because the login server's own error() used to call
 * System.exit(1): the failure either took the process down at a moment nobody
 * connected to logging, or -- once error() was made to just log -- had nowhere
 * to be printed. It only surfaced when a world logged an action against a login
 * server whose error path both survived and reported, which is to say the first
 * time both fixes were in at once.
 *
 * Worth being clear what it cost: Logger.mod, Logger.event and Logger.error on
 * the GAME server all forward here. Every moderator action ever taken -- bans,
 * mutes, spawns -- was thrown away instead of recorded.
 *
 * Opened on first use and kept open, appending rather than truncating so a
 * restart does not erase the trail. If the log cannot be opened that is
 * reported once and logging then does nothing: losing the log is bad, refusing
 * to run the login server because of it is worse.
 */
public class LogHandler implements PacketHandler {

   private static final SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss dd-MM-yy");

   private static PrintWriter event;
   private static PrintWriter error;
   private static PrintWriter mod;
   private static boolean opened;
   private static boolean broken;

   private static String getDate() {
      return formatter.format(System.currentTimeMillis());
   }

   private static synchronized void open() {
      if (opened || broken) {
         return;
      }

      opened = true;

      try {
         File dir = new File(Config.LOG_DIR == null ? "logs" : Config.LOG_DIR);
         if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("could not create " + dir.getPath());
         }

         event = writer(new File(dir, "event.log"));
         error = writer(new File(dir, "error.log"));
         mod = writer(new File(dir, "mod.log"));
      } catch (IOException e) {
         broken = true;
         Server.error("Cannot open the action logs, so they will not be written: " + e);
      }
   }

   private static PrintWriter writer(File file) throws IOException {
      return new PrintWriter(new FileWriter(file, true));
   }

   public void handlePacket(Packet p, Connection session) throws Exception {
      byte type = p.readByte();
      String message = getDate() + ": " + p.readString();

      open();
      if (broken) {
         return;
      }

      PrintWriter out = type == 1 ? event : type == 2 ? error : type == 3 ? mod : null;
      if (out == null) {
         Server.error("Unknown log type " + type + ": " + message);
         return;
      }

      out.println(message);
      out.flush();
   }
}
