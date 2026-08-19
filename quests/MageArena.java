import org.rscdaemon.server.entityhandling.EntityHandler;
import org.rscdaemon.server.entityhandling.defs.extras.ItemWieldableDef;
import org.rscdaemon.server.event.DelayedEvent;
import org.rscdaemon.server.model.Entity;
import org.rscdaemon.server.model.GameObject;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Item;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.Shop;
import org.rscdaemon.server.quest.Quest;
import org.rscdaemon.server.quest.QuestTrigger;
import org.rscdaemon.server.quest.Quests;
import org.rscdaemon.server.quest.dialogue.Choice;
import org.rscdaemon.server.quest.dialogue.Conversation;
import org.rscdaemon.server.quest.dialogue.Effect;
import org.rscdaemon.server.util.DataConversions;

/**
 * The Mage Arena, northern Wilderness.
 *
 * Rebuilt end to end against a recorded playthrough. The version this
 * replaces was written before any transcript existed and was wrong in
 * mechanism as well as in wording: it spawned Kolodion's forms next to the
 * player wherever they happened to be standing, never teleported anyone into
 * the arena at all, and had the chamber guardian hand out the cape. None of
 * that is what happens.
 *
 * What actually happens, and what this now implements:
 *
 *   1. Kolodion (712) stands in his cave under the Wilderness. Talk to him,
 *      agree to fight, and he teleports you into the arena at (229,130) --
 *      after checking you carry no weapons or armour, and after zeroing your
 *      Attack and Strength, because "hand to hand combat is useless" in here
 *      is a rule the arena enforces rather than advice.
 *
 *   2. He fights you through five shapeshifted forms in a row, blasting you
 *      with spells the whole time. Beat all five and he teleports you back to
 *      his cave and tells you to step into the magic pool.
 *
 *   3. The pool (1155) carries you down to the chamber. You chant at one of
 *      the three god stones -- Jagex put a "chant to" command on those objects
 *      themselves -- and that god's cape appears in your inventory.
 *
 *   4. The chamber guardian (784) then gives you a staff. One, free, once.
 *
 *   5. After that the barrier (1027) at (228,119) is your own way back into
 *      the arena, so you can cast your god spell at the three battle mages
 *      until it works outside. That unlock is counted separately -- see
 *      GodCharges.java -- and is not this class's business beyond making the
 *      arena reachable and the mages fightable.
 *
 * The second entrance is worth spelling out because it reads like a separate
 * minigame and is not one. Kolodion's route and the barrier route lead into
 * the same arena; the difference is only how the "no weapons or armour" rule
 * is applied. Kolodion checks you before he teleports you, the barrier checks
 * you at the door. The barrier stays shut until he has accepted you, which is
 * what his own refusal message says: "you cannot enter without the permission
 * of kolodion".
 *
 * World data, all of it already shipped and none of it previously used:
 *
 *     Kolodion's cave, plane 3
 *         Kolodion 712 (445,3370) . Lundail 793 rune shop (441,3376)
 *         Gundai 792 banker (452,3376) . magic pool 1155 (446,3374)
 *     The chamber, plane 3
 *         return pool 1166 (471,3383) . chamber guardian 784 (470,3387),
 *         (473,3387) . Saradomin stone 1152 (465,3398),(465,3400) .
 *         Guthix stone 1153 (470,3401),(468,3401) . Zamorak stone 1154
 *         (473,3398),(473,3400)
 *     The arena, plane 0
 *         barrier 1027 (228,119) . gates 1019 (237,129) / 1020 (219,129) .
 *         battle mages 789 Guthix, 790 Zamorak, 791 Saradomin, 120 hits each
 *
 * Every one of those object ids appears exactly once in the world and nowhere
 * outside this minigame, so associating them by id alone is safe -- checked
 * against GameObjectLoc rather than assumed.
 *
 * Every teleport destination below was checked walkable with tools/collision.py
 * before use. Two of the obvious-looking choices are solid rock: (446,3373)
 * beside the cave pool, and (471,3384) beside the chamber pool. Landing a
 * player in either would have been the teleport-into-rock bug this project has
 * already shipped once.
 */
public class MageArena extends Quest {

    public final static int UID = Quests.MAGE_ARENA;

    // ------------------------------------------------------------ entities --

    private static final int KOLODION = 712;
    private static final int GUARDIAN = 784;

    /** The five shapeshifted forms, in fight order: human, ogre, spider, souless, demon. */
    private static final int[] FORMS = { 713, 757, 758, 759, 760 };

    private static final int MAGE_GUTHIX = 789, MAGE_ZAMORAK = 790, MAGE_SARADOMIN = 791;

    private static final int POOL_DOWN = 1155, POOL_UP = 1166;
    private static final int BARRIER = 1027;
    private static final int GATE_EAST = 1019, GATE_WEST = 1020;
    private static final int STONE_SARADOMIN = 1152, STONE_GUTHIX = 1153, STONE_ZAMORAK = 1154;

    private static final int CAPE_ZAMORAK = 1213, CAPE_SARADOMIN = 1214, CAPE_GUTHIX = 1215;
    private static final int STAFF_ZAMORAK = 1216, STAFF_GUTHIX = 1217, STAFF_SARADOMIN = 1218;

    private static final int[] GOD_CAPES = { CAPE_ZAMORAK, CAPE_SARADOMIN, CAPE_GUTHIX };

    private static final int ATTACK = 0, STRENGTH = 2, HITS = 3, MAGIC = 6;

    /** By name, not by tile: the guardian's chamber has no shop box of its own. */
    private static final String STAFF_SHOP = "Mage Arena Staves";

    // --------------------------------------------------------- geography --

    private static final int ARENA_X = 229, ARENA_Y = 130;
    private static final int FORM_X = 227, FORM_Y = 130;
    private static final int CAVE_X = 446, CAVE_Y = 3370;
    private static final int CHAMBER_X = 471, CHAMBER_Y = 3382;
    private static final int BARRIER_IN_X = 228, BARRIER_IN_Y = 120;
    private static final int BARRIER_OUT_X = 228, BARRIER_OUT_Y = 118;

    /**
     * The barrier is the arena's north edge. Anything at or below this y is
     * inside; the tile the barrier itself stands on is (228,119).
     */
    private static final int BARRIER_Y = 119;

    // ------------------------------------------------------------- stages --

    /*
     * One persisted int carries two things, the same trick GodCharges uses for
     * three counters: the low nibble is how far through the minigame the
     * player is, and bits 8 and up remember which form they were on if they
     * died or logged out mid-gauntlet. Kolodion resumes them at that form
     * rather than starting the five again, which is what he means by "let us
     * continue with the battle".
     */
    private static final int NOT_STARTED = 0;
    private static final int FIGHTING = 1;
    private static final int ACCEPTED = 2;      // beat all five forms
    private static final int CAPE_TAKEN = 3;    // chanted at a stone
    private static final int STAFF_TAKEN = 4;   // guardian handed over the staff

    private static final int PROGRESS_MASK = 0x0F;
    private static final int FORM_SHIFT = 8;
    private static final int FORM_MASK = 0x0F;

    /** The form currently spawned for this player, so a kill can be matched to it. */
    private Npc currentForm;

    /** Kolodion's recurring spell, live only while a form is up. */
    private DelayedEvent blast;

    /** The battle mage this player is currently engaged with, so it shouts once and not every tick. */
    private Npc shoutedAt;

    public MageArena(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Mage Arena");
        setFinalStage(Integer.MIN_VALUE); // never completes, same as GangMembership

        /* No 2003 manual page survives for this minigame; description is ours. */
        describe("Fight Kolodion through his five shapeshifted forms, then chant at the stone of a god to take up their cape, staff and spell.");
        setStartPoint("Kolodion's cave, beneath the Mage Arena in the northern Wilderness");
        setSpeakTo("Kolodion");
        requireLevel(MAGIC, 60);
        rewardOther("The cape and staff of your chosen god");
        rewardOther("That god's spell, once you have cast it enough times inside the arena");

        associateNpc(KOLODION);
        associateNpc(GUARDIAN);
        for (int form : FORMS) {
            associateNpc(form);
        }
        associateNpc(MAGE_GUTHIX);
        associateNpc(MAGE_ZAMORAK);
        associateNpc(MAGE_SARADOMIN);

        associateObject(POOL_DOWN);
        associateObject(POOL_UP);
        associateObject(BARRIER);
        associateObject(GATE_EAST);
        associateObject(GATE_WEST);
        associateObject(STONE_SARADOMIN);
        associateObject(STONE_GUTHIX);
        associateObject(STONE_ZAMORAK);

        /* Claimed only so refusesPickup() below is consulted for them. */
        for (int cape : GOD_CAPES) {
            associateItem(cape);
        }
    }

    /**
     * The other half of the one-cape rule, and the half that actually closes
     * the hole: the stone refusing to speak stops you being *given* a second
     * cape, but nothing stopped you dropping your Guthix cape, chanting
     * Zamorak, and picking the Guthix one back up off the floor.
     *
     * Jagex enforced it here and this is Jagex's own message for it, from
     * Transcript:Guthix stone (identical on the Saradomin and Zamorak stone
     * transcripts): "Attempting to pick up more than one God Cape (Even from
     * same God)" -> "you may only possess one sacred cape at a time".
     */
    public boolean refusesPickup(InvItem item) {
        if (!isGodCape(item.getID()) || !holdsAnyGodCape(getOwner())) {
            return false;
        }
        getOwner().getActionSender().sendMessage("@que@you may only possess one sacred cape at a time");
        return true;
    }

    /**
     * Telekinetic grab is the third door onto a god cape and Jagex shut it
     * with a different sentence -- same transcript, "Attempting to cast
     * Telekinetic grab on a God Cape" -> "I can't use telekinetic grab on this
     * object". Note it refuses unconditionally, whether or not you already
     * hold a cape: the pickup rule is about possession, this one is about the
     * spell.
     */
    public boolean refusesTelegrab(InvItem item) {
        if (!isGodCape(item.getID())) {
            return false;
        }
        getOwner().getActionSender().sendMessage("@que@I can't use telekinetic grab on this object");
        return true;
    }

    public void completeQuest() {
    }

    // ------------------------------------------------------------- state --

    /** getStage() is -1 for "never spoken to him", not 0 -- see GodCharges for the same trap. */
    private int packed() {
        int s = getStage();
        return s < 0 ? 0 : s;
    }

    private int progress() {
        return packed() & PROGRESS_MASK;
    }

    private int formIndex() {
        return (packed() >>> FORM_SHIFT) & FORM_MASK;
    }

    private void setProgress(int progress) {
        setStage((packed() & ~PROGRESS_MASK) | (progress & PROGRESS_MASK));
    }

    private void setFormIndex(int index) {
        setStage((packed() & ~(FORM_MASK << FORM_SHIFT)) | ((index & FORM_MASK) << FORM_SHIFT));
    }

    // ------------------------------------------------------- equipment --

    /*
     * The staves the arena lets through, listed rather than inferred.
     *
     * The wiki states the rule as a table on the Mage Arena page -- weapons:
     * permitted "Staves", not permitted "Melee, Ranged, Battlestaves, Staff of
     * Iban" -- so every staff comes in and exactly two families stay out.
     * Dramen is a staff by that reading and is in the list. (OpenRSC's own
     * arena omits Dramen; the wiki's generic "Staves" is the older and more
     * direct source, so it wins.)
     *
     * This used to be a ceiling on aim and power instead -- anything at or
     * under 8 aim, 4 power counted as a staff. That worked only by accident:
     * our wieldable table clamps four staves to 8/4 that Jagex did not, and
     * the wiki's real numbers for them are Staff of fire 11/6, Staff of earth
     * 10/5, Magic staff 12/7 and Dramen 10/10. Every one of those is over the
     * ceiling, so the first time anyone corrects that table the arena would
     * have started refusing four legitimate staves -- silently, at the
     * barrier, with the same message that sends you away to bank. A list does
     * not rot that way.
     *
     * Staff of Armadyl is deliberately absent: it carries no magic bonus at
     * all and is a quest weapon that happens to be staff-shaped.
     */
    private static final int[] ARENA_STAVES = {
        100,   // staff
        101,   // Staff of Air
        102,   // Staff of water
        103,   // Staff of earth
        197,   // Staff of fire
        198,   // Magic Staff
        509,   // Dramen Staff
        1216,  // Staff of zamorak
        1217,  // Staff of guthix
        1218   // Staff of Saradomin
    };

    private static boolean isArenaStaff(int id) {
        for (int i = 0; i < ARENA_STAVES.length; ++i) {
            if (ARENA_STAVES[i] == id) {
                return true;
            }
        }
        return false;
    }

    /**
     * "you may not take armour or weapons into the arena", enforced.
     *
     * Kolodion says it and the barrier repeats it, so it lives here once and
     * both entrances call it. Carried counts, not just worn: worn items are
     * inventory items with a flag in this server, so walking in with a
     * platebody in your pack is refused the same as wearing it, which is what
     * "take into" means.
     *
     * Decided from the wieldable table rather than from a list of ids, so
     * nothing has to be added here when an item is added to the game.
     *
     * Letting the god staves through is not a nicety. A god spell will not
     * cast without its own staff wielded, and the arena is the only place the
     * spell can be trained, so a rule that read them as weapons would lock
     * every player out of the entire reason they came back.
     */
    private boolean carriesWeaponOrArmour(Player p) {
        for (InvItem item : p.getInventory().getItems()) {
            if (!item.isWieldable()) {
                continue;
            }
            ItemWieldableDef def = EntityHandler.getItemWieldableDef(item.getID());
            if (def == null) {
                continue;
            }
            if (!allowedInArena(item.getID(), def)) {
                return true;
            }
        }
        return false;
    }

    /*
     * Wield slots, as the wieldable table numbers them. Only the three that
     * carry a rule of their own are named.
     */
    private static final int WIELD_SHIELD = 3, WIELD_WEAPON = 4,
                             WIELD_AMULET = 8, WIELD_CAPE = 11;

    /**
     * Kolodion's rule is "no armour and no weapons", and it is worth reading it
     * as exactly that and nothing more.
     *
     * This used to demand that an item carry a positive magic bonus to come in,
     * which is a different and much harsher rule. It refused seventy-six items
     * that are neither a weapon nor a piece of armour -- every plain amulet and
     * necklace, priest and monk and druid robes, party hats -- and, the way it
     * was actually found, it refused the *charged dragonstone amulet*, because
     * that one carries 3 armour and 2 ranged in the wieldable table alongside
     * its magic. A player wearing nothing but a dragonstone amulet and a Staff
     * of guthix -- the arena's own reward, and a legal mage's kit by any
     * reading -- was turned away at the barrier.
     *
     * So the question is asked by slot instead:
     *
     *   amulet, cape   always in. Neither is armour or a weapon, whatever
     *                  incidental points the table hangs on it. Attested: a
     *                  replay shows a player entering in amulet, robes and
     *                  wizard hat.
     *   weapon slot    only a staff. Every staff in the game sits at or under
     *                  8 aim and 4 power with a magic bonus; battlestaves jump
     *                  to 35 and Iban's to 50, and a sword or bow carries no
     *                  magic at all, so all three are refused.
     *   everywhere     armour is armour wherever it is worn -- refused on
     *      else        armourPoints alone. That keeps helmets, platebodies,
     *                  chainmail, shields and plate legs out while letting
     *                  wizard and black robes, wizard hats and skirts in,
     *                  since those carry no armour points at all.
     */
    private boolean allowedInArena(int id, ItemWieldableDef def) {
        /*
         * Amulets and capes, whole slots, per the wiki table's permitted
         * column: "Amulets, Silver Jewellery, capes". That is every cosmetic
         * cape -- black, blue, green, orange, yellow, purple, red -- as well
         * as the three the arena itself hands out, and every necklace and
         * amulet including the charged dragonstone, which the wieldable table
         * hangs 3 armour and 2 ranged on and which is what this rule was
         * found refusing.
         */
        if (def.getWieldPos() == WIELD_AMULET || def.getWieldPos() == WIELD_CAPE) {
            return true;
        }
        /*
         * The shield slot goes out whole. It holds shields, which are armour,
         * and bows, which are weapons -- RSC hangs a bow off the shield hand,
         * so a bow carries no armour points and would otherwise have walked
         * straight in through the armour test below. There is nothing in the
         * slot a mage wants.
         */
        if (def.getWieldPos() == WIELD_SHIELD) {
            return false;
        }
        if (def.getArmourPoints() > 0) {
            return false;
        }
        if (def.getWieldPos() == WIELD_WEAPON) {
            return isArenaStaff(id);
        }
        /*
         * Everything left is worn somewhere that is not a weapon, a shield, an
         * amulet or a cape, and armourPoints above has already thrown out the
         * melee and ranged sets and the gauntlets the wiki names. What passes
         * is what it names as permitted: magic and prayer robes and hats,
         * Santa hats. Leather boots and gloves stay out on their 1-2 armour
         * points -- the wiki lists those as "(Probable)" permitted under
         * Unconfirmed, and a guess is not worth opening the rule for.
         */
        return true;
    }

    /**
     * Two lines, paced apart, then whatever was going to happen next does not.
     * Kolodion and the barrier both use it, so both refuse the same way.
     */
    private void refuseEntry(Player p) {
        new Conversation(p, null)
            .message("You cannot enter the arena...")
            .message("...while carrying weapons or armour")
            .start();
    }

    // ---------------------------------------------------------- capes --

    private boolean isGodCape(int id) {
        for (int cape : GOD_CAPES) {
            if (cape == id) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether this player already holds any god's cape, anywhere.
     *
     * Inventory and bank both, and any of the three rather than the one being
     * chanted for: a Saradomin cape in the bank stops the Guthix stone
     * answering, which is the rule as recorded -- the stones give nothing at
     * all while you already have a cape, and you have to be rid of the one you
     * have before another god will speak to you. Deliberately not keyed on the
     * quest stage: a player who drops a cape to swap gods has not gone
     * backwards through the minigame, and a stage test would either lock them
     * out or pay them twice.
     */
    private boolean holdsAnyGodCape(Player p) {
        for (InvItem i : p.getInventory().getItems()) {
            if (isGodCape(i.getID())) {
                return true;
            }
        }
        for (InvItem i : p.getBank().getItems()) {
            if (isGodCape(i.getID())) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------- dispatch --

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        if (entity instanceof Npc) {
            Npc npc = (Npc) entity;
            if (trigger == QuestTrigger.NPC_KILLED) {
                formDefeated(npc);
                return;
            }
            if (trigger != QuestTrigger.NPC_TALK) {
                return;
            }
            if (npc.getID() == KOLODION) {
                kolodion(npc);
            } else if (npc.getID() == GUARDIAN) {
                guardian(npc);
            }
            return;
        }
        if (!(entity instanceof GameObject) || trigger != QuestTrigger.OBJECT_ACT1) {
            return;
        }
        GameObject object = (GameObject) entity;
        switch (object.getID()) {
            case POOL_DOWN:       poolDown(); break;
            case POOL_UP:         poolUp(); break;
            case BARRIER:         barrier(); break;
            case GATE_EAST:       gate(237, -1); break;
            case GATE_WEST:       gate(219, +1); break;
            case STONE_SARADOMIN: chant("saradomin", CAPE_SARADOMIN); break;
            case STONE_GUTHIX:    chant("guthix", CAPE_GUTHIX); break;
            case STONE_ZAMORAK:   chant("zamorak", CAPE_ZAMORAK); break;
        }
    }

    /**
     * The battle mages are not fightable until Kolodion has accepted you.
     *
     * Refusing the attack rather than hiding the npcs keeps them visible from
     * outside the barrier, which is how a player learns what the place is for.
     */
    public boolean refusesAttack(Npc npc) {
        if (!isBattleMage(npc.getID())) {
            return false;
        }
        if (progress() < ACCEPTED) {
            getOwner().getActionSender().sendMessage("@que@You are not yet ready to fight the battle mages");
            return true;
        }
        shout(npc);
        return false;
    }

    /** Same gate for spells, which is how a god spell is actually trained here. */
    public void spellCast(Npc npc, int spellId, int damage) {
        if (isBattleMage(npc.getID())) {
            shout(npc);
        }
    }

    private boolean isBattleMage(int id) {
        return id == MAGE_GUTHIX || id == MAGE_ZAMORAK || id == MAGE_SARADOMIN;
    }

    /**
     * Each mage calls out for its own god as it engages -- once per
     * engagement, not once per hit.
     *
     * Guthix's line is not the one the other two use. Saradomin's and
     * Zamorak's are "feel the wrath of", Guthix's mage simply hails him.
     */
    private void shout(Npc npc) {
        if (npc.equals(this.shoutedAt)) {
            return;
        }
        this.shoutedAt = npc;
        String line;
        switch (npc.getID()) {
            case MAGE_SARADOMIN: line = "@yel@Saradomin mage: feel the wrath of Saradomin"; break;
            case MAGE_ZAMORAK:   line = "@yel@zamorak mage: feel the wrath of zamorak"; break;
            default:             line = "@yel@guthix mage: hail guthix"; break;
        }
        getOwner().getActionSender().sendMessage(line);
    }

    // -------------------------------------------------------- Kolodion --

    private void kolodion(final Npc npc) {
        Player p = getOwner();

        if (p.getMaxStat(MAGIC) < 60) {
            new Conversation(p, npc)
                .player("hello there")
                .player("what is this place?")
                .npc("do not waste my time with trivial questions!")
                .npc("i am the great kolodion, master of battle magic")
                .npc("i have an arena to run")
                .player("can i enter?")
                .npc("hah, a wizard of your level..don't be absurd")
                .start();
            return;
        }

        int prog = progress();

        if (prog == FIGHTING) {
            new Conversation(p, npc)
                .player("hi")
                .npc("you return young conjurer..")
                .npc("..you obviously have a taste for the darkside of magic")
                .npc("let us continue with the battle...now")
                .then(new Effect() {
                    public void run(Conversation c) {
                        enterArena(c, true);
                    }
                })
                .start();
            return;
        }

        if (prog == ACCEPTED) {
            new Conversation(p, npc)
                .player("hello kolodion")
                .npc("hello  young mage.. you're a tough one you")
                .player("what now?")
                .npc("step into the magic pool, it will take you to the chamber")
                .npc("there you must decide which god you'll represent in the arena")
                .player("ok .. thanks kolodion")
                .npc("that's what i'm here for")
                .start();
            return;
        }

        if (prog >= CAPE_TAKEN) {
            new Conversation(p, npc)
                .player("hello kolodion")
                .npc("hey there, how are you?, enjoying the bloodshed?")
                .player("it's not bad, i've seen worse")
                .options(new Choice("I think i've have enough for now",
                                    "how can i use my new spells outside of the arena?") {
                    public void picked(int option, Conversation c) {
                        if (option == 0) {
                            c.npc("shame , you're a good battle mage");
                            c.npc("hope to see you soon");
                            return;
                        }
                        c.npc("experience my friend, experience");
                        c.npc("once you've used the spell enough times in the arena...");
                        c.npc("...you'll be able to use them in the rest of runescape");
                        c.player("good stuff");
                        c.npc("not so good for the citizens, they won't stand a chance");
                    }
                }.says(0, "i think i've had enough for now"))
                .start();
            return;
        }

        // Never spoken to him.
        new Conversation(p, npc)
            .player("hello there")
            .player("what is this place?")
            .npc("i am the great kolodion, master of battle magic ...")
            .npc("... and this is my battle arena")
            .npc("top wizards travel from all over to fight here")
            .options(new Choice("can i fight here?", "what's the point of that?", "that's barbaric") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        canIFight(c);
                    } else if (option == 1) {
                        whatsThePoint(c);
                    } else {
                        barbaric(c);
                    }
                }
            })
            .start();
    }

    private void canIFight(Conversation c) {
        c.npc("my arena is open to any high level wizard");
        c.npc("but this is no game traveller, wizards fall in this arena..");
        c.npc("..never to rise again, the strongest of mage's have been destroyed");
        c.npc("but if you're sure you want in?");
        c.options(new Choice("yes indeedy", "no, i don't") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    joinFight(c);
                } else {
                    c.npc("your loss");
                }
            }
        });
    }

    private void whatsThePoint(Conversation c) {
        c.npc("we learn how to use our magic to it fullest...");
        c.npc("..,how to channel forces of the cosmos into our world..");
        c.npc("..,but mainly I just like blasting people into dust");
        c.options(new Choice("can i fight here?", "that's barbaric") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    canIFight(c);
                } else {
                    barbaric(c);
                }
            }
        });
    }

    private void barbaric(Conversation c) {
        c.npc("nope, it's magic, but I know what you mean");
        c.npc("so do you want to join us?");
        c.options(new Choice("yes indeedy", "no, i don't") {
            public void picked(int option, Conversation c) {
                if (option == 0) {
                    joinFight(c);
                } else {
                    c.npc("your loss");
                }
            }
        });
    }

    private void joinFight(Conversation c) {
        c.npc("good..good, you have a healthy sense of competition");
        c.npc("remember traveller in my arena hand to hand combat is useless");
        c.npc("your strength will diminish as you enter the arena");
        c.npc("but the spells you can learn are amongst the most powerful in runescape");
        c.npc("but before i can accept you in, we must duel");
        c.npc("you may not take armour or weapons into the arena");
        c.options(new Choice("ok let's fight", "no thanks") {
            public void picked(int option, Conversation c) {
                if (option != 0) {
                    c.npc("your loss");
                    return;
                }
                c.npc("i must check that you're up to scratch");
                c.player("you don't need to worry about that");
                c.npc("not just any magician can enter traveller");
                c.npc("only the most powerful, the most feared");
                c.npc("before you use the power of this arena");
                c.npc("you must prove yourself against me");
                c.npc("now!");
                c.then(new Effect() {
                    public void run(Conversation c) {
                        enterArena(c, false);
                    }
                });
            }
        });
    }

    /**
     * @param resuming true when picking the gauntlet back up at the form the
     *                 player last reached, false when starting from the human
     *                 form. Only the first attempt is announced with a blast;
     *                 a resumed one drops you back in beside a form already
     *                 mid-swing.
     */
    private void enterArena(Conversation c, boolean resuming) {
        Player p = c.getPlayer();
        if (carriesWeaponOrArmour(p)) {
            c.stop();
            refuseEntry(p);
            return;
        }
        if (!resuming) {
            setProgress(FIGHTING);
            setFormIndex(0);
        }
        p.teleport(ARENA_X, ARENA_Y, false);
        // "your strength will diminish as you enter the arena" -- not a warning,
        // a rule. Both stats come back the ordinary way once out.
        p.setCurStat(ATTACK, 0);
        p.setCurStat(STRENGTH, 0);
        p.getActionSender().sendStat(ATTACK);
        p.getActionSender().sendStat(STRENGTH);
        spawnForm(p, formIndex(), !resuming);
    }

    // ------------------------------------------------------- the fight --

    private void spawnForm(Player p, int index, boolean announce) {
        if (index < 0 || index >= FORMS.length) {
            return;
        }
        setFormIndex(index);
        Npc form = new Npc(FORMS[index], FORM_X, FORM_Y,
                           FORM_X - 6, FORM_X + 6, FORM_Y - 6, FORM_Y + 6);
        form.setRespawn(false);
        world.registerNpc(form);
        this.currentForm = form;
        if (announce) {
            // Only the human and the ogre are described as using the staff;
            // from the spider on he is simply blasting you again.
            p.getActionSender().sendMessage("kolodion blasts you "
                + (index <= 1 ? "with his staff" : "again"));
            hurt(7 + DataConversions.getRandom().nextInt(9));
        }
        form.attackPlayer(p);
        startBlasting(p);
    }

    /**
     * Kolodion keeps casting at you for as long as a form of his is standing.
     *
     * Which spell he throws widens as he changes: claws and lightning from the
     * start, flames only once he is past the spider. The taunt that comes with
     * each is the human form's in the first fight and the ogre's thereafter --
     * "this is your end" and "die fool" give way to "roooaar" and "aaarrgghhh"
     * once he stops being a man.
     *
     * The damage figure is a curve fitted by OpenRSC to observed play rather
     * than anything Jagex published, reimplemented here from its description:
     * between 16 and 25 Hits levels per point of damage depending on how high
     * the player's Hits is, shifted upward once per transformation. At 99 Hits
     * it lands between 5 and 8 a cast. Recorded as a reconstruction, not a
     * recovered constant.
     */
    private void startBlasting(final Player p) {
        stopBlasting();
        this.blast = new DelayedEvent(p, 8000) {
            public void run() {
                MageArena quest = MageArena.this;
                Npc form = quest.currentForm;
                if (form == null || form.isRemoved() || !p.loggedIn() || !p.getLocation().inMageArena()) {
                    stop();
                    return;
                }
                quest.castAtPlayer(p, quest.formIndex());
            }
        };
        world.getDelayedEventHandler().add(this.blast);
    }

    private void stopBlasting() {
        if (this.blast != null) {
            this.blast.stop();
            this.blast = null;
        }
    }

    private void castAtPlayer(Player p, int index) {
        // Human and ogre throw two spells; the spider onward has all three.
        int phase = index <= 1 ? 0 : index - 1;
        int spell = DataConversions.getRandom().nextInt(phase >= 2 ? 3 : 2);
        boolean human = index == 0;

        if (spell == 0) {
            p.getActionSender().sendMessage(human ? "@yel@kolodion: this is your end" : "@yel@kolodion: roooaar");
            p.getActionSender().sendMessage("claws grab you from below");
        } else if (spell == 1) {
            p.getActionSender().sendMessage(human ? "@yel@kolodion: die fool" : "@yel@kolodion: aaarrgghhh");
            p.getActionSender().sendMessage("@yel@kolodion: feel the power of the elements");
            p.getActionSender().sendMessage("you are hit by a lightning bolt");
        } else {
            boolean demon = index == FORMS.length - 1;
            p.getActionSender().sendMessage(demon && DataConversions.getRandom().nextBoolean()
                ? "@yel@kolodion: burn fool ....burn"
                : "@yel@kolodion: feel the power of the elements mortal");
            p.getActionSender().sendMessage("you burst into flames");
        }

        int maxHits = p.getMaxStat(HITS);
        int perPoint = (int) Math.floor(1.0 / (0.06 - (0.01 / 48.0) * maxHits));
        if (perPoint < 1) {
            perPoint = 1;
        }
        int shift = (int) Math.round((0.004 * maxHits + 0.4) * perPoint);
        int scaled = Math.max(p.getCurStat(HITS) + (phase - 1) * shift, 0);
        hurt((int) Math.ceil((double) scaled / perPoint) + 1);
    }

    private void formDefeated(Npc npc) {
        if (this.currentForm == null || !npc.equals(this.currentForm)) {
            return;
        }
        this.currentForm = null;
        stopBlasting();

        final int next = formIndex() + 1;
        final Player p = getOwner();

        if (next < FORMS.length) {
            transform(p, next);
            return;
        }

        setProgress(ACCEPTED);
        setFormIndex(0);
        new Conversation(p, null)
            .message("kolodion again slumps to the floor..motionless")
            .message("..he slowly rises to his feet in his true form")
            .message("@yel@Kolodion: \"well done young adventurer\"")
            // Recorded as "you are truly"; OpenRSC's transcription has "you
            // truly are". The playthrough is the source of record here.
            .message("@yel@Kolodion: \"you are truly a worthy battle mage\"")
            .message("kolodion teleports you to his cave")
            .then(new Effect() {
                public void run(Conversation c) {
                    c.getPlayer().teleport(CAVE_X, CAVE_Y, false);
                }
            })
            .then(new Effect() {
                public void run(Conversation c) {
                    afterVictory();
                }
            })
            .start();
    }

    /**
     * The five transitions, each worded differently. He slumps, then slumps
     * "once more", then "again"; the demon is the only one he grows larger for.
     */
    private void transform(final Player p, final int next) {
        Conversation c = new Conversation(p, null);
        if (next == 1) {
            c.message("kolodion slumps to the floor..");
            c.message("..his body begins to grow and changes form");
            c.message("He becomes an intimidating ogre");
        } else if (next == 2) {
            c.message("kolodion slumps to the floor once more..");
            c.message("..but again his body begins to grow and he changes form");
            c.message("He becomes an enormous spider");
        } else if (next == 3) {
            c.message("kolodion again slumps to the floor..");
            c.message("..but again his body begins to grow as he changes form");
            c.message("He becomes an ethereal being");
        } else {
            c.message("kolodion again slumps to the floor..motionless");
            c.message("..but again his body begins to grow as he changes form");
            c.message("...larger this time");
            c.message("He becomes a vicious demon");
        }
        c.then(new Effect() {
            public void run(Conversation c) {
                spawnForm(c.getPlayer(), next, true);
            }
        });
        c.start();
    }

    /**
     * The conversation Kolodion opens himself, the moment you land back in his
     * cave. He is standing at (445,3370) and you arrive next to him.
     */
    private void afterVictory() {
        Player p = getOwner();
        Npc kolodion = nearby(KOLODION);
        if (kolodion == null) {
            p.getActionSender().sendMessage("kolodion is currently busy");
            return;
        }
        new Conversation(p, kolodion)
            .player("what now kolodion? how can i learn some of those spells?")
            .npc("these spells are gifts from the gods")
            .npc("first you must choose which god...")
            .npc("...you will represent in the mage arena")
            .player("cool")
            .npc("step into the magic pool, it will carry you to the chamber")
            .player("the chamber?")
            .npc("there you must decide your loyalty")
            .player("ok kolodion , thanks for the battle")
            .npc("remember young mage, you must use the spells...")
            .npc("...many times in the arena before you can use them outside")
            .player("no problem")
            .start();
    }

    /** The nearest visible npc of this id, or null. */
    private Npc nearby(int id) {
        for (Npc n : getOwner().getViewArea().getNpcsInView()) {
            if (n.getID() == id && !n.isRemoved()) {
                return n;
            }
        }
        return null;
    }

    // ------------------------------------------------------- the pools --

    private void poolDown() {
        Player p = getOwner();
        if (progress() < ACCEPTED) {
            p.getActionSender().sendMessage("@que@Nothing happens.");
            return;
        }
        new Conversation(p, null)
            .message("you step into the pool of sparkling water")
            .message("you feel energy rush through your veins")
            .message("you are teleported further under ground")
            .then(new Effect() {
                public void run(Conversation c) {
                    c.getPlayer().teleport(CHAMBER_X, CHAMBER_Y, false);
                }
            })
            .start();
    }

    private void poolUp() {
        Player p = getOwner();
        new Conversation(p, null)
            .message("you step into the pool of sparkling water")
            .message("you feel energy rush through your veins")
            .then(new Effect() {
                public void run(Conversation c) {
                    c.getPlayer().teleport(CAVE_X, CAVE_Y, false);
                }
            })
            .start();
    }

    // ------------------------------------------------------ the stones --

    /**
     * Jagex put the "chant to" command on the stones themselves, which is why
     * this is an object action and not a spell or a dialogue.
     *
     * The stone answers only if you hold no god's cape at all. Holding one --
     * any of the three, in your inventory or your bank -- and it stays silent:
     * "but there is no response". Drop the one you have and any of the three
     * will speak to you again, which is how a player changes gods.
     */
    private void chant(final String god, final int cape) {
        Player p = getOwner();
        if (progress() < ACCEPTED) {
            p.getActionSender().sendMessage("@que@Nothing happens.");
            return;
        }
        final boolean first = progress() == ACCEPTED;
        Conversation c = new Conversation(p, null);
        c.message(first ? "you kneel and begin to chant to " + god : "you kneel and chant to " + god);
        if (holdsAnyGodCape(p)) {
            c.message("but there is no response");
            c.start();
            return;
        }
        c.message("you feel a rush of energy charge through your veins");
        c.message("...and a cape appears before you");
        c.then(new Effect() {
            public void run(Conversation c) {
                Player pl = c.getPlayer();
                /* The stone does not hand the cape over. It puts it on the
                   ground -- "a cape appears before you" is literal -- and it
                   comes to you the way a telekinetic grab does, with the orange
                   bubble at your feet. Recorded on video; without the bubble
                   the cape simply materialises in the pack and the two lines
                   above stop making sense. */
                teleGrab(pl, new InvItem(cape, 1));
                if (progress() == ACCEPTED) {
                    setProgress(CAPE_TAKEN);
                }
            }
        });
        c.start();
    }

    /**
     * Puts an item in the player's hands the way the god stones do: the grab
     * bubble on the floor where the item was, then the item.
     *
     * The bubble goes to everyone who can see it, not just the player -- the
     * packet carries an offset from the receiver's own position, so each viewer
     * is sent the same absolute tile and works out their own offset.
     *
     * If there is no room the cape stays where it appeared rather than
     * evaporating, which is what a real grab would do too.
     */
    private void teleGrab(Player p, InvItem item) {
        p.getActionSender().sendTeleBubble(p.getX(), p.getY(), true);
        for (Player viewer : p.getWatchedPlayers().getAllEntities()) {
            viewer.getActionSender().sendTeleBubble(p.getX(), p.getY(), true);
        }
        if (p.getInventory().canHold(item)) {
            p.getInventory().add(item);
            p.getActionSender().sendInventory();
        } else {
            world.registerItem(new Item(item.getID(), p.getX(), p.getY(), item.getAmount(), p));
        }
    }

    // ---------------------------------------------------- the guardian --

    private void guardian(final Npc npc) {
        Player p = getOwner();
        int prog = progress();

        if (prog < ACCEPTED) {
            new Conversation(p, npc)
                .player("hello")
                .npc("sssshhh...the gods are talking..i can hear their whispers")
                .npc("only those kolodion has accepted may hear them too")
                .start();
            return;
        }

        if (prog == ACCEPTED) {
            // First arrival in the chamber: he explains what the stones are for.
            new Conversation(p, npc)
                .player("hello my friend, kolodion sent me down")
                .npc("sssshhh...the gods are talking..i can hear their whispers")
                .npc("..can you hear them adventurer...they're calling you")
                .player("erm...ok!")
                // The doubled "the" is Jagex's own, in the recording. Left as it was said.
                .npc("go and chant to the the sacred stone of your chosen god")
                .npc("you will be rewarded")
                .player("ok?")
                .npc("once you're done come back to me...")
                .npc("...and i'll supply you with a mage staff ready for battle")
                .start();
            return;
        }

        if (prog == CAPE_TAKEN) {
            new Conversation(p, npc)
                .player("hi")
                .npc("hello adventurer, have you made your choice?")
                .player("i have")
                .npc("good, good .. i hope you chose well")
                .npc("you will have been rewarded with a magic cape")
                .npc("now i will give you a magic staff")
                .npc("these are all the weapons and armour you'll need here")
                .message("the mage guardian gives you a magic staff")
                .then(new Effect() {
                    public void run(Conversation c) {
                        Player pl = c.getPlayer();
                        pl.getInventory().add(new InvItem(staffForCape(pl), 1));
                        pl.getActionSender().sendInventory();
                        setProgress(STAFF_TAKEN);
                    }
                })
                .start();
            return;
        }

        // Staff already taken. Every later staff is bought over the counter --
        // including one for a god the player switches to afterwards.
        new Conversation(p, npc)
            .player("hello again")
            .npc("hello adventurer, are you looking for another staff?")
            .options(new Choice("what do you have to offer?", "no thanks") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        c.npc("well, let me know if you need one");
                        return;
                    }
                    c.npc("take a look");
                    c.then(new Effect() {
                        public void run(Conversation c) {
                            Shop shop = world.getShop(STAFF_SHOP);
                            if (shop == null) {
                                return;
                            }
                            c.getPlayer().setAccessingShop(shop);
                            c.getPlayer().getActionSender().showShop(shop);
                        }
                    });
                }
            })
            .start();
    }

    /**
     * The staff matches the cape the player chanted for. Read off the cape
     * rather than stored, so it cannot disagree with what they are actually
     * carrying; Guthix is the fallback because a player who reached here with
     * no cape at all has nothing else to key on.
     */
    private int staffForCape(Player p) {
        for (InvItem i : p.getInventory().getItems()) {
            if (i.getID() == CAPE_ZAMORAK) {
                return STAFF_ZAMORAK;
            }
            if (i.getID() == CAPE_SARADOMIN) {
                return STAFF_SARADOMIN;
            }
            if (i.getID() == CAPE_GUTHIX) {
                return STAFF_GUTHIX;
            }
        }
        for (InvItem i : p.getBank().getItems()) {
            if (i.getID() == CAPE_ZAMORAK) {
                return STAFF_ZAMORAK;
            }
            if (i.getID() == CAPE_SARADOMIN) {
                return STAFF_SARADOMIN;
            }
        }
        return STAFF_GUTHIX;
    }

    // -------------------------------------------- barrier and the gates --

    /**
     * The arena's own door, and the reason the minigame looks like it has two
     * entrances when it has one arena.
     *
     * Outward is unconditional: whatever you are carrying, you may always
     * leave, and leaving clears away any form of Kolodion still standing so a
     * player cannot park a half-finished gauntlet and come back to it from
     * outside. Inward is refused until Kolodion has accepted you, in his own
     * words, and then checked for weapons and armour exactly as he checks you
     * himself.
     */
    private void barrier() {
        Player p = getOwner();
        if (p.getY() >= BARRIER_IN_Y) {
            new Conversation(p, null)
                .message("you pass through the mystical barrier")
                .then(new Effect() {
                    public void run(Conversation c) {
                        c.getPlayer().teleport(BARRIER_OUT_X, BARRIER_OUT_Y, false);
                        abandonFight();
                    }
                })
                .start();
            return;
        }
        if (progress() < ACCEPTED) {
            p.getActionSender().sendMessage("@que@you cannot enter without the permission of kolodion");
            return;
        }
        new Conversation(p, null)
            .message("the barrier is checking your person for weapons")
            .then(new Effect() {
                public void run(Conversation c) {
                    Player pl = c.getPlayer();
                    if (carriesWeaponOrArmour(pl)) {
                        c.stop();
                        refuseEntry(pl);
                        return;
                    }
                    pl.teleport(BARRIER_IN_X, BARRIER_IN_Y, false);
                }
            })
            .start();
    }

    /**
     * The two gates in the arena's east and west walls, at (237,129) and
     * (219,129). Both are 1 wide by 2 tall, so each occupies its own column at
     * y 129 and y 130, and crossing one means changing x.
     *
     * They are scenery rather than doors, so there is nothing to swing open --
     * the player is stepped to the far side, which is what the pair of
     * messages describes. One gate handles both directions.
     *
     * @param gateX  the gate's own column
     * @param inward which way the arena lies from it, -1 or +1; used only to
     *               break a tie that should not happen (see crossing()).
     */
    private void gate(final int gateX, final int inward) {
        final Player p = getOwner();
        new Conversation(p, null)
            .message("you open the gate ...")
            .message("... and walk through")
            .then(new Effect() {
                public void run(Conversation c) {
                    Player pl = c.getPlayer();
                    pl.teleport(gateX + crossing(pl, gateX, inward), pl.getY(), false);
                }
            })
            .start();
    }

    /**
     * Which side of the gate to put the player down on: -1 for the column
     * before it, +1 for the column after.
     *
     * This used to be one line -- {@code x >= gateX ? gateX - 1 : gateX + 1}
     * -- and it broke both gates, because it assumed the player is never
     * standing on the gate's own column. They almost always are. The gate does
     * not block, and atObject() counts any tile of the footprint as "at" it,
     * so clicking a gate walks you *onto* it; by the time this runs, x equals
     * gateX and the old test resolved that tie to gateX - 1 every time.
     * Whichever gate you were at, one of the two directions was a bounce:
     * the east gate let you in and refused to let you out, and the west gate
     * shoved you back a tile when you tried to enter. Both were the same line.
     *
     * The way out is the facing sprite. You cannot be standing on the gate
     * without having stepped onto it, and updateSprite() sets the facing from
     * that step: sprites 1-3 mean the step raised x, 5-7 mean it lowered x
     * (Mob.mobSprites). So the direction you were already travelling is the
     * direction you get carried in -- which is what walking through a gate
     * means.
     *
     * Sprites 0 and 4 are a pure north-south step, which cannot have brought
     * you onto the column, and 4 is also the value a freshly spawned player
     * carries. Both mean the facing tells us nothing, so fall back to sending
     * them into the arena: they clicked a gate, and someone already inside is
     * off the column and never reaches this line.
     */
    private int crossing(Player p, int gateX, int inward) {
        if (p.getX() < gateX) {
            return +1;
        }
        if (p.getX() > gateX) {
            return -1;
        }
        int sprite = p.getSprite();
        if (sprite >= 1 && sprite <= 3) {
            return +1;
        }
        if (sprite >= 5 && sprite <= 7) {
            return -1;
        }
        return inward;
    }

    /**
     * Called when a player leaves the arena with a form still up: through the
     * barrier, or by dying. The form goes with them, and the stage stays at
     * FIGHTING with the form index where it was, so Kolodion resumes them
     * there rather than starting the five again.
     */
    private void abandonFight() {
        stopBlasting();
        if (this.currentForm != null) {
            if (!this.currentForm.isRemoved()) {
                world.unregisterNpc(this.currentForm);
            }
            this.currentForm = null;
        }
        this.shoutedAt = null;
    }

    // ------------------------------------------------------------ util --

    private void hurt(int damage) {
        Player p = getOwner();
        p.setCurStat(HITS, Math.max(0, p.getCurStat(HITS) - damage));
        p.getActionSender().sendStat(HITS);
    }
}
