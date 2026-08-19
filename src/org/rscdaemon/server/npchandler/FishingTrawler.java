package org.rscdaemon.server.npchandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import org.rscdaemon.server.event.DelayedEvent;
import org.rscdaemon.server.model.Bubble;
import org.rscdaemon.server.model.ChatMessage;
import org.rscdaemon.server.model.GameObject;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Item;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.Point;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.quest.dialogue.Choice;
import org.rscdaemon.server.quest.dialogue.Conversation;
import org.rscdaemon.server.quest.dialogue.Effect;
import org.rscdaemon.server.util.DataConversions;

/**
 * The Fishing Trawler, Port Khazard. 28 July 2003, the last minigame RSC
 * ever got.
 *
 * The vanilla map already carries every piece of it -- this supersedes an
 * earlier note here that the deck layout was undocumented. GameObjectLoc
 * ships two fully furnished boats per side of the harbour: a sound one
 * (east deck around x271-277, west around x319-325, both y740-744) and a
 * waterlogged one (east x246-252, west x294-300, both y727-731), each with
 * its pair of "Trawler net" objects (1101/1102) on the stern and a Murphy
 * (734) pacing the deck, plus a debris field of floating barrels (1070)
 * around (254,759) and (302,759) where the sunk ship ends up. Two more
 * Murphys (733) stand on the dock at (538,703) next to the three "Trawler
 * catch" nets (1106). Only the server logic was lost; the furniture never
 * moved.
 *
 * All dialogue is Murphy's own, from the classic.runescape.wiki
 * transcripts, spelling mistakes and all -- "the dawn thing's full of
 * holes", "net rip's", "keep your eys pealed" are Jagex's, not typos here.
 * Lines with no surviving transcript are marked OURS at the call site.
 * Numeric facts (leak/wave pacing, water thresholds, the 4-minute join
 * window, crew cap, catch odds) were cross-checked against OpenRSC's
 * AGPL implementation as an oracle and re-expressed here; no code was
 * copied from it.
 *
 * How a round runs: the crew boards the sound boat, and a recurring
 * 640ms tick drives the rest. Waves periodically punch leaks (scenery
 * 1071/1077, "fill") into the two gunwale rows; every open leak lets
 * water in each tick. At 500 water the crew is moved to the waterlogged
 * boat and the round continues; at 1000 the ship is lost and everyone is
 * dumped into the barrel field, to climb ashore for 1-3 damage. Survive
 * the 5-12 minute trip and Murphy sails home: the catch (2 x fish
 * counter, split per head) waits in the dock net, one Search per item,
 * fish-or-junk decided as it comes out -- which is why a fishing potion
 * sipped at the net genuinely raises what you can pull from it, exactly
 * as the wiki says it did.
 *
 * Honest caveats, documented rather than papered over:
 *  - the uncollected catch and the have-met-Murphy flag live in server
 *    memory, not the database. A restart loses an unclaimed net and
 *    replays the long introduction once. Wiring either into PlayerSave
 *    needs a schema decision, so it is left for the user's call.
 *  - the wiki records the real 2003 service as riddled with glitches
 *    (players vanishing from each other's boats, the east boat ignoring
 *    bailing buckets, ships that never came home). None of that is
 *    reproduced: those were defects, not features.
 */
public class FishingTrawler implements NpcHandler {

    public static final World world = World.getWorld();

    private static final int ROPE = 237;
    private static final int SWAMP_PASTE = 785;
    private static final int BAILING_BUCKET = 1282;
    private static final int NET_ITEM = 376;

    private static final int FISHING = 10;
    private static final int HITS = 3;
    private static final int MIN_FISHING_LEVEL = 15;

    public static final int MURPHY_DOCK = 733;
    public static final int MURPHY_SHIP = 734;

    /* Two Leak ids exist with identical defs; presumably one sprite faces
       each gunwale. Which faces which is unrecorded, so north row gets
       1071 and south 1077 -- cosmetic either way, both answer "fill". */
    private static final int LEAK_NORTH = 1071;
    private static final int LEAK_SOUTH = 1077;
    private static final int BARREL = 1070;

    private static final int TICK_MS = 640;
    private static final int JOIN_CUTOFF_TICKS = 4 * 60 * 1000 / TICK_MS;
    private static final int MAX_CREW = 10;
    private static final int SINKING_WATER = 500;
    private static final int SUNK_WATER = 1000;

    /** Where the barrel washes you up: behind the general store, a little
        south-west of the dock, the same spot every time. */
    private static final int SHORE_X = 550, SHORE_Y = 711;
    private static final int DOCK_X = 538, DOCK_Y = 703;

    private static class Boat {
        final String name;
        final int deckMinX, deckMaxX, deckMinY, deckMaxY;
        final int deckSpawnX, deckSpawnY;
        final int sinkMinX, sinkMaxX, sinkMinY, sinkMaxY;
        final int sinkSpawnX, sinkSpawnY;
        final int failX, failY;
        int stage;          // 0 docked, 1 out fishing, 2 taking on water
        int water, fish, tick, tripTicks, nextWave;
        boolean netBroken;
        final List<Player> crew = new ArrayList<Player>();
        final List<GameObject> leaks = new ArrayList<GameObject>();
        DelayedEvent event;

        Boat(String name,
             int dMinX, int dMaxX, int dMinY, int dMaxY, int dSpawnX, int dSpawnY,
             int sMinX, int sMaxX, int sMinY, int sMaxY, int sSpawnX, int sSpawnY,
             int failX, int failY) {
            this.name = name;
            this.deckMinX = dMinX; this.deckMaxX = dMaxX;
            this.deckMinY = dMinY; this.deckMaxY = dMaxY;
            this.deckSpawnX = dSpawnX; this.deckSpawnY = dSpawnY;
            this.sinkMinX = sMinX; this.sinkMaxX = sMaxX;
            this.sinkMinY = sMinY; this.sinkMaxY = sMaxY;
            this.sinkSpawnX = sSpawnX; this.sinkSpawnY = sSpawnY;
            this.failX = failX; this.failY = failY;
        }

        boolean containsDeck(int x, int y) {
            return x >= this.deckMinX && x <= this.deckMaxX && y >= this.deckMinY && y <= this.deckMaxY;
        }

        boolean containsSink(int x, int y) {
            return x >= this.sinkMinX && x <= this.sinkMaxX && y >= this.sinkMinY && y <= this.sinkMaxY;
        }

        boolean aboard(Player p) {
            return this.stage == 2 ? this.containsSink(p.getX(), p.getY())
                                   : this.containsDeck(p.getX(), p.getY());
        }
    }

    /* Hull bounds are the vanilla furniture's own: nets sit one tile in
       from the stern, Murphy's pace box gives the bow, the gunwale rows
       are deckMinY+1 and deckMaxY-1 (all four verified walkable against
       Landscape.rscd). */
    private static final Boat EAST = new Boat("east",
        270, 278, 740, 744, 272, 742,
        245, 253, 727, 731, 251, 729,
        254, 759);
    private static final Boat WEST = new Boat("west",
        318, 326, 740, 744, 320, 742,
        293, 301, 727, 731, 299, 729,
        302, 759);

    /** Items still waiting in the dock net, by lowercase username. Lives
        in memory only -- see the class comment. */
    private static final HashMap<String, Integer> pendingCatch = new HashMap<String, Integer>();

    /** Who has heard the full introduction. Memory only, resets on
        restart; the cost is one replayed conversation. */
    private static final HashSet<String> introduced = new HashSet<String>();

    /* Fish ladder, highest tier first: level requirement, item id,
       Fishing exp. Ids and exp are the wiki's table verbatim (our exp
       scale is 1x wiki). The roll per tier is this project's house
       success curve, not OpenRSC's formula. */
    private static final int[][] FISH_TIERS = {
        {81, 1190, 115},   // Raw Manta ray
        {79, 1192,  95},   // Raw Sea turtle
        {76,  545, 110},   // Raw Shark
        {50,  369, 100},   // Raw Swordfish
        {40,  372,  90},   // Raw Lobster
        {30,  366,  80},   // Raw Tuna
        {15,  351,  40},   // Raw Anchovies
        { 5,  354,  20},   // Raw Sardine
    };
    private static final int FALLBACK_FISH = 349, FALLBACK_XP = 10;   // Raw Shrimp

    /* The wiki's junk table is exactly these eight (no vase -- 1168 in
       other emulators is their own addition), plus the two "Other"
       finds. Junk is 1.25 exp on the wiki; our exp store is 1x integer,
       so it rounds to 1. */
    private static final int[][] JUNK = {
        {1155,  1},   // Old boot
        {1157,  1},   // Damaged armour
        {1158,  1},   // Damaged armour (second id, same name)
        {1159,  1},   // Rusty sword
        {1165,  1},   // broken arrow
        {1166,  1},   // buttons
        {1167,  1},   // broken staff
        {1169,  1},   // ceramic remains
        {1170,  1},   // Broken glass
        {1245,  5},   // Edible seaweed
        { 791, 10},   // oyster
    };

    /**
     * What Murphy's net announces as each thing comes out, from
     * Transcript:Trawler_catch. These are hand-written strings, not
     * anything derived from the item names, and the inconsistencies are
     * Jagex's own: "Broken glass" and "Old boot" keep their capitals
     * while everything else is lower case, swordfish is "a sword fish"
     * in two words, and only the shark, manta ray, sea turtle and oyster
     * earned an exclamation mark. Reproduced literally for that reason.
     */
    private static String reveal(int id) {
        switch (id) {
            case  349: return "..some shrimp";
            case  354: return "..a sardine";
            case  351: return "..some anchovies";
            case  366: return "..some tuna";
            case  372: return "..a lobster";
            case  369: return "..a sword fish";
            case  545: return "..a shark!";
            case 1192: return "..a sea turtle!";
            case 1190: return "..a manta ray!";
            case 1155: return "..an Old boot";
            case 1157:
            case 1158: return "..some damaged armour";
            case 1159: return "..a rusty sword";
            case 1165: return "..a broken arrow";
            case 1166: return "..some buttons";
            case 1167: return "..a broken staff";
            case 1169: return "..some ceramic remains";
            case 1170: return "..some Broken glass";
            case 1245: return "..some seaweed";
            case  791: return "..an oyster!";
        }
        return null;
    }

    public static boolean isAboard(Player player) {
        return boatWith(player) != null;
    }

    private static Boat boatWith(Player player) {
        if (EAST.crew.contains(player)) {
            return EAST;
        }
        if (WEST.crew.contains(player)) {
            return WEST;
        }
        return null;
    }

    private static Boat boatAt(int x, int y) {
        for (Boat b : new Boat[]{EAST, WEST}) {
            if (b.containsDeck(x, y) || b.containsSink(x, y)) {
                return b;
            }
        }
        return null;
    }

    public void handleNpc(Npc npc, Player player) throws Exception {
        if (npc.getID() == MURPHY_SHIP) {
            this.shipMurphy(npc, player);
        } else {
            this.dockMurphy(npc, player);
        }
    }

    /*
     * Murphy on the dock. Three conversations, split exactly as the
     * transcript splits them: the full first-time introduction, the same
     * opener cut short for a returner who still lacks the level, and the
     * quick "fancy hitting the high seas again?" for everyone else.
     */
    private void dockMurphy(final Npc npc, Player player) {
        String name = player.getUsername().toLowerCase();
        Integer waiting = pendingCatch.get(name);
        if (waiting != null && waiting > 0) {
            // OURS -- no transcript survives for this gate, but the net
            // must be emptied or a second trip would overwrite it.
            new Conversation(player, npc)
                .npc("looks like your net is still full from your last trip")
                .npc("go and search the catch on the dock before we head out again")
                .start();
            return;
        }
        boolean met = player.getCurStat(FISHING) >= MIN_FISHING_LEVEL;
        boolean known = introduced.contains(name);
        if (known && met) {
            this.returningConversation(npc, player);
            return;
        }
        introduced.add(name);
        Conversation c = new Conversation(player, npc)
            .player("good day to you sir")
            .npc("well hello my brave adventurer")
            .player("what are you up to?")
            .npc("getting ready to go fishing of course")
            .npc("there's no time to waste")
            .npc("i've got all the supplies i need from the shop at the end of the pier")
            .npc("they sell good rope, although their bailing buckets aren't too effective");
        // Coming back still under the level gets the opener and nothing
        // else -- the transcript's "After first time / If requirement are
        // not met" branch simply ends here, with no menu.
        if (!known) {
            this.firstTimeMenu(c, met, false, false);
        }
        c.start();
    }

    /*
     * The three-way menu after the introduction. The transcript lets you
     * hear the two flavour answers once each, in either order, always
     * keeping "could i help?" on the table -- so the two seen flags
     * rebuild the option list as branches are used up.
     */
    private void firstTimeMenu(Conversation c, final boolean met,
                               final boolean seenFish, final boolean seenBoat) {
        List<String> opts = new ArrayList<String>();
        if (!seenFish) {
            opts.add("what fish do you catch?");
        }
        if (!seenBoat) {
            opts.add("your boat doesn't look too safe");
        }
        opts.add("could i help?");
        final FishingTrawler self = this;
        c.options(new Choice(opts.toArray(new String[opts.size()])) {
            public void picked(int option, Conversation c) {
                String picked = opts.get(option);
                if (picked.equals("what fish do you catch?")) {
                    c.player("what fish do you catch?")
                     .npc("i get all sorts, anything that lies on the sea bed")
                     .npc("you never know what you're going to get until...")
                     .npc("...you pull up the net");
                    self.firstTimeMenu(c, met, true, seenBoat);
                } else if (picked.equals("your boat doesn't look too safe")) {
                    c.player("your boat doesn't look too safe")
                     .npc("that's because it's not, the dawn thing's full of holes")
                     .player("oh, so i suppose you can't go out for a while")
                     .npc("oh no, i don't let a few holes stop an experienced sailor like me")
                     .npc("i could sail these seas in a barrel")
                     .npc("i'll be going out soon enough");
                    self.firstTimeMenu(c, met, seenFish, true);
                } else {
                    self.couldIHelp(c, met);
                }
            }
        });
    }

    private void couldIHelp(Conversation c, boolean met) {
        c.player("could i help?")
         .npc("well of course you can")
         .npc("i'll warn you though, the seas are merciless")
         .npc("and with out fishing experience you won't catch much")
         .message("you need a fishing level of 15 or above to catch any fish on the trawler");
        if (!met) {
            // The transcript ends the under-levelled variant right here.
            return;
        }
        final FishingTrawler self = this;
        c.npc("on occasions the net rip's, so you'll need some rope to repair it")
         .player("rope...ok")
         .npc("there's also a slight problem with leaks")
         .player("leaks!")
         .npc("nothing some swamp paste won't fix")
         .player("swamp paste?")
         .npc("oh, and one more thing...")
         .npc("..i hope you're a good swimmer")
         .picker(new Choice("actually, i think i'll leave it", "i'll be fine, lets go", "what's swamp paste?") {
             public void picked(int option, Conversation c) {
                 if (option == 0) {
                     c.player("actually, i think i'll leave it")
                      .npc("bloomin' land lover's");
                 } else if (option == 1) {
                     c.player("i'll be fine, lets go");
                     self.sailChoice(c, true);
                 } else {
                     c.player("what's swamp paste?")
                      .npc("swamp tar mixed with flour...")
                      .npc("...which is then heated over a fire")
                      .player("where can i find swamp tar?")
                      .npc("unfortunately the only supply of swamp tar is in the swamps below lumbridge");
                 }
             }
         });
    }

    private void returningConversation(final Npc npc, Player player) {
        final FishingTrawler self = this;
        new Conversation(player, npc)
            .player("hello again murphy")
            .npc("good day to you land lover")
            .npc("fancy hitting the high seas again?")
            .picker(new Choice("no thanks, i still feel ill from last time", "yes, lets do it") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.player("no thanks, i still feel ill from last time")
                         .npc("hah..softy");
                        return;
                    }
                    c.player("yes, lets do it");
                    self.sailChoice(c, false);
                }
            })
            .start();
    }

    private void sailChoice(Conversation c, final boolean firstTime) {
        c.npc("would you like to sail east or west?")
         .options(new Choice("east please", "west please") {
             public void picked(int option, Conversation c) {
                 final Boat boat = option == 0 ? EAST : WEST;
                 c.npc("good stuff, jump aboard");
                 if (firstTime) {
                     c.npc("ok m hearty, keep your eys pealed")
                      .npc("i need you to clog up those holes quick time")
                      .player("i'm ready and waiting");
                 }
                 c.then(new Effect() {
                     public void run(Conversation c) {
                         board(c.getPlayer(), boat);
                     }
                 });
             }
         });
    }

    private static void board(Player player, Boat boat) {
        if (boat.stage == 0) {
            startTrip(boat);
        } else if (boat.stage != 1 || boat.tick > JOIN_CUTOFF_TICKS
                || boat.crew.size() >= MAX_CREW) {
            /* A trip more than four minutes gone (or already sinking, or
               full at 10) can't be joined -- the window and the cap are
               OpenRSC-verified numbers.

               Source note on the two lines below: neither appears
               anywhere on classic.runescape.wiki. They come from the
               OpenRSC implementation, and "appeears" reads as a sic
               carried over from a real capture rather than an invention,
               so they are reproduced as found -- but unlike the rest of
               Murphy's dialogue here, they are not wiki-attested. */
            Npc dockMurphy = world.getNpc(MURPHY_DOCK, DOCK_X - 6, DOCK_X + 6, DOCK_Y - 6, DOCK_Y + 6);
            if (dockMurphy != null) {
                player.informOfNpcMessage(new ChatMessage(dockMurphy,
                    "sorry m hearty it appeears the boat is in the middle of a game", player));
            }
            player.getActionSender().sendMessage("The boat should be available in a couple of minutes");
            return;
        }
        boat.crew.add(player);
        player.teleport(boat.deckSpawnX, boat.deckSpawnY, true);
    }

    private static void startTrip(final Boat boat) {
        boat.stage = 1;
        boat.water = 0;
        boat.fish = 0;
        boat.tick = 0;
        boat.netBroken = false;
        boat.tripTicks = DataConversions.random(5, 12) * 60 * 1000 / TICK_MS;
        boat.nextWave = waveInterval(1);
        boat.event = new DelayedEvent(null, TICK_MS){
            public void run() {
                tickBoat(boat);
            }
        };
        world.getDelayedEventHandler().add(boat.event);
    }

    private static void tickBoat(Boat boat) {
        boat.tick++;
        for (Iterator<Player> it = boat.crew.iterator(); it.hasNext();) {
            Player p = it.next();
            if (p.isRemoved() || !p.loggedIn() || !boat.aboard(p)) {
                it.remove();
            }
        }
        if (boat.crew.isEmpty()) {
            endTrip(boat);
            return;
        }
        boat.water += boat.leaks.size();
        if (boat.tick >= boat.nextWave) {
            wave(boat);
            boat.nextWave = boat.tick + waveInterval(boat.crew.size());
        }
        if (boat.stage == 1 && boat.water >= SINKING_WATER) {
            startSinking(boat);
            return;
        }
        if (boat.stage == 2 && boat.water >= SUNK_WATER) {
            sink(boat);
            return;
        }
        if (boat.tick >= boat.tripTicks) {
            finish(boat);
        }
    }

    /* Pacing, OpenRSC-verified: a lone sailor gets a wave every 15-24
       ticks of 1-5 leaks; two crew every 8-15 of 2-6; a full boat is
       relentless. */
    private static int waveInterval(int crew) {
        if (crew <= 1) {
            return DataConversions.random(15, 24);
        }
        if (crew == 2) {
            return DataConversions.random(8, 15);
        }
        return DataConversions.random(Math.max(3, 5 - crew / 2), 10);
    }

    private static int leaksPerWave(int crew) {
        if (crew <= 1) {
            return DataConversions.random(1, 5);
        }
        if (crew == 2) {
            return DataConversions.random(2, 6);
        }
        return DataConversions.random(4, 5 + crew);
    }

    private static void wave(Boat boat) {
        int n = leaksPerWave(boat.crew.size());
        for (int i = 0; i < n; i++) {
            spawnLeak(boat);
        }
        if (boat.netBroken) {
            shout(boat, "check those nets");
            return;
        }
        if (DataConversions.random(0, 99) >= 75) {
            boat.netBroken = true;
            shout(boat, "check those nets");
            return;
        }
        if (DataConversions.random(0, 1) == 0) {
            boat.fish += DataConversions.random(0, boat.crew.size() + 3);
        }
        if (boat.stage == 2) {
            shout(boat, DataConversions.random(0, 1) == 0
                ? "we're going under" : "we'll all end up in a watery grave");
        } else {
            shout(boat, DataConversions.random(0, 1) == 0
                ? "That's the stuff, fill those holes" : "it's a fierce sea today traveller");
        }
    }

    private static void spawnLeak(Boat boat) {
        int minX = boat.stage == 2 ? boat.sinkMinX : boat.deckMinX;
        int maxX = boat.stage == 2 ? boat.sinkMaxX : boat.deckMaxX;
        int minY = boat.stage == 2 ? boat.sinkMinY : boat.deckMinY;
        int maxY = boat.stage == 2 ? boat.sinkMaxY : boat.deckMaxY;
        for (int tries = 0; tries < 10; tries++) {
            boolean north = DataConversions.random(0, 1) == 0;
            int x = DataConversions.random(minX + 1, maxX - 1);
            int y = north ? minY + 1 : maxY - 1;
            if (world.getTile(x, y).hasGameObject()) {
                continue;
            }
            // Direction 0/4 to face away from each gunwale -- a guess, as
            // no capture records the leak sprites' orientation.
            GameObject leak = new GameObject(Point.location(x, y),
                north ? LEAK_NORTH : LEAK_SOUTH, north ? 0 : 4, 0);
            world.registerGameObject(leak);
            boat.leaks.add(leak);
            return;
        }
    }

    /** Murphy calls out over the deck; every crew member hears it. */
    private static void shout(Boat boat, String msg) {
        int minX = boat.stage == 2 ? boat.sinkMinX : boat.deckMinX;
        int maxX = boat.stage == 2 ? boat.sinkMaxX : boat.deckMaxX;
        int minY = boat.stage == 2 ? boat.sinkMinY : boat.deckMinY;
        int maxY = boat.stage == 2 ? boat.sinkMaxY : boat.deckMaxY;
        Npc murphy = world.getNpc(MURPHY_SHIP, minX - 1, maxX + 1, minY - 1, maxY + 1);
        for (Player p : boat.crew) {
            if (murphy != null) {
                p.informOfNpcMessage(new ChatMessage(murphy, msg, p));
            } else {
                p.getActionSender().sendMessage(msg);
            }
        }
    }

    private static void startSinking(Boat boat) {
        for (GameObject leak : boat.leaks) {
            world.unregisterGameObject(leak);
        }
        boat.leaks.clear();
        boat.stage = 2;
        for (Player p : boat.crew) {
            p.getActionSender().sendMessage("the boats full of water");
            p.getActionSender().sendMessage("it's sinking!");
            p.teleport(boat.sinkSpawnX, boat.sinkSpawnY, true);
        }
        shout(boat, "we're going under");
    }

    private static void sink(Boat boat) {
        for (Player p : boat.crew) {
            p.getActionSender().sendMessage("the boats gone under");
            p.getActionSender().sendMessage("you're lost at sea!");
            p.teleport(boat.failX, boat.failY, true);
        }
        endTrip(boat);
    }

    private static void finish(Boat boat) {
        int share = boat.crew.isEmpty() ? 0 : 2 * boat.fish / boat.crew.size();
        for (Player p : boat.crew) {
            if (share > 0) {
                String name = p.getUsername().toLowerCase();
                Integer already = pendingCatch.get(name);
                pendingCatch.put(name, (already == null ? 0 : already) + share);
            }
            // OURS -- the homeward line has no surviving transcript.
            p.getActionSender().sendMessage("murphy turns the boat towards the shore");
            p.teleport(DOCK_X, DOCK_Y, true);
        }
        endTrip(boat);
    }

    private static void endTrip(Boat boat) {
        for (GameObject leak : boat.leaks) {
            world.unregisterGameObject(leak);
        }
        boat.leaks.clear();
        boat.crew.clear();
        boat.stage = 0;
        boat.water = 0;
        boat.fish = 0;
        boat.tick = 0;
        boat.netBroken = false;
        if (boat.event != null) {
            boat.event.stop();
            boat.event = null;
        }
    }

    /*
     * Murphy at the wheel, mid-trip. Both branches are the transcript's:
     * the quit flow always ends in the sinking messages and the west
     * debris field (OpenRSC-verified: quitters wash up at the west
     * wreckage whichever boat they left), and asking after him gets his
     * honest opinion of bailing.
     */
    private void shipMurphy(final Npc npc, Player player) {
        final Boat boat = boatWith(player);
        Conversation c = new Conversation(player, npc).npc("whoooahh sailor");
        if (boat == null) {
            c.start();
            return;
        }
        c.picker(new Choice("i've had enough, take me back", "how you doing murphy?") {
            public void picked(int option, Conversation c) {
                if (option == 1) {
                    c.player("how you doing murphy?")
                     .npc("don't bail..it's a waste of time")
                     .npc("just fill those holes");
                    return;
                }
                c.player("i've had enough, take me back")
                 .npc("haa .. the soft land lovers lost there see legs have they?")
                 .player("something like that")
                 .npc("we're too far out now, it'd be dangerous")
                 .options(new Choice("I insist murphy, take me back", "Ok then murphy, just keep us afloat") {
                     public void picked(int option, Conversation c) {
                         if (option == 1) {
                             c.player("ok then murphy, just keep us afloat")
                              .npc("that's the attitude sailor");
                             return;
                         }
                         c.player("i insist murphy, take me back")
                          .npc("ok, ok, i'll try, but don't say i didn't warn you")
                          .message("murphy sharply turns the large ship")
                          .message("the boats gone under")
                          .message("you're lost at sea!")
                          .then(new Effect() {
                              public void run(Conversation c) {
                                  Player p = c.getPlayer();
                                  boat.crew.remove(p);
                                  p.teleport(WEST.failX, WEST.failY, true);
                              }
                          });
                     }
                 });
            }
        });
        c.start();
    }

    /** "fill" on a leak (1071/1077) -- see ObjectAction. */
    public static void fillLeak(Player player, GameObject object) {
        if (player.getInventory().countId(SWAMP_PASTE) < 1) {
            // OURS -- the transcript for an empty-handed fill is lost.
            player.getActionSender().sendMessage("you'll need some swamp paste to fill that");
            return;
        }
        player.getInventory().remove(SWAMP_PASTE, 1);
        player.getActionSender().sendInventory();
        player.getActionSender().sendMessage("you fill the hole with swamp paste");
        world.unregisterGameObject(object);
        Boat boat = boatAt(object.getX(), object.getY());
        if (boat != null) {
            boat.leaks.remove(object);
        }
    }

    /** "inspect" on a Trawler net (1101/1102) -- see ObjectAction. The
        dock's moored display boat has a pair too; those are simply never
        damaged. Rope is only spent on success, exactly as recorded. */
    public static void inspectNet(Player player, GameObject object) {
        player.getActionSender().sendMessage("you inspect the net");
        Boat boat = boatAt(object.getX(), object.getY());
        if (boat == null || boat.stage == 0 || !boat.netBroken) {
            player.getActionSender().sendMessage("it is not damaged");
            return;
        }
        player.getActionSender().sendMessage("it's begining to rip");
        if (player.getInventory().countId(ROPE) < 1) {
            // OURS -- no transcript for the rope-less attempt.
            player.getActionSender().sendMessage("you'll need some rope to fix it");
            return;
        }
        player.getActionSender().sendMessage("you attempt to fix it with your rope");
        if (DataConversions.random(0, 1) == 0) {
            player.getInventory().remove(ROPE, 1);
            player.getActionSender().sendInventory();
            player.getActionSender().sendMessage("you manage to fix the net");
            boat.netBroken = false;
        } else {
            // Wording from the wiki's prose ("due to the harsh
            // conditions"); the transcript's failure line is lost.
            player.getActionSender().sendMessage("but you fail due to the harsh conditions");
        }
    }

    /** "bail with " on the Bailing Bucket (1282) -- see InvActionHandler.
        It works on both boats: the east boat ignoring it in 2003 is on
        the wiki's glitch list, and defects don't get restored. */
    public static void bail(Player player) {
        Boat boat = boatWith(player);
        if (boat == null || boat.stage == 0) {
            player.getActionSender().sendMessage("@pnk@ Nothing interesting happens.");
            return;
        }
        int out = boat.stage == 2 ? DataConversions.random(2, 4) : DataConversions.random(1, 3);
        boat.water = Math.max(0, boat.water - out);
        Bubble bubble = new Bubble(player, BAILING_BUCKET);
        for (Player p : player.getViewArea().getPlayersInView()) {
            p.informOfBubble(bubble);
        }
        // OURS -- Murphy's "don't bail" is recorded, the bail itself isn't.
        player.getActionSender().sendMessage("you bail some water out of the boat");
    }

    /** "Search" on the dock's Trawler catch (1106) -- see ObjectAction.
        One item per search, fish-or-junk decided as it comes out, exp on
        the spot: this is why the wiki's fishing-potion trick works, the
        level check happens here and reads the boosted stat. */
    public static void collectCatch(Player player) {
        String name = player.getUsername().toLowerCase();
        Integer left = pendingCatch.get(name);
        if (left == null || left <= 0) {
            pendingCatch.remove(name);
            player.getActionSender().sendMessage("the smelly net is empty");
            return;
        }
        player.getActionSender().sendMessage("you search the smelly net");
        Bubble bubble = new Bubble(player, NET_ITEM);
        for (Player p : player.getViewArea().getPlayersInView()) {
            p.informOfBubble(bubble);
        }
        int id, xp;
        if (DataConversions.random(0, 1) == 0) {
            int[] junk = JUNK[DataConversions.random(0, JUNK.length - 1)];
            id = junk[0];
            xp = junk[1];
        } else {
            id = FALLBACK_FISH;
            xp = FALLBACK_XP;
            int level = player.getCurStat(FISHING);
            for (int[] tier : FISH_TIERS) {
                int diff = level - tier[0];
                if (diff >= 0 && DataConversions.percentChance(diff > 40 ? 70 : 30 + diff)) {
                    id = tier[1];
                    xp = tier[2];
                    break;
                }
            }
        }
        String found = reveal(id);
        if (found != null) {
            player.getActionSender().sendMessage("you find...");
            player.getActionSender().sendMessage(found);
        }
        if (player.getInventory().full()) {
            // The wiki: a full inventory drops the haul at your feet.
            world.registerItem(new Item(id, player.getX(), player.getY(), 1, player));
        } else {
            player.getInventory().add(new InvItem(id, 1));
            player.getActionSender().sendInventory();
        }
        player.incExp(FISHING, xp, true);
        player.getActionSender().sendStat(FISHING);
        left--;
        if (left <= 0) {
            pendingCatch.remove(name);
            player.getActionSender().sendMessage("that's the lot");
        } else {
            pendingCatch.put(name, left);
        }
    }

    /** "climb on" a floating barrel (1070) -- see ObjectAction. The
        barrels only exist in the two debris fields, so anyone clicking
        one is a castaway, and the shore they wash up on is the same spot
        every time.

        The three lines are Transcript:Barrel_(floating), verbatim.

        Damage is NOT random, and it is not the flat 2 the Fishing Trawler
        page claims either. Talk:Barrel_(floating) collects six 2018 replay
        datapoints of Hits level against damage taken:

            76 -> 3    67 -> 3    57 -> 2    56 -> 2    51 -> 2    20 -> 1

        which is deterministic and scales with the Hits LEVEL (not current
        hitpoints). round(level / 25) reproduces all six exactly -- in
        integer arithmetic (level + 12) / 25 -- and predicts 4 at 99 Hits,
        which is the figure that talk page separately argues for on its own
        interpolation grounds. Below 13 Hits the expression yields 0, so it
        is clamped to a minimum of 1: the swim always costs something.

        This is a best fit over the recorded datapoints rather than a
        recovered formula, but it is better evidenced than either constant
        the article pages give. Capped so the sea can leave you on 1 hit
        and never drowns you. */
    public static void escapeBarrel(Player player) {
        player.getActionSender().sendMessage("you climb onto the floating barrel");
        player.getActionSender().sendMessage("and begin to kick your way to the shore");
        int cur = player.getCurStat(HITS);
        int damage = Math.max(1, (player.getMaxStat(HITS) + 12) / 25);
        damage = Math.min(damage, Math.max(0, cur - 1));
        if (damage > 0) {
            player.setCurStat(HITS, cur - damage);
            player.getActionSender().sendStat(HITS);
        }
        player.getActionSender().sendMessage("you make it to the shore tired and weary");
        player.teleport(SHORE_X, SHORE_Y, true);
    }
}
