import org.rscdaemon.server.event.SingleEvent;
import org.rscdaemon.server.model.Entity;
import org.rscdaemon.server.model.GameObject;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.quest.Quest;
import org.rscdaemon.server.quest.QuestTrigger;
import org.rscdaemon.server.quest.Quests;
import org.rscdaemon.server.quest.dialogue.Choice;
import org.rscdaemon.server.quest.dialogue.Conversation;
import org.rscdaemon.server.quest.dialogue.Effect;

/**
 * Grand tree. Released 12 December 2002, written by Thomas Woode, and the
 * update that brought Agility online.
 *
 * The grand tree is dying and the chief tree guardian is the one killing it:
 * Glough wants the timber for thirty battleships and a war on humanity. The
 * quest is four accusations nobody believes, a jail cell, a glider crash, and
 * finally a black demon in the roots.
 *
 * The tree's inside is mapped somewhere else entirely. The stronghold stands
 * around (700,460); the trunk you walk into is at (416,162) with its upper
 * floors at +944 and +1888, and the top level -- Charlie's cage and the glider
 * -- is on the underground plane at (418,2994). ObjectTelePoints already
 * carries every door between them, so this quest only has to move the player
 * where vanilla moved them by hand.
 *
 *     King Narnode Shareen  npc 541, (417,163), inside the trunk
 *     King Narnode Shareen  npc 545, (704,3283), the dungeon under the roots
 *     Hazelmere             npc 546, (532,754), his island south of the arena
 *     Glough                npc 547, (714,1424), his tree house
 *     charlie               npc 550, (419,2991), the cage at the top
 *     Gnome guard           npc 551 by the tree and on the top floor,
 *                           npc 562 on the stronghold gate itself
 *     Gnome pilot           npc 552, (414,2996), and five more at the gliders
 *     Shipyard worker       npc 557, (402,766), on the shipyard gate
 *     Shipyard foreman      npc 560 (396,740) and 561 (409,753)
 *     Femi                  npc 563, (708,534), outside the stronghold gate,
 *                           npc 564, (708,509), inside it
 *     Anita                 npc 565, (739,1382), the north west tree house
 *     Black Demon           npc 568, summoned, level 175
 *
 *     tree gnome translation  item 918
 *     Bark sample             item 919
 *     gloughs journal         item 921
 *     invoice                 item 922
 *     glough's key            item 925
 *     glough's notes          item 926
 *     Pebble                  items 927 hO, 928 NI, 929 :::, 930 hA
 *     Daconia rock            item 931
 *
 *     Gnome stronghold gate object 626, (703,531)
 *     cupboard              object 620, (716,1419), Glough's journal
 *     Chest                 object 632 shut / 631 open, (715,1418)
 *     Watch tower           object 635 up (710,1420), 646 down (710,2364)
 *     Stone stand           object 634, (709,2364)
 *     stone tile            object 643, (416,161), the king's tunnel
 *     cage                  object 617, (420,2991) and (420,2992)
 *     glider                object 618 flyable, 622 grounded
 *     gate                  object 624 shut / 623 open, (401,762), the shipyard
 *     Root                  objects 609 and 610 search, 637 the one that
 *                           hides the rock, 638 and 639 push, into the mine
 *     stronghold spirit Tree object 661, (703,486)
 *
 * Deviations:
 *
 *  - The Watch tower above Glough's house is an Agility obstacle: vanilla asks
 *    for level 25 and pays 7.5 experience, and this asks for the same. Agility
 *    cannot yet be trained on this server -- there is no obstacle handler and
 *    no course -- so in practice nobody can climb it, and the quest stops at
 *    the pebbles until that is built. The check is vanilla's and the missing
 *    piece is the skill, so the check stays and the skill is a separate job.
 *    The 7.5 is paid as 7: experience here is a whole number.
 *
 *  - Femi is asked about the barrel by talking to her rather than by opening
 *    the gate. Vanilla starts that conversation when you first walk up to the
 *    gate; a gate trigger cannot both hold a conversation and put the player
 *    through, and whether she was helped is remembered either way.
 *
 *  - Whether Femi was helped is a bit above the finished stage, cleared when
 *    the king ends the quest. The stages here are a count rather than a set of
 *    flags, so it could not be folded into one of them.
 *
 *  - What is sitting on the stone stand is not saved. Only the stage number
 *    survives a logout, and four pebble positions do not fit in it. Log out
 *    half way through the puzzle and the stand is bare again -- but the king
 *    hands out replacement pebbles for any that are neither in the inventory
 *    nor on the stand, so it costs a walk rather than the quest.
 *
 *  - The black demon is unregistered after its 250 second timeout, as vanilla
 *    does. A player who runs away from it and comes back finds the dungeon
 *    empty and has to work the stand again, which re-summons it.
 *
 *  - The demon drops nothing. Its drop table is the ordinary black demon's and
 *    belongs to the drop tables, not to a quest class.
 *
 *  - Glough is not fought and cannot be attacked. Vanilla never lets you; he
 *    flees when the demon falls and is found by the king's guards offstage.
 *
 *  - The gnome glider network and the stronghold spirit tree are rewards of
 *    this quest and are implemented here. The village spirit tree and the two
 *    saplings belong to Tree gnome village and are left to it; the stronghold
 *    tree offers the village as a destination once both quests are done, which
 *    is what the recorded dialogue does.
 *
 *  - The Karamja glider stays broken forever once you have crashed it, so the
 *    Karamja pilot never flies anybody home. That is vanilla: his dialogue has
 *    no working branch.
 *
 *  - A lever is recorded under this quest -- "you pull on the lever / you hear
 *    a loud mechanical churning / as the huge railing raises to the cave roof /
 *    the cage lowers behind you" -- and there is no lever object anywhere in
 *    the tree, the tree top or the dungeon to hang it on. Either the placement
 *    is missing from the world data or the transcript is filed under the wrong
 *    quest. It is left out rather than invented a home for.
 *
 *  - Invented, because no transcript records them: the stone tile's lines when
 *    the quest is over, the cage's refusal, the gnome guard on the gate before
 *    the warrant, and the shipyard gate's own message. Everything else is
 *    Jagex's.
 */
public class GrandTree extends Quest {

    public final static int UID = Quests.GRAND_TREE;

    // ----------------------------------------------------------------- npcs --

    private static final int KING_TREE = 541, KING_DUNGEON = 545;
    private static final int HAZELMERE = 546;
    private static final int GLOUGH = 547;
    private static final int CHARLIE = 550;
    /**
     * The gnome guards. There are three ids, all named "Gnome guard", and the
     * distinction is where they stand:
     *
     *   551  (699,454) (701,455) by the tree, and (417,2994) on the top floor
     *   562  (704,526) (700,526) ON THE STRONGHOLD GATE, plus (705,465)
     *        (701,467) at the inner approach
     *   582  four spawns inside the tree itself, plane 2
     *
     * This mattered. The gate is object 626 at (703,531) and its refusals were
     * being hung on a guard looked up by id 551 -- whose nearest spawn is 77
     * tiles away and has never once been in view when a player touches that
     * gate. nearby() returned null, and a null speaker reaches
     * NpcUpdatePacketBuilder:29 as cm.getSender().getIndex(). The guards who
     * are actually standing there are 562.
     *
     * So the lookup takes every guard id and picks whichever is in view, and
     * the refusal survives finding none at all. 582 is deliberately not part of
     * the gate lookup: those are inside the tree, nowhere near it, and no
     * recorded text belongs to them.
     */
    private static final int GATE_GUARD = 551;
    private static final int GATE_GUARD_POST = 562;
    private static final int PILOT_TREE = 552;
    private static final int PILOT_FELDIP = 556;
    private static final int GATE_WORKER = 557;
    private static final int FOREMAN_A = 560, FOREMAN_B = 561;
    /**
     * Femi, twice: 563 at (708,534) just outside the stronghold gate, where
     * the barrel and the cart both are, and 564 at (708,509) inside it. Same
     * name, same def, one character -- so both run the same script rather than
     * one of them standing there mute.
     *
     * In practice the inside one only ever reaches the barrel scene, because
     * the states that matter for the rest of it are states where the guards
     * have you on the wrong side of the gate. Helping her indoors still counts;
     * the smuggle is owed for the favour, not for the tile it happened on.
     */
    private static final int FEMI = 563;
    private static final int FEMI_INSIDE = 564;
    private static final int ANITA = 565;
    private static final int DEMON = 568;
    private static final int PILOT_KARAMJA = 569;
    private static final int PILOT_KHARID = 570;
    private static final int PILOT_VARROCK = 571;
    private static final int PILOT_WOLF = 572;

    // ---------------------------------------------------------------- items --

    private static final int BOOK = 918;
    private static final int BARK = 919;
    private static final int JOURNAL = 921;
    private static final int INVOICE_ITEM = 922;
    private static final int KEY = 925;
    private static final int NOTES = 926;
    private static final int ROCK = 931;
    private static final int COINS = 10;

    /**
     * The four pebbles, in the order they have to end up on the stand.
     *
     * Cut into them, in that order, are hO NI ::: hA. Read off the alphabet in
     * the translation book that is O P E N. Each has its own sprite, so the
     * player can tell them apart in the inventory without being told.
     */
    private static final int[] PEBBLE = { 927, 928, 929, 930 };

    // -------------------------------------------------------------- scenery --

    private static final int STRONGHOLD_GATE = 626, GATE_X = 703, GATE_Y = 531;
    private static final int CUPBOARD = 620, CUPBOARD_X = 716, CUPBOARD_Y = 1419;
    private static final int CHEST_SHUT = 632, CHEST_OPEN = 631;
    private static final int CHEST_X = 715, CHEST_Y = 1418;
    private static final int TOWER_UP = 635, TOWER_UP_X = 710, TOWER_UP_Y = 1420;
    private static final int TOWER_DOWN = 646, TOWER_DOWN_X = 710, TOWER_DOWN_Y = 2364;
    private static final int STAND = 634, STAND_X = 709, STAND_Y = 2364;
    private static final int TILE = 643, TILE_X = 416, TILE_Y = 161;
    private static final int CAGE = 617;
    private static final int SHIPYARD_GATE = 624, SHIPYARD_GATE_X = 401, SHIPYARD_GATE_Y = 762;
    private static final int GLIDER = 618, GROUNDED_GLIDER = 622;
    private static final int SPIRIT_TREE = 661, SPIRIT_TREE_X = 703, SPIRIT_TREE_Y = 486;

    private static final int ROOT_A = 609, ROOT_B = 610;
    private static final int ROOT_ROCK = 637, ROOT_ROCK_X = 707, ROOT_ROCK_Y = 3297;
    private static final int ROOT_PUSH_A = 638, ROOT_PUSH_A_X = 701, ROOT_PUSH_A_Y = 3280;
    private static final int ROOT_PUSH_B = 639, ROOT_PUSH_B_X = 701, ROOT_PUSH_B_Y = 3279;

    // ------------------------------------------------------------- landings --

    /** Inside the trunk, at the foot of the ladder the dungeon comes up on. */
    private static final int TRUNK_X = 416, TRUNK_Y = 162;
    /** The dungeon, beside the king. */
    private static final int DUNGEON_X = 703, DUNGEON_Y = 3284;
    /** The cage at the top of the tree. Charlie has the other one. */
    private static final int CAGE_X = 420, CAGE_Y = 2992;
    /**
     * Where the guards put you. The cage object's own tiles at x 420 are
     * blocked (that is what makes it a cage), so the player stands beside
     * Charlie's column at x 419 -- walked in game, not guessed.
     */
    private static final int JAIL_X = 419, JAIL_Y = 2992;

    private static final int GLIDER_TREE_X = 414, GLIDER_TREE_Y = 2997;
    private static final int GLIDER_KARAMJA_X = 390, GLIDER_KARAMJA_Y = 753;
    private static final int GLIDER_VARROCK_X = 88, GLIDER_VARROCK_Y = 662;
    private static final int GLIDER_KHARID_X = 57, GLIDER_KHARID_Y = 504;
    private static final int GLIDER_WOLF_X = 402, GLIDER_WOLF_Y = 462;

    // Feldip Hills was deliberately left out of the glider menu below: it is an
    // OSRS-only stop added by One Small Favour, a quest that never existed in
    // Classic. Real Classic gliders only ever went to the four places above.

    /** Object 618 (glider) sits here too -- a built mid-air stop shared by every route, not a destination of its own. */
    private static final int GLIDER_MIDAIR_X = 221, GLIDER_MIDAIR_Y = 3567;

    /** The spirit tree network, matching Tree gnome village's own landings. */
    private static final int TREE_BATTLEFIELD_X = 628, TREE_BATTLEFIELD_Y = 630;
    private static final int TREE_VARROCK_X = 160, TREE_VARROCK_Y = 454;
    private static final int TREE_VILLAGE_X = 659, TREE_VILLAGE_Y = 696;

    private static final int AGILITY = 16, ATTACK = 0, MAGIC = 6;
    /** Glough's watch tower: level 25 and 7.5 experience, rounded up. */
    private static final int TOWER_LEVEL = 25, TOWER_EXP = 8;

    /** How long Glough's demon stays before it gives up. */
    private static final int DEMON_TIMEOUT = 250000;

    // -------------------------------------------------------------- stages --

    private static final int STARTED = 1;     /* took the bark sample */
    private static final int HAZELMERE_MET = 2;  /* heard the old tongue */
    private static final int TRANSLATED = 3;  /* told the king; go warn Glough */
    private static final int WARNED = 4;      /* Glough told, and pleased */
    private static final int CULPRIT = 5;     /* a human is in the cage */
    private static final int SUSPICIOUS = 6;  /* Charlie named Glough */
    private static final int JOURNAL_READ = 7;/* the cupboard gave up the book */
    private static final int JAILED = 8;      /* Glough had the guards take you */
    private static final int FREED = 9;       /* the king opened the cage */
    private static final int INVOICE = 10;    /* the foreman's timber order */
    private static final int NAMED_ANITA = 11;/* Charlie remembered the keys */
    private static final int GOT_KEY = 12;    /* Anita handed them over */
    private static final int GOT_NOTES = 13;  /* the chest held the invasion */
    private static final int PEBBLES = 14;    /* the king gave four pebbles */
    private static final int UNDERGROUND = 15;/* the stand opened, Glough spoke */
    private static final int DEMON_DEAD = 16;
    private static final int SEARCHING = 17;  /* the king wants the last rock */
    private static final int FINISHED = 18;

    /**
     * Femi's barrel, remembered above the last stage.
     *
     * She asks for help the first time anybody walks up to the gate, long
     * before this quest starts, and whether she got it decides much later
     * whether sneaking back in is free or costs a thousand coins. The stages
     * are a count and had no room for it, so it rides above them and the king
     * wipes it by assigning the finished stage outright.
     */
    private static final int HELPED_FEMI = 32;
    private static final int STAGE_MASK = 31;

    /** Which indent each pebble is sitting in, or -1. Index 0 is far left. */
    /**
     * The stand's four indents, persisted in quest var 0 as four 3-bit
     * fields (0 empty, else pebble index + 1). It began as an in-memory
     * array, which a relog -- or a mid-QA server restart -- silently wiped
     * between placements, leaving the puzzle unsolvable with no feedback.
     */
    private static final int VAR_STAND = 0;

    private int standAt(int slot) {
        return ((getVar(VAR_STAND, 0) >> (slot * 3)) & 7) - 1;
    }

    private void standPut(int slot, int which) {
        int v = getVar(VAR_STAND, 0);
        v = (v & ~(7 << (slot * 3))) | ((which + 1) << (slot * 3));
        setVar(VAR_STAND, v);
    }

    /** Is this pebble sitting in any indent? */
    private boolean onStand(int which) {
        for (int i = 0; i < 4; i++) {
            if (standAt(i) == which) {
                return true;
            }
        }
        return false;
    }

    public GrandTree(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Grand tree");
        setFinalStage(FINISHED);

        associateNpc(KING_TREE);
        associateNpc(KING_DUNGEON);
        associateNpc(HAZELMERE);
        associateNpc(GLOUGH);
        associateNpc(CHARLIE);
        associateNpc(GATE_GUARD);
        associateNpc(GATE_GUARD_POST);
        associateNpc(GATE_WORKER);
        associateNpc(FOREMAN_A);
        associateNpc(FOREMAN_B);
        associateNpc(FEMI);
        associateNpc(FEMI_INSIDE);
        associateNpc(ANITA);
        associateNpc(DEMON);
        associateNpc(PILOT_TREE);
        associateNpc(PILOT_FELDIP);
        associateNpc(PILOT_KARAMJA);
        associateNpc(PILOT_KHARID);
        associateNpc(PILOT_VARROCK);
        associateNpc(PILOT_WOLF);

        associateObject(STRONGHOLD_GATE, GATE_X, GATE_Y);
        associateObject(CUPBOARD, CUPBOARD_X, CUPBOARD_Y);
        associateObject(CHEST_SHUT, CHEST_X, CHEST_Y);
        associateObject(CHEST_OPEN, CHEST_X, CHEST_Y);
        associateObject(TOWER_UP, TOWER_UP_X, TOWER_UP_Y);
        associateObject(TOWER_DOWN, TOWER_DOWN_X, TOWER_DOWN_Y);
        associateObject(STAND, STAND_X, STAND_Y);
        associateObject(TILE, TILE_X, TILE_Y);
        associateObject(CAGE);
        associateObject(SHIPYARD_GATE, SHIPYARD_GATE_X, SHIPYARD_GATE_Y);
        associateObject(GLIDER);
        associateObject(GROUNDED_GLIDER);
        associateObject(SPIRIT_TREE, SPIRIT_TREE_X, SPIRIT_TREE_Y);
        associateObject(ROOT_A);
        associateObject(ROOT_B);
        associateObject(ROOT_ROCK, ROOT_ROCK_X, ROOT_ROCK_Y);
        associateObject(ROOT_PUSH_A, ROOT_PUSH_A_X, ROOT_PUSH_A_Y);
        associateObject(ROOT_PUSH_B, ROOT_PUSH_B_X, ROOT_PUSH_B_Y);

        associateItem(BOOK);
        associateItem(JOURNAL);
        associateItem(INVOICE_ITEM);
        associateItem(NOTES);

        /* No 2003 manual page survives for this quest; description is ours. */
        describe("The grand tree is dying and Glough, the chief tree guardian, is the one killing it; prove what he is planning before his war on humanity can begin.");
        setStartPoint("Inside the trunk of the grand tree");
        setSpeakTo("King Narnode Shareen");
        /* Checked where it bites, at the watch tower over Glough's house. */
        requireLevel(AGILITY, 25);
        rewardExp(AGILITY, 400, 300);
        rewardExp(ATTACK, 400, 300);
        rewardExp(MAGIC, 150, 50);
        rewardOther("Use of the gnome glider network");
        rewardOther("Use of the gnome stronghold spirit tree");
    }

    public void completeQuest() {
        grantRewards();
        getOwner().getActionSender().sendMessage("@gre@well done you have completed the grand tree quest");
    }

    // ------------------------------------------------------------- helpers --

    private int stage() {
        return getStage() & STAGE_MASK;
    }

    private boolean at(int s) {
        return stage() == s;
    }

    private boolean past(int s) {
        return questStarted() && stage() >= s;
    }

    /** Move to a stage, carrying the Femi bit along. */
    private void step(int s) {
        setStage(s | (getStage() & HELPED_FEMI));
    }

    private boolean helpedFemi() {
        return (getStage() & HELPED_FEMI) == HELPED_FEMI;
    }

    private void say(String line) {
        getOwner().getActionSender().sendMessage(line);
    }

    private boolean holds(int id) {
        return getOwner().getInventory().countId(id) > 0;
    }

    private void give(int id) {
        Player p = getOwner();
        p.getInventory().add(new InvItem(id, 1));
        p.getActionSender().sendInventory();
    }

    private void take(int id) {
        Player p = getOwner();
        p.getInventory().remove(id, 1);
        p.getActionSender().sendInventory();
    }

    private boolean villageDone() {
        return getOwner().getQuestManager().completed(Quests.TREE_GNOME_VILLAGE);
    }

    // ------------------------------------------------------------ dispatch --

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        this.triggerEntity(trigger, entity, null);
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity, InvItem used) {
        if (entity instanceof GameObject) {
            scenery(trigger, (GameObject) entity, used);
            return;
        }
        if (entity instanceof InvItem) {
            if (trigger == QuestTrigger.ITEM_COMMAND) {
                read(((InvItem) entity).getID());
            }
            return;
        }
        if (!(entity instanceof Npc)) {
            return;
        }
        Npc npc = (Npc) entity;
        if (trigger == QuestTrigger.NPC_KILLED) {
            if (npc.getID() == DEMON) {
                demonKilled();
            } else if (npc.getID() == FOREMAN_A || npc.getID() == FOREMAN_B) {
                foremanKilled();
            }
            return;
        }
        if (trigger != QuestTrigger.NPC_TALK) {
            return;
        }
        switch (npc.getID()) {
            case KING_TREE: kingInTree(npc); break;
            case KING_DUNGEON: kingUnderground(npc); break;
            case HAZELMERE: hazelmere(npc); break;
            case GLOUGH: glough(npc); break;
            case CHARLIE: charlie(npc); break;
            case GATE_GUARD:
            case GATE_GUARD_POST: gateGuard(npc); break;
            case GATE_WORKER: say("The Shipyard worker does not appear interested in talking"); break;
            case FOREMAN_A:
            case FOREMAN_B: foreman(npc); break;
            case FEMI:
            case FEMI_INSIDE: femi(npc); break;
            case ANITA: anita(npc); break;
            case DEMON: break;
            case PILOT_TREE: strongholdPilot(npc); break;
            case PILOT_KARAMJA: brokenPilot(npc); break;
            case PILOT_FELDIP:
            case PILOT_KHARID:
            case PILOT_VARROCK:
            case PILOT_WOLF: returnPilot(npc); break;
            default: break;
        }
    }

    // ------------------------------------------------------------- scenery --

    private void scenery(QuestTrigger trigger, GameObject object, InvItem used) {
        switch (object.getID()) {
            case STRONGHOLD_GATE: strongholdGate(trigger, object); return;
            case CUPBOARD: cupboard(trigger); return;
            case CHEST_SHUT:
            case CHEST_OPEN: chest(trigger, object, used); return;
            case TOWER_UP: climbTower(trigger, true); return;
            case TOWER_DOWN: climbTower(trigger, false); return;
            case STAND: stoneStand(trigger, used); return;
            case TILE: stoneTile(trigger); return;
            case CAGE:
                if (trigger == QuestTrigger.OBJECT_ACT1) {
                    say("the cage is bolted shut");
                    // A player already locked in (including anyone jailed
                    // before the scripted scene existed) can restart the
                    // scene from the bars themselves.
                    if (at(JAILED)) {
                        Npc cellmate = world.getNpc(CHARLIE,
                            JAIL_X - 3, JAIL_X + 3, JAIL_Y - 3, JAIL_Y + 3);
                        if (cellmate != null) {
                            charlie(cellmate);
                        }
                    }
                }
                return;
            case SHIPYARD_GATE: shipyardGate(trigger); return;
            case GLIDER:
            case GROUNDED_GLIDER: if (trigger == QuestTrigger.OBJECT_ACT1) { say("you need a pilot to fly this"); } return;
            case SPIRIT_TREE: strongholdTree(trigger); return;
            case ROOT_A:
            case ROOT_B: searchRoot(trigger, false); return;
            case ROOT_ROCK: searchRoot(trigger, true); return;
            case ROOT_PUSH_A:
            case ROOT_PUSH_B: pushRoot(trigger, object); return;
            default: return;
        }
    }

    /**
     * The stronghold gate.
     *
     * Ordinarily it just opens. While Glough has a warrant out the guards keep
     * you in, and once the foreman has handed over the invoice they keep you
     * out -- which is the wrong way round only until you notice that Glough
     * wants you where he can find you until you have proof, and gone after.
     */
    private void strongholdGate(QuestTrigger trigger, GameObject gate) {
        if (trigger != QuestTrigger.OBJECT_ACT1) {
            return;
        }
        Player p = getOwner();
        boolean leaving = p.getY() <= GATE_Y;
        Npc guard = nearby(GATE_GUARD_POST, GATE_GUARD);
        if (!completed() && leaving && past(FREED) && !past(INVOICE)) {
            if (guard == null) {
                say("the gnome guards refuse to open the gate");
                return;
            }
            new Conversation(p, guard)
                .npc("halt human")
                .player("what?, why?")
                .npc("from order of the head tree guardian...")
                .npc("..you cannot leave")
                .player("that's crazy, why?")
                .npc("humans are planning to attack our stronghold")
                .npc("you could be a spy")
                .player("that's ridiculous")
                .npc("maybe, but that's the orders, I'm sorry")
                .message("the gnome refuses to open the gate")
                .start();
            return;
        }
        if (!completed() && !leaving && past(INVOICE)) {
            if (guard == null) {
                say("the gnome guards refuse to open the gate");
                return;
            }
            new Conversation(p, guard)
                .npc("i'm afraid that we have orders not to let you in")
                .player("orders from who?")
                .npc("the head tree guardian, he say's you're a spy")
                .player("glough!")
                .npc("i'm sorry but you'll have to leave")
                .start();
            return;
        }
        p.getActionSender().sendSound("opendoor");
        p.teleport(p.getX(), leaving ? GATE_Y + 1 : GATE_Y, false);
    }

    /** Glough's cupboard, which is where he left his diary. */
    private void cupboard(QuestTrigger trigger) {
        if (trigger == QuestTrigger.OBJECT_ACT2) {
            say("you close the cupboard");
            return;
        }
        if (trigger != QuestTrigger.OBJECT_ACT1) {
            return;
        }
        say("you search the cupboard");
        if (!at(SUSPICIOUS) || holds(JOURNAL)) {
            say("but find nothing of interest");
            return;
        }
        say("inside you find glough's journal");
        say("the book contains several hurried notes");
        give(JOURNAL);
        step(JOURNAL_READ);
    }

    /**
     * Glough's chest.
     *
     * Anita's key is the only thing that opens it, and it is not consumed --
     * nothing says it is, and the notes are only handed out once.
     */
    private void chest(QuestTrigger trigger, GameObject chest, InvItem used) {
        if (trigger == QuestTrigger.ITEM_ON_OBJECT) {
            if (used == null || used.getID() != KEY) {
                say("Nothing interesting happens");
                return;
            }
            openChest();
            return;
        }
        if (trigger == QuestTrigger.OBJECT_ACT2) {
            // Claiming an object takes its second command too, so examine and
            // close have to be answered here or they answer with nothing.
            say(chest.getID() == CHEST_OPEN ? "you close the chest"
                                            : "A chest, it is locked");
            return;
        }
        if (trigger != QuestTrigger.OBJECT_ACT1) {
            return;
        }
        if (holds(KEY)) {
            openChest();
            return;
        }
        say("the chest is locked");
    }

    private void openChest() {
        say("the key fits the chest");
        say("you open the chest");
        say("and search it...");
        if (past(GOT_NOTES) || holds(NOTES)) {
            say("but it is empty now");
            say("you close the chest");
            return;
        }
        say("inside you find some paper work");
        say("and an old gnome tongue translation book");
        say("you close the chest");
        give(NOTES);
        if (!holds(BOOK)) {
            give(BOOK);
        }
        step(GOT_NOTES);
    }

    /**
     * The Watch tower over Glough's house.
     *
     * An Agility obstacle rather than a ladder: vanilla asks for level 25 both
     * ways and pays out on the way up. See the note in the class comment about
     * the skill not being trainable yet.
     */
    private void climbTower(QuestTrigger trigger, boolean up) {
        if (up ? trigger != QuestTrigger.OBJECT_ACT1 : trigger != QuestTrigger.OBJECT_ACT2) {
            return;
        }
        Player p = getOwner();
        if (p.getMaxStat(AGILITY) < TOWER_LEVEL) {
            say("@gry@[srvmsg] You need an agility level of " + TOWER_LEVEL + " to climb this");
            return;
        }
        if (up) {
            say("you jump up and grab hold of the platform");
            say("and pull yourself up");
            p.incExp(AGILITY, TOWER_EXP, false);
            p.getActionSender().sendStat(AGILITY);
            p.teleport(TOWER_UP_X, TOWER_UP_Y + 944, false);
            return;
        }
        // Once the pillar has shifted, "climb down" from the tower top means
        // the ladder it revealed, not the drop back to glough's landing --
        // that is what the walkthrough's "select the climb option" is, and
        // players click the tower rather than reopen the stand's menu.
        if (at(PEBBLES) && solved()) {
            trunkDescent(new Conversation(p, null)).start();
            return;
        }
        say("you climb down the tower");
        say("and drop to the platform below");
        // Beside the tower on the floor below, not on it: object 635 stands on
        // (710,1420) and is solid, so the drop landed inside it. One tile east
        // is open and is the way to the ladder down.
        p.teleport(TOWER_DOWN_X + 1, TOWER_DOWN_Y - 944, false);
    }

    /**
     * The stone stand at the top of the tower.
     *
     * Four indents left to right. Pebbles go in one at a time and crumble the
     * moment they are placed -- that is vanilla, the stone remembers the mark
     * and the pebble is gone -- and pushing the stand asks whether hO NI :::
     * hA are lined up, which reads OPEN in the old tongue.
     */
    private void stoneStand(QuestTrigger trigger, InvItem used) {
        if (trigger == QuestTrigger.ITEM_ON_OBJECT) {
            placePebble(used);
            return;
        }
        if (trigger != QuestTrigger.OBJECT_ACT2) {
            return;
        }
        say("you push down on the pillar");
        say("you feel it shift downwards slightly");
        if (!solved()) {
            say("you here some noise below the pillar...");
            say("...but nothing seems to happen");
            return;
        }
        say("the pillar shifts back revealing a ladder");
        say("it seems to lead down through the tree trunk");
        new Conversation(getOwner(), null)
            .options(new Choice("climb down", "come back later") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        c.message("you decide to come back later");
                        return;
                    }
                    trunkDescent(c);
                }
            })
            .start();
    }

    /** Down the inside of the trunk to the mud floor, however it was begun. */
    private Conversation trunkDescent(Conversation c) {
        return c.message("you squeeze down the inner of the tree trunk")
         .message("you drop out of the bottom onto a mud floor")
         .message("around you, you can see piles of strange looking rocks")
         .message("you here the sound of small footsteps coming from the darkness")
         .then(new Effect() {
            public void run(Conversation c) {
                c.getPlayer().teleport(DUNGEON_X, DUNGEON_Y, false);
                enterDungeon();
            }
        });
    }

    private void placePebble(InvItem used) {
        if (used == null) {
            return;
        }
        final int which = pebbleIndex(used.getID());
        if (which < 0) {
            say("Nothing interesting happens");
            return;
        }
        say("on top are four pebble size indents");
        say("they span from left to right");
        say("you place the pebble...");
        new Conversation(getOwner(), null)
            .options(new Choice("To the far left", "Centre left", "Centre right", "To the far right") {
                public void picked(int slot, Conversation c) {
                    c.message("you place the pebble in the indent")
                     .message("it crumbles into dust")
                     .then(new Effect() {
                        public void run(Conversation c) {
                            take(PEBBLE[which]);
                            standPut(slot, which);
                        }
                    });
                }
            })
            .start();
    }

    private int pebbleIndex(int id) {
        for (int i = 0; i < PEBBLE.length; i++) {
            if (PEBBLE[i] == id) {
                return i;
            }
        }
        return -1;
    }

    /** hO NI ::: hA, far left to far right. */
    private boolean solved() {
        for (int i = 0; i < 4; i++) {
            if (standAt(i) != i) {
                return false;
            }
        }
        return true;
    }

    /**
     * Glough is waiting at the bottom of the ladder with a demon.
     *
     * The speech runs whether or not the player has reached this stage the
     * intended way, because the only way down is the stand and the stand only
     * opens for someone holding the king's pebbles.
     */
    private void enterDungeon() {
        if (completed() || past(DEMON_DEAD)) {
            return;
        }
        final Player p = getOwner();
        Npc him = new Npc(GLOUGH, p.getX(), p.getY() + 1,
            p.getX() - 4, p.getX() + 4, p.getY() - 4, p.getY() + 4);
        him.setRespawn(false);
        world.registerNpc(him);
        final Npc glough = him;
        new Conversation(p, glough)
            .npc("you really are becoming a headache")
            .npc("well, at least now you can die knowing you were right")
            .npc("it will save me having to hunt you down")
            .npc("like all the over human filth of runescape")
            .player("you're crazy glough")
            .npc("i'm angry, you think you're so special")
            .npc("well, soon you'll see, the gnome's are ready to fight")
            .npc("in three weeks this tree will be dead wood")
            .npc("in ten weeks it will be 30 battleships")
            .npc("ready to finally rid the world of the disease called humanity")
            .player("what makes you think i'll let you get away with it?")
            .npc("ha, do you think i would challange you humans alone")
            .npc("fool.....meet my little friend")
            .message("from the darkness you hear a deep growl")
            .message("and the sound of heavy footsteps")
            .then(new Effect() {
                public void run(Conversation c) {
                    c.stop();
                    world.unregisterNpc(glough);
                    step(UNDERGROUND);
                    summonDemon();
                }
            })
            .start();
    }

    private void summonDemon() {
        Player p = getOwner();
        int x = p.getX(), y = p.getY();
        final Npc beast = new Npc(DEMON, x, y + 1, x - 6, x + 6, y - 6, y + 6);
        beast.setRespawn(false);
        world.registerNpc(beast);
        p.informOfNpcMessage(new org.rscdaemon.server.model.ChatMessage(beast, "grrrrr", p));
        beast.attackPlayer(p);
        world.getDelayedEventHandler().add(new SingleEvent(null, DEMON_TIMEOUT){
            public void action() {
                if (beast.getID() == DEMON) {
                    world.unregisterNpc(beast);
                }
            }
        });
    }

    private void demonKilled() {
        if (!at(UNDERGROUND)) {
            return;
        }
        say("the beast slumps to the floor");
        say("glough has fled");
        step(DEMON_DEAD);
    }

    /**
     * The king's own way down, under the tile in the trunk.
     *
     * It is his doorway and it only ever opens for him; the player uses it in
     * his company at the start of the quest and by the ladder afterwards.
     */
    private void stoneTile(QuestTrigger trigger) {
        if (trigger != QuestTrigger.OBJECT_ACT1) {
            return;
        }
        if (!questStarted()) {
            say("you twist the tile, but nothing happens");
            return;
        }
        say("you here a creak as you turn the tile clockwise");
        say("the tile slides away, revealing a small tunnel");
        getOwner().teleport(DUNGEON_X, DUNGEON_Y, false);
    }

    /** The shipyard gate. The worker on it does the talking. */
    private void shipyardGate(QuestTrigger trigger) {
        if (trigger != QuestTrigger.OBJECT_ACT1) {
            return;
        }
        say("the gate is locked");
        if (!questStarted() || completed() || past(INVOICE)) {
            return;
        }
        askThePassword();
    }

    private void askThePassword() {
        new Conversation(getOwner(), nearby(GATE_WORKER))
            .npc("hey you, what are you up to?")
            .player("i'm trying to open the gate")
            .npc("i can see that, but why?")
            .options(new Choice("i've come to check that you're working safley",
                                "glough sent me",
                                "i just fancied looking around") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("what business is that of yours?")
                         .player("as a runescape resident i have a right to know")
                         .npc("get out of here before you get a beating")
                         .player("that's not very friendly");
                        return;
                    }
                    if (option == 2) {
                        c.npc("this isn't a museum")
                         .npc("leave now")
                         .player("i'll leave when i choose")
                         .npc("we'll see");
                        return;
                    }
                    c.npc("hmm, really, what for?")
                     .player("your wasting my time, take me to your superior")
                     .npc("ok, i can let you in but i need the password")
                     .options(new Choice("Ka", "ko", "ke") {
                        public void picked(final int first, Conversation c) {
                            c.options(new Choice("lo", "lu", "le") {
                                public void picked(final int second, Conversation c) {
                                    c.options(new Choice("mon", "min", "men") {
                                        public void picked(int third, Conversation c) {
                                            password(c, first == 0 && second == 1 && third == 1);
                                        }
                                    });
                                }
                            });
                        }
                    });
                }
            })
            .start();
    }

    private void password(Conversation c, boolean right) {
        if (!right) {
            c.npc("you have no idea");
            return;
        }
        c.player("ka lu min")
         .npc("i'm sorry to have kept you")
         .npc("but obviously high security is essential")
         .message("the worker opens the gate")
         .message("you walk through")
         .npc("you'll need to speak to the foreman")
         .npc("he's on the pier, it'll give you a chance..")
         .npc("...to see the fleet")
         .then(new Effect() {
            public void run(Conversation c) {
                // Two tiles north, not one: the shipyard is the north side of
                // the gate (both foremen stand there) and a palm tree sits on
                // (401,761), the tile immediately through it.
                c.getPlayer().teleport(SHIPYARD_GATE_X, SHIPYARD_GATE_Y - 2, false);
            }
        });
    }

    /** Ordinary roots, and the one that is not. */
    private void searchRoot(QuestTrigger trigger, boolean theOne) {
        if (trigger != QuestTrigger.OBJECT_ACT1) {
            return;
        }
        say("you search the root...");
        if (!theOne || !at(SEARCHING) || holds(ROCK)) {
            say("...but find nothing");
            return;
        }
        say("and find a small glowing rock");
        give(ROCK);
    }

    /**
     * The roots into the mine.
     *
     * The king opens the mine as the last of his thanks, so pushing them does
     * nothing at all until the quest is over.
     */
    private void pushRoot(QuestTrigger trigger, GameObject root) {
        if (trigger != QuestTrigger.OBJECT_ACT1) {
            return;
        }
        if (!completed()) {
            say("you push against the root");
            say("but it will not move");
            return;
        }
        Player p = getOwner();
        say("the root pulls aside");
        // Past the roots, not onto them. The two roots stand one above the
        // other and both tiles are solid, so landing on either walled the
        // player in; the open ground is one tile beyond each.
        p.teleport(ROOT_PUSH_A_X,
            p.getY() >= ROOT_PUSH_A_Y ? ROOT_PUSH_B_Y - 1 : ROOT_PUSH_A_Y + 1, false);
    }

    /**
     * The stronghold spirit tree.
     *
     * It answers to either gnome quest, and what it offers depends on which:
     * Tree gnome village earns you the village, this quest earns you the
     * stronghold, and either way the battlefield and the Varrock forest are
     * on the network.
     */
    private void strongholdTree(QuestTrigger trigger) {
        if (trigger != QuestTrigger.OBJECT_ACT1) {
            return;
        }
        if (!completed() && !villageDone()) {
            say("The tree doesn't feel like talking");
            return;
        }
        new Conversation(getOwner(), null)
            .message("The tree talks in an old tired voice...")
            .message("You friend of gnome people, you friend of mine")
            .message("Would you like me to take you somewhere?")
            .options(new Choice("Where can i go?", "No thanks old tree") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        return;
                    }
                    c.message("The tree talks again..")
                     .message("You can travel to the trees")
                     .message("Which are related to myself")
                     .options(new Choice("Battlefield of Khazard",
                                         "Forest north of Varrock",
                                         "the gnome tree village") {
                        public void picked(int option, Conversation c) {
                            if (option == 0) {
                                travel(c, TREE_BATTLEFIELD_X, TREE_BATTLEFIELD_Y);
                            } else if (option == 1) {
                                travel(c, TREE_VARROCK_X, TREE_VARROCK_Y);
                            } else {
                                travel(c, TREE_VILLAGE_X, TREE_VILLAGE_Y);
                            }
                        }
                    });
                }
            })
            .start();
    }

    private void travel(Conversation c, final int x, final int y) {
        c.message("You place your hands on the dry tough bark of the spirit tree")
         .message("and feel a surge of energy run through your veins")
         .then(new Effect() {
            public void run(Conversation c) {
                c.getPlayer().teleport(x, y, false);
            }
        });
    }

    // ---------------------------------------------------------------- king --

    /** The king inside the trunk, which is where all but the ending happens. */
    private void kingInTree(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            new Conversation(p, npc)
                .player("hello narnode")
                .npc("well hello again adventurer")
                .npc("how are you?")
                .player("i'm good thanks, how's the tree?")
                .npc("better than ever, thanks for asking")
                .start();
            return;
        }
        if (past(UNDERGROUND)) {
            // Once Glough's demon is loose the king is only found below.
            say("the king is not here");
            return;
        }
        if (!questStarted()) {
            opening(npc);
            return;
        }
        switch (stage()) {
            case STARTED: sampleErrand(npc); return;
            case HAZELMERE_MET: theTranslation(npc); return;
            case TRANSLATED:
                new Conversation(p, npc)
                    .player("hello narnode")
                    .npc("hello traveller, did you speak to glough?")
                    .player("not yet")
                    .npc("ok, he lives just in front of the grand tree")
                    .npc("let me know once you've spoken to him")
                    .start();
                return;
            case WARNED: caughtHim(npc); return;
            case CULPRIT:
                new Conversation(p, npc)
                    .player("hi narnode")
                    .npc("hello traveller")
                    .npc("if you wish to talk to the prisoner")
                    .npc("go to the top tree level")
                    .npc("you'll find him there")
                    .player("thanks")
                    .start();
                return;
            case SUSPICIOUS: charliesStory(npc); return;
            case JOURNAL_READ:
                new Conversation(p, npc)
                    .player("king shareem, i'm concerned about glough")
                    .npc("why, don't worry yourself about him")
                    .npc("now the culprit has been caught...")
                    .npc("..i'm sure glough's resentment of humans will die away")
                    .player("i'm not so sure")
                    .npc("he just has an active imagination")
                    .npc("if your really concerned, speak to him")
                    .start();
                return;
            case JAILED: release(npc); return;
            case FREED:
                new Conversation(p, npc)
                    .player("hello narnode")
                    .npc("traveller, haven't you heard")
                    .npc("glough has set a warrant for your arrest")
                    .npc("he has guards at the exit")
                    .npc("i shouldn't have told you this")
                    .npc("but i can see your a good person")
                    .npc("please take the glider and leave before it's too late")
                    .player("all the best narnode")
                    .start();
                return;
            case INVOICE: disbelief(npc); return;
            case NAMED_ANITA:
            case GOT_KEY:
                new Conversation(p, npc)
                    .player("hello narnode")
                    .npc("please traveller, if the gnomes see me talking to you")
                    .npc("they'll revolt against me")
                    .player("that's crazy")
                    .npc("glough's scared the whole town")
                    .npc("he expects the humans to attack any day")
                    .npc("he's even began to recuit hundreds of gnome soldiers")
                    .player("don't you understand he's creating his own army")
                    .npc("please traveller, just leave before it's too late")
                    .start();
                return;
            case GOT_NOTES: theNotes(npc); return;
            default: morePebbles(npc); return;
        }
    }

    private void opening(Npc npc) {
        new Conversation(getOwner(), npc)
            .player("hello there")
            .npc("hello traveller, i'm king shareem, welcome")
            .npc("it's nice to see an outsider")
            .player("it seems to be quite a busy settlement")
            .npc("for now it is, thankfully")
            .message("King shareem seems troubled")
            .options(new Choice("you seem worried, what's wrong?", "well, i'll be on my way") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        c.npc("ok then, enjoy your stay with us")
                         .npc("there's many shops and sights to see");
                        return;
                    }
                    c.npc("adventurer, can i speak to you in the strictest confidence")
                     .player("of course narnode")
                     .npc("not here, follow me")
                     .message("king shareem bends down and places his hands on the stone tile")
                     .message("you here a creak as he turns the tile clockwise")
                     .message("the tile slides away, revealing a small tunnel")
                     .message("you follow king shareem down")
                     .then(new Effect() {
                        public void run(Conversation c) {
                            c.getPlayer().teleport(DUNGEON_X, DUNGEON_Y, false);
                        }
                    })
                     .player("so what is this place?")
                     .npc("these my friend, are the foundations of the stronghold")
                     .player("they just look like roots")
                     .npc("not any roots traveller")
                     .npc("these were conjured in the past age by gnome mages")
                     .npc("since then, they have grown into our mighty stronghold")
                     .player("impressive, but what exactly is the problem?")
                     .npc("in the last two months our tree guardians have reported...")
                     .npc("...continuing deterioration of the grand trees health")
                     .npc("i've never seen this before, it could mean the end for all of us")
                     .player("you mean the tree is ill")
                     .npc("in a magical sense yes")
                     .npc("would you be willing to help us discover the cause of this illness")
                     .options(new Choice("i'd be happy to help", "i'm sorry i don't want to get involved") {
                        public void picked(int option, Conversation c) {
                            if (option != 0) {
                                c.npc("i understand traveller")
                                 .npc("please keep this to yourself")
                                 .player("of course")
                                 .npc("i'll show you the way back up")
                                 .message("you follow king shareem up the ladder")
                                 .then(new Effect() {
                                    public void run(Conversation c) {
                                        c.getPlayer().teleport(TRUNK_X, TRUNK_Y, false);
                                    }
                                });
                                return;
                            }
                            c.npc("thank guthix for you arrival")
                             .npc("the first task is to find out what's killing my tree")
                             .player("have you any ideas?")
                             .npc("my top tree guardian, glough, believes it's human sabotage")
                             .npc("i'm not so sure")
                             .npc("the only way to really know, is to talk to Hazelmere")
                             .player("who's hazelmere?")
                             .npc("a once all powerful mage who created the grand tree")
                             .npc("one of the only survivors of the old age")
                             .npc("take this bark sample to him, he should be able to help")
                             .npc("the mage only talks in the old tongue, you'll need this")
                             .player("what is it?")
                             .npc("a translation book, translate carefully, his words may save us all")
                             .npc("you'll find his dwellings high upon a towering hill..")
                             .npc("..on a island south of the khazard fight arena")
                             .message("king shareem gives you a book and a bark sample")
                             .give(new InvItem(BOOK, 1))
                             .give(new InvItem(BARK, 1))
                             .npc("i'll show you the way back up")
                             .message("you follow king shareem up the ladder")
                             .then(new Effect() {
                                public void run(Conversation c) {
                                    step(STARTED);
                                    c.getPlayer().teleport(TRUNK_X, TRUNK_Y, false);
                                }
                            });
                        }
                    });
                }
            })
            .start();
    }

    /** He replaces the bark and the book as often as they are lost. */
    private void sampleErrand(Npc npc) {
        Conversation c = new Conversation(getOwner(), npc)
            .player("hello king shareem")
            .npc("traveller, you've returned")
            .npc("any word from hazelmere?")
            .player("not yet i'm afraid");
        boolean lostBark = !holds(BARK), lostBook = !holds(BOOK);
        if (lostBark) {
            c.player("but i've lost the bark sample")
             .npc("here take another and try to hang on to it")
             .message("king shareem gives you another bark sample")
             .give(new InvItem(BARK, 1));
        }
        if (lostBook) {
            c.player("but i've lost the book you gave me")
             .npc("don't worry i have more")
             .npc("here you go")
             .message("king shareem gives you a translation book")
             .give(new InvItem(BOOK, 1));
        }
        if (!lostBook) {
            c.npc("hazalmere lives on a island just south of the fight arena")
             .npc("give him the sample and translate his reply")
             .npc("i just hope he can help in our hour of need");
        }
        c.start();
    }

    /**
     * Repeating Hazelmere back to the king.
     *
     * Six sets of five, and only one line through them: none, none, none,
     * "human came with king's seal", "gave human daconia rock", "daconia rocks
     * will kill tree". Anything else and the king sends you away to translate
     * it again.
     */
    private void theTranslation(Npc npc) {
        new Conversation(getOwner(), npc)
            .player("hello again king shareem")
            .npc("well hello traveller, did you speak to hazelmere?")
            .player("yes, i managed to find him")
            .npc("and do you know what he said?")
            .options(new Choice("i think so", "no, i need to go back") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        c.npc("time is of the essence adventurer");
                        return;
                    }
                    c.npc("so what did he say?")
                     .options(new Choice("hello there traveller",
                                         "king shareem must be stopped",
                                         "praise to the great zamorak",
                                         "have you any bread",
                                         "none of the above") {
                        public void picked(int option, Conversation c) {
                            if (option != 4) { mistranslated(c); return; }
                            c.options(new Choice("you must warn the gnomes",
                                                 "soon the eternal night will come",
                                                 "the seven must reunite",
                                                 "only one of the fifth night",
                                                 "none of the above") {
                                public void picked(int option, Conversation c) {
                                    if (option != 4) { mistranslated(c); return; }
                                    c.options(new Choice("all shall peril",
                                                         "chicken, it must be chicken",
                                                         "and then you will know",
                                                         "the tree will live",
                                                         "none of the above") {
                                        public void picked(int option, Conversation c) {
                                            if (option != 4) { mistranslated(c); return; }
                                            fourthSet(c);
                                        }
                                    });
                                }
                            });
                        }
                    });
                }
            })
            .start();
    }

    private void fourthSet(Conversation c) {
        c.options(new Choice("monster came with king's sword",
                             "giant left with tree stone",
                             "ogre came with king's head",
                             "human came with king's seal",
                             "fairy came with eternal flower") {
            public void picked(int option, Conversation c) {
                if (option != 3) { mistranslated(c); return; }
                c.options(new Choice("gave the ever-light to human",
                                     "gave human daconia rock",
                                     "gave human rock to daconia",
                                     "human attacked by daconia",
                                     "human destroyed daconia rock") {
                    public void picked(int option, Conversation c) {
                        if (option != 1) { mistranslated(c); return; }
                        c.options(new Choice("daconia rocks will save tree",
                                             "daconia will fall to gnome kingdom",
                                             "gnome kingdom will fall to daconia",
                                             "daconia rocks will kill tree",
                                             "daconia rocks killed human") {
                            public void picked(int option, Conversation c) {
                                if (option != 3) { mistranslated(c); return; }
                                translated(c);
                            }
                        });
                    }
                });
            }
        });
    }

    private void mistranslated(Conversation c) {
        c.npc("wait a minute, that doesn't sound like hazelmere")
         .npc("are you sure you translated correctly?")
         .player("erm...i think so")
         .npc("i'm sorry traveller but this is no good")
         .npc("the translation must be perfect or the infomation's no use")
         .npc("please come back when you know exactly what hazelmere said");
    }

    private void translated(Conversation c) {
        c.player("he said a human came to him with the king's seal")
         .player("hazelmere gave the man daconia rocks")
         .player("and daconia rocks will kill the tree")
         .npc("of course, i should have known")
         .npc("some one must have forged my royal seal")
         .npc("and convinced hazelmere that i sent for the daconia stones")
         .player("what are daconia stones?")
         .npc("hazelmere created the daconia stones")
         .npc("they were a safty measure, in case the tree grew out of control")
         .npc("they're the only thing that can kill the tree")
         .npc("this is terrible, those stones must be retrieved")
         .player("can i help?")
         .npc("first i must warn the tree guardians")
         .npc("please, could you tell the chief tree guardian glough")
         .npc("he lives in a tree house just in front of the grand tree")
         .npc("if he's not there he will be at anita's, his girlfriend")
         .npc("meet me back here once you've told him")
         .player("ok, i'll be back soon")
         .then(new Effect() {
            public void run(Conversation c) {
                step(TRANSLATED);
            }
        });
    }

    private void caughtHim(Npc npc) {
        new Conversation(getOwner(), npc)
            .player("hello king shareem")
            .player("have you any news on the daconia stones?")
            .npc("it's ok traveller, thank's to glough")
            .npc("he found a human sneaking around...")
            .npc("...with three daconia stones in his satchel")
            .player("i'm amazed that you retrieved them so easily")
            .npc("yes, glough must really know what he's doing")
            .npc("the human has been detained until we know who's involved")
            .npc("maybe glough was right, maybe humans are invading")
            .player("i doubt it, can i speak to the prisoner")
            .npc("certainly, he's on the top level of the grand tree")
            .npc("be careful up there, it's a long way down")
            .then(new Effect() {
                public void run(Conversation c) {
                    step(CULPRIT);
                }
            })
            .start();
    }

    private void charliesStory(Npc npc) {
        new Conversation(getOwner(), npc)
            .player("king shareem")
            .npc("hello adventurer, so did you speak to the culprit?")
            .player("yes i did and something's not right")
            .npc("what do you mean?")
            .player("the prisoner claims he was paid by glough to get the stones")
            .npc("that's an absurd story, he's just trying to save himself")
            .npc("since glough's wife died he has been a little strange")
            .npc("but he would never wrongly imprison someone")
            .npc("now the culprit's locked up we can all relax")
            .npc("it's sad but i think glough was right")
            .npc("humans are planning to invade and wipe us tree gnomes out")
            .player("but why?")
            .npc("who knows? but you may have to leave soon adventurer")
            .npc("i trust you, but the local gnomes are getting paranoid")
            .player("that's a shame")
            .npc("hopefully i can keep my people calm, we'll see")
            .start();
    }

    /**
     * The king comes up to the cage himself.
     *
     * There is no Narnode spawn at the top of the tree -- the release scene
     * summons a temporary one beside the cage once Charlie has said his
     * piece, exactly the OpenRSC-witnessed sequence, and he is unregistered
     * again by his own timer. Before this the release dialogue was dead code
     * hung on a king the player could never reach from inside the cage.
     */
    private void kingComesUp() {
        Player p = getOwner();
        if (!at(JAILED)) {
            return;
        }
        final Npc king = new Npc(KING_TREE, JAIL_X, JAIL_Y + 1,
            JAIL_X - 2, JAIL_X + 2, JAIL_Y - 1, JAIL_Y + 3);
        king.setRespawn(false);
        world.registerNpc(king);
        world.getDelayedEventHandler().add(new SingleEvent(null, 60000) {
            public void action() {
                world.unregisterNpc(king);
            }
        });
        release(king);
    }

    /** The cage opens itself the moment the king hears about it. */
    private void release(Npc npc) {
        new Conversation(getOwner(), npc)
            .npc("adventurer please accept my apologies")
            .npc("glough had no right to arrest you")
            .npc("i just think he's scared of humans")
            .npc("let me get you out of there")
            .message("king shareem opens the cage")
            .then(new Effect() {
                public void run(Conversation c) {
                    c.getPlayer().teleport(JAIL_X - 1, JAIL_Y + 1, false);
                }
            })
            .player("i don't think you can trust glough, narnode")
            .player("he seems to have a unatural hatred for humans")
            .npc("i know he can seem a little extreme at times")
            .npc("but he's the best tree guardian i have")
            .npc("he has however caused much fear towards humans")
            .npc("i'm afraid he's placed guards on the front gate...")
            .npc("...to stop you escaping")
            .npc("let my glider pilot fly you away")
            .npc("untill things calm down around here")
            .player("well, if that's how you feel")
            .then(new Effect() {
                public void run(Conversation c) {
                    step(FREED);
                }
            })
            .start();
    }

    private void disbelief(Npc npc) {
        new Conversation(getOwner(), npc)
            .player("king shareem,i need to talk")
            .npc("traveller, what are you doing here?")
            .npc("the stronghold has been put on full alert")
            .npc("it's not safe for you here")
            .player("narnode, i believe glough is killing the trees")
            .player("in order to make a mass fleet of warships")
            .npc("that's an absurd accusation")
            .player("his hatred for humanity is stronger than you know")
            .npc("that's enough traveller, you sound as paranoid as him")
            .npc("traveller please leave")
            .npc("it's bad enough having one human locked up")
            .start();
    }

    /**
     * The notes buy four pebbles and nothing else.
     *
     * He still does not believe a word of it. The pebbles are handed over as
     * junk from a search that found nothing, which is the joke: they are the
     * key to the room Glough has been hiding in.
     */
    private void theNotes(Npc npc) {
        new Conversation(getOwner(), npc)
            .player("look, i found this at glough's home")
            .message("you give the king the strategic notes")
            .take(NOTES, 1)
            .npc("hmmm, these are interesting")
            .npc("but it's not proof, any one could have made these")
            .npc("traveller, i understand your concern")
            .npc("i had guards search glough's house")
            .npc("but they found nothing suspicious")
            .npc("just these old pebbles")
            .message("narnode gives you four old pebbles")
            .give(new InvItem(PEBBLE[0], 1))
            .give(new InvItem(PEBBLE[1], 1))
            .give(new InvItem(PEBBLE[2], 1))
            .give(new InvItem(PEBBLE[3], 1))
            .npc("on the other hand, if glough's right about the humans")
            .npc("we will need an army of gnomes to protect ourselves")
            .npc("so i've decided to allow glough to raise a mighty gnome army")
            .npc("the grand tree's still slowly dying, if it is human sabotage")
            .npc("we must respond")
            .then(new Effect() {
                public void run(Conversation c) {
                    step(PEBBLES);
                }
            })
            .start();
    }

    /** Losing a pebble is not the end of it; he has a drawer of them. */
    private void morePebbles(Npc npc) {
        Conversation c = new Conversation(getOwner(), npc)
            .player("hello again narnode")
            .npc("please traveller, take my advice and leave");
        if (missingPebbles()) {
            c.player("have you any more of those pebbles")
             .npc("well, yes as it goes, why?")
             .player("i lost some")
             .npc("here take these, i don't see how it will help though")
             .message("narnode replaces your lost pebbles");
            for (int i = 0; i < PEBBLE.length; i++) {
                if (!holds(PEBBLE[i]) && !onStand(i)) {
                    c.give(new InvItem(PEBBLE[i], 1));
                }
            }
        } else {
            c.npc("it's not safe for you here");
        }
        c.start();
    }

    private boolean missingPebbles() {
        for (int i = 0; i < PEBBLE.length; i++) {
            if (!holds(PEBBLE[i]) && !onStand(i)) {
                return true;
            }
        }
        return false;
    }

    /** The king below, who only has anything to say once the demon is down. */
    private void kingUnderground(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            new Conversation(p, npc)
                .player("hello narnode")
                .npc("well hello again adventurer")
                .npc("how are you?")
                .player("i'm good thanks, how's the tree?")
                .npc("better than ever, thanks for asking")
                .start();
            return;
        }
        if (at(DEMON_DEAD)) {
            afterTheDemon(npc);
            return;
        }
        if (at(SEARCHING)) {
            theRock(npc);
            return;
        }
        if (at(UNDERGROUND)) {
            new Conversation(p, npc)
                .player("narnode, it's true about glough i tell you")
                .player("he's planning to take over runescape")
                .npc("i'm sorry traveller but it's just not realistic")
                .npc("how could glough- even with a gnome army- take over?")
                .player("he plans to make a fleet of warships from the grand tree's wood")
                .npc("that's enough traveller, i've no time for make believe")
                .npc("the tree's still dying, i must get to the truth of this")
                .start();
            return;
        }
        kingInTree(npc);
    }

    private void afterTheDemon(Npc npc) {
        new Conversation(getOwner(), npc)
            .npc("traveller you're wounded, what happened?")
            .player("it's glough, he set a demon on me")
            .npc("what, glough, with a demon?")
            .player("glough has a store of daconia rocks further up the passage way")
            .player("he's been accessing the roots from a secret passage at his home")
            .npc("never, not glough, he's a good gnome at heart")
            .npc("guard, go and check out that passage way")
            .message("one of the king's guards runs of up the passage")
            .npc("look, maybe it's stress playing with your mind")
            .message("the gnome guard returns")
            .message("and talks to the king")
            .npc("what?, never, why that little...")
            .npc("they found glough hiding under a horde of daconia rocks..")
            .player("that's what i've been trying to tell you")
            .player("glough's been fooling you")
            .npc("i..i don't know what to say")
            .npc("how could i have been so blind")
            .message("king shareem calls out to another guard")
            .npc("guard, call off the military training")
            .npc("the humans are not attacking")
            .npc("you have my full apologies traveller, and my gratitude")
            .npc("a reward will have to wait though, the tree is still dying")
            .npc("the guards are clearing glough's rock supply now")
            .npc("but there must be more daconia hidden somewhere in the roots")
            .npc("please traveller help us search, we have little time")
            .then(new Effect() {
                public void run(Conversation c) {
                    step(SEARCHING);
                }
            })
            .start();
    }

    private void theRock(Npc npc) {
        Conversation c = new Conversation(getOwner(), npc)
            .npc("traveller, have you managed to find the rock")
            .npc("i think there's only one");
        if (!holds(ROCK)) {
            c.player("no sign of it so far")
             .npc("the tree will still die if we don't find it")
             .npc("it could be anywhere")
             .player("don't worry narnode, we'll find it")
             .start();
            return;
        }
        c.player("is this it?")
         .npc("yes, excellent, well done")
         .message("you give king shareem the daconia rock")
         .take(ROCK, 1)
         .npc("it's incredible, the tree's health is improving already")
         .npc("i don't what to say, we owe you so much")
         .npc("to think glough had me fooled all along")
         .player("all that matters now is that man...")
         .player("...and gnome can live together in peace")
         .npc("i'll drink to that")
         .then(new Effect() {
            public void run(Conversation c) {
                // Outright, not a step: this drops Femi's bit as well.
                setStage(FINISHED);
            }
        })
         .npc("from now on i vow to make this stronghold")
         .npc("a welcome place for all no matter what their creed")
         .npc("i'll grant you access to all our facilities")
         .player("thanks, i think")
         .npc("it should make your stay here easier")
         .npc("you can use the spirit tree to transport yourself")
         .npc("..as well as the gnome glider")
         .npc("i also give you access to our mine")
         .player("mine?")
         .npc("very few know of the secret mine under the grand tree")
         .npc("if you push on the roots just to my north")
         .npc("the grand tree will take you there")
         .player("strange")
         .npc("that's magic trees for you")
         .npc("all the best traveller and thanks again")
         .player("you too narnode")
         .start();
    }

    // ----------------------------------------------------------- hazelmere --

    private void hazelmere(Npc npc) {
        Player p = getOwner();
        if (!questStarted() || past(TRANSLATED)) {
            say("the mage mumbles in an ancient tounge");
            say("you can't understand a word");
            return;
        }
        if (!holds(BARK)) {
            new Conversation(p, npc)
                .player("hello")
                .message("the mage mumbles in an ancient tounge")
                .message("you can't understand a word")
                .message("you need to give him the bark sample")
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("hello")
            .message("you give the mage the bark sample")
            .take(BARK, 1)
            .message("the mage speaks in a strange ancient tongue")
            .message("he says....")
            .message("@yel@xzql:vha za:vql::: h:xalatx voxahaqasol sol::::vva")
            .message("@yel@qa:v::::: xzql:vha qe:vha qe:vzahohaxa:v qihozavo")
            .message("@yel@qe:vzahohaxa:v qihozavosol h:xavava voxavava latqi::::::")
            .then(new Effect() {
                public void run(Conversation c) {
                    step(HAZELMERE_MET);
                }
            })
            .start();
    }

    // -------------------------------------------------------------- glough --

    private void glough(Npc npc) {
        Player p = getOwner();
        if (at(TRANSLATED)) {
            warnGlough(npc);
            return;
        }
        if (at(JOURNAL_READ)) {
            accuseGlough(npc);
            return;
        }
        if (at(INVOICE) || at(NAMED_ANITA)) {
            new Conversation(p, npc)
                .player("I know what you're up to glough")
                .npc("you have no idea human")
                .player("you may be able to make a fleet")
                .player("but the tree gnomes will never follow you into battle")
                .npc("so, you know more than i thought, i'm impressed")
                .npc("the gnomes fear humanity more than any other race")
                .npc("i just need to give them a push in the right direction")
                .npc("there's nothing you can do traveller")
                .npc("leave before it's too late")
                .npc("soon all of runescape will feel the wrath of glough")
                .player("king shareem won't allow it")
                .npc("the king's a fool and a coward, he'll soon bow to me")
                .npc("and you'll soon be back in that cage")
                .start();
            return;
        }
        if (at(GOT_KEY) || at(GOT_NOTES) || at(PEBBLES)) {
            new Conversation(p, npc)
                .player("i'm going to stop you glough")
                .npc("you're becoming quite annoying traveller")
                .message("glough is searching his pockets")
                .message("he seems very uptight")
                .npc("damn keys")
                .npc("leave human, before i have you put in the cage")
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("hello there")
            .npc("you shouldn't be here human")
            .player("what do you mean?")
            .npc("the gnome stronghold is for gnomes alone")
            .player("surely not!")
            .npc("we don't need you're sort around here")
            .message("he doesn't seem very nice")
            .start();
    }

    private void warnGlough(Npc npc) {
        new Conversation(getOwner(), npc)
            .player("hello")
            .message("the gnome is munching on a worm hole")
            .npc("can i help human, can't you see i'm eating?")
            .npc("these are my favourite")
            .message("the gnome continues to eat")
            .player("the king asked me to inform you...")
            .player("that the daconia rocks have been taken")
            .npc("surley not!")
            .player("apparently a human took them from hazelmere")
            .player("he had a permission note with the king's seal")
            .npc("i should have known, the humans are going to invade")
            .player("never")
            .npc("your type can't be trusted")
            .npc("i'll take care of this, you go back to the king")
            .then(new Effect() {
                public void run(Conversation c) {
                    step(WARNED);
                }
            })
            .start();
    }

    /** Accusing him to his face is how the cage happens. */
    private void accuseGlough(Npc npc) {
        new Conversation(getOwner(), npc)
            .player("glough, i don't know what you're up to...")
            .player("...but i know you paid charlie to get those rocks")
            .npc("you're a fool human")
            .npc("you have no idea whats going on")
            .player("i know the grand tree's dying")
            .player("and i think you're part of the reason")
            .npc("how dare you accuse me, i'm the head tree guardian")
            .npc("guards...guards")
            .message("gnome guards hurry up the ladder")
            .npc("take him away")
            .player("what for?")
            .npc("grand treason against his majesty king shareem")
            .npc("this man is a human spy")
            .npc("lock him up")
            .message("the gnome guards take you to the top of the grand tree")
            .then(new Effect() {
                public void run(Conversation c) {
                    step(JAILED);
                    c.getPlayer().teleport(JAIL_X, JAIL_Y, false);
                    /*
                     * The cage's own walls block the walk-up a click-to-talk
                     * needs, so nothing in the cell can be reached by
                     * clicking. The whole scene is scripted instead, the way
                     * OpenRSC plays it: Charlie speaks as soon as you land,
                     * and the king comes up after.
                     */
                    Npc cellmate = world.getNpc(CHARLIE,
                        JAIL_X - 3, JAIL_X + 3, JAIL_Y - 3, JAIL_Y + 3);
                    if (cellmate != null) {
                        charlie(cellmate);
                    }
                }
            })
            .start();
    }

    // ------------------------------------------------------------- charlie --

    private void charlie(Npc npc) {
        Player p = getOwner();
        if (completed() || !past(CULPRIT)) {
            say("the prisoner is in no need to talk");
            return;
        }
        if (at(CULPRIT)) {
            new Conversation(p, npc)
                .player("tell me,why would you want to kill the grand tree?")
                .npc("what do you mean?")
                .player("don't tell me, you just happened to be caught carrying daconia rocks!")
                .npc("all i know, is that i did what i was asked")
                .player("i don't understand?")
                .npc("glough paid me to go see this gnome on a hill")
                .npc("i gave the gnome a letter glough gave me")
                .npc("and he gave me some rocks to give glough")
                .npc("i've been doing it for weeks, it's just this time..")
                .npc("...when i returned glough locked me up here")
                .npc("i just don't understand it")
                .player("sounds like glough's hiding something")
                .npc("i don't know what he's up to")
                .npc("but if you want to find out...")
                .npc("..you better search his home")
                .player("ok, thanks charlie")
                .npc("good luck")
                .then(new Effect() {
                    public void run(Conversation c) {
                        step(SUSPICIOUS);
                    }
                })
                .start();
            return;
        }
        if (at(SUSPICIOUS) || at(JOURNAL_READ)) {
            new Conversation(p, npc)
                .player("hello charlie")
                .npc("hello adventurer, have you figured out what's going on?")
                .player("no idea")
                .npc("to get to the bottom of this you'll need to search glough's home")
                .start();
            return;
        }
        if (at(JAILED)) {
            new Conversation(p, npc)
                .npc("so, they've got you as well")
                .player("it's glough, he's trying to cover something up")
                .npc("i shouldn't tell you this adventurer")
                .npc("but if you want to get to the bottom of this")
                .npc("you should go and talk to the karamja foreman")
                .player("why?")
                .npc("glough sent me to karamja to meet him")
                .npc("i delivered a large amount of gold")
                .npc("for what i do not know")
                .npc("but he may be able to tell you what glough's up to")
                .npc("that's if you can get out of here")
                .npc("you'll find him in a ship yard south of birmhaven")
                .npc("be careful, if he discovers that you're not...")
                .npc("...working for glough there'll be trouble")
                .npc("the sea men use the pass word ka-lu-min")
                .player("thanks charlie")
                .then(new Effect() {
                    public void run(Conversation c) {
                        kingComesUp();
                    }
                })
                .start();
            return;
        }
        if (at(FREED)) {
            new Conversation(p, npc)
                .player("i can't figure this out charlie")
                .npc("go and see a forman in west karamja")
                .npc("there's a shipyard there,you might find some clues")
                .npc("don't forget the password's ka-lu-min")
                .npc("if they realise that you're not working for glough...")
                .npc("...there'll be trouble")
                .start();
            return;
        }
        if (at(INVOICE)) {
            new Conversation(p, npc)
                .player("how are you doing charlie")
                .npc("i've been better")
                .player("glough has some plan to rule runescape")
                .npc("i wouldn't put it past him, the gnome's crazy")
                .player("i need some proof to convince the king")
                .npc("hmmm, you could be in luck")
                .npc("before glough had me locked up i heard him mention..")
                .npc("..that he'd left his chest lock keys at his girlfriends")
                .player("where does she live?")
                .npc("just west of the toad swamp")
                .player("okay, i'll see what i can find")
                .then(new Effect() {
                    public void run(Conversation c) {
                        step(NAMED_ANITA);
                    }
                })
                .start();
            return;
        }
        say("the prisoner is in no need to talk");
    }

    // --------------------------------------------------------------- anita --

    private void anita(Npc npc) {
        if (!at(NAMED_ANITA) || holds(KEY)) {
            say("anita is to busy cleaning to talk");
            return;
        }
        new Conversation(getOwner(), npc)
            .player("hello there")
            .npc("oh hello, i've seen you with the king")
            .player("yes, i'm helping him with a problem")
            .npc("you must know my boy friend glough then")
            .player("indeed!")
            .npc("could you do me a favour?")
            .player("i suppose so")
            .npc("give this key to glough")
            .npc("he left it here last night")
            .message("anita gives you a key")
            .give(new InvItem(KEY, 1))
            .npc("thanks a lot")
            .player("no, thankyou")
            .then(new Effect() {
                public void run(Conversation c) {
                    step(GOT_KEY);
                }
            })
            .start();
    }

    // ---------------------------------------------------------------- femi --

    /**
     * Femi and her barrel.
     *
     * The barrel is the whole point of her: helping costs a click and saves a
     * thousand coins much later, and refusing is remembered just as long.
     */
    private void femi(Npc npc) {
        Player p = getOwner();
        if (!completed() && past(INVOICE) && !past(PEBBLES)) {
            sneakIn(npc);
            return;
        }
        if (helpedFemi() || completed() || past(INVOICE)) {
            say("the little gnome is too busy to talk");
            return;
        }
        new Conversation(p, npc)
            .npc("hello there")
            .player("hi")
            .npc("could you help me lift this barrel")
            .npc("it's really heavy")
            .options(new Choice("ok then", "sorry i'm a bit busy") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        c.npc("oh, ok, i'll do it myself");
                        return;
                    }
                    c.npc("thanks traveller")
                     .message("you help the gnome lift the barrel")
                     .message("it's very heavy and quite hard work")
                     .npc("thanks again friend")
                     .then(new Effect() {
                        public void run(Conversation c) {
                            setStage((questStarted() ? getStage() : 0) | HELPED_FEMI);
                        }
                    });
                }
            })
            .start();
    }

    private void sneakIn(Npc npc) {
        Conversation c = new Conversation(getOwner(), npc)
            .player("i can't believe they won't let me in")
            .npc("i don't believe all this rubbish about an invasion")
            .npc("if mankind wanted to, they could have invaded before now")
            .player("i really need to see king shareem")
            .player("could you help sneak me in");
        if (helpedFemi()) {
            c.npc("well, as you helped me i suppose i could")
             .npc("we'll have to be careful")
             .npc("if i get caught i'll be in the cage")
             .player("ok, what should i do")
             .npc("jump in the back of the cart")
             .npc("it's a food delivery, we should be fine");
            cart(c, false);
            c.npc("ok traveller, you'd better get going")
             .player("thanks again femi")
             .npc("that's ok, all the best")
             .start();
            return;
        }
        c.npc("why should i help you, you wouldn't help me")
         .player("erm i know, but this is an emergency")
         .npc("so was lifting that barrel")
         .npc("tell you what, let's call it a round 1000 gold piece's")
         .player("1000 gold pieces")
         .npc("that's right 1000 and i'll sneak you in")
         .options(new Choice("ok then, here you go", "no chance") {
            public void picked(int option, Conversation c) {
                if (option != 0) {
                    return;
                }
                if (getOwner().getInventory().countId(COINS) < 1000) {
                    c.npc("you haven't got a thousand coins")
                     .npc("come back when you have");
                    return;
                }
                c.npc("alright, jump in the back of the cart")
                 .npc("it's a food delivery, we should be fine");
                cart(c, true);
                c.npc("ok traveller, you'd better get going");
            }
        })
         .start();
    }

    private void cart(Conversation c, boolean paying) {
        c.message("you hide in the cart")
         .message("femi covers you with a sheet...")
         .message("...and drags the cart to the gate");
        if (paying) {
            c.message("you give femi 1000 gold coins")
             .take(COINS, 1000);
        }
        c.message("femi pulls you into the stronghold")
         .then(new Effect() {
            public void run(Conversation c) {
                c.getPlayer().teleport(GATE_X, GATE_Y, false);
            }
        });
    }

    // ---------------------------------------------------------- gnome guard --

    private void gateGuard(Npc npc) {
        Player p = getOwner();
        if (!completed() && past(FREED) && !past(INVOICE)) {
            new Conversation(p, npc)
                .npc("halt human")
                .player("what?, why?")
                .npc("from order of the head tree guardian...")
                .npc("..you cannot leave")
                .start();
            return;
        }
        say("the gnome guard is on duty and does not want to talk");
    }

    // ------------------------------------------------------------- shipyard --

    private void foreman(final Npc npc) {
        Player p = getOwner();
        if (completed() || !questStarted() || past(INVOICE)) {
            say("the forman is too busy to talk");
            return;
        }
        new Conversation(p, npc)
            .player("hello, are you in charge?")
            .npc("that's right, and you are?")
            .player("glough sent me to check up on things")
            .npc("is that right, glough sent a human")
            .player("his gnomes were all busy")
            .npc("ok, we had better go inside, follow me")
            .message("you follow the foreman into the wooden hut")
            .npc("so tell me again why you're here")
            .player("erm...glough sent me?")
            .npc("ok and how is glough..still with his wife?")
            .options(new Choice("yes, they're both getting on great",
                                "always arguing as usual",
                                "his wife is no longer with us") {
                public void picked(int option, Conversation c) {
                    if (option != 2) {
                        deathSentence(c, npc, "..that's strange, considering she died last year");
                        return;
                    }
                    c.npc("right answer, i have to watch out for imposters")
                     .npc("if you really know glough...")
                     .npc("you will know his favorite gnome dish")
                     .options(new Choice("he loves tangled toads legs",
                                         "he loves worm holes",
                                         "he loves choc bombs") {
                        public void picked(int option, Conversation c) {
                            if (option != 1) {
                                deathSentence(c, npc, "he hates them");
                                return;
                            }
                            c.npc("ok, one more question")
                             .npc("what's the name of his new girlfriend")
                             .options(new Choice("Alia", "Anita", "Elena") {
                                public void picked(int option, Conversation c) {
                                    if (option != 1) {
                                        deathSentence(c, npc, "you almost fooled me");
                                        return;
                                    }
                                    passed(c);
                                }
                            });
                        }
                    });
                }
            })
            .start();
    }

    private void deathSentence(Conversation c, final Npc npc, String line) {
        c.npc("really...")
         .npc(line)
         .npc("die imposter")
         .then(new Effect() {
            public void run(Conversation c) {
                c.stop();
                npc.attackPlayer(c.getPlayer());
            }
        });
    }

    private void passed(Conversation c) {
        c.npc("well, well ,well, you do know glough")
         .npc("sorry for the interrogation but i'm sure you understand")
         .player("oh course, security is paramount")
         .npc("as you can see the ship builders are ready")
         .player("indeed")
         .npc("when i was asked to build a fleet large enough...")
         .npc("..to invade port sarim and carry 300 gnome troops...")
         .npc("..i said if anyone can, i can")
         .player("that's a lot of troops")
         .npc("true but if the gnomes are really going to..")
         .npc("..take over runescape, they'll need at least that")
         .player("take over?")
         .npc("of course, why else would glough want 30 battleships")
         .npc("between you and me, i don't think he stands a chance")
         .player("no")
         .npc("i mean, for the kind of battleships glough's ordered..")
         .npc("..i'll need ton's and ton's of timber")
         .npc("more than any forest i can think of could supply")
         .npc("still, if he say's he can supply the wood i'm sure he can")
         .npc("any way, here's the invoice")
         .player("ok, thanks")
         .npc("i'll need the wood as soon as possible")
         .npc("if the orders going to be finished in time")
         .player("ok i'll tell glough")
         .message("the foreman hands you the invoice")
         .give(new InvItem(INVOICE_ITEM, 1))
         .then(new Effect() {
            public void run(Conversation c) {
                step(INVOICE);
            }
        });
    }

    /** Lying to him and then killing him works just as well as answering. */
    private void foremanKilled() {
        if (completed() || !questStarted() || past(INVOICE)) {
            return;
        }
        say("you kill the foreman");
        say("inside his pocket you find an invoice..");
        say("it seems to be an order for timber");
        give(INVOICE_ITEM);
        step(INVOICE);
    }

    // ------------------------------------------------------------- gliders --

    /**
     * The pilot at the top of the grand tree.
     *
     * Before the quest ends he is the escape the king arranges and he only
     * flies to Karamja, where he crashes. Afterwards he is the whole network.
     */
    private void strongholdPilot(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            new Conversation(p, npc)
                .player("hello")
                .npc("well hello again traveller")
                .npc("can i take you somewhere?")
                .npc("i can fly like the birds")
                .options(new Choice("karamja", "varrock", "Al kharid",
                                    "white wolf mountain", "I'll stay here thanks") {
                    public void picked(int option, Conversation c) {
                        switch (option) {
                            case 0: flight(c, "karamja", GLIDER_KARAMJA_X, GLIDER_KARAMJA_Y); return;
                            case 1: flight(c, "Varrock", GLIDER_VARROCK_X, GLIDER_VARROCK_Y); return;
                            case 2: flight(c, "Al kharid", GLIDER_KHARID_X, GLIDER_KHARID_Y); return;
                            case 3: flight(c, "White wolf mountain", GLIDER_WOLF_X, GLIDER_WOLF_Y); return;
                            default:
                                c.npc("no worries, let me know if you change your mind");
                        }
                    }
                /* The destination list is bare place names, but the player
                   asks for them in a sentence. Note the capitals move too:
                   the menu says "varrock", the player says "Varrock". */
                }.says(0, "take me to karamja")
                 .says(1, "take me to Varrock")
                 .says(2, "take me to Al kharid")
                 .says(3, "take me to White wolf mountain"))
                .start();
            return;
        }
        if (!past(FREED)) {
            new Conversation(p, npc)
                .player("hello")
                .npc("hello traveller")
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("hello")
            .npc("hi, the king said that you need to leave")
            .player("yes, apparently humans are invading")
            .npc("i find that hard to believe")
            .npc("i have lots of human friends")
            .player("it seems a bit strange to me")
            .npc("well, would you like me to take you somewhere?")
            .options(new Choice("actually yes, take me to karamja",
                                "no thanks i'm going to hang around") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        c.npc("ok, i'll be here if you need me");
                        return;
                    }
                    c.npc("ok, your the boss, jump on")
                     .npc("hold on tight, it'll be a rough ride")
                     .message("you hold on tight to the glider's wooden beam")
                     .message("the pilot leans back and then pushes the glider forward")
                     .message("you float softly off the grand tree")
                     .player("whhaaaaaaaaaagghhh");
                    // The ouches are the impact, so they come after the
                    // flying scene, not on takeoff.
                    midFlight(c, GLIDER_KARAMJA_X, GLIDER_KARAMJA_Y);
                    c.player("ouch")
                     .npc("ouch")
                     .message("you crash in south karamja")
                     .npc("sorry about that, are you ok")
                     .player("i seem to be fine, can't say the same for your glider")
                     .npc("i don't think i can fix this")
                     .npc("looks like we'll be heading back by foot")
                     .npc("i hope you find what you came for adventurer")
                     .player("me too, take care little man");
                }
            })
            .start();
    }

    private void flight(Conversation c, String where, final int x, final int y) {
        c.player("take me to " + where)
         .npc("ok, your the boss, jump on")
         .npc("hold on tight, it'll be a rough ride")
         .message("you hold on tight to the glider's wooden beam")
         .message("the pilot leans back and then pushes the glider forward")
         .message("you float softly off the grand tree")
         .player("whhaaaaaaaaaagghhh");
        midFlight(c, x, y);
        // Landing bump -- after the flight, same ordering as the crash.
        c.player("ouch");
    }

    /**
     * Every glider trip stops here for a few lines before landing -- object 618
     * is really planted at (221,3567), out over open ocean, so a long flight
     * reads as a journey instead of a blink.
     */
    private void midFlight(Conversation c, final int x, final int y) {
        c.then(new Effect() {
            public void run(Conversation c) {
                c.getPlayer().teleport(GLIDER_MIDAIR_X, GLIDER_MIDAIR_Y, false);
            }
        })
         .message("the glider climbs above the clouds")
         .message("far below, the ocean stretches out in every direction")
         .then(new Effect() {
            public void run(Conversation c) {
                c.getPlayer().teleport(x, y, false);
            }
        });
    }

    /** The four pilots who can still fly, all of whom fly home. */
    private void returnPilot(Npc npc) {
        Player p = getOwner();
        if (!completed()) {
            say("the gnome doesn't seem interested in talking");
            return;
        }
        new Conversation(p, npc)
            .player("hello again")
            .npc("well hello adventurer")
            .npc("would you like to go to the tree gnome stronghold?")
            .options(new Choice("ok then", "no thanks") {
                public void picked(int option, Conversation c) {
                    if (option != 0) {
                        return;
                    }
                    c.npc("ok, hold on tight")
                     .message("you both hold onto the wooden beam")
                     .message("you take a few steps backand rush forwards")
                     .message("the glider just lifts of the ground")
                     .player("whhaaaaaaaaaagghhh");
                    midFlight(c, GLIDER_TREE_X, GLIDER_TREE_Y);
                }
            })
            .start();
    }

    /** The one at the crash site, who never flies again. */
    private void brokenPilot(Npc npc) {
        if (!past(FREED)) {
            say("the gnome doesn't seem interested in talking");
            return;
        }
        new Conversation(getOwner(), npc)
            .player("hello again")
            .npc("well hello adventurer")
            .npc("as you can see we crashed on impact")
            .npc("i don't think it'll fly again")
            .npc("sorry but you'll have to walk")
            .start();
    }

    // ------------------------------------------------------------ reading --

    /**
     * The four things in this quest with writing on them.
     *
     * Vanilla opens a page interface for each. There is no such interface
     * here, so they are read out as messages, in the order the page has them.
     */
    private void read(int id) {
        switch (id) {
            case BOOK: theAlphabet(); return;
            case JOURNAL: theJournal(); return;
            case INVOICE_ITEM: theInvoice(); return;
            case NOTES: readNotes(); return;
            default: return;
        }
    }

    /**
     * As chat lines the whole table scrolled past faster than anyone could
     * read it, so it goes up as the full-screen text window instead (the Old
     * Journal treatment) and stays until the player clicks it closed.
     */
    private void theAlphabet() {
        say("the book contains the alphabet...");
        say("translated into the old gnome tounge");
        getOwner().getActionSender().sendAlert(
            "The old gnome tounge%"
            + "%A = :v   B = x:   C = za   D = qe   E = :::"
            + "%F = hb   G = qa   H = x    I = xa   J = ve"
            + "%K = vo   L = va   M = ql   N = ha   O = ho"
            + "%P = ni   Q = na   R = qi   S = sol  T = lat"
            + "%U = z    V = ::   W = h:   X = :i:  Y = im"
            + "%Z = dim", true);
    }

    private void theJournal() {
        new Conversation(getOwner(), null)
            .message("the book contains several hurried notes")
            .options(new Choice("the migration failed", "they must be stopped", "gaining support") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        page(c, new String[] {
                            "@yel@The migration failed",
                            "After spending half a century hiding underground you would",
                            "think that the great migration would have improved life on",
                            "runescape for tree gnome. However, rather than the great",
                            "liberation promised to us by king Healthorg at the end of the",
                            "last age, we have been forced to live in hiding, up trees or in",
                            "the gnome maze, laughed at and mocked by man. Living in",
                            "constant fear of human aggression, we are in a no better",
                            "situation now then when we lived in the caves",
                            "Change must come soon" });
                    } else if (option == 1) {
                        page(c, new String[] {
                            "@yel@They must be stopped",
                            "Today I heard of three more gnomes slain by Khazard's human",
                            "troops for fun, I cannot control my anger",
                            "Humanity seems to have aquired a level of arrogance",
                            "comparable to that of zamorak, killing and pillaging at will. We",
                            "are small and at heart not warriors, but something must be",
                            "done, we will pick up arms and go forth into the human world.",
                            "We will defend ourselves and we will pursue justice for all",
                            "gnomes who fell at the hands of humans" });
                    } else {
                        page(c, new String[] {
                            "@yel@gaining support",
                            "Some of the local gnomes seem strangly deluded about",
                            "humans, many actually believe that humans are not all",
                            "naturally evil but instead vary from person to person",
                            "This sort of talk could be the end for the tree gnomes and i",
                            "must continue to convince my fellow gnome folk the cold truth",
                            "about these human creatures, how they will not stop until all",
                            "gnome life is destroyed - unless we can destroy them first" });
                    }
                }
            })
            .start();
    }

    private void page(Conversation c, String[] lines) {
        for (int i = 0; i < lines.length; i++) {
            c.message(lines[i]);
        }
    }

    /* Both go up as the full-screen text window (the Old Journal treatment)
       -- as chat lines they scrolled past too fast to read. */
    private void theInvoice() {
        say("you open the invoice");
        getOwner().getActionSender().sendAlert(
            "Order%"
            + "%30 karamja battleships to be constructed in karamja"
            + "%Timber needed - 2000 tons"
            + "%Troops to be carried - 300", true);
    }

    private void readNotes() {
        say("the notes contain sketched maps and diagrams");
        say("the text reads");
        getOwner().getActionSender().sendAlert(
            "invasion%"
            + "%Troops board three fleets at karamja"
            + "%Fleet one attacks misthalin from south"
            + "%Fleet two groups at crandor and attacks Asgarnia from west coast"
            + "%Fleet three sails north attack Kandarin from south rienforced by"
            + "%gnome foot soldiers leaving gnome stronghold"
            + "%All prisoners to be slain", true);
    }

    // --------------------------------------------------------------- lookup --

    /** The nearest npc of an id that is in view, or null. */
    /**
     * The first npc in view carrying any of these ids, or null.
     *
     * Takes several because more than one id can fill the same role -- see the
     * note on GATE_GUARD. Callers that can be handed a null must cope; a null
     * speaker is not merely a missing chat bubble, it is an NPE inside the npc
     * update packet.
     */
    private Npc nearby(int... ids) {
        for (Npc n : getOwner().getViewArea().getNpcsInView()) {
            for (int id : ids) {
                if (n.getID() == id) {
                    return n;
                }
            }
        }
        return null;
    }
}
