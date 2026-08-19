import java.util.List;

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
 * The Hazeel cult. Released 15 August 2002, written by Thomas Woode.
 *
 * Sir Ceril Carnillean has been burgled five times and wants his armour back.
 * Clivet, in a cave south of the city, explains why: the Carnilleans took the
 * house, and everything in it, off Lord Hazeel's murdered family three hundred
 * years ago. The player then picks a side, and the quest runs twice over.
 *
 *     Ceril 418      (619,620) downstairs and (613,1562) in Jones' room
 *     butler 419     (616,617) and (613,1562)
 *     guard 420      (614,610) (617,609) (617,610)
 *     henryeta 422   (611,620)      philipe 423 (616,1565)
 *     clivet 424     (619,3477)     claus 429   (616,3452)
 *     cult member 425 x6, alomone 427 (582,3419), Lord hazeel 426 (580,3419)
 *
 *     Sewer valves 412..416, one placement each, north to south down the
 *     Ardougne wall: 412 (623,634) 413 (622,603) 414 (622,593) 415 (622,582)
 *     416 (622,578). The raft only runs when they read right, left, right,
 *     right, left -- the shape of the amulet, read from the tail.
 *
 *     log raft 432 (621,3477) in Clivet's cave; the 433s at (589,3409) by
 *     the hideout entrance and (600,3410) in the passage are the ride back
 *     out -- the only way home. The other 433s are scenery in the sewer
 *     channel and keep the engine's brush-off.
 *     range 435 (618,3453), crate 439 (611,3451) in the cellar
 *     Bookcase 436 (617,1559) hides the stair to the top floor
 *     Carnillean Chest 438 (620,2506), Butlers cupboard 441 (614,1562)
 *
 * The stage is a bit set: bit 0 the quest, bit 2 the side taken, bits 5-9 the
 * five valves, and single bits for each step of whichever half is being played.
 * Both halves end at one number, because a quest is complete when its stage
 * equals its final stage exactly and there is only one of those.
 *
 * Deviations:
 *
 *  - Which side the player took is forgotten once the quest is over, for the
 *    reason just given. Jagex gave every npc in the household two sets of
 *    post-quest lines. Here a player still wearing the Mark of Hazeel is
 *    treated as one of the cult and everyone else gets the other set, which is
 *    what the amulet was for in the first place -- "I see you have the mark".
 *
 *  - The cupboard scene is a Conversation hung on Ceril, who is standing in
 *    the room, with Jones voiced as a bystander -- the goblin-generals
 *    pattern. If Ceril has somehow wandered out of view the search only
 *    reports the finds and waits; nothing is consumed, so searching again
 *    with him present plays the scene.
 *
 *  - Nothing in the world changes afterwards. Jagex did not change it either:
 *    Jones is still behind the same desk on both endings, and Lord Hazeel is
 *    already standing by his tomb before anybody resurrects him.
 *
 *  - The raft ride is a teleport, and a wrongly set valve turns the player
 *    back at the water's edge rather than dumping them somewhere unpleasant.
 *
 *  - The cave mouth at (619,649) had no entry point in the world data at all
 *    and has been given one, which is world repair rather than quest content
 *    and lives in ObjectTelePoints.xml.gz with every other one.
 *
 * Dialogue is Jagex's, from the recorded transcripts.
 */
public class TheHazeelCult extends Quest {

    public final static int UID = Quests.THE_HAZEEL_CULT;

    private static final int CERIL = 418, BUTLER = 419, GUARD = 420;
    private static final int HENRYETA = 422, PHILIPE = 423, CLIVET = 424;
    private static final int CULTIST = 425, HAZEEL = 426, ALOMONE = 427;
    private static final int CLAUS = 429;

    private static final int[] VALVES = { 412, 413, 414, 415, 416 };
    /** true = right, false = left, in valve order. */
    private static final boolean[] CORRECT = { true, false, true, true, false };

    private static final int RAFT = 432, RAFT_BACK = 433, RANGE = 435, BOOKCASE = 436;
    private static final int CHEST = 438, CRATE = 439, CUPBOARD = 441;

    private static final int COINS = 10, POISON = 177, SCRIPT_ITEM = 747;
    private static final int MARK = 753, ARMOUR = 755, KEY = 756;

    private static final int HIDEOUT_X = 588, HIDEOUT_Y = 3410;
    private static final int TOP_X = 616, TOP_Y = 2505;
    private static final int THIEVING = 17;

    private static final int STARTED = 1;
    private static final int MET_CLIVET = 2;
    private static final int EVIL = 4;
    private static final int POISONED = 8;
    private static final int MARKED = 16;
    private static final int VALVES_ALL = 992;      /* bits 5-9 */
    private static final int MET_ALOMONE = 1024;
    /** Killed him, on the good side; found the script, on the evil one. */
    private static final int DEED_DONE = 2048;
    private static final int ACCUSED = 4096;
    private static final int FINISHED = 1048576;

    public TheHazeelCult(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("The Hazeel Cult");
        setFinalStage(FINISHED);
        associateNpc(CERIL);
        associateNpc(BUTLER);
        associateNpc(GUARD);
        associateNpc(HENRYETA);
        associateNpc(PHILIPE);
        associateNpc(CLIVET);
        associateNpc(CULTIST);
        associateNpc(HAZEEL);
        associateNpc(ALOMONE);
        associateNpc(CLAUS);
        /* Every one of these stands in exactly one place. */
        for (int i = 0; i < VALVES.length; i++) {
            associateObject(VALVES[i]);
        }
        associateObject(RAFT);
        associateObject(RAFT_BACK);
        associateObject(RANGE);
        associateObject(BOOKCASE);
        associateObject(CHEST);
        associateObject(CRATE);
        associateObject(CUPBOARD);

        /* Description and manual lines are Jagex's, from the 2003 manual. */
        describe("Discover the truth behind the Carnillean family fortune. Decide for yourself wheather to aid the Carnilleans in retrieving stolen goods, or join the hazeel cult members in their mission to resurrect the infamous Lord Hazeel.");
        setStartPoint("South Ardounge");
        setSpeakTo("Mr Carnillean");
        setMissionLength("Medium");
        rewardExp(THIEVING, 500, 50);
        /* Both endings pay 2000 coins in their own scenes -- see ceril's
         * apology and Lord Hazeel's farewell -- so the coins stay there. */
        rewardOther("2000 coins, paid by whichever side you helped");
    }

    public void completeQuest() {
        grantRewards();
        getOwner().getActionSender().sendMessage("Well done.You have completed the Hazeel cult quest");
    }

    // ------------------------------------------------------------- helpers --

    private boolean has(int bit) {
        return questStarted() && (getStage() & bit) == bit;
    }

    private void set(int bit) {
        setStage(questStarted() ? getStage() | bit : bit);
    }

    private void clear(int bit) {
        if (questStarted()) {
            setStage(getStage() & ~bit);
        }
    }

    private boolean holding(int id) {
        return getOwner().getInventory().countId(id) > 0;
    }

    private boolean evil() {
        return has(EVIL);
    }

    /** After the quest the side is gone; the amulet is what is left of it. */
    private boolean cultist() {
        return completed() ? holding(MARK) : evil();
    }

    private boolean valvesSet() {
        return has(VALVES_ALL);
    }

    private void give(int id, int amount) {
        Player p = getOwner();
        p.getInventory().add(new InvItem(id, amount));
        p.getActionSender().sendInventory();
    }

    /** The named npc if the player can see one, else null. */
    private Npc nearby(int id) {
        List<Npc> inView = getOwner().getViewArea().getNpcsInView();
        for (Npc n : inView) {
            if (n.getID() == id) {
                return n;
            }
        }
        return null;
    }

    // ------------------------------------------------------------ dispatch --

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        this.triggerEntity(trigger, entity, null);
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity, InvItem used) {
        if (entity instanceof Npc) {
            Npc npc = (Npc) entity;
            if (trigger == QuestTrigger.NPC_KILLED) {
                if (npc.getID() == ALOMONE && !evil() && has(MET_ALOMONE) && !has(DEED_DONE)) {
                    set(DEED_DONE);
                    getOwner().getActionSender().sendMessage("@gre@You find the carnillean family armour on him");
                    give(ARMOUR, 1);
                }
                return;
            }
            if (trigger != QuestTrigger.NPC_TALK) {
                return;
            }
            switch (npc.getID()) {
                case CERIL:    ceril(npc); return;
                case CLIVET:   clivet(npc); return;
                case ALOMONE:  alomone(npc); return;
                case BUTLER:   butler(npc); return;
                case GUARD:    guard(npc); return;
                case HENRYETA: henryeta(npc); return;
                case PHILIPE:  philipe(npc); return;
                case CLAUS:    claus(npc); return;
                case CULTIST:  cultMember(npc); return;
                case HAZEEL:   hazeel(npc); return;
            }
            return;
        }
        if (!(entity instanceof GameObject)) {
            return;
        }
        GameObject object = (GameObject) entity;
        for (int i = 0; i < VALVES.length; i++) {
            if (VALVES[i] == object.getID()) {
                if (trigger == QuestTrigger.OBJECT_ACT1) {
                    valve(i, false);
                } else if (trigger == QuestTrigger.OBJECT_ACT2) {
                    valve(i, true);
                }
                return;
            }
        }
        if (trigger == QuestTrigger.ITEM_ON_OBJECT) {
            if (object.getID() == RANGE) {
                poisonRange(used);
            } else if (object.getID() == CHEST) {
                unlockChest(used);
            }
            return;
        }
        if (trigger != QuestTrigger.OBJECT_ACT1) {
            return;
        }
        switch (object.getID()) {
            case RAFT:     raft(); return;
            case RAFT_BACK: raftBack(object); return;
            case BOOKCASE: bookcase(); return;
            case CHEST:    unlockChest(null); return;
            case CRATE:    crate(); return;
            case CUPBOARD: cupboard(); return;
        }
    }

    // ------------------------------------------------------------- valves --

    private void valve(int index, boolean right) {
        Player p = getOwner();
        p.getActionSender().sendMessage("you turn the valve " + (right ? "right" : "left"));
        if (!questStarted()) {
            return;
        }
        int bit = 32 << index;
        if (right == CORRECT[index]) {
            if (!has(bit)) {
                set(bit);
            }
        } else {
            clear(bit);
        }
        p.getActionSender().sendMessage("you hear the water below change course");
    }

    // -------------------------------------------------------------- rafts --

    private void raft() {
        Player p = getOwner();
        if (!has(MET_CLIVET)) {
            p.getActionSender().sendMessage("You have no reason to go rafting");
            return;
        }
        if (!valvesSet()) {
            p.getActionSender().sendMessage("you push the raft out into the water");
            p.getActionSender().sendMessage("the current carries it straight back to the bank");
            p.getActionSender().sendMessage("the sewer valves above must be set wrong");
            return;
        }
        p.getActionSender().sendMessage("you board the raft");
        p.getActionSender().sendMessage("the current carries you off into the darkness");
        p.teleport(HIDEOUT_X, HIDEOUT_Y, false);
    }

    /** The hideout raft, and the ride home to Clivet's cave. */
    private void raftBack(GameObject object) {
        Player p = getOwner();
        boolean hideoutSide = (object.getX() == 589 && object.getY() == 3409)
            || (object.getX() == 600 && object.getY() == 3410);
        if (!hideoutSide) {
            p.getActionSender().sendMessage("@pnk@ You must talk to the owner about this.");
            return;
        }
        p.getActionSender().sendMessage("you board the raft");
        p.getActionSender().sendMessage("the current carries you back through the sewers");
        p.teleport(620, 3477, false);
    }

    // ------------------------------------------------------- Carnillean house --

    private void poisonRange(InvItem used) {
        Player p = getOwner();
        if (used == null || used.getID() != POISON) {
            p.getActionSender().sendMessage("Nothing interesting happens");
            return;
        }
        if (!evil() || has(POISONED)) {
            p.getActionSender().sendMessage("You have no reason to do that");
            return;
        }
        p.getInventory().remove(POISON, 1);
        p.getActionSender().sendInventory();
        p.getActionSender().sendMessage("you poor the poison into the hot pot");
        p.getActionSender().sendMessage("the poison desolves into the soup");
        set(POISONED);
    }

    private void crate() {
        Player p = getOwner();
        p.getActionSender().sendMessage("you search the crate");
        if (!has(MET_ALOMONE) || !evil() || has(DEED_DONE) || holding(KEY)) {
            p.getActionSender().sendMessage("but you find nothing of interest");
            return;
        }
        p.getActionSender().sendMessage("under the food packages");
        p.getActionSender().sendMessage("you find an old rusty key");
        give(KEY, 1);
    }

    private void bookcase() {
        Player p = getOwner();
        p.getActionSender().sendMessage("you search the bookcase");
        if (!has(MET_ALOMONE) || !evil()) {
            p.getActionSender().sendMessage("you find nothing but dusty carnillean family histories");
            return;
        }
        p.getActionSender().sendMessage("the bookcase swings away from the wall");
        p.getActionSender().sendMessage("behind it a narrow stair leads upwards");
        p.teleport(TOP_X, TOP_Y, false);
    }

    private void unlockChest(InvItem used) {
        Player p = getOwner();
        if (has(DEED_DONE) || completed()) {
            p.getActionSender().sendMessage("the chest is empty");
            return;
        }
        if (!holding(KEY) && (used == null || used.getID() != KEY)) {
            p.getActionSender().sendMessage("the chest is locked");
            p.getActionSender().sendMessage("Perhaps I should search the house for a key");
            return;
        }
        p.getInventory().remove(KEY, 1);
        p.getActionSender().sendInventory();
        p.getActionSender().sendMessage("you use the key to open");
        p.getActionSender().sendMessage("the chest");
        p.getActionSender().sendMessage("inside the chest you find the sacred script of hazeel");
        give(SCRIPT_ITEM, 1);
        set(DEED_DONE);
    }

    /**
     * The evidence. Jones and Ceril are both standing in this room and both
     * speak; a cupboard cannot open a conversation, so the scene is narrated.
     */
    private void cupboard() {
        Player p = getOwner();
        p.getActionSender().sendMessage("you search the cupboard");
        if (evil() || !has(ACCUSED) || completed()) {
            p.getActionSender().sendMessage("but you find nothing of interest");
            return;
        }
        Npc ceril = nearby(CERIL);
        if (ceril == null) {
            /* Nothing is consumed; the search plays the scene once he is. */
            p.getActionSender().sendMessage("you find a bottle of poison");
            p.getActionSender().sendMessage("and a strange amulet");
            p.getActionSender().sendMessage("you should show these to ceril");
            return;
        }
        Npc jones = nearby(BUTLER);
        final Npc butler = jones != null ? jones : ceril;
        new Conversation(p, ceril)
            .message("you find a bottle of poison")
            .message("and a strange amulet")
            .message("you pass your finds to ceril")
            .player("look what i've found?")
            .npc("what's this for jones?")
            .message("ceril takes the bottle")
            .npc("i don't believe it, it's poison")
            .npc(butler, "mr carnillean, it's for the rats")
            .npc(butler, "i'm just a loyal servent")
            .npc("i've seen this amulet before")
            .npc("the thieves that broke in")
            .npc("one of them was wearing exactly the same amulet")
            .npc("jones i don't believe it")
            .npc("we trusted you")
            .npc(butler, "that's because you're an old fool ceril")
            .npc(butler, "I should have got rid of you and your family weeks ago")
            .message("ceril calls for the guards")
            .npc(butler, "don't worry ceril")
            .npc(butler, "we'll make sure you and your family pay")
            .npc("looks like i owe you an apology traveller")
            .npc("if it wasn't for you he could have poisoned my whole family")
            .npc("the least i can do is give you a proper reward")
            .message("ceril gives you 2000 gold coins")
            .then(new Effect() {
                public void run(Conversation c) {
                    give(COINS, 2000);
                    setStage(FINISHED);
                }
            })
            .start();
    }

    // -------------------------------------------------------------- Ceril --

    private void ceril(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            if (cultist()) {
                new Conversation(p, npc)
                    .player("hello ceril")
                    .npc("i maybe wrong")
                    .npc("but ever since i asked for your help")
                    .npc("thing's have gone from bad to worse")
                    .npc("i think from now on you better keep out of my way")
                    .start();
            } else {
                new Conversation(p, npc)
                    .player("hello ceril")
                    .npc("well hello there")
                    .npc("brave adventurer, it's good to see you again")
                    .npc("if it wasn't for you")
                    .npc("that butler jones would have poisoned me by now")
                    .start();
            }
            return;
        }
        if (!questStarted()) {
            offer(npc);
            return;
        }
        if (evil()) {
            cerilEvil(npc);
            return;
        }
        if (has(ACCUSED)) {
            new Conversation(p, npc)
                .player("you owe me money")
                .npc("i owe you nothing now go away")
                .npc("before i have jones throw you out")
                .start();
            return;
        }
        if (has(DEED_DONE) && holding(ARMOUR)) {
            armour(npc);
            return;
        }
        if (has(DEED_DONE)) {
            // Alomone is dead and the armour is still down there. This state
            // was missing until the sweep against Transcript:Ceril; a player
            // who came back empty-handed used to get the generic nag below.
            new Conversation(p, npc)
                .player("ceril, how are you?")
                .npc("Im ok. Have you found the armour")
                .player("i'm afraid not")
                .npc("well i'm not paying you to see the sights")
                .player("okay, i'll go and try and retrieve it for you")
                .start();
            return;
        }
        if (has(MET_CLIVET)) {
            new Conversation(p, npc)
                .npc("have you had any luck yet?")
                .player("hello ceril, i've discovered the hideout")
                .npc("well done... and the armour?")
                .player("i'm afraid not")
                .player("i spoke to a cult member in the entrance of the cave")
                .player("but he escaped into the sewer systems")
                .player("seems they have a grievance with your family")
                .player("something to do with some bloke called hazeel")
                .npc("err errmm... no")
                .npc("They're obviously all mad")
                .npc("just find them and bring back the armour")
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("hello ceril")
            .npc("it's sir ceril to you")
            .npc("and shouldn't you be out recovering my suit of armour?")
            .start();
    }

    private void offer(Npc npc) {
        Player p = getOwner();
        new Conversation(p, npc)
            .player("hello there")
            .npc("blooming, thieving, wierdos")
            .npc("why don't they leave me alone?")
            .options(new Choice("What's wrong?", "You probably deserve it",
                                "You seem uptight, I'll leave you alone") {
                public void picked(int option, Conversation c) {
                    if (option == 1) {
                        c.npc("who are you to judge me?")
                         .npc("hmmm, you look like a peasant")
                         .npc("i'm wasting my time talking to you");
                        return;
                    }
                    if (option == 2) {
                        c.npc("yes, i doubt you could help");
                        return;
                    }
                    c.npc("it's those strange folk from the forest")
                     .npc("those freaks keep breaking into my house")
                     .player("have they taken much?")
                     .npc("they first broke in months ago and stole a suit of armour")
                     .npc("the strange thing is that they've broken in four times since")
                     .npc("but took nothing")
                     .player("and you are...?")
                     .npc("why, i'm ceril carnillean")
                     .npc("we really are quite a famous bloodline")
                     .npc("we've played a large part in ardounge pollitics for generations")
                     .npc("maybe you could help retrieve the armour?")
                     .npc("of course there would be a handsom cash reward for yourself")
                     .options(new Choice("yes, off course,i'd be happy to help",
                                         "no thanks i've got plans") {
                         public void picked(int option, Conversation c) {
                             if (option != 0) {
                                 c.npc("no wonder i'm the one with the big house and you're on the streets");
                                 return;
                             }
                             c.npc("that's very kind of you")
                              .npc("I caught a glimpse of the thieves leaving")
                              .npc("but due to ermm... my cold... I was unable to give chase")
                              .npc("they were dressed all in black")
                              .npc("I think they may have belonged to some sort of cult")
                              .player("do you know where they are?")
                              .npc("my old butler once followed them")
                              .npc("to a cave entrance in the forest south of here")
                              .npc("unfortunately the next night he died in his sleep")
                              .player("that's awful")
                              .npc("it's ok, a replacement arrived the next day")
                              .npc("he's been great, cooks an excellent broth")
                              .player("ok ceril, i'll see what i can do")
                              .then(new Effect() {
                                  public void run(Conversation c) {
                                      set(STARTED);
                                  }
                              });
                         }
                     }.says(0, "yes of course, i'd be happy to help"));
                }
            })
            .start();
    }

    private void armour(Npc npc) {
        Player p = getOwner();
        new Conversation(p, npc)
            .player("ceril, how are you?")
            .player("Look, I've found the armour")
            .npc("well done i must say i am impressed")
            .message("you give ceril the family armour")
            .take(ARMOUR, 1)
            .npc("before we send you on your way")
            .npc("i'll get our butler jones")
            .npc("to whip you up some of his special broth")
            .player("i'd rather not")
            .player("i overheard the cult members talking")
            .player("the buttler is really working for them")
            .npc("that's it, come with me")
            .npc("we'll sort this out once and for all")
            .message("you follow ceril up to butler Jones' room")
            .message("ceril speaks briefly with Jones")
            .npc("Well, he assures me that he's a loyal hard working man")
            .npc("I cannot fathom, why you would believe he is a spy")
            .player("surely you won't take his word for it?")
            .npc("we have also decided that due to the humilliation you have caused")
            .npc("it is only fair that Jones shall recieve your reward")
            .npc("you shall recieve payment more suited to your low life personality")
            .message("ceril gives you 5 gold coins")
            .message("ceril gives jones 695 gold coins")
            .npc("now take it and leave")
            .message("butler Jones has a slight grin")
            .message("You're going to need more than just your word")
            .message("To prove Jones' treachary")
            .then(new Effect() {
                public void run(Conversation c) {
                    give(COINS, 5);
                    set(ACCUSED);
                    c.getPlayer().teleport(613, 1563, false);
                }
            })
            .start();
    }

    private void cerilEvil(Npc npc) {
        Player p = getOwner();
        if (has(DEED_DONE)) {
            new Conversation(p, npc)
                .player("hello ceril, how are you?")
                .npc("I think the thieves may have been back in the house")
                .player("Why?")
                .npc("i'm not sure but it seem's as if some of my books")
                .npc("have been re-arranged in my study")
                .npc("it's either that or i'm losing my marbles")
                .start();
            return;
        }
        if (has(MET_ALOMONE)) {
            new Conversation(p, npc)
                .player("ceril, how are you?")
                .npc("I'm devestated")
                .npc("i don't know what to do with myself since i lost scruffy")
                .message("ceril bursts into tears")
                .start();
            return;
        }
        if (has(POISONED)) {
            new Conversation(p, npc)
                .npc("oh my, the misery, the pain")
                .npc("my son is a good boy but stupid as well")
                .npc("i can't believe he gave his dinner to scruffy")
                .npc("without having the servents check it for poison first")
                .npc("how could he be so careless?")
                .player("scruffy?")
                .npc("he's been in the family for twenty years the poor dog")
                .npc("what did he ever do to hurt anyone?")
                .start();
            return;
        }
        new Conversation(p, npc)
            .npc("have you had any luck yet?")
            .player("i'm afraid not ceril")
            .npc("well that's strange")
            .npc("the butler seemed quite sure about their location")
            .start();
    }

    // ------------------------------------------------------------- Clivet --

    private void clivet(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            if (cultist()) {
                new Conversation(p, npc)
                    .player("hello")
                    .npc("It's good to see you again")
                    .npc("i am patiently waiting for hazeel to call upon me")
                    .start();
            } else {
                new Conversation(p, npc)
                    .player("hello")
                    .npc("You again! I warned you to keep away")
                    .npc("bother someone else")
                    .npc("go find some goblins to hack up")
                    .start();
            }
            return;
        }
        if (!questStarted()) {
            new Conversation(p, npc)
                .player("hello there")
                .npc("what do you want traveller")
                .player("just passing by")
                .npc("you have no business here")
                .npc("leave...now")
                .start();
            return;
        }
        if (evil()) {
            clivetEvil(npc);
            return;
        }
        if (has(MET_CLIVET)) {
            new Conversation(p, npc)
                .player("hello there")
                .npc("oh not you again")
                .player("where is the cult hideout?")
                .npc("you're a fool if you think you'll ever find it")
                .npc("soon hazeel will return and you'll be punished")
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("do you know the carnilleans?")
            .npc("i'll mind my business you mind yours")
            .player("look i know you're hiding something")
            .player("i've heard there's a cult hideout down here")
            .npc("if you know what's best for you you'll leave now")
            .player("i have my orders")
            .npc("so that two faced cold hearted snob has got to you too has he?")
            .player("ceril carnillean is a decent man")
            .npc("there's a lot more than meets the eye to the carnilleans")
            .npc("and none of it's decent")
            .player("what do you mean?")
            .npc("the carnillean family house does not belong to them")
            .npc("it's original owner was lord hazeel")
            .npc("hazeel was one of the mahjarrat followers of zamorak")
            .npc("The carnilleans harassed hazeel and his family for decades")
            .npc("then one night they stormed hazeel's home")
            .npc("one by one they tortured and then butchered him and his family")
            .npc("the next day the carnillean forefathers moved into the property")
            .npc("they've lived there on hazeel's wealth ever since")
            .player("ardounge history and pollitics are not my concern")
            .player("i've been asked to do a job and i plan to carry it through")
            .npc("well now i'm asking you to do a job")
            .npc("hazeel is going to return my friend")
            .npc("those who aid his journey will gain rewards")
            .npc("help us avenge hazeel's spirit so he may return")
            .options(new Choice("You're crazy, i'd never help you",
                                "so what would i have to do?") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("then you're a fool")
                         .npc("go back to your adventures traveller")
                         .then(new Effect() {
                             public void run(Conversation c) {
                                 set(STARTED | MET_CLIVET);
                                 departOnRaft(npc);
                             }
                         });
                        return;
                    }
                    c.npc("first you must prove your loyalty to the cause")
                     .npc("you must kill one of the carnillean family members")
                     .npc("then we will know who's side you're really on")
                     .npc("so will you do it?")
                     .options(new Choice("ok, i'll do it", "no i won't do it") {
                         public void picked(int option, Conversation c) {
                             if (option != 0) {
                                 c.npc("then you're a fool")
                                  .npc("go back to your adventures traveller")
                                  .then(new Effect() {
                                      public void run(Conversation c) {
                                          set(STARTED | MET_CLIVET);
                                          departOnRaft(npc);
                                      }
                                  });
                                 return;
                             }
                             c.npc("good, few see through the carnillean lies")
                              .npc("but i guessed you were of stronger character")
                              .npc("here take this poison, pour it into one of their meals")
                              .npc("once the deed is done return here")
                              .give(new InvItem(POISON, 1))
                              .then(new Effect() {
                                  public void run(Conversation c) {
                                      set(STARTED | MET_CLIVET | EVIL);
                                  }
                              });
                         }
                     });
                }
            })
            .start();
    }

    /**
     * The recorded escape scene after a refusal: four narrated lines, and
     * Clivet is gone. remove() hands him to the world respawn (30s), which is
     * as close as a shared world gets to him leaving -- the next player finds
     * him back at his post, exactly as the real game had to allow too.
     */
    private void departOnRaft(Npc npc) {
        Player p = getOwner();
        p.getActionSender().sendMessage("the man jumps onto a small raft");
        p.getActionSender().sendMessage("and pushes off down the sewer system");
        p.getActionSender().sendMessage("you hear him call out");
        p.getActionSender().sendMessage("you'll never find us...");
        npc.remove();
    }

    private void clivetEvil(Npc npc) {
        Player p = getOwner();
        if (has(DEED_DONE)) {
            new Conversation(p, npc)
                .player("hello again")
                .npc("have you managed to find the script of hazeel?")
                .player("yes, i found it in the house")
                .npc("amazing, the last piece")
                .npc("now the time has come to change history and avenge lord hazeel")
                .npc("take the script to alomone as quick as you can")
                .start();
            return;
        }
        if (has(MET_ALOMONE)) {
            new Conversation(p, npc)
                .player("hello")
                .npc("hello traveller")
                .npc("all we need now is the sacred script of hazeel")
                .npc("once we have that Hazeel can return")
                .start();
            return;
        }
        if (has(POISONED)) {
            if (has(MARKED)) {
                new Conversation(p, npc)
                    .player("hello")
                    .npc("the flow of the sewer's are controlled by 5 sewer valves above")
                    .npc("turn them correctly and the sewer will carry you to the hideout")
                    .npc("the sign of hazeel is your guide - you must begin at the tail")
                    .npc("The cult leader alomone shall be expecting you")
                    .start();
                return;
            }
            new Conversation(p, npc)
                .player("hello")
                .player("I poured the poison into the carnillean's meal as requested")
                .npc("yes we have people on the inside who informed me of your deed")
                .npc("hazeel will reward you for your loyalty")
                .player("ok, so what's next?")
                .npc("first you must wear the sign of hazeel")
                .npc("the amulet is proof to other cult members that you're one of us")
                .npc("it is also the key to finding the cult hideout")
                .give(new InvItem(MARK, 1))
                .player("in what way?")
                .npc("the flow of the sewer's are controlled by 5 sewer valves above")
                .npc("turn them correctly and the sewer will carry you to the hideout")
                .npc("the sign of hazeel is your guide - you must begin at the tail")
                .npc("The cult leader alomone shall be expecting you")
                .then(new Effect() {
                    public void run(Conversation c) {
                        set(MARKED);
                    }
                })
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("hello there")
            .npc("traveller you have a mission")
            .npc("go to the carnillean house and poison their meal")
            .start();
    }

    // ------------------------------------------------------------ Alomone --

    private void alomone(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            if (cultist()) {
                new Conversation(p, npc)
                    .player("hello again")
                    .npc("we wait patiently for lord hazeel's calling")
                    .player("ok, take care")
                    .start();
            } else {
                new Conversation(p, npc)
                    .player("hello again")
                    .npc("leave here now intruder")
                    .npc("before i loose my patience")
                    .start();
            }
            return;
        }
        if (!evil()) {
            /* Talking to him is the fight. The conversation is stopped rather
               than left to run down, because attackPlayer() will not touch a
               player the dialogue still has marked busy. */
            final Npc him = npc;
            Conversation c = new Conversation(p, npc);
            if (has(DEED_DONE)) {
                c.player("hello alomone")
                 .npc("out of my way")
                 .npc("can't you see we're busy here?");
            } else {
                c.npc("How did get you get in here?")
                 .player("I've come for the carnillean family armour")
                 .npc("I thought I told the butler to get rid of you")
                 .npc("he must be going soft")
                 .player("so the butler is working for you too?")
                 .player("Why's it always the Butler? I should have guessed");
            }
            c.then(new Effect() {
                public void run(Conversation c) {
                    set(MET_ALOMONE);
                    c.stop();
                    him.attackPlayer(c.getPlayer());
                }
            }).start();
            return;
        }
        if (has(DEED_DONE) && holding(SCRIPT_ITEM)) {
            resurrection(npc);
            return;
        }
        if (has(MET_ALOMONE)) {
            new Conversation(p, npc)
                .player("hello alomone")
                .npc("hazeel has waited long enough traveller")
                .npc("the sooner you find the hazeel script the better")
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("hi there")
            .npc("well well, we have a new recruit")
            .npc("Clivet told me about your willingness to prove yourself")
            .npc("we must retrieve the sacred script of hazeel")
            .npc("From the Carnillean house")
            .npc("an ancient spell which if read over Hazeel's grave")
            .npc("will bring him back to this world")
            .npc("the Carnilleans aren't aware of it's existence")
            .npc("we have eyes in the house")
            .npc("Butler Jones is one of us")
            .npc("go back to the house and try to find the script")
            .then(new Effect() {
                public void run(Conversation c) {
                    set(MET_ALOMONE);
                }
            })
            .start();
    }

    /**
     * Hazeel is already standing by his tomb, so if the player can see him he
     * speaks for himself; otherwise Alomone reads his part, which is the same
     * fallback the goblin generals use.
     */
    private void resurrection(Npc npc) {
        Player p = getOwner();
        Npc lord = nearby(HAZEEL);
        final Npc voice = lord != null ? lord : npc;
        new Conversation(p, npc)
            .player("hello")
            .npc("Do you have the sacred script of hazeel?")
            .player("yes I have it here")
            .take(SCRIPT_ITEM, 1)
            .npc("finally our lord hazeel can return")
            .npc("with these words our lord will return and save us all")
            .npc("come with me adventurer and let the ceromony begin")
            .npc("I do this for you lord hazeel and all followers of zamorak")
            .npc(voice, "my followers i am proud of you all")
            .npc(voice, "I never expected to retun to these lands")
            .npc(voice, "I can see I have much to attend to")
            .npc(voice, "In due time you will all be rewarded for your part")
            .npc(voice, "brave adventurer, i believe your contribution was the most critical")
            .npc(voice, "i owe you much, you may not be a follower of the great zamorak")
            .npc(voice, "but you understand injustice and anger")
            .npc(voice, "for this I certainly shall call upon your help in the future")
            .npc(voice, "my people gain strength day to day")
            .npc(voice, "you would be wise to join us while you can")
            .player("I fight for myself")
            .npc(voice, "hmm, fair enough for now")
            .npc(voice, "I shall reward you with money")
            .npc(voice, "But your reward of Zamorak's approval is far greater")
            .message("Lord hazeel gives you 2000 gold coins")
            .then(new Effect() {
                public void run(Conversation c) {
                    give(COINS, 2000);
                    setStage(FINISHED);
                }
            })
            .npc(voice, "now i must leave you")
            .npc(voice, "i have much business to attend to with my brothers in the north")
            .npc(voice, "i will see you all again but be aware")
            .npc(voice, "soon much blood will be spilt over runescape")
            .start();
    }

    private void hazeel(Npc npc) {
        Player p = getOwner();
        new Conversation(p, npc)
            .player("hello")
            .npc("my people gain strength day to day")
            .npc("you would be wise to join us while you can")
            .start();
    }

    // ------------------------------------------------- the household staff --

    private void butler(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            if (cultist()) {
                new Conversation(p, npc)
                    .player("hello jones")
                    .npc("it's an honour to be in your presence again traveller")
                    .npc("I hope things are well")
                    .player("not bad, yourself")
                    .npc("i'm good thanks")
                    .start();
            } else {
                new Conversation(p, npc)
                    .player("hello stranger")
                    .npc("why hello there")
                    .player("i take it you're the new butler")
                    .npc("that's right")
                    .npc("i think they had some problems with the last one")
                    .player("you could say that")
                    .start();
            }
            return;
        }
        if (!questStarted()) {
            new Conversation(p, npc)
                .player("hello there")
                .npc("hello,how are you today?")
                .player("good thanks and yourself")
                .npc("fine and dandy")
                .start();
            return;
        }
        if (has(ACCUSED)) {
            new Conversation(p, npc)
                .player("hello")
                .npc("you fool")
                .npc("did you think you could simply accuse me and save the day?")
                .npc("we've been working on this for years")
                .npc("your interference is only a minor set back to our plans")
                .npc("and when the mighty hazeel does return")
                .npc("the likes of you and the carnilleans will be the first of many to suffer")
                .start();
            return;
        }
        if (!evil() && has(DEED_DONE)) {
            new Conversation(p, npc)
                .player("jones i need to talk to you")
                .npc("do you need some help with your quest?")
                .player("you can stop the act jones")
                .player("i know you're working for the cult")
                .npc("what? don't be so silly")
                .player("I overheard the cult leader talking about you")
                .npc("look here,you may think you know something")
                .npc("but really you have no idea")
                .player("i know once i reveal the truth")
                .player("you'll be locked up")
                .npc("you think that old fool ceril")
                .npc("will take your word over mine")
                .npc("he completely trust's me")
                .player("we will have to see about that")
                .npc("i'll warn you once more traveller")
                .npc("don't get involved")
                .start();
            return;
        }
        if (evil() && has(DEED_DONE)) {
            new Conversation(p, npc)
                .player("hello jones")
                .npc("have you managed to find the script?")
                .player("I have it here")
                .npc("incredible, we owe you a lot")
                .npc("you better get it back to our hideout as quick as you can")
                .npc("these our exciting times traveller")
                .npc("once the great hazeel returns")
                .npc("things are going to really change around here")
                .start();
            return;
        }
        if (evil() && has(MET_ALOMONE)) {
            new Conversation(p, npc)
                .npc("hello again friend")
                .npc("I see you you have the mark")
                .npc("you should keep that covered up")
                .player("oh that's just an old family pass down")
                .npc("you don't have to pretend to me friend")
                .npc("our cause is one and the same")
                .npc("the sooner lord hazeel is avenged")
                .npc("the better for us and this city")
                .player("have you any idea where the sacred script is")
                .npc("no idea i'm afraid")
                .npc("it must be somewhere in the house")
                .npc("but i can't find it for the life of me")
                .npc("i've searched high and low")
                .player("doesn't ceril get suspisous")
                .npc("that old fool")
                .npc("he can't see the forest for the tree's")
                .start();
            return;
        }
        if (evil() && has(POISONED)) {
            new Conversation(p, npc)
                .npc("hello friend,i heard about your handy work")
                .npc("quite amusing really")
                .npc("I'm sure hazeel will be pleased with you anyway")
                .npc("keep up the good work")
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("hello, what is this building?")
            .npc("this is the property of Sir Ceril Carnillean")
            .npc("of the noble carnillean family")
            .npc("you're welcome to look around")
            .npc("but i'm afraid i'll have to keep an eye on you")
            .npc("we've been having a real problem with thieves")
            .npc("strange cult folk coming out the forest")
            .player("that's a shame")
            .npc("yes well these things are bound to happen")
            .npc("when you're as wealthy as the Varnilleans")
            .options(new Choice("Have you any more info on the carnilleans?",
                                "how long have you worked here?",
                                "ok then take care") {
                public void picked(int option, Conversation c) {
                    if (option == 0) {
                        c.npc("there's a lot i could tell you")
                         .npc("about the carnillean family history")
                         .npc("i'm afraid if did speak about such matter's")
                         .npc("i would lose my job and that i cannot risk");
                    } else if (option == 1) {
                        c.npc("long enough to know the carnilleans")
                         .npc("are not as innocent or noble as they seem");
                    } else {
                        c.npc("you to");
                    }
                }
            })
            .start();
    }

    private void guard(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            if (cultist()) {
                new Conversation(p, npc)
                    .player("hello")
                    .npc("you again")
                    .npc("didn't i tell you you're not welcome around here")
                    .npc("now leave before we have to get rough with you")
                    .start();
            } else {
                new Conversation(p, npc)
                    .player("hello")
                    .npc("well if it isn't our own local hero")
                    .npc("it's good to see you in these parts again")
                    .start();
            }
            return;
        }
        if (!questStarted()) {
            new Conversation(p, npc)
                .player("hello")
                .npc("hello,i haven't seen you before")
                .npc("if you've come to look at the carnillean family home")
                .npc("just make sure you behave yourself")
                .npc("we've had enough wierdos causing trouble")
                .npc("round here of late")
                .start();
            return;
        }
        if (has(ACCUSED)) {
            new Conversation(p, npc)
                .npc("hello adventurer")
                .npc("i heard you've accused butler jones")
                .npc("of being involved with the cult")
                .npc("to be honest i haven't")
                .npc("trusted him since he turned up here")
                .npc("a day after the old butler died")
                .npc("it seems too much of a coincidence to me")
                .start();
            return;
        }
        if (!evil() && has(DEED_DONE)) {
            new Conversation(p, npc)
                .player("hello")
                .npc("hello brave adventurer")
                .npc("keep up the good work")
                .start();
            return;
        }
        if (evil() && has(DEED_DONE)) {
            new Conversation(p, npc)
                .player("hello guard")
                .npc("hello there")
                .npc("i hope you find the cult soon")
                .npc("we think there may have been another burglary")
                .player("that's worrying")
                .npc("i just don't know how they do it")
                .npc("it seems like they're right under our noses")
                .start();
            return;
        }
        if (evil() && has(MET_ALOMONE)) {
            new Conversation(p, npc)
                .player("hello")
                .npc("hello adventurer")
                .npc("you're still hanging around then")
                .start();
            return;
        }
        if (evil() && has(POISONED)) {
            new Conversation(p, npc)
                .player("hello there")
                .npc("oh hello, did you hear?")
                .npc("the cult members have been back")
                .npc("I don't know what they've done")
                .npc("but ceril is really upset")
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("hello")
            .npc("hi i heard you're after the cult")
            .npc("who broke in the other night")
            .npc("blooming wierdo's")
            .start();
    }

    private void henryeta(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            if (cultist()) {
                new Conversation(p, npc)
                    .player("hello")
                    .npc("i've been instructed by my husband not to talk to you")
                    .npc("so go away and leave me alone")
                    .player("charming")
                    .start();
            } else {
                new Conversation(p, npc)
                    .player("hello")
                    .npc("hello again adventurer")
                    .npc("things really have picked up around here")
                    .npc("since you dealt with those nasty cult members")
                    .player("good to hear")
                    .start();
            }
            return;
        }
        if (!questStarted()) {
            new Conversation(p, npc)
                .player("hello")
                .npc("oh hello")
                .npc("if you wish to look around the carnillean family home")
                .npc("please refraine from touching anything")
                .npc("with those grubby hands of yours")
                .start();
            return;
        }
        if (has(ACCUSED)) {
            new Conversation(p, npc)
                .player("hello henyeta")
                .npc("don't think you can accuse my trusted staff")
                .npc("then be friends with me")
                .player("what i said about jones is true")
                .npc("don't be so ridiculous")
                .npc("next you'll tell me he murdered our old butler")
                .start();
            return;
        }
        if (!evil() && has(DEED_DONE)) {
            new Conversation(p, npc)
                .player("hello")
                .npc("oh, hello there adventurer")
                .npc("i hope you were careful dealing with those nasty men")
                .player("i was fine, thanks")
                .start();
            return;
        }
        if (evil() && has(MET_ALOMONE)) {
            new Conversation(p, npc)
                .player("hello")
                .npc("i'm sorry i'm too depressed to talk to you")
                .npc("poor scruffy...")
                .player("yeah, poor scruffy!")
                .start();
            return;
        }
        if (evil() && has(POISONED)) {
            new Conversation(p, npc)
                .player("hello are you ok?")
                .npc("no i'm not ok")
                .npc("those animals slaughtered my precious scruffy")
                .npc("i'll never recover")
                .npc("i'm emotionaly scarred for life")
                .player("i'm sorry to hear that")
                .npc("don't be sorry it's not your fault")
                .npc("just find those animals and punish them severely")
                .npc("before i get to them first")
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("hello madam")
            .npc("i hope you've found those awful holigans")
            .npc("I can't sleep at night")
            .player("i'm working on it madam")
            .npc("i don't know")
            .npc("there really are some strange folk around these parts")
            .start();
    }

    private void philipe(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            /* Jagex gave him the same lines on both endings. */
            new Conversation(p, npc)
                .player("hello philipe")
                .npc("i want more toys")
                .player("sorry i don't have any")
                .npc("i want sweets, gimme sweets")
                .player("no sorrry")
                .player("I don't have any sweets either")
                .npc("i hate you,i want my mum")
                .start();
            return;
        }
        if (!questStarted()) {
            new Conversation(p, npc)
                .player("hello there")
                .npc("what have you brought me?")
                .npc("I want some more toys")
                .player("I'm afraid i don't have any")
                .npc("toys, i want toys")
                .start();
            return;
        }
        if (has(ACCUSED)) {
            new Conversation(p, npc)
                .player("hello youngster")
                .npc("daddy say's you dont like Jones")
                .npc("Jones is nice")
                .npc("he brings me toys and sweets")
                .player("jones is a bad person philipe")
                .npc("you're a bad person")
                .npc("i don't like you")
                .player("ok")
                .start();
            return;
        }
        if (evil() && has(DEED_DONE)) {
            new Conversation(p, npc)
                .player("hello youngster")
                .npc("why are you still here?")
                .player("just looking around")
                .npc("have you got me some toys?")
                .player("no")
                .npc("then i don't like you")
                .player("that's a shame")
                .start();
            return;
        }
        if (has(MET_ALOMONE) || (!evil() && has(DEED_DONE))) {
            new Conversation(p, npc)
                .player("hello")
                .npc("mommy said your here to")
                .npc("kill all the nasty men")
                .npc("that come into our house")
                .player("something like that")
                .npc("can i watch?")
                .player("no")
                .start();
            return;
        }
        if (evil() && has(POISONED)) {
            new Conversation(p, npc)
                .player("hello youngster")
                .message("the boy looks very upset")
                .npc("someone killed scruffy")
                .npc("i liked scruffy")
                .npc("he never told me off")
                .player("that's unfortunate")
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("hello")
            .npc("i want more toys")
            .player("sorry i don't have any")
            .npc("i want sweets, gimme sweets")
            .player("no sorrry i don't have sweets either")
            .npc("i hate you, i want my mum")
            .start();
    }

    private void claus(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            if (cultist()) {
                new Conversation(p, npc)
                    .player("hello cook")
                    .npc("get out of my kitchen")
                    .npc("can't you tell your not welcome around here")
                    .start();
            } else {
                new Conversation(p, npc)
                    .player("hello cook")
                    .npc("well hello there traveller")
                    .npc("are we fit and well")
                    .player("yes i'm fine")
                    .npc("good to hear")
                    .start();
            }
            return;
        }
        if (!questStarted()) {
            new Conversation(p, npc)
                .player("hello")
                .npc("sorry i can't talk now")
                .npc("you would be amazed how many")
                .npc("meals this family can go through")
                .start();
            return;
        }
        if (evil() && has(MET_ALOMONE)) {
            new Conversation(p, npc)
                .player("hello there")
                .npc("i don't understand it")
                .npc("how could someone slip poison in my cooking")
                .npc("without me even noticing")
                .npc("I'll be lucky if the carnilleans don't fire me")
                .npc("those animals how could they do it")
                .npc("poor scruffy")
                .start();
            return;
        }
        if (evil() && has(POISONED)) {
            new Conversation(p, npc)
                .npc("hello there")
                .npc("caught any thieves yet?")
                .player("afraid not")
                .npc("keep at it")
                .start();
            return;
        }
        if (!evil() && has(DEED_DONE)) {
            new Conversation(p, npc)
                .player("hello there")
                .npc("hello, how are you today")
                .player("not bad thanks")
                .npc("good good")
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("hello")
            .npc("you're that chap they've asked to help get those nasty folk")
            .npc("that keep breaking in")
            .player("yep, that's me")
            .npc("well i wish the best of luck")
            .start();
    }

    private void cultMember(Npc npc) {
        Player p = getOwner();
        if (completed()) {
            if (cultist()) {
                new Conversation(p, npc)
                    .player("hello")
                    .npc("the traveller returns")
                    .npc("we are forever in your dept brave adventurer")
                    .npc("i bow before you")
                    .start();
            } else {
                new Conversation(p, npc)
                    .player("hello")
                    .npc("An outsider!")
                    .npc("how did you get in here?")
                    .npc("leave now fool or die")
                    .start();
            }
            return;
        }
        if (!evil()) {
            new Conversation(p, npc)
                .player("hello there")
                .npc("what, how did you get in here")
                .npc("leave now traveller")
                .start();
            return;
        }
        if (has(DEED_DONE)) {
            new Conversation(p, npc)
                .player("hello")
                .npc("you truly are our savior")
                .npc("you found the script of hazeel")
                .npc("now his injustice can be resolved")
                .start();
            return;
        }
        new Conversation(p, npc)
            .player("hi")
            .npc("hello, oh, are you new")
            .player("that's right")
            .npc("well it's good to have you on board")
            .npc("soon we should retrieved the sacred hazeel script")
            .npc("then at last we can bring are lord back from the dead")
            .start();
    }
}
