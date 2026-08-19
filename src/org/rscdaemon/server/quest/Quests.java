package org.rscdaemon.server.quest;

/**
 * The canonical quest ids.
 *
 * These are not ours to choose. The client holds Jagex's quest list as a fixed
 * array (mudclient.QUEST_NAMES) and draws the quest tab straight from it, so a
 * quest's id is its index in that array and nothing else. Sending completion for
 * id 1 lights up "Cook's assistant" because Cook's assistant is second in the
 * client's list -- not because the server named it that.
 *
 * The names below are copied from the client verbatim, capitalisation and all,
 * so the two can be diffed by eye. Nothing here may be reordered or removed.
 *
 * Ids at {@link #FIRST_CUSTOM} and above are free for quests of our own. They are
 * saved and loaded like any other, but the stock client has no row to show them
 * in, so they belong in the F2 panel rather than the vanilla quest tab.
 */
public final class Quests {

    public static final int BLACK_KNIGHTS_FORTRESS = 0;
    public static final int COOKS_ASSISTANT = 1;
    public static final int DEMON_SLAYER = 2;
    public static final int DORICS_QUEST = 3;
    public static final int THE_RESTLESS_GHOST = 4;
    public static final int GOBLIN_DIPLOMACY = 5;
    public static final int ERNEST_THE_CHICKEN = 6;
    public static final int IMP_CATCHER = 7;
    public static final int PIRATES_TREASURE = 8;
    public static final int PRINCE_ALI_RESCUE = 9;
    public static final int ROMEO_AND_JULIET = 10;
    public static final int SHEEP_SHEARER = 11;
    public static final int SHIELD_OF_ARRAV = 12;
    public static final int THE_KNIGHTS_SWORD = 13;
    public static final int VAMPIRE_SLAYER = 14;
    public static final int WITCHS_POTION = 15;
    public static final int DRAGON_SLAYER = 16;
    public static final int WITCHS_HOUSE = 17;
    public static final int LOST_CITY = 18;
    public static final int HEROS_QUEST = 19;
    public static final int DRUIDIC_RITUAL = 20;
    public static final int MERLINS_CRYSTAL = 21;
    public static final int SCORPION_CATCHER = 22;
    public static final int FAMILY_CREST = 23;
    public static final int TRIBAL_TOTEM = 24;
    public static final int FISHING_CONTEST = 25;
    public static final int MONKS_FRIEND = 26;
    public static final int TEMPLE_OF_IKOV = 27;
    public static final int CLOCK_TOWER = 28;
    public static final int THE_HOLY_GRAIL = 29;
    public static final int FIGHT_ARENA = 30;
    public static final int TREE_GNOME_VILLAGE = 31;
    public static final int THE_HAZEEL_CULT = 32;
    public static final int SHEEP_HERDER = 33;
    public static final int PLAGUE_CITY = 34;
    public static final int SEA_SLUG = 35;
    public static final int WATERFALL_QUEST = 36;
    public static final int BIOHAZARD = 37;
    public static final int JUNGLE_POTION = 38;
    public static final int GRAND_TREE = 39;
    public static final int SHILO_VILLAGE = 40;
    public static final int UNDERGROUND_PASS = 41;
    public static final int OBSERVATORY_QUEST = 42;
    public static final int TOURIST_TRAP = 43;
    public static final int WATCHTOWER = 44;
    public static final int DWARF_CANNON = 45;
    public static final int MURDER_MYSTERY = 46;
    public static final int DIGSITE = 47;
    public static final int GERTRUDES_CAT = 48;
    public static final int LEGENDS_QUEST = 49;
    /**
     * The first quest past Jagex's RSC list. Rune Mysteries shipped with
     * RS2's Runecrafting, so RSC never had it -- but this server does have
     * Runecrafting, and the quest is its gate. Taking id 50 means our
     * client's QUEST_NAMES grows a row to match; stock clients past this
     * point are already incompatible (19-skill wire format), so extending
     * the vanilla range costs nothing extra.
     */
    public static final int RUNE_MYSTERIES = 50;

    /** Copied from mudclient.QUEST_NAMES. Index == quest id. */
    public static final String[] NAMES = new String[]{
        "Black knight's fortress",
        "Cook's assistant",
        "Demon slayer",
        "Doric's quest",
        "The restless ghost",
        "Goblin diplomacy",
        "Ernest the chicken",
        "Imp catcher",
        "Pirate's treasure",
        "Prince Ali rescue",
        "Romeo & Juliet",
        "Sheep shearer",
        "Shield of Arrav",
        "The knight's sword",
        "Vampire slayer",
        "Witch's potion",
        "Dragon slayer",
        "Witch's house (members)",
        "Lost city (members)",
        "Hero's quest (members)",
        "Druidic ritual (members)",
        "Merlin's crystal (members)",
        "Scorpion catcher (members)",
        "Family crest (members)",
        "Tribal totem (members)",
        "Fishing contest (members)",
        "Monk's friend (members)",
        "Temple of Ikov (members)",
        "Clock tower (members)",
        "The Holy Grail (members)",
        "Fight Arena (members)",
        "Tree Gnome Village (members)",
        "The Hazeel Cult (members)",
        "Sheep Herder (members)",
        "Plague City (members)",
        "Sea Slug (members)",
        "Waterfall quest (members)",
        "Biohazard (members)",
        "Jungle potion (members)",
        "Grand tree (members)",
        "Shilo village (members)",
        "Underground pass (members)",
        "Observatory quest (members)",
        "Tourist trap (members)",
        "Watchtower (members)",
        "Dwarf Cannon (members)",
        "Murder Mystery (members)",
        "Digsite (members)",
        "Gertrude's Cat (members)",
        "Legend's Quest (members)",
        "Rune mysteries"
    };

    /**
     * Quest points awarded by each quest, by id.
     *
     * Jagex's own values, taken from the ==Rewards== section of each quest's
     * wiki page (see _reference/gamedata/derived/quest_rewards.json). They matter
     * beyond bragging rights: the Champions' Guild wants 32 and Legend's Quest
     * wants 107, so getting these wrong locks or unlocks content.
     *
     * 49 of the 50 are confirmed against that source. Legend's Quest is the
     * exception -- its wiki page has no rewards section at all -- so its 4 is the
     * one value here still taken on trust.
     */
    public static final int[] POINTS = new int[]{
        3,  /* Black knight's fortress */
        1,  /* Cook's assistant */
        3,  /* Demon slayer */
        1,  /* Doric's quest */
        1,  /* The restless ghost */
        5,  /* Goblin diplomacy */
        4,  /* Ernest the chicken */
        1,  /* Imp catcher */
        2,  /* Pirate's treasure */
        3,  /* Prince Ali rescue */
        5,  /* Romeo & Juliet */
        1,  /* Sheep shearer */
        1,  /* Shield of Arrav */
        1,  /* The knight's sword */
        3,  /* Vampire slayer */
        1,  /* Witch's potion */
        2,  /* Dragon slayer */
        4,  /* Witch's house */
        3,  /* Lost city */
        1,  /* Hero's quest */
        4,  /* Druidic ritual */
        6,  /* Merlin's crystal */
        1,  /* Scorpion catcher */
        1,  /* Family crest */
        1,  /* Tribal totem */
        1,  /* Fishing contest */
        1,  /* Monk's friend */
        1,  /* Temple of Ikov */
        1,  /* Clock tower */
        2,  /* The Holy Grail */
        2,  /* Fight Arena */
        2,  /* Tree Gnome Village */
        1,  /* The Hazeel Cult */
        4,  /* Sheep Herder */
        1,  /* Plague City */
        1,  /* Sea Slug */
        1,  /* Waterfall quest */
        3,  /* Biohazard */
        1,  /* Jungle potion */
        5,  /* Grand tree */
        2,  /* Shilo village */
        5,  /* Underground pass */
        2,  /* Observatory quest */
        2,  /* Tourist trap */
        4,  /* Watchtower */
        1,  /* Dwarf Cannon */
        3,  /* Murder Mystery */
        2,  /* Digsite */
        1,  /* Gertrude's Cat */
        4,  /* Legend's Quest */
        1   /* Rune mysteries -- RS2's own value */
    };

    /** Ids from here up are ours; the stock client's quest tab cannot show them. */
    public static final int FIRST_CUSTOM = 1000;
    /* GangMembership.java owns FIRST_CUSTOM (1000) itself. */
    /** Kolodion's shapeshift-form progress -- see quests/MageArena.java. */
    public static final int MAGE_ARENA = FIRST_CUSTOM + 1;
    /**
     * The three god spells' permanent per-spell cast counters, 0-100,
     * earned by casting inside the Mage Arena -- see quests/GodCharges.java.
     * A record, not a quest, the same reasoning as GangMembership: this is
     * the only channel src/ code (SpellHandler, which cannot name a class
     * compiled in the default package) has to read or write per-player
     * state that isn't a skill or an inventory slot.
     */
    public static final int GOD_CHARGES = FIRST_CUSTOM + 2;
    /**
     * Tutorial Island -- see quests/TutorialIsland.java.
     *
     * A custom id on purpose, and not because the tutorial is ours: Jagex never
     * gave it a row in the quest tab either. Being past {@link #FIRST_CUSTOM}
     * is what keeps it off the list and out of the quest-point total, since
     * QuestManager.fillProgress skips a uid the client has no row for and
     * {@link #points} scores an unlisted id zero. It still saves, loads and
     * completes like any other quest.
     */
    public static final int TUTORIAL_ISLAND = FIRST_CUSTOM + 3;
    /**
     * Blurberry's bar, second floor of the Grand Tree -- see quests/GnomeBar.java.
     *
     * A minigame, not a quest, and Jagex gave it no quest-tab row either. It is
     * a Quest class for the same reason MageArena is: it is the only per-player
     * state channel there is, and both the five-cocktail audition and whichever
     * order is outstanding have to survive a logout ("this minigame will pick
     * off where you left off").
     */
    public static final int GNOME_BAR = FIRST_CUSTOM + 4;
    /** Aluft Gianne's restaurant, the same again -- see quests/GnomeRestaurant.java. */
    public static final int GNOME_RESTAURANT = FIRST_CUSTOM + 5;
    /**
     * The Alfred Grimhand bar crawl -- see quests/BarCrawl.java.
     *
     * A mini-quest. Jagex gave it no quest-tab row and no quest points, so it
     * belongs past FIRST_CUSTOM even though it is entirely vanilla content;
     * putting it in the vanilla range would invent a 51st quest and shift
     * everybody's quest-point total. The stage is a signature bitmask --
     * model/BarCrawlCard.java documents the encoding.
     */
    public static final int BAR_CRAWL = FIRST_CUSTOM + 6;
    /**
     * Which wizard last teleported the player into the rune essence mine --
     * see model/EssenceMine.java. Not a quest at all: this is the mine's
     * way-home memory (an npc id), parked here because the quest-stage map
     * is the one per-player store that both persists and is reachable from
     * src/ code, which cannot name the boot-compiled quest classes.
     */
    public static final int ESSENCE_MINE_SENDER = FIRST_CUSTOM + 7;

    public static int count() {
        return NAMES.length;
    }

    public static boolean isVanilla(int id) {
        return id >= 0 && id < NAMES.length;
    }

    public static String name(int id) {
        return isVanilla(id) ? NAMES[id] : ("Quest #" + id);
    }

    public static int points(int id) {
        return isVanilla(id) ? POINTS[id] : 0;
    }

    private Quests() {
    }
}
