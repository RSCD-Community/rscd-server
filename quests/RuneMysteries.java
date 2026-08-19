import org.rscdaemon.server.model.Entity;
import org.rscdaemon.server.model.EssenceMine;
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
 * Rune mysteries.
 *
 * The gate quest for Runecrafting, and the first quest past Jagex's RSC list
 * (id 50). RSC never had it -- it shipped with RS2's Runecrafting -- so unlike
 * every quest before it there is no recorded transcript to restore. The shape
 * is RS2's (Duke's talisman, Sedridor, Aubury's errand, the mine as the prize);
 * every line of dialogue is written fresh in the period register and is ours.
 *
 * The errand: the Duke of Lumbridge digs up an air talisman (his branch lives
 * in DragonSlayer.java -- the conversation dispatcher gives an npc one owner
 * -- and reports here through note()). Sedridor in the tower basement trades
 * it for a research package bound for Aubury's rune shop in Varrock; Aubury
 * (his own handler, same reason -- he keeps a shop) swaps it for his notes;
 * Sedridor reads them, declares the mine safe to open, and hands the talisman
 * back with the teleport privilege.
 *
 * Completion is what every teleporter wizard consults before casting a player
 * into the mine -- see model/EssenceMine.java for who they are and how the
 * way home is remembered.
 */
public class RuneMysteries extends Quest {

    public final static int UID = Quests.RUNE_MYSTERIES;

    private static final int TALISMAN_GIVEN = 1;  /* Duke handed it over */
    private static final int PACKAGE_GIVEN = 2;   /* Sedridor's parcel is out */
    private static final int NOTES_GIVEN = 3;     /* Aubury has answered */
    private static final int FINISHED = 4;

    private static final int SEDRIDOR = 799;
    private static final int DISTENTOR = 800;
    private static final int BRIMSTAIL = 590;

    private static final int AIR_TALISMAN = 1291;
    private static final int PACKAGE = 1306;
    private static final int NOTES = 1307;

    public RuneMysteries(Player owner, Integer uid) {
        super(owner, UID);
    }

    public void define() {
        setName("Rune mysteries");
        setFinalStage(FINISHED);
        associateNpc(SEDRIDOR);
        associateNpc(DISTENTOR);
        associateNpc(BRIMSTAIL);

        describe("The Duke of Lumbridge has unearthed a strange talisman. The wizards of the tower would give a great deal to know where it came from.");
        setStartPoint("Lumbridge Castle");
        setSpeakTo("The Duke of Lumbridge");
        setMissionLength("Short");
        rewardItem(AIR_TALISMAN, 1);
        rewardOther("Access to the rune essence mine");
    }

    public void completeQuest() {
        grantRewards();
        getOwner().getActionSender().sendMessage("Well done.You have completed the Rune mysteries quest");
    }

    public boolean reached(String key) {
        if (key.equals("started")) {
            return getStage() >= TALISMAN_GIVEN;
        }
        if (key.equals("package")) {
            return getStage() == PACKAGE_GIVEN;
        }
        if (key.equals("notes")) {
            return getStage() >= NOTES_GIVEN;
        }
        return false;
    }

    public void note(String key) {
        if (key.equals("duke-sent") && getStage() < TALISMAN_GIVEN) {
            setStage(TALISMAN_GIVEN);
        }
        if (key.equals("aubury-swapped") && getStage() == PACKAGE_GIVEN) {
            setStage(NOTES_GIVEN);
        }
    }

    public void triggerEntity(QuestTrigger trigger, Entity entity) {
        if (trigger != QuestTrigger.NPC_TALK || !(entity instanceof Npc)) {
            return;
        }
        Npc npc = (Npc) entity;
        switch (npc.getID()) {
            case SEDRIDOR:  talkToSedridor(npc);  break;
            case DISTENTOR: talkToDistentor(npc); break;
            case BRIMSTAIL: talkToBrimstail(npc); break;
        }
    }

    // ------------------------------------------------------------ sedridor --

    private void talkToSedridor(Npc npc) {
        Player p = getOwner();
        Conversation c = new Conversation(p, npc);
        if (completed()) {
            offerTeleport(c, "Ah, my fellow keeper of the mysteries", SEDRIDOR);
            c.start();
            return;
        }
        switch (getStage()) {
            case TALISMAN_GIVEN:
                if (p.getInventory().countId(AIR_TALISMAN) < 1) {
                    c.npc("You have the look of an errand runner without an errand")
                     .npc("Come back when you carry what you were sent with");
                    break;
                }
                c.player("The Duke of Lumbridge sent me. His workmen dug this up")
                 .npc("Let me see that")
                 .npc("By the founders... a talisman of elemental air")
                 .npc("I have theorised these existed but never held one")
                 .npc("This changes everything about our essence research")
                 .npc("Will you carry my research package to Aubury?")
                 .npc("He keeps the rune shop in south east Varrock")
                 .npc("He must see this before I dare say more")
                 .then(new Effect() {
                     public void run(Conversation c) {
                         Player pl = c.getPlayer();
                         pl.getInventory().remove(AIR_TALISMAN, 1);
                         pl.getInventory().add(new InvItem(PACKAGE, 1));
                         pl.getActionSender().sendInventory();
                         setStage(PACKAGE_GIVEN);
                     }
                 })
                 .message("Sedridor takes the talisman and hands you a research package");
                break;
            case PACKAGE_GIVEN:
                if (p.getInventory().countId(PACKAGE) < 1) {
                    c.npc("You've lost my package? Careless")
                     .npc("Fortunately I keep copies of everything")
                     .then(new Effect() {
                         public void run(Conversation c) {
                             c.getPlayer().getInventory().add(new InvItem(PACKAGE, 1));
                             c.getPlayer().getActionSender().sendInventory();
                         }
                     })
                     .message("Sedridor hands you another research package");
                    break;
                }
                c.npc("Aubury awaits my package in south east Varrock")
                 .npc("The mysteries will keep until he has read it");
                break;
            case NOTES_GIVEN:
                if (p.getInventory().countId(NOTES) < 1) {
                    c.npc("Where are Aubury's notes? Without them we have nothing")
                     .npc("Return to his shop and ask him again");
                    break;
                }
                c.player("Aubury sent you these notes")
                 .npc("At last. Let me read them")
                 .npc("Remarkable. His findings agree with mine entirely")
                 .npc("The talisman is a key, and we have found what it unlocks:")
                 .npc("a mine of raw rune essence, far beyond the reach of any road")
                 .npc("Take back the talisman, you have more than earned it")
                 .npc("And speak to me, or Aubury, whenever you wish to visit the mine")
                 .then(new Effect() {
                     public void run(Conversation c) {
                         Player pl = c.getPlayer();
                         pl.getInventory().remove(NOTES, 1);
                         pl.getActionSender().sendInventory();
                         setStage(getFinalStage());
                     }
                 });
                break;
            default:
                c.npc("Welcome to the wizards tower, traveller")
                 .npc("I am Sedridor, head of essence research here")
                 .npc("Strange forces are stirring, but I may say no more for now");
                break;
        }
        c.start();
    }

    // --------------------------------------------------- the other wizards --

    private void talkToDistentor(Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        if (completed()) {
            offerTeleport(c, "Ah, one of Sedridor's essence carriers", DISTENTOR);
        } else {
            c.npc("Welcome to the magic guild of Yanille")
             .npc("Only those versed in the deeper mysteries find much use for me");
        }
        c.start();
    }

    /**
     * Brimstail chants in his cave, and everything ever recorded of him is a
     * refusal to be interrupted -- that refusal stays for anyone outside the
     * mysteries (he came off FlavorNpcs when this quest took him over, and
     * this branch keeps its content). For the initiated the chant turns out to
     * have been essence work all along.
     */
    private void talkToBrimstail(Npc npc) {
        Conversation c = new Conversation(getOwner(), npc);
        if (completed()) {
            offerTeleport(c, "Hm? Oh, a fellow student of the essence", BRIMSTAIL);
        } else {
            c.message("Brimstail is chanting and does not respond");
        }
        c.start();
    }

    private void offerTeleport(Conversation c, String greeting, final int wizard) {
        c.npc(greeting)
         .options(new Choice("Can you teleport me to the rune essence mine?",
                             "Just passing through") {
             public void picked(int option, Conversation c) {
                 if (option != 0) {
                     c.npc("Safe travels then");
                     return;
                 }
                 c.npc("Senventior disthine molenko!")
                  .then(new Effect() {
                      public void run(Conversation c) {
                          c.stop();
                          EssenceMine.teleportIn(c.getPlayer(), wizard);
                      }
                  });
             }
         });
    }
}
