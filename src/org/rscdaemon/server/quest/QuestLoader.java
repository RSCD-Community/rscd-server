/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.quest;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Vector;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.rscdaemon.server.model.Player;

/*
 * Loads the quest classes out of the quests/ directory.
 *
 * The original listed the .class files in that directory and then loaded them
 * with Class.forName(), which does not read the directory at all -- it searches
 * the system classpath. The two only agreed if the operator remembered to put
 * quests/ on -cp, and the server is launched with `-cp build:lib/*`, so it
 * never did: every quest threw ClassNotFoundException and the count was always
 * "0 Quest class files loaded". The directory listing was, in effect, decoration.
 *
 * It now loads through a URLClassLoader rooted at the directory it just listed,
 * so the two halves refer to the same files and the launch classpath is
 * irrelevant. A .java with no .class -- or one newer than its .class -- is
 * compiled first, the same trick the client's ScriptRunner uses for scripts, so
 * dropping a .java into quests/ is enough.
 *
 * Anything that is not a usable quest is named and skipped rather than left to
 * fail later: QuestManager builds every quest afresh for every player who logs
 * in, so a class without the (Player, Integer) constructor would throw on each
 * login instead of once here.
 */
public class QuestLoader {
    private static Vector<Class> classes = null;

    /** Where quests live. Overridable so the directory can be moved or tested. */
    public static File questDirectory() {
        String configured = System.getProperty("rscd.quests");
        if (configured != null) {
            return new File(configured);
        }
        return new File(System.getProperty("user.dir") + File.separator + "quests" + File.separator);
    }

    public static final void loadClasses() {
        if (classes == null) {
            // loadClasses() is public and was happy to NPE on its own first line.
            classes = new Vector();
        }
        classes.clear();

        File dir = questDirectory();
        System.out.println();
        System.out.println("<-- Loading Quest class files -->");
        System.out.println();

        String[] files = dir.list();
        if (files == null) {
            // File.list() returns null when the directory is absent or
            // unreadable. The original dereferenced it unguarded, so a server
            // started without a quests/ directory died here during startup.
            System.out.println(" - No quests directory at " + dir.getPath() + ", skipping.");
            System.out.println();
            System.out.println("<-- 0 Quest class files loaded -->");
            System.out.println();
            return;
        }

        Arrays.sort(files);
        int compiled = compileSources(dir, files);
        // Recheck: compiling may have produced .class files that were not there
        // when the directory was first listed.
        files = dir.list();
        if (files == null) {
            files = new String[0];
        }
        Arrays.sort(files);

        ClassLoader loader = loaderFor(dir);
        if (loader == null) {
            System.out.println("<-- 0 Quest class files loaded -->");
            System.out.println();
            return;
        }

        HashMap<Integer, String> takenUids = new HashMap<Integer, String>();

        for (int x = 0; x < files.length; ++x) {
            // Inner classes ($) belong to their outer class, not to us.
            if (!files[x].endsWith(".class") || files[x].indexOf(36) >= 0) continue;
            String name = files[x].substring(0, files[x].length() - 6);
            try {
                Class<?> c = loader.loadClass(name);
                String rejected = whyUnusable(c);
                if (rejected != null) {
                    System.out.println(" - Skipped:  " + name.toLowerCase() + " (" + rejected + ")");
                    continue;
                }

                /* A quest's UID is its database identifier, so two quests
                   sharing one means each overwrites the other's progress for
                   every player. Nothing checked, and the two quests that
                   shipped both used UID 1. First one in wins, because the
                   alternative -- dropping both -- loses a working quest to a
                   newcomer's typo. */
                Integer uid = declaredUid(c);
                if (uid != null) {
                    String owner = takenUids.get(uid);
                    if (owner != null) {
                        System.out.println(" - Skipped:  " + name.toLowerCase()
                            + " (UID " + uid + " is already " + owner + "'s; give it its own)");
                        continue;
                    }
                    takenUids.put(uid, name);
                }

                classes.add(c);
                System.out.println(" - Loaded:   " + name.toLowerCase()
                    + (uid == null ? " (no UID field, so not checked for collisions)" : ""));
            }
            catch (Throwable e) {
                // Throwable, not Exception: a quest compiled against an older
                // server throws NoClassDefFoundError, which is an Error, and
                // used to escape this loop and abort the whole load.
                System.out.println(" - Failed:   " + name.toLowerCase() + " (" + e + ")");
            }
        }

        System.out.println();
        System.out.println("<-- " + classes.size() + " Quest class files loaded -->");
        System.out.println();

        if (compiled > 0) {
            runQuestDataHook(compiled);
        }
    }

    /**
     * Run the operator's quest-data hook, if one is configured.
     *
     * Quest classes are the source of truth for more than the game: the
     * website's Beastiary reads their npc associations and final stages
     * through tools/generate-game-data.py. This runs whatever command the
     * `rscd.quests.hook` system property names -- but only on a load where
     * at least one quest source was actually (re)compiled, so an ordinary
     * boot with everything current adds nothing. Unset, it does nothing,
     * which is the shipped default: the hook is a deployment's choice, not
     * the server's business.
     */
    private static void runQuestDataHook(int compiled) {
        String hook = System.getProperty("rscd.quests.hook");
        if (hook == null || hook.trim().isEmpty()) {
            return;
        }
        System.out.println(" - " + compiled + " quest(s) recompiled; running quest-data hook: " + hook);
        try {
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", hook);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("   hook: " + line);
            }
            int exit = p.waitFor();
            if (exit != 0) {
                System.out.println(" - Quest-data hook exited " + exit);
            }
        }
        catch (Exception e) {
            System.out.println(" - Quest-data hook failed: " + e);
        }
        System.out.println();
    }

    /**
     * Why this class cannot be used as a quest, or null if it can.
     *
     * QuestManager instantiates every one of these per player, per login, via
     * the (Player, Integer) constructor. Anything that would fail there is
     * better refused once, here, with a reason.
     */
    private static String whyUnusable(Class<?> c) {
        if (!Quest.class.isAssignableFrom(c)) {
            return "does not extend Quest";
        }
        if (Modifier.isAbstract(c.getModifiers())) {
            return "is abstract";
        }
        try {
            c.getConstructor(Player.class, Integer.class);
        }
        catch (NoSuchMethodException e) {
            return "has no public " + c.getSimpleName() + "(Player, Integer) constructor";
        }
        return null;
    }

    /**
     * A quest's declared UID, or null if it does not declare one.
     *
     * Read from the `public static int UID` field both bundled quests document
     * as "the database identifier", rather than from a built instance: getUID()
     * would be the truer answer, but reaching it means constructing the quest,
     * and constructing it means running a stranger's constructor during startup
     * just to ask its name. A quest that declares one UID and passes another to
     * super() is lying in its own documentation; this reports the declaration.
     */
    private static Integer declaredUid(Class<?> c) {
        try {
            Field field = c.getField("UID");
            if (field.getType() != Integer.TYPE || !Modifier.isStatic(field.getModifiers())) {
                return null;
            }
            return Integer.valueOf(field.getInt(null));
        }
        catch (Throwable e) {
            // No such field, or not readable. Not fatal -- the quest just opts
            // out of the collision check.
            return null;
        }
    }

    /** A loader rooted at the quests directory, falling back to ours for the server API. */
    private static ClassLoader loaderFor(File dir) {
        try {
            return new URLClassLoader(new URL[]{dir.toURI().toURL()}, QuestLoader.class.getClassLoader());
        }
        catch (Exception e) {
            System.out.println(" - Could not read " + dir.getPath() + ": " + e);
            return null;
        }
    }

    /**
     * Compiles any .java without an up-to-date .class beside it, so a quest can
     * be dropped in as source. Silent when there is nothing to do; skipped
     * entirely on a JRE, where already-compiled quests still load fine.
     */
    /** @return how many sources compiled successfully, for the data hook. */
    private static int compileSources(File dir, String[] files) {
        Vector<File> stale = new Vector<File>();
        for (int x = 0; x < files.length; ++x) {
            if (!files[x].endsWith(".java")) continue;
            File source = new File(dir, files[x]);
            File compiled = new File(dir, files[x].substring(0, files[x].length() - 5) + ".class");
            if (!compiled.isFile() || source.lastModified() > compiled.lastModified()) {
                stale.add(source);
            }
        }
        if (stale.isEmpty()) {
            return 0;
        }

        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        if (javac == null) {
            System.out.println(" - " + stale.size() + " quest source(s) need compiling, but this is a JRE, not a JDK.");
            return 0;
        }

        int succeeded = 0;
        for (File source : stale) {
            /* Compiled against this server's own classpath, so a quest sees the
               same org.rscdaemon.server API the rest of the process does, and
               output next to the source so the .class sits where the loader
               below will look for it.

               Diagnostics are captured rather than left to fall through to the
               console: one quest written against an older API can produce sixty
               lines of javac output, which buries the rest of startup. The
               summary keeps the first few and says how many there were. */
            java.io.ByteArrayOutputStream diagnostics = new java.io.ByteArrayOutputStream();
            int result = javac.run(null, null, diagnostics, new String[]{
                "-nowarn",
                "-classpath", System.getProperty("java.class.path"),
                "-d", dir.getPath(),
                source.getPath()
            });
            if (result == 0) {
                System.out.println(" - Compiled: " + source.getName());
                ++succeeded;
            } else {
                System.out.println(" - Failed to compile " + source.getName() + ":");
                summarise(diagnostics.toString());
            }
        }
        return succeeded;
    }

    /** The first few javac errors and a count of the rest. */
    private static void summarise(String diagnostics) {
        String[] lines = diagnostics.split("\n");
        int shown = 0;
        int total = 0;
        for (int i = 0; i < lines.length; ++i) {
            if (lines[i].indexOf("error:") < 0) continue;
            ++total;
            if (shown < 3) {
                System.out.println("     " + lines[i].trim());
                ++shown;
            }
        }
        if (total > shown) {
            System.out.println("     ... and " + (total - shown) + " more error(s). Compile it by hand to see them all:");
            System.out.println("     javac -cp \"build:lib/*\" -d quests quests/<TheQuest>.java");
        }
    }

    public static final Vector<Class> getClasses() {
        if (classes == null) {
            return null;
        }
        return (Vector)classes.clone();
    }

    public static final void initClasses() {
        if (classes == null) {
            classes = new Vector();
            QuestLoader.loadClasses();
        }
    }
}
