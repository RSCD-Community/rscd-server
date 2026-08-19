/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.util;

import org.rscdaemon.server.model.World;

public class Logger {
    private static final World world = World.getWorld();

    public static void print(Object o) {
        System.out.println(o.toString());
    }

    public static void connection(Object o) {
    }

    public static void mod(Object o) {
        world.getServer().getLoginConnector().getActionSender().logAction(o.toString(), 3);
    }

    public static void event(Object o) {
        world.getServer().getLoginConnector().getActionSender().logAction(o.toString(), 1);
    }

    /**
     * Logs. Does not exit, and does not kill the server.
     *
     * It used to do both. Handed an Exception, this called System.exit(1) if the
     * world was not yet initialised and world.getServer().kill() otherwise --
     * so *logging an error* was the same act as shutting the game down, at
     * thirty-eight call sites. Anything reaching one of them dropped every
     * player on the world:
     *
     *   - "Lost connection the login server!" -- a link that reconnects by
     *     design, logged as an Exception, therefore fatal
     *   - a codec fault on ONE connection, arriving via exceptionCaught
     *   - a failed world save, an unhandled packet, a bad definition file
     *
     * A game server that logs a bad packet is strictly better than one that
     * dies on it. Startup failures that genuinely leave nothing to serve call
     * fatal() instead, so exiting is now a decision made at the call site
     * rather than a side effect of writing to the log.
     *
     * This is the same fix already applied to org.rscdaemon.ls.Server.error --
     * the game server had the identical defect and had kept it.
     */
    public static void error(Object o) {
        if (o instanceof Throwable) {
            ((Throwable)o).printStackTrace();
            return;
        }

        System.err.println(o);

        /* Best-effort forwarding to the login server's action log. It is a
           remote call over a link that may be exactly what is broken, so a
           failure here must not turn into a second error. */
        try {
            if (world != null && world.getServer() != null
                    && world.getServer().getLoginConnector() != null) {
                world.getServer().getLoginConnector().getActionSender().logAction(o.toString(), 2);
            }
        } catch (Throwable t) {
            // Nowhere left to report it; stderr above already carries the message.
        }
    }

    /**
     * Logs and exits. Startup failures only -- no port to bind, no login server
     * to register with, no world data to serve -- where the process has nothing
     * left to do and nobody is connected to drop.
     */
    public static void fatal(String what, Throwable cause) {
        System.err.println("FATAL: " + what);
        if (cause != null) {
            cause.printStackTrace();
        }

        System.exit(1);
    }
}

