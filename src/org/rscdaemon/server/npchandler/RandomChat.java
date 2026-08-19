package org.rscdaemon.server.npchandler;

import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.quest.dialogue.Choice;
import org.rscdaemon.server.quest.dialogue.Conversation;
import org.rscdaemon.server.quest.dialogue.Effect;
import org.rscdaemon.server.util.DataConversions;

/**
 * The three crowd NPCs whose whole purpose is to say something different every
 * time: the Man, the Pirate and the Barbarian. Sixty-odd wandering npcs between
 * them, and before this every one of them was silent.
 *
 * Transcript:Man, Transcript:Pirate and Transcript:Barbarian, all three carrying
 * the ordinary Jagex banner.
 *
 * ------------------------------------------------------------------ ids ----
 *
 * Getting the ids right matters more here than the lines do, because two of the
 * outcomes below start a fight and one hands over an item, and hanging that off
 * the wrong npc breaks a quest in a way that looks like a chat bug.
 *
 * Six npcs in the defs are called "Man". Only three of them are this Man, and
 * the wiki records three different examine strings for the three versions --
 * which our own NPCDef reproduces character for character, sic and all:
 *
 *    11  "One of runescapes many citizens"   no apostrophe
 *    72  "One of Runescapes many citizens"   capital R, still no apostrophe
 *   318  "One of Runescape's citizens"       correctly punctuated
 *
 * A coincidence does not reproduce three different mistakes in the right order,
 * so that is the identification. The other three are somebody else's:
 *
 *    24  "A shifty looking man"    Straven -- Shield of Arrav's phoenix leader
 *   750  "A thirsty looking man"   Murder Mystery
 *   307  "A well dressed nobleman" lowercase "man", not on the Man page at all
 *
 * 318 is Ardougne and therefore members content, which is not a reason to leave
 * it out: this server ships the members game too.
 *
 * Pirate 137 and 264 (the level 30) share one transcript and one table.
 * Barbarian is 76 alone.
 *
 * ------------------------------------------------- the Man's other faces ----
 *
 * Several more npcs use the Man's table outright rather than having one of
 * their own. Transcript:Rogue, Transcript:Warrior, Transcript:Thief and
 * Transcript:Farmer are not transcripts at all -- each is a 28-byte page
 * reading "#REDIRECT [[Transcript:Man]]".
 *
 * The evidence for them is NOT all of one strength, and it is worth keeping the
 * two tiers apart rather than averaging them into one confident sentence:
 *
 *   Rogue and Warrior -- the redirect AND a sentence in the article saying they
 *   "use the same dialogue as Men, and as a result, might randomly attack the
 *   player or give a Flier when spoken to". That sentence is what licenses the
 *   fight and the flier travelling with them, not just the words.
 *
 *   Thief and Farmer -- the redirect only. No such sentence anywhere on either
 *   article. Taken on the grounds that a page whose entire content is a
 *   redirect into this table is an editor asserting the tables are the same
 *   one, and taken knowingly: if either turns out to have had its own lines,
 *   this is where the mistake will be.
 *
 *   342  Rogue    "He needs a shave"                  6 spawns, deep wilderness
 *    86  Warrior  "A member of Al Kharid's military"  9 spawns, Al Kharid palace
 *   159  Warrior  "A skilled fighter"                 2 spawns, Varrock palace
 *   320  Warrior  "A skilled fighter"                10 spawns, Ardougne palace
 *    64  Thief    "He'll take anything that isn't nailed down"   14 spawns
 *    63  Farmer   F2P; 319 is the members Farmer and is NOT this  9 spawns
 *
 * Unlike the Man these needed almost no separating: exactly four npcs in the
 * whole defs are named Rogue or Warrior and all four are these, examine strings
 * and combat stats matching the wiki entry for entry.
 *
 * Thief did need separating, and the trap is the Man's in reverse. Two npcs are
 * named Thief: 64 above, and 351 "A dastardly blanket thief", members-only, two
 * spawns in the Ardougne sewers. The article names 351 as the blanket source
 * for Monk's Friend. That quest is not built here, so 351 is referenced by no
 * code at all today -- which is exactly the argument that was true of Straven
 * up until the moment it was not. 351 is deliberately left out. Holding it
 * costs an unbuilt quest nothing; wiring it costs that quest its item.
 *
 * ---------------------------------------------------- the flier as a drop ----
 *
 * Separately, item 201 is also a monster drop -- Imp 114, Barbarian 76 and
 * Gunthor the Brave 78. Two independent sources say so: the wiki, and tip.it's
 * 2004 bestiary, which lists the flier in the loot for both the Imp and the
 * Barbarian. None of the three carries it in its drop table here, so today the
 * flier reaches players only through this file.
 *
 * It is deliberately NOT fixed by adding one row, for two reasons worth writing
 * down because both are easy to miss:
 *
 *   The rate cannot be transcribed. The wiki quotes 1/128 and 2/128, but our
 *   ItemDropDef weight is a share of the table's own total (see Npc.java's
 *   drop roll), not a fixed 128th. The Barbarian's weights total 42, so a
 *   weight of 1 there is 1/43 -- about three times too common. Writing 1/128
 *   honestly would mean rescaling all seventeen of his rows, which asserts the
 *   wiki's denominator over ours across the whole table.
 *
 *   And the flier is not the gap. The Imp's table has ONE entry and Gunthor's
 *   has two, while tip.it lists a dozen-plus items for the Imp alone. These
 *   tables are stubs. Slipping the one item we happened to be looking at into
 *   a stub makes the table no more complete and the flier no more correct.
 *
 * So it is flagged as its own piece of work -- restore those tables from the
 * bestiary as a set -- rather than half-done here. Note that the Jagex client
 * cache cannot settle it either way: drops were server-side and were never in
 * the cache, so the usual ground-truth oracle is silent on this by design.
 *
 * ------------------------------------------------------------ weighting ----
 *
 * Not recovered. Said plainly because it would be easy to make this look
 * recovered: the {{trandom}} template that heads all three tables expands to
 * the single sentence "A random dialogue is selected from the following:" and
 * carries no numbers, no rates and no roll size. No individual outcome on any
 * of the three pages carries a replay or data citation.
 *
 * The one piece of evidence that exists is qualitative and it is capture-cited:
 * the Barbarian article says they "in rare circumstances can either attack or
 * give the player bones when spoken to". So the specials are rare -- not one in
 * twelve, which a flat draw over the whole table would give.
 *
 * Hence the shape here: ordinary lines are drawn flat among themselves, and the
 * specials sit behind one roll with one named constant. RARE_ODDS is OUR
 * number, not Jagex's. If a capture ever pins it down, it is one constant to
 * change rather than a table to unpick.
 */
public class RandomChat implements NpcHandler {

    public static final World world = World.getWorld();

    public static final int MAN_LIGHT = 11;
    public static final int MAN_DARK = 72;
    public static final int MAN_ARDOUGNE = 318;
    public static final int PIRATE = 137;
    public static final int PIRATE_LEVEL_30 = 264;
    public static final int BARBARIAN = 76;

    /* Redirect into the Man's table -- see "the Man's other faces" above.
       There is no THIEF_BLANKET = 351 here on purpose. */
    public static final int ROGUE = 342;
    public static final int WARRIOR_AL_KHARID = 86;
    public static final int WARRIOR_VARROCK = 159;
    public static final int WARRIOR_ARDOUGNE = 320;
    public static final int THIEF = 64;
    public static final int FARMER = 63;

    /*
     * Head Thief, Ardougne, one spawn, members. Not a redirect -- the wiki
     * keeps its own copy of the text, titled "Standard dialogue" -- but the
     * text IS this table: seventeen of his nineteen recorded lines already
     * exist verbatim in this file.
     *
     * The two that do not are worth knowing about rather than quietly
     * rounding away: "I think we need a new king" / "The one we've got isn't
     * very good". That exchange is the entry the Man page numbers "Dialogue 7"
     * and attributes to a Thief, and it was left out of the table on purpose,
     * because Transcript:Thief redirects into Transcript:Man and a shared
     * numbering makes it unrecoverable who actually said it.
     *
     * The Head Thief's page is a second, independent copy carrying the same
     * exchange, which is real evidence that it belongs to the thief-shaped
     * npcs rather than to every Man in the game. That is a reason to look
     * again, not a reason to add it here: putting it in this table would hand
     * it to Men, Rogues, Warriors and Farmers as well, on evidence that points
     * the other way. So 352 gets seventeen of his nineteen lines, which is
     * better than the silence he has now, and the remaining two stay logged.
     */
    public static final int HEAD_THIEF = 352;

    /**
     * The Tree Gnome shipyard, seventeen silent workers north of the gate.
     * 558 has two spawns at (408,743) and (410,751); 559 has fifteen, all inside
     * the yard between y=739 and y=760.
     *
     * NPC 557 IS NOT HERE AND MUST NEVER BE ADDED. The wiki infobox lists
     * "557,558" together as the light version, which makes wiring all three look
     * obviously right. It would break The Grand Tree. 557 has exactly ONE spawn,
     * at (401,763) -- one tile south of the shipyard gate at (401,762) -- and
     * GrandTree.java owns him as GATE_WORKER: he runs the whole ka-lu-min
     * password scene, and his "does not appear interested in talking" refusal is
     * itself recorded text. A handler here would take NPC_TALK dispatch away from
     * the quest and shut the player out of the shipyard permanently.
     *
     * That is handler-beats-quest, which has now silently broken three quests in
     * this codebase. It is the reason to check spawn counts before believing an
     * infobox: one npc standing alone at a gate is not a member of the crowd
     * working inside it.
     *
     * These two are combat npcs -- attack 48, strength 48, hits 40, aggression 2
     * -- unlike every other table in this file, which is passive. That is fine:
     * an NpcHandler only takes NPC_TALK, and touches nothing in the combat path.
     */
    public static final int SHIPYARD_WORKER_LIGHT = 558;
    public static final int SHIPYARD_WORKER_DARK = 559;

    /**
     * The Tree Gnome Village and Stronghold locals -- fifty-eight npcs between
     * the two, and the single largest silent population left in the game.
     *
     * TWO NPCS, TWO DIFFERENT TABLES, AND THE SPLIT IS CERTAIN. Transcript:Gnome
     * local is not one table with a stray heading; it is two sections titled, in
     * the page's own words, "Level 9 gnome local" and "Level 3 gnome local", each
     * with its own {{trandom}} and its own numbered entries. Our NPCDef settles
     * which is which without any inference at all: 592 is attack/strength/hits/
     * defence 9/9/9/9 and 593 is 3/3/3/3. Both examine as "A tree gnome villager".
     *
     *   592  level 9  31 spawns  3 entries
     *   593  level 3  27 spawns  4 entries
     *
     * This is the opposite situation to the Man, where six npcs shared a name and
     * the examine strings were the only thing separating them. Here the transcript
     * hands over the discriminator itself.
     *
     * Both are attackable in the defs, like the shipyard workers and unlike every
     * other table in this file. Irrelevant -- an NpcHandler only takes NPC_TALK.
     *
     * ------------------------------------------------------------ the worm ----
     *
     * Level 3's entry 3 hands over a free King worm, on a repeat-rollable outcome
     * with no recorded cooldown -- roughly one talk in four, across 27 spawns. The
     * shape of that is a giveaway loop, so it was checked before it shipped rather
     * than after, and it is not one:
     *
     *   King worm is item 897. basePrice 2, NOT stackable, edible for 2 hits, and
     *   no fishing code anywhere touches it -- it is not bait.
     *
     *   It has essentially no sink. An item can only be sold where
     *   Shop.shouldStock is true, which means general=true or the id is already
     *   in that shop's own list. No shop stocks 897, and exactly two of the 86
     *   shops in the game are general: Champion's Store and the Hero's Guild.
     *   Their sell modifiers are 40 and 55, and the price is
     *   sellModifier * basePrice / 100 in INTEGER arithmetic -- so Champion's
     *   pays 0 gp for a worm and the Hero's Guild pays 1 gp. Both are quest
     *   gated.
     *
     *   Cooking does not launder it either, and this is the interesting part:
     *   Jagex shipped two parallel gnome-food families. The one a player cooks
     *   is worm batta 904 / worm hole 909 / worm crunchies 912, all basePrice 2.
     *   Gianne's shop stock is 947/952/955 at 120/150/85. GnomeCooking produces
     *   the cheap family only, and Gianne's cuisine is general=false stocking
     *   only the expensive one, so it will not buy a player-cooked dish at all.
     *   Worm hole eats SIX worms to make a 2 gp item. Jagex closed this loop
     *   themselves by giving the cooked output a separate worthless id.
     *
     *   And the same npcs already give worms anyway: NpcPickpocket.xml lists
     *   King worm in the loot for 579-583, 585, 586, 591, 592 and 593. Two of
     *   these very gnomes have been a worm source since before this file
     *   existed. The dialogue lowers the requirement from thieving 75 to saying
     *   hello; it does not create the item.
     *
     * So the worm is given exactly as recorded, on every roll, with no cooldown
     * and no deviation.
     */
    public static final int GNOME_LOCAL_LEVEL_9 = 592;
    public static final int GNOME_LOCAL_LEVEL_3 = 593;

    /**
     * The gnome children -- three tables, twenty-four entries, the largest
     * single body of recorded text in the Tree Gnome cluster.
     *
     * ------------------------------------------------------ which is which ---
     *
     * The transcript's three sections are headed by appearance, not by level:
     *
     *   1  "Unattackable, blue shirt, light green/blue pants/hat"     6 entries
     *   2  "Attackable, Light green/blue shirt + pink hat/pants"      9 entries
     *   3  "Attackable, pink shirt blue pants/hat"                    9 entries
     *
     * and NPCDef carries the two colours the headings are describing:
     *
     *   585  top #FF00FF pink    bottom #00FF00 green   attackable   20 spawns
     *   586  top #FF00CC pink    bottom #0000FF blue    attackable   10 spawns
     *   591  top #FF00CC pink    bottom #0000FF blue    UNATTACKABLE  3 spawns
     *
     * Section 2 names green and pink, and 585 is the ONLY gnome child def in the
     * game containing green -- #00FF00 appears nowhere else. Section 3 names
     * pink and blue, which is 586 and 591 exactly. So section 2 is 585 on a
     * two-colour match, and between 586 and 591 the attackable flag decides:
     * 591 is the only unattackable child in the defs and section 1 is the only
     * heading that says unattackable. That leaves 586 for section 3, where its
     * colours match anyway.
     *
     *   591 -> section 1,  585 -> section 2,  586 -> section 3.
     *
     * ONE THING DOES NOT FIT, and it is worth putting here rather than leaving
     * for someone to rediscover as a bug. Section 1's colour text -- "blue
     * shirt, light green/blue pants" -- does NOT describe 591, which is pink
     * over blue, byte-identical to 586. Sections 2 and 3 match their npcs
     * exactly on both garments; section 1 matches on neither.
     *
     * It is still 591, because "Unattackable" is a field in NPCDef and the rest
     * of that heading is a person describing a picture. When a hard flag and a
     * prose description disagree, the flag wins. And nothing else can take the
     * section: the only other unattackable candidate would have to be a child,
     * and 591 is the only one there is.
     *
     * (Note also which way round the garments run. Sections 2 and 3 both read as
     * though "shirt" is our bottomColour and "hat/pants" is our topColour. That
     * consistent inversion does not change any assignment -- both colours are
     * matched as a pair -- so it is recorded and not acted on.)
     *
     * 583 IS A FOURTH GNOME CHILD AND IS DELIBERATELY ABSENT. Yellow over red,
     * matching no heading on the page, and it has ZERO spawns in NpcLoc -- no
     * player has ever met it. No recorded text and nobody to say it to.
     */
    public static final int GNOME_CHILD_GREEN = 585;
    public static final int GNOME_CHILD_PINK = 586;
    public static final int GNOME_CHILD_UNATTACKABLE = 591;

    /**
     * The gnome troop -- 31 spawns on the Tree Gnome Village battlefield, three
     * lines, and a table that is here rather than in the quest for a reason
     * worth stating.
     *
     * ------------------------------------------- why this is not stage-gated ---
     *
     * The transcript presents six sections, each headed by a point in the quest,
     * but they hold only THREE distinct texts, and they appear in this order:
     *
     *   before the quest ................................. A
     *   after accepting the quest ........................ B
     *   after accepting to help Commander Montai ......... A
     *   after a successful hit with the ballista ......... C
     *   after Bolren says the orbs were stolen ........... B
     *   after finishing the quest ........................ C
     *
     * A, B, A, C, B, C. Under any linear stage model that npc goes forward to B,
     * back to A, forward to C, back to B, forward to C again. Nothing in this
     * game does that. Read instead as six visits to a three-entry random table,
     * it is an unremarkable sample -- two of each, in no particular order.
     *
     * The content agrees. Local gnome 399 and Kalron 402, on the same page and
     * with the same six-section layout, say things that only make sense at their
     * stage: "must save the orbs and kill the khazard warlord", "soon we're
     * gonna have the sacred ceremony". Those two ARE staged, and they live in
     * TreeGnomeVillage.java. All three troop lines are interchangeable battle
     * barks -- every one of them is true from the moment you first walk onto the
     * battlefield to long after the quest ends.
     *
     * OpenRSC (AGPL, read as an oracle, no code taken) treats this npc as
     * staged, which is evidence the other way and is recorded here as such. But
     * their mapping disagrees with the transcript at two of the six points, and
     * their middle branch is guarded by "stage == 0 || stage >= 2 || stage <= 4"
     * -- a condition every stage satisfies, so their third line is unreachable
     * and no player of theirs has ever heard it. That looks like someone hitting
     * this same contradiction and routing around it rather than an independent
     * reading of the game.
     *
     * OURS, then, on the balance of the evidence. The failure mode is mild in
     * either direction: if it really was staged, players hear three barks
     * instead of one per stage, and all three are in character at every stage.
     */
    public static final int GNOME_TROOP = 409;

    private static final int FLIER = 201;
    private static final int BONES = 20;
    private static final int KING_WORM = 897;

    /**
     * One in this many conversations takes a special outcome -- combat, the
     * flier, the bones -- instead of an ordinary line.
     *
     * Ours, not Jagex's. See the weighting note above.
     */
    private static final int RARE_ODDS = 32;

    private static boolean rare() {
        return DataConversions.random(0, RARE_ODDS - 1) == 0;
    }

    private static int pick(int count) {
        return DataConversions.random(0, count - 1);
    }

    public void handleNpc(Npc npc, Player player) throws Exception {
        switch (npc.getID()) {
            case MAN_LIGHT:
            case MAN_DARK:
            case MAN_ARDOUGNE:
            /* Same table, by way of a redirect rather than by resemblance. */
            case ROGUE:
            case WARRIOR_AL_KHARID:
            case WARRIOR_VARROCK:
            case WARRIOR_ARDOUGNE:
            case THIEF:
            case FARMER:
            case HEAD_THIEF:
                man(npc, player);
                return;
            case PIRATE:
            case PIRATE_LEVEL_30:
                pirate(npc, player);
                return;
            case BARBARIAN:
                barbarian(npc, player);
                return;
            case SHIPYARD_WORKER_LIGHT:
            case SHIPYARD_WORKER_DARK:
                shipyardWorker(npc, player);
                return;
            /* Two npcs, two tables. Not a shared case label -- see the note on
               GNOME_LOCAL_LEVEL_9 for why the split is certain. */
            case GNOME_LOCAL_LEVEL_9:
                gnomeLocalLevel9(npc, player);
                return;
            case GNOME_LOCAL_LEVEL_3:
                gnomeLocalLevel3(npc, player);
                return;
            case GNOME_CHILD_UNATTACKABLE:
                gnomeChildUnattackable(npc, player);
                return;
            case GNOME_CHILD_GREEN:
                gnomeChildGreen(npc, player);
                return;
            case GNOME_CHILD_PINK:
                gnomeChildPink(npc, player);
                return;
            case GNOME_TROOP:
                gnomeTroop(npc, player);
                return;
        }
    }

    // ------------------------------------------------------------- shared --

    /** End the dialogue, then swing -- attackPlayer ignores a busy player. */
    private static Effect attack(final Npc npc) {
        return new Effect() {
            public void run(Conversation c) {
                c.stop();
                npc.attackPlayer(c.getPlayer());
            }
        };
    }

    // ---------------------------------------------------------------- man --

    /**
     * Every branch but one opens "Hello" / "How's it going?". Dialogue 22 opens
     * with "Hello" alone, and since 22 is otherwise a complete outcome that
     * short opening is real rather than a transcription slip.
     *
     * Three entries on the page are deliberately not here:
     *
     *  - The second entry labelled "Dialogue 7", whose speaker is the Thief and
     *    not the Man. Transcript:Thief is a redirect to Transcript:Man, so the
     *    two share a page and one numbering sequence -- which means the page
     *    cannot tell you whether that line is the Man's, the Thief's, or both.
     *    The duplicate label is certainly a wiki error; the attribution is not
     *    recoverable from it, so the line is left out rather than guessed at.
     *  - Dialogue 9, which is "Player: Hello" and nothing else. Not an outcome
     *    where the man says nothing -- that already exists as Dialogue 8, the
     *    "The man ignores you" message. It is a stub, so it stays a gap.
     *  - Dialogues 4, 5 and 6, which are Dialogue 3's three sub-options replayed
     *    as standalone outcomes with the player's line auto-delivered. Read as
     *    one conversation captured twice rather than four separate outcomes, so
     *    Dialogue 3 is built once, interactive. That reading is ours; if it is
     *    wrong the table is three entries short and nothing else breaks.
     */
    private void man(final Npc npc, Player player) {
        Conversation c = new Conversation(player, npc);
        if (rare()) {
            if (pick(2) == 0) {
                // Dialogue 12.
                c.player("Hello").player("How's it going?")
                 .npc("Are you asking for a fight?")
                 .then(attack(npc))
                 .start();
                return;
            }
            // Dialogue 18. The only outcome on the page with no end marker --
            // an omission, since nothing follows it.
            c.player("Hello").player("How's it going?")
             .npc("Have this flier")
             .give(new InvItem(FLIER, 1))
             .start();
            return;
        }

        int roll = pick(16);
        if (roll == 15) {
            // Dialogue 22, the one with the short opening.
            c.player("Hello")
             .npc("No, I don't have any spare change")
             .start();
            return;
        }

        c.player("Hello").player("How's it going?");
        switch (roll) {
            case 0: // Dialogue 1
                c.npc("I'm a little worried")
                 // The trailing comma is Jagex's -- the line really is split
                 // across two chat lines mid-sentence.
                 .npc("I've heard there's lots of people going about,")
                 .npc("killing citizens at random");
                break;
            case 1: // Dialogue 2
                c.npc("I'm a little worried about the increase in Goblins these days")
                 .player("Don't worry. I'll kill them");
                break;
            case 2: // Dialogue 3, the only interactive one
                c.npc("How can I help you?")
                 .options(new Choice("Do you wish to trade?",
                                     "I'm in search of a quest",
                                     "I'm in search of enemies to kill") {
                     public void picked(int option, Conversation c) {
                         if (option == 0) {
                             c.npc("No, I have nothing I wish to get rid of")
                              .npc("If you want to do some trading,")
                              .npc("there are plenty of shops and market stalls around though");
                         } else if (option == 1) {
                             c.npc("I'm sorry I can't help you there");
                         } else {
                             c.npc("I've heard there are many fearsome creatures under the ground");
                         }
                     }
                 });
                break;
            case 3: // Dialogue 7
                c.npc("Not too bad");
                break;
            case 4: // Dialogue 8 -- a message, not speech
                c.message("The man ignores you");
                break;
            case 5: // Dialogue 10
                c.npc("Get out of my way").npc("I'm in a hurry");
                break;
            case 6: // Dialogue 11
                c.npc("I'm fine").npc("How are you?").player("Very well, thank you");
                break;
            case 7: // Dialogue 13
                c.npc("Hello");
                break;
            case 8: // Dialogue 14
                c.npc("Hello").npc("Nice weather we've been having");
                break;
            case 9: // Dialogue 15
                c.npc("That is classified information");
                break;
            case 10: // Dialogue 16
                c.npc("Who are you?")
                 .player("I am a bold adventurer")
                 .npc("A very noble profession");
                break;
            case 11: // Dialogue 17
                c.npc("Do I know you?")
                 .player("No, I was just wondering if you had anything interesting to say");
                break;
            case 12: // Dialogue 19
                c.npc("Yo wassup!");
                break;
            case 13: // Dialogue 20
                c.npc("No, I don't want to buy anything");
                break;
            default: // Dialogue 21
                c.npc("None of your business");
                break;
        }
        c.start();
    }

    /**
     * "Oi what do you think you're doing", then he swings.
     *
     * Attested for the Man and nobody else -- it sits under "Failing
     * pickpocketing" on his own transcript. NpcCommand already sends the grey
     * "You fail to pick the ...'s pocket" and starts the fight for every
     * thieving target; this adds the spoken line for the one npc it is recorded
     * against. Do not widen it to every pickpocketable npc without a source:
     * that it reads like a generic line is not evidence that it was one.
     */
    /*
     * Deliberately still the three Men only -- not widened to the redirect ids
     * above, even though they share the transcript page. The chat table travels
     * with the redirect because the Rogue, Warrior and Thief articles say in so
     * many words that it does. This line has no such sentence behind it, and
     * "it is the same page" is a weaker argument than an explicit statement. A
     * Thief caught picking a pocket is the likeliest of the five to have had a
     * line of his own, so guessing is the expensive direction here.
     */
    public static boolean pickpocketFailLine(Npc npc, Player player) {
        int id = npc.getID();
        if (id != MAN_LIGHT && id != MAN_DARK && id != MAN_ARDOUGNE) {
            return false;
        }
        player.informOfNpcMessage(
            new org.rscdaemon.server.model.ChatMessage(npc, "Oi what do you think you're doing", player));
        return true;
    }

    // ------------------------------------------------------------- pirate --

    /**
     * Twenty-five outcomes, all one line except the fight and the last.
     *
     * The spelling is his. "Arrrh" has three r's in entry 3 where everywhere
     * else it has two; "shiver me timbers" is lower case where every other line
     * is capitalised; "and bottle of alchopop" is missing an "a" and "a bottle
     * of a rum" has one too many; "scury", "Batton" and "a brewin" are as
     * written; "3 days" is a digit. None of it is a typo of ours.
     *
     * Entries 19 and 25 are the same words differing only by a capital A, and
     * 25 goes on to the retort. Two separate outcomes, and the first thing a
     * tidy-up would merge. Leave them.
     */
    private void pirate(final Npc npc, Player player) {
        Conversation c = new Conversation(player, npc).player("Hello");
        if (rare()) {
            // Dialogue 20.
            c.npc("I think ye'll be taking a long walk off a short plank")
             .then(attack(npc))
             .start();
            return;
        }
        int roll = pick(PIRATE_LINES.length + 1);
        if (roll == PIRATE_LINES.length) {
            // Dialogue 25 -- same words as 19, different capital, plus a retort.
            c.npc("avast behind").player("I'm not that fat").start();
            return;
        }
        c.npc(PIRATE_LINES[roll]).start();
    }

    /** Dialogues 1-19 and 21-24; 20 is the fight and 25 has a retort. */
    private static final String[] PIRATE_LINES = {
        "I'm the scourge of the seven seas",
        "Arrh, I be in search of buried treasure",
        "Arrrh ye lily livered landlubber",
        "Ahoy there",
        "Arrh",
        "Yo ho ho me hearties",
        "Splice the mainbrace",
        "Avast me hearties",
        "Arrh I'll keel haul ye",
        "shiver me timbers",
        "Arrh be off with ye",
        "Yo ho ho and bottle of alchopop",
        "Arrh ye scury sea dog",
        "Batton down the hatches there's a storm a brewin",
        "A pox on ye",
        "Yo ho ho and a bottle of a rum",
        "3 days at port for resupply then out on the high sea",
        "Keel haul them I say",
        "Avast behind",
        "Good day to you my dear sir",
        "Great blackbeard's beard",
        "Arrh arrh",
        "Man overboard",
    };

    // ---------------------------------------------------------- barbarian --

    /**
     * Twelve listed outcomes, eleven built.
     *
     * Dialogue 11 is "Barbarian: Beer?" with no follow-up and no end marker.
     * Dialogue 12 is structurally identical and does hand over bones, so 11
     * almost certainly hands over a beer -- but the transcript does not say so,
     * and "almost certainly" is how invented content gets in. It is built as
     * the bare line.
     *
     * Dialogues 2 and 5 are messages rather than speech, and the "You are under
     * attack!" in 7 is the combat message, not something he says.
     *
     * 7 and 12 are the two the capture-cited "rare circumstances" sentence is
     * about, so they are the two behind the rare roll.
     */
    private void barbarian(final Npc npc, Player player) {
        Conversation c = new Conversation(player, npc).player("Hello");
        if (rare()) {
            if (pick(2) == 0) {
                // Dialogue 7.
                c.npc("Wanna fight?").then(attack(npc)).start();
                return;
            }
            // Dialogue 12.
            c.npc("Bones?")
             .message("The barbarian gives you some bones")
             .give(new InvItem(BONES, 1))
             .player("Err, thanks")
             .start();
            return;
        }
        switch (pick(10)) {
            case 0: c.npc("Hello"); break;
            case 1: c.message("The barbarian grunts"); break;
            case 2: c.npc("Good day, my dear fellow"); break;
            case 3: c.npc("ug"); break;
            case 4: c.message("The barbarian ignores you"); break;
            case 5: c.npc("Grr"); break;
            case 6: c.npc("I'm a little busy right now")
                     .npc("We're getting ready for our next barbarian raid"); break;
            case 7: c.npc("Go away").npc("This is our village"); break;
            case 8: c.npc("Who are you?")
                     .player("I'm a bold adventurer")
                     .npc("You don't look very strong"); break;
            default: c.npc("Beer?"); break;
        }
        c.start();
    }

    // --------------------------------------------------- shipyard worker --

    /**
     * Fifteen entries on the page, fourteen built. No combat, no item, no state:
     * every outcome is a conversation that simply ends. Entry 12 is a brush-off
     * and entry 4 ends with the worker asking you to leave, but neither does
     * anything -- they just stop.
     *
     * ALL LOWERCASE, including "i" and "i'm", exactly as recorded. The page is
     * unusually consistent about it across all fifteen entries, which is what
     * makes it a shipped style rather than a transcription habit.
     *
     * THE DUPLICATE. Entries 9 and 14 are character-for-character identical.
     * Either the wiki recorded the same roll twice, or vanilla listed it twice
     * and it is genuinely twice as likely as its neighbours. {{trandom}} carries
     * no weighting data anywhere -- the same dead end as the Man and Barbarian
     * tables -- so nothing on the page can settle it. Built ONCE, because
     * shipping it twice would assert a rate we cannot source, and asserting a
     * rate is the failure mode this whole file was written to avoid.
     */
    private void shipyardWorker(Npc npc, Player player) {
        Conversation c = new Conversation(player, npc);
        c.player("hello");
        switch (pick(14)) {
            case 0: // Dialogue 1
                c.npc("hello matey")
                 .player("how are you?")
                 .npc("tired")
                 .player("you shouldn't work so hard"); break;
            case 1: // Dialogue 2
                c.npc("hello there")
                 .npc("are you too lazy to work as well")
                 .player("something like that")
                 .npc("i'm just sun bathing"); break;
            case 2: // Dialogue 3
                c.player("looks like hard work")
                 .npc("i like to keep busy"); break;
            case 3: // Dialogue 4
                c.npc("can i help you")
                 .player("i'm just looking around")
                 .npc("well there's plenty of work to be done")
                 .npc("so if you don't mind...")
                 .player("of course, sorry to have disturbed you"); break;
            case 4: // Dialogue 5
                c.player("quite a few ships you're building")
                 .npc("this is just the start")
                 .npc("the completed fleet will be awesome"); break;
            case 5: // Dialogue 6
                c.player("how are you?")
                 .npc("too busy to waste time gossiping")
                 .player("touchy"); break;
            case 6: // Dialogue 7
                c.player("you look busy")
                 .npc("we need double the men to get..")
                 .npc("...this order out on time"); break;
            case 7: // Dialogue 8
                c.npc("what do you want?")
                 .player("is that any way to talk to your new superior?")
                 .npc("oh, i'm sorry, i didn't realise"); break;
            case 8: // Dialogues 9 AND 14 -- the duplicate, built once.
                c.npc("ouch")
                 .player("what's wrong?")
                 .npc("i cut my finger")
                 .npc("do you have a bandage?")
                 .player("i'm afraid not")
                 .npc("that's ok, i'll use my shirt"); break;
            case 9: // Dialogue 10
                c.npc("hello there")
                 .npc("i haven't seen you before")
                 .player("i'm new")
                 .npc("well it's hard work, but the pay is good"); break;
            case 10: // Dialogue 11
                c.player("what are you building?")
                 .npc("are you serious?")
                 .player("of course not")
                 .player("you're obviously building a boat"); break;
            case 11: // Dialogue 12
                c.npc("no time to talk")
                 .npc("we've a fleet to build"); break;
            case 12: // Dialogue 13
                c.player("quite an impressive set up")
                 .npc("it needs to be...")
                 .npc("..there's no other way to build a fleet of this size"); break;
            default: // Dialogue 15
                c.player("so where are you sailing?")
                 .npc("what do you mean?")
                 .player("don't worry, just kidding!"); break;
        }
        c.start();
    }

    // ------------------------------------------------- gnome local, lvl 9 --

    /**
     * 592, thirty-one spawns, three entries. All lowercase, and note "cant"
     * without its apostrophe in entry 2 -- both are as recorded.
     *
     * Entry 2 is the same shape as the Man's "ignores you" and the Barbarian's
     * grunt: the npc speaks once and then a message closes it out. The gnome
     * saying he is too busy AND the narrator saying he is too busy is not a
     * duplicated line, it is how Jagex wrote the brush-off, so both stay.
     */
    private void gnomeLocalLevel9(Npc npc, Player player) {
        Conversation c = new Conversation(player, npc);
        c.player("hello");
        switch (pick(3)) {
            case 0: // Dialogue 1
                c.npc("i don't think i can take much more")
                 .player("what's wrong?")
                 .npc("it's just the wife, she won't stop moaning")
                 .player("maybe you should give her less to moan about")
                 .npc("she'll always find something"); break;
            case 1: // Dialogue 2
                c.npc("cant stop sorry, busy, busy, busy")
                 .message("the gnome is too busy to talk"); break;
            default: // Dialogue 3
                c.npc("hello traveller")
                 .npc("are you enjoying your stay?")
                 .player("it's a nice place")
                 .npc("yes, we try to keep it that way"); break;
        }
        c.start();
    }

    // ------------------------------------------------- gnome local, lvl 3 --

    /**
     * 593, twenty-seven spawns, four entries.
     *
     * THE CASE IS INCONSISTENT AND IT IS SHIPPED THAT WAY. Entries 1 and 2 open
     * with capitals -- "Some people grumble", "I'm thankful" -- while 3 and 4 are
     * lowercase throughout, and so is every line of the level 9 table. That is
     * not a transcription slip to tidy up: it is the same kind of inconsistency
     * as the two Pirate "Avast behind" entries that differ only by a capital A,
     * and the same rule applies. Copy it, do not improve it.
     *
     * Three sic markers on the page, all preserved: "your" for "you're" and
     * "blurberrys" for "Blurberry's" in entry 2, and the comma straight after a
     * question mark in "are you eating properly?, you look tired" in entry 3.
     *
     * ENTRY 4 IS REAL, AND THE DISTINCTION MATTERS. It is one line -- the player
     * says hello and the gnome says nothing back. That is character-for-character
     * the shape of the Man's "Dialogue 9", which this file deliberately leaves
     * out as a truncated capture. The difference is one template: the Man's stub
     * has NO {{tact|end}} marker, and this one does. The transcriber closed it,
     * which means they recorded a complete outcome rather than stopping halfway
     * through writing one down. So it is built, and the worm below is one talk in
     * four rather than one in three.
     */
    private void gnomeLocalLevel3(Npc npc, Player player) {
        Conversation c = new Conversation(player, npc);
        c.player("hello");
        switch (pick(4)) {
            case 0: // Dialogue 1
                c.npc("Some people grumble because roses have thorns")
                 .npc("I'm thankful that thorns have roses")
                 .player("good attitude"); break;
            case 1: // Dialogue 2
                c.npc("well good day to you kind sir")
                 .npc("are you new to these parts?")
                 .player("kind of")
                 .npc("well if your looking for a good night out")
                 .npc("blurberrys cocktail bar's great"); break;
            case 2: // Dialogue 3 -- the worm. See the note on the constants.
                c.npc("hello traveller")
                 .npc("are you eating properly?, you look tired")
                 .player("i think so")
                 .npc("here get this worm down you")
                 .npc("it'll do you the world of good")
                 .message("the gnome gives you a worm")
                 .give(new InvItem(KING_WORM, 1))
                 .player("thanks!"); break;
            default: // Dialogue 4 -- one line, and it is meant to be one line.
                break;
        }
        c.start();
    }

    // ------------------------------------------------------- gnome child --

    /**
     * The worm outcome, which two different children share character for
     * character -- 591's entry 3 and 586's entry 8, opener included.
     *
     * That is worth noticing rather than deduplicating quietly: 586's other
     * eight entries all open "hello little man" and this one opens "hi there",
     * exactly as 591's does. An entry that arrives in a table carrying another
     * table's greeting is one Jagex copied across, and copying it back out is
     * how the greeting would get "fixed" into the wrong thing. It is built once
     * here so there is one place for it to stay right.
     *
     * The sic markers on the page sit on "the" (lowercase, mid-message) and on
     * "thanks" (no exclamation mark). Both stay as recorded. "recieve" is
     * Jagex's own.
     */
    private static void wormEntry(Conversation c) {
        c.player("hi there")
         .npc("hello, would you like a worm?")
         .player("erm ok")
         .message("the gnome gives you a worm")
         .give(new InvItem(KING_WORM, 1))
         .player("thanks")
         .npc("in the gnome village those who are needy..")
         .npc("recieve what they need, and those who are able..")
         .npc("... give what they can");
    }

    /**
     * 591, the unattackable child, plane 1, three spawns. Six entries.
     *
     * Entry 5 is the only one on the whole page that does NOT open with the
     * player speaking: it opens on the narrator, because the gnome is already
     * singing when you walk up. Built that way. (The recorded message has a
     * trailing space -- "the gnome appears to be singing " -- which is wikitext
     * whitespace and not observable in game, so it is the one thing here that
     * is not reproduced literally.)
     *
     * Entry 2 is the bare greeting with an end marker, the same shape as the
     * level 3 local's entry 4, and built for the same reason.
     */
    private void gnomeChildUnattackable(Npc npc, Player player) {
        Conversation c = new Conversation(player, npc);
        switch (pick(6)) {
            case 0: // Dialogue 1
                c.player("hi there")
                 .npc("hello, why aren't you green?")
                 .player("i don't know")
                 .npc("maybe you should eat more vegtables"); break;
            case 1: // Dialogue 2 -- greeting, no reply.
                c.player("hi there"); break;
            case 2: // Dialogue 3 -- the worm.
                wormEntry(c); break;
            case 3: // Dialogue 4
                c.player("hi there")
                 .npc("low")
                 .player("what?")
                 .npc("when?")
                 .player("cheeky")
                 .npc("hee hee"); break;
            case 4: // Dialogue 5 -- opens on the narrator, not the player.
                c.message("the gnome appears to be singing")
                 .npc("Oh baby, Oh my sweet")
                 .player("are you talking to me?")
                 .npc("no, i'm just singing")
                 .npc("i'm gonna sweep you of your feet"); break;
            default: // Dialogue 6
                c.player("hi there")
                 .npc("she loves me")
                 .player("really")
                 .npc("she does i tell you")
                 .npc("she really loves me"); break;
        }
        c.start();
    }

    /**
     * 585, the green one, ground floor, twenty spawns. Nine entries, and the
     * only child that tells riddles.
     *
     * TWO WORDS ON THIS PAGE ARE CORRECTED RATHER THAN REPRODUCED, which is a
     * departure from how everything else in this file is handled, so here is
     * the whole argument.
     *
     * Entry 1 reads "On what dav is it half grown?" and entry 7 ends "erm..not
     * sure...annoving". Neither carries a {{sic}} marker. Both are shipped here
     * as "day" and "annoying".
     *
     * The reason is that this page contains two DIFFERENT classes of error and
     * they are separable. Jagex's own, which are everywhere in this cluster and
     * are all preserved: "vegtables", "recieve", "obstical", "agilty",
     * "oppisite", "im", "cant", "preying" for praying. Every one of those is a
     * mistake you make by ear -- a plausible misspelling of a word you know.
     *
     * "dav" and "annoving" are not that. Nobody has ever typed "dav" for "day".
     * They are the same single substitution, y to v, twice on one page, and
     * that is a mistake you make with your eyes -- someone reading a low
     * resolution screenshot. It is the transcriber's error, not Jagex's.
     *
     * Reproducing it would not be authenticity. The point of copying Jagex's
     * typos is to reproduce what players saw on screen, and no player ever saw
     * "dav". Where the recording is corrupted, copying the corruption is the
     * less faithful choice, not the more careful one.
     *
     * It is still OURS and it is still a judgement call, so it is written down
     * here in full and flagged rather than buried. Two edits to reverse.
     */
    private void gnomeChildGreen(Npc npc, Player player) {
        Conversation c = new Conversation(player, npc);
        c.player("hello");
        switch (pick(9)) {
            case 0: // Dialogue 1 -- the doubling tree. "day", see above.
                c.npc("i have a riddle for you")
                 .player("ok")
                 .npc("A tree which is planted on Monday and doubles in size each day...")
                 .npc("...is fully grown on the following sunday")
                 .npc("On what day is it half grown?")
                 .player("Erm..i'm not sure")
                 .npc("saturday")
                 .npc("you big folk really aren't the quickest"); break;
            case 1: // Dialogue 2
                c.npc("To be or not to be")
                 .player("Hey I know that. Where's it from?")
                 .npc("Existentialism for insects"); break;
            case 2: // Dialogue 3
                c.npc("I worship Guthix, the god of balance")
                 .npc("He really does have exceptional co-ordination"); break;
            case 3: // Dialogue 4
                c.npc("The human mind is a tremendous thing"); break;
            case 4: // Dialogue 5
                c.npc("hardy ha ha")
                 .npc("hee hee hee")
                 .player("are you ok?")
                 .npc("i'm a little tree gnome")
                 .npc("that is me")
                 .player("i've heard better"); break;
            case 5: // Dialogue 6 -- greeting, no reply.
                break;
            case 6: // Dialogue 7 -- the letter E. "annoying", see above.
                c.npc("i have a riddle for you")
                 .player("ok")
                 .npc("I am the beginning of eternity and the end of time and space...")
                 .npc("I am the beginning of every end and the end of every place. What am i?")
                 .player("?")
                 .player("erm..not sure...annoying")
                 .npc("i'm E, hee hee, do you get it"); break;
            case 7: // Dialogue 8
                c.npc("Nice weather we're having today")
                 .npc("But then it doesn't tend to rain much round here"); break;
            default: // Dialogue 9 -- the player greets twice, as recorded.
                c.player("hello there")
                 .npc("bla bla bla")
                 .player("what?")
                 .npc("bla bla bla")
                 .message("rude little gnome"); break;
        }
        c.start();
    }

    /**
     * 586, pink over blue, plane 1, ten spawns. Nine entries, and the one that
     * hands out proverbs.
     *
     * Every entry opens "hello little man" EXCEPT entry 8, the worm, which
     * opens "hi there" -- see wormEntry above for why that is kept rather than
     * regularised. So the greeting is not hoisted out of the switch here the
     * way it is for the other tables in this file.
     *
     * "preying" in entry 4 is Jagex's, marked {{sic}} on the page, and stays.
     */
    private void gnomeChildPink(Npc npc, Player player) {
        Conversation c = new Conversation(player, npc);
        int roll = pick(9);
        if (roll == 7) { // Dialogue 8 -- the worm, and it brings its own opener.
            wormEntry(c);
            c.start();
            return;
        }
        c.player("hello little man");
        switch (roll) {
            case 0: // Dialogue 1
                c.npc("my mum says...")
                 .npc("A friendly look, a kindly smile")
                 .npc("one good act, and life's worthwhile!")
                 .player("sweet"); break;
            case 1: // Dialogue 2
                c.npc("hello")
                 .player("are you alright?")
                 .npc("i just want something to happen")
                 .player("what?")
                 .npc("something, anything i don't know what"); break;
            case 2: // Dialogue 3
                c.npc("a little inaccuracy sometimes...")
                 .npc("..saves tons of explanation")
                 .player("true"); break;
            case 3: // Dialogue 4
                c.message("the gnome is preying")
                 .npc("guthix's angels so high as to be beyond our sight")
                 .npc("but they are always looking down upon us")
                 .player("maybe"); break;
            case 4: // Dialogue 5
                c.player("how are you")
                 .npc("a warning traveller, the new world..")
                 .npc("..will rise from the underground")
                 .player("what do you mean underground?")
                 .npc("just a warning"); break;
            case 5: // Dialogue 6
                c.npc("some advice traveller")
                 .npc("we can walk, run, row or fly")
                 .npc("but never lose sight of the reason for the journey")
                 .npc("or miss the chance to see a rainbow on the way")
                 .player("i like that"); break;
            case 6: // Dialogue 7
                c.player("you look happy")
                 .npc("i'm always at peace with myself")
                 .player("how do you manage that?")
                 .npc("i know, therefore i am"); break;
            default: // Dialogue 9 -- greeting, no reply.
                break;
        }
        c.start();
    }

    // --------------------------------------------------------- gnome troop --

    /**
     * Three barks, evenly weighted. See GNOME_TROOP for why this is a table and
     * not a stage check.
     *
     * The transcript opens two of the three with "hello" and one with "hi"; the
     * openers travel with their entries rather than being hoisted, because that
     * is how they were recorded and there is no reason to think the difference
     * is a transcription slip.
     */
    private void gnomeTroop(Npc npc, Player player) {
        Conversation c = new Conversation(player, npc);
        switch (pick(3)) {
            case 0:
                c.player("hello")
                 .npc("i can't talk now")
                 .npc("can't you see we're trying")
                 .npc("to win a battle here?"); break;
            case 1:
                c.player("hello")
                 .npc("death to khazard and all who serve him!"); break;
            default:
                c.player("hi")
                 .npc("draw your sword warrior")
                 .npc("and fight along side us!"); break;
        }
        c.start();
    }
}
