/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server;

import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import org.rscdaemon.server.util.Config;
import org.rscdaemon.server.util.Logger;

/*
 * Collects the XML for whatever a moderator spawns with ::npc and ::object, so
 * that an afternoon of placing things by hand comes out as something that can
 * be pasted into the world files. A world-building tool, not part of running a
 * game.
 *
 * It used to open two Swing windows on every boot, unconditionally, from
 * GameEngine.run(). That made an X display a hard requirement of starting the
 * game server: on a headless host -- which is how a real server runs -- the
 * first frame throws HeadlessException before a single player can connect.
 *
 * So the windows are opt-in now (builder_windows in conf/server/Conf.xml), and the
 * collecting still works without them: with no window to append to, each entry
 * goes to the log instead. ::npc and ::object behave the same either way,
 * which matters because they used to dereference a text area that existed only
 * if the windows had been opened.
 */
public class ObjectXMLBuilder {
    static JFrame objectframe;
    static JTextArea objectoutput;
    static JFrame npcframe;
    static JTextArea npcoutput;
    private static final String newline = "\n";

    public static void StartObjectXMLBuilder() {
        objectframe = new JFrame("ObjectBuilder");
        // Was setDefaultCloseOperation(3) -- EXIT_ON_CLOSE. These builder
        // windows run inside the game server JVM, so closing one called
        // System.exit and killed the whole server, dropping every player.
        objectframe.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        objectframe.setLayout(new GridLayout(1, 2));
        objectoutput = new JTextArea(35, 25);
        JScrollPane scrollingResult = new JScrollPane(objectoutput);
        scrollingResult.setVerticalScrollBarPolicy(22);
        scrollingResult.setHorizontalScrollBarPolicy(31);
        objectframe.add(scrollingResult);
        objectframe.pack();
        objectframe.setVisible(true);
    }

    public static void StartNPCXMLBuilder() {
        npcframe = new JFrame("NPCBuilder");
        // Was EXIT_ON_CLOSE -- see StartObjectXMLBuilder above.
        npcframe.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        npcframe.setLayout(new GridLayout(1, 2));
        npcoutput = new JTextArea(35, 25);
        JScrollPane scrollingResult = new JScrollPane(npcoutput);
        scrollingResult.setVerticalScrollBarPolicy(22);
        scrollingResult.setHorizontalScrollBarPolicy(31);
        npcframe.add(scrollingResult);
        npcframe.pack();
        npcframe.setVisible(true);
    }

    public static void AppendObjectXMLOutput(int id, int x, int y, int dir) {
        append(objectoutput, "ObjectBuilder",
              "   <GameObjectLoc>" + newline
            + "      <id>" + id + "</id>" + newline
            + "      <x>" + x + "</x>" + newline
            + "      <y>" + y + "</y>" + newline
            + "      <direction>" + dir + "</direction>" + newline
            + "      <type>0</type>" + newline
            + "   </GameObjectLoc>" + newline);
    }

    public static void AppendNPCXMLOutput(int id, int x, int y, int minx, int maxx, int miny, int maxy) {
        append(npcoutput, "NPCBuilder",
              "   <NPCLoc>" + newline
            + "      <id>" + id + "</id>" + newline
            + "      <startX>" + x + "</startX>" + newline
            + "      <startY>" + y + "</startY>" + newline
            + "      <minX>" + minx + "</minX>" + newline
            + "      <maxX>" + maxx + "</maxX>" + newline
            + "      <minY>" + miny + "</minY>" + newline
            + "      <maxY>" + maxy + "</maxY>" + newline
            + "   </NPCLoc>" + newline);
    }

    /* The window if there is one, the log if there is not. Either way the
       moderator who ran the command gets their XML. */
    private static void append(JTextArea target, String label, String xml) {
        if (target != null) {
            target.append(xml);
            return;
        }

        Logger.print("[" + label + "]" + newline + xml);
    }

    /**
     * Opens both windows, if they were asked for and there is a display to put
     * them on. Returns quietly otherwise -- a server told to open them on a
     * headless host has made a configuration mistake, not a fatal one, and
     * should still come up and serve players.
     */
    public static void InitiateBuilder() {
        if (!Config.BUILDER_WINDOWS) {
            return;
        }

        if (GraphicsEnvironment.isHeadless()) {
            Logger.print("builder_windows is on but this host has no display; "
                + "::npc and ::object will write their XML to the log instead");
            return;
        }

        JFrame.setDefaultLookAndFeelDecorated(true);
        ObjectXMLBuilder.StartObjectXMLBuilder();
        ObjectXMLBuilder.StartNPCXMLBuilder();
    }
}
