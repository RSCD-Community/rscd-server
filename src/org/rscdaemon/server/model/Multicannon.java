package org.rscdaemon.server.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.rscdaemon.server.event.DelayedEvent;
import org.rscdaemon.server.quest.Quests;
import org.rscdaemon.server.util.DataConversions;
import org.rscdaemon.server.util.Formulae;

/**
 * The dwarf multicannon, from four items on the floor to a thing that fires.
 *
 * The cannon is the one ranged weapon a player does not hold. It is built on
 * the ground out of four parts bought from the Dwarf Cannon engineer, fed from
 * the player's own inventory, and it picks its own targets:
 *
 *     set down Dwarf cannon base 1032       -> object 946
 *     use Dwarf cannon stand 1033 on it     -> object 947
 *     use Dwarf cannon barrels 1034 on it   -> object 948
 *     use Dwarf cannon furnace 1035 on it   -> object 943, ready
 *
 * and 'fire' on 943 starts it. The four object ids, their 'pick up' and 'fire'
 * commands and the four item ids were all already in the definitions; nothing
 * here invents an id.
 *
 * What the instruction manual states, and this class obeys:
 *
 *   - "The cannon will only fire when monsters are available to target."
 *   - "If you are carrying enough ammo the multi cannon will fire up to 20
 *     rounds before stopping."  Ammo is spent out of the inventory, not loaded
 *     into the cannon, and twenty rounds is one pull of the trigger.
 *   - "The cannon will automatically target non friendly creatures."
 *   - "It is only possible to operate one cannon at a time."
 *   - "firing the cannon is exhausting work and can leave adventurers too
 *     fatigued to carry the cannon, so rest well before using" -- so firing
 *     tires the player, and a player who is spent cannot lift it again.
 *   - "You should be well rested before attempting to lift the heavy cannon."
 *
 * Two numbers the manual does not give:
 *
 *   - Range. Five tiles, which is the reach every other missile in this server
 *     already has (RangeEvent asks withinRange(mob, 5)). Picking the same reach
 *     rather than a second, invented one keeps the cannon inside the rules the
 *     rest of ranged combat plays by.
 *
 *   - Rate. Never recorded for Classic. 1200ms a sweep, which is RS2's two-tick
 *     cannon; it is the closest thing to a source that exists, and against one
 *     target it makes the twenty rounds a twenty-four second burst, which
 *     matches "in short bursts it's very effective" better than a bow's 2000ms
 *     would. Against a crowd the same twenty rounds go a good deal faster,
 *     because a sweep spends one ball per enemy in range -- see FireEvent.
 *
 * The maximum hit is a fit, not a guess. Two points were measured in the live
 * game and written down: 16 at 52 Ranged, 21 at 77. The line through them is
 * exactly (ranged + 28) / 5 in integers -- 80/5 = 16, 105/5 = 21 -- so that is
 * what is used, and it is right wherever the two recorded points are right.
 */
public class Multicannon {

    /** The four parts, in the order they go on. */
    public static final int BASE = 1032, STAND = 1033, BARRELS = 1034, FURNACE = 1035;
    public static final int CANNON_BALL = 1041, AMMO_MOULD = 1057, MANUAL = 1073;

    /** The same four, as they stand in the world. */
    public static final int OBJ_BASE = 946, OBJ_STAND = 947, OBJ_BARRELS = 948, OBJ_READY = 943;

    /** Rounds in one pull of the trigger, per the instruction manual. */
    private static final int BURST = 20;

    /** How far the cannon can see, and how often it fires. See the header. */
    private static final int RANGE = 5;
    private static final int ROUND_MS = 1200;

    /** Sixth of the seven sprites loaded at 3160. See shoot(). */
    private static final int PROJECTILE_SPIKED_BALL = 5;

    /**
     * One cannon per player, and the reverse lookup the object handlers need.
     *
     * Objects in this server are global -- every player in the region sees the
     * same one -- so the cannon has to remember whose it is, or anyone walking
     * past could pick it up.
     */
    private static final Map<Player, Multicannon> byOwner = new HashMap<Player, Multicannon>();

    private final Player owner;
    private GameObject object;
    /** What has been fitted so far, as the object id standing in the world. */
    private int stage;
    private FireEvent firing;

    private Multicannon(Player owner, GameObject object) {
        this.owner = owner;
        this.object = object;
        this.stage = OBJ_BASE;
    }

    // ------------------------------------------------------------ lookups --

    public static Multicannon of(Player p) {
        return byOwner.get(p);
    }

    /** Whose cannon this object is, or null if it is nobody's. */
    public static Multicannon at(GameObject o) {
        for (Multicannon c : byOwner.values()) {
            if (c.object == o) {
                return c;
            }
        }
        return null;
    }

    /** True for the four ids this class owns, so the handlers can hand over. */
    public static boolean isCannonObject(int id) {
        return id == OBJ_BASE || id == OBJ_STAND || id == OBJ_BARRELS || id == OBJ_READY;
    }

    public Player getOwner() {
        return this.owner;
    }

    public boolean isComplete() {
        return this.stage == OBJ_READY;
    }

    // ------------------------------------------------------------ setting --

    /**
     * Set the base down where the player stands.
     *
     * Refused while in combat, on an occupied tile, and while the player
     * already has one standing somewhere -- the warranty is explicit that only
     * one can be operated at a time.
     */
    public static void setDown(Player p) {
        if (!p.getQuestManager().completed(Quests.DWARF_CANNON)) {
            p.getActionSender().sendMessage("@gry@ You don't know how to set this up.");
            return;
        }
        if (p.inCombat() || p.isBusy()) {
            p.getActionSender().sendMessage("@gry@ You can't set the cannon up during combat.");
            return;
        }
        if (byOwner.containsKey(p)) {
            p.getActionSender().sendMessage("@gry@ You can only operate one cannon at a time.");
            return;
        }
        ActiveTile tile = World.getWorld().getTile(p.getLocation());
        if (tile == null || tile.hasGameObject()) {
            p.getActionSender().sendMessage("@gry@ There isn't room to set the cannon up here.");
            return;
        }
        if (p.getInventory().remove(BASE, 1) < 0) {
            return;
        }
        GameObject o = new GameObject(p.getLocation(), OBJ_BASE, 0, 0);
        World.getWorld().registerGameObject(o);
        byOwner.put(p, new Multicannon(p, o));
        p.getActionSender().sendInventory();
        p.getActionSender().sendMessage("@pnk@ You place the cannon base on the ground.");
    }

    /**
     * Fit the next part.
     *
     * Returns false when the item is not a cannon part or does not belong on
     * this cannon yet, so the caller can carry on and let the item mean
     * whatever else it means.
     */
    public boolean fit(Player p, int itemId) {
        if (p != this.owner) {
            p.getActionSender().sendMessage("@gry@ That isn't your cannon.");
            return true;
        }
        int wants;
        int becomes;
        String said;
        switch (this.stage) {
            case OBJ_BASE:
                wants = STAND;
                becomes = OBJ_STAND;
                said = "You add the stand to the base.";
                break;
            case OBJ_STAND:
                wants = BARRELS;
                becomes = OBJ_BARRELS;
                said = "You add the barrels to the stand.";
                break;
            case OBJ_BARRELS:
                wants = FURNACE;
                becomes = OBJ_READY;
                said = "You add the furnace. The cannon is ready to fire.";
                break;
            default:
                p.getActionSender().sendMessage("@gry@ The cannon is already built.");
                return true;
        }
        if (itemId != wants) {
            if (itemId != STAND && itemId != BARRELS && itemId != FURNACE) {
                return false;
            }
            p.getActionSender().sendMessage("@gry@ That doesn't go on next.");
            return true;
        }
        if (p.getInventory().remove(itemId, 1) < 0) {
            return true;
        }
        this.replaceWith(becomes);
        p.getActionSender().sendInventory();
        p.getActionSender().sendMessage("@pnk@ " + said);
        return true;
    }

    private void replaceWith(int id) {
        Point at = this.object.getLocation();
        World.getWorld().unregisterGameObject(this.object);
        this.object = new GameObject(at, id, 0, 0);
        World.getWorld().registerGameObject(this.object);
        this.stage = id;
    }

    // ------------------------------------------------------------- firing --

    public void fire(Player p) {
        if (p != this.owner) {
            p.getActionSender().sendMessage("@gry@ That isn't your cannon.");
            return;
        }
        if (!this.isComplete()) {
            p.getActionSender().sendMessage("@gry@ The cannon isn't finished yet.");
            return;
        }
        if (p.inCombat()) {
            p.getActionSender().sendMessage("@gry@ You can't fire the cannon during combat.");
            return;
        }
        if (this.firing != null) {
            p.getActionSender().sendMessage("@gry@ The cannon is already firing.");
            return;
        }
        if (p.getInventory().countId(CANNON_BALL) < 1) {
            p.getActionSender().sendMessage("@gry@ You don't have any cannon balls.");
            return;
        }
        this.firing = new FireEvent();
        World.getWorld().getDelayedEventHandler().add(this.firing);
    }

    /** Everything in reach the owner would be allowed to shoot at. */
    private List<Npc> targets() {
        List<Npc> out = new ArrayList<Npc>();
        for (Npc n : this.owner.getViewArea().getNpcsInView()) {
            if (n.getHits() <= 0 || n.isRemoved()) {
                continue;
            }
            if (!n.getDef().isAttackable()) {
                continue;
            }
            if (!this.inReach(n)) {
                continue;
            }
            out.add(n);
        }
        return out;
    }

    /** Measured from the cannon, not from the player -- it does its own aiming. */
    private boolean inReach(Npc n) {
        int dx = this.object.getLocation().getX() - n.getLocation().getX();
        int dy = this.object.getLocation().getY() - n.getLocation().getY();
        if (dx < 0) {
            dx = -dx;
        }
        if (dy < 0) {
            dy = -dy;
        }
        return dx <= RANGE && dy <= RANGE;
    }

    private void stopFiring() {
        if (this.firing != null) {
            this.firing.stop();
            this.firing = null;
        }
    }

    private class FireEvent extends DelayedEvent {
        private int fired;

        FireEvent() {
            super(Multicannon.this.owner, ROUND_MS);
        }

        public void run() {
            Player p = Multicannon.this.owner;
            if (!p.loggedIn() || Multicannon.this.object == null || !Multicannon.this.isComplete()) {
                Multicannon.this.stopFiring();
                return;
            }
            if (this.fired >= BURST) {
                p.getActionSender().sendMessage("@pnk@ The cannon stops firing.");
                Multicannon.this.stopFiring();
                return;
            }
            if (p.getInventory().countId(CANNON_BALL) < 1) {
                p.getActionSender().sendMessage("@gry@ Your cannon is out of ammo.");
                Multicannon.this.stopFiring();
                return;
            }
            List<Npc> in = Multicannon.this.targets();
            if (in.isEmpty()) {
                /* "The cannon will only fire when monsters are available to
                   target." It waits rather than winding down, so walking
                   something into range restarts the noise without a new pull
                   of the trigger. */
                return;
            }
            /*
             * A sweep, not a single shot. The cannon "will search for enemies
             * in a flash, then fire at least once per enemy, automatically
             * using ammo in the process" -- so one turn of the barrels is a
             * volley across everything standing in range, and each ball spent
             * is one of the twenty rounds the manual promises, not each turn.
             * "Enemies that are moving may be struck more than one time" then
             * needs no code of its own: something walking through the arc is
             * simply still in range on the next sweep.
             */
            for (Npc victim : in) {
                if (this.fired >= BURST) {
                    break;
                }
                if (p.getInventory().remove(CANNON_BALL, 1) < 0) {
                    break;
                }
                ++this.fired;
                Multicannon.this.shoot(victim);
            }
            p.getActionSender().sendInventory();
        }
    }

    /**
     * One round.
     *
     * Damage, the projectile, the death and the experience follow RangeEvent
     * exactly, so a monster killed by the cannon dies the same way and drops
     * the same things as one killed by a bow. The one difference is that the
     * cannon does its own aiming, and the manual's warning about fatigue is
     * charged to the player who pulled the trigger.
     *
     * The line is drawn from the player, not from the cannon, and that is a
     * protocol fact rather than a choice made here. A projectile rides inside
     * the caster's own mob update block -- index, then victim kind, then
     * sprite, then victim index -- so the only thing that can carry one is a
     * mob the client is already tracking. The cannon is scenery (object 943)
     * and scenery has no such block, so there is no way to name it as the
     * origin. Jagex had the same constraint: every reimplementation on hand
     * fires this cannon from the player for exactly this reason.
     *
     * What is fixable is the sprite. The client loads seven of them at 3160
     * and they are, in order: orb, magic, ranged, gnomeball, skull, spiked
     * ball, blank. This used to send 2, the arrow -- which is why it looked
     * like a bow shot. The cannonball's own examine text is "A heavy metal
     * spiked ball", so 5 is not a guess.
     */
    private void shoot(Npc victim) {
        int max = (this.owner.getCurStat(4) + 28) / 5;
        int damage = DataConversions.random(0, max);

        victim.setLastDamage(damage);
        int newHp = victim.getHits() - damage;
        victim.setHits(newHp);

        Projectile shot = new Projectile(this.owner, victim, PROJECTILE_SPIKED_BALL);
        List<Player> toInform = new ArrayList<Player>();
        toInform.addAll(this.owner.getViewArea().getPlayersInView());
        toInform.addAll(victim.getViewArea().getPlayersInView());
        for (Player p : toInform) {
            p.informOfProjectile(shot);
            p.informOfModifiedHits(victim);
        }
        this.owner.getActionSender().sendSound("shoot");

        /* Firing is "exhausting work". One point a round, on the same 0-100
           scale everything else in this server uses, is what makes twenty
           rounds something a rested player can do and a tired one cannot. */
        if (this.owner.getFatigue() < 100) {
            this.owner.setFatigue(this.owner.getFatigue() + 1);
            this.owner.getActionSender().sendFatigue();
        }

        this.owner.getQuestManager().triggerRanged(victim, CANNON_BALL, damage);

        if (newHp <= 0) {
            victim.killedBy(this.owner, false);
            // Paid like any other ranged kill; cannon xp itself is unattested.
            this.owner.incExpQuarters(4, Formulae.combatExperienceQuarters(victim) * 4, true);
            this.owner.getActionSender().sendStat(4);
        }
    }

    // ------------------------------------------------------------ reading --

    private static final String[] PAGES = {
        "Constructing the cannon", "Making ammo", "firing the cannon", "warrenty"
    };

    /**
     * The instruction manual, all four pages.
     *
     * Jagex opened a parchment interface per page. The server cannot open one
     * -- the same wall Nulodion's notes ran into -- so the pages are printed
     * into the chat box instead, word for word from the recorded transcript.
     * Their spelling is Jagex's, "warrenty" and "dwarwven" and the doubled
     * "the the" included, and is left alone.
     */
    public static void read(final Player p) {
        p.getActionSender().sendMessage("@pnk@ the manual has four pages");
        p.setMenuHandler(new MenuHandler(PAGES) {

            public void handleReply(int option, String reply) {
                Player r = this.owner;
                switch (option) {
                    case 0:
                        r.getActionSender().sendMessage("@yel@Constructing the cannon");
                        r.getActionSender().sendMessage("To construct the cannon, firstly set down Dwarf cannon base on the ground.");
                        r.getActionSender().sendMessage("Next add the Dwarf cannon stand to the Dwarf cannon base.");
                        r.getActionSender().sendMessage("Then add the the Dwarf cannon barrels (this can be tiring work).");
                        r.getActionSender().sendMessage("Last of all add the Dwarf cannon furnace which powers the cannon.");
                        r.getActionSender().sendMessage("You should now have a fully set up dwarf multi cannon ready to splat some nasty creatures.");
                        r.getActionSender().sendMessage("@red@WARNING: You should be well rested before attempting to lift the heavy cannon");
                        break;
                    case 1:
                        r.getActionSender().sendMessage("@yel@Making ammo");
                        r.getActionSender().sendMessage("The ammo for the cannon is made from steel bars.");
                        r.getActionSender().sendMessage("Firstly you must heat up a steel bar in a furnace");
                        r.getActionSender().sendMessage("Then pour the molten steel into a cannon ammo mould");
                        r.getActionSender().sendMessage("You should now have a ready to fire multi cannon ball");
                        break;
                    case 2:
                        r.getActionSender().sendMessage("@yel@Firing the cannon");
                        r.getActionSender().sendMessage("The cannon will only fire when monsters are available to target.");
                        r.getActionSender().sendMessage("If you are carrying enough ammo the multi cannon will fire up to 20 rounds before stopping.");
                        r.getActionSender().sendMessage("The cannon will automatically target non friendly creatures.");
                        r.getActionSender().sendMessage("@red@Warning - firing the cannon is exhausting work and can leave adventurers too fatigued to carry the cannon, so rest well before using");
                        break;
                    default:
                        r.getActionSender().sendMessage("@red@Dwarf cannon warrenty");
                        r.getActionSender().sendMessage("If your cannon is stolen or lost, after or during being set up, the dwarf engineer will happily replace the parts");
                        r.getActionSender().sendMessage("However cannon parts that were given away or dropped will not be replaced for free");
                        r.getActionSender().sendMessage("It is only possible to operate one cannon at a time");
                        r.getActionSender().sendMessage("by order of the dwarwven black guard");
                        break;
                }
            }
        });
        p.getActionSender().sendMenu(PAGES);
    }

    // ----------------------------------------------------------- taking up --

    /**
     * Take the cannon back apart.
     *
     * Everything fitted so far comes back, which is what 'pick up' on each of
     * the three part objects means -- a half-built cannon is not a loss. The
     * manual's warning is honoured here: lifting it is heavy work and a player
     * with nothing left cannot do it.
     */
    public void pickUp(Player p) {
        if (p != this.owner) {
            p.getActionSender().sendMessage("@gry@ That isn't your cannon.");
            return;
        }
        if (p.getFatigue() >= 100) {
            p.getActionSender().sendMessage("@gry@ You're too tired to lift the cannon.");
            return;
        }
        int[] back = this.partsFitted();
        if (Inventory.MAX_SIZE - p.getInventory().size() < back.length) {
            p.getActionSender().sendMessage("@gry@ You don't have room for all the cannon parts.");
            return;
        }
        this.remove();
        for (int id : back) {
            p.getInventory().add(new InvItem(id, 1));
        }
        p.getActionSender().sendInventory();
        p.getActionSender().sendMessage("@pnk@ You pick up the cannon.");
    }

    private int[] partsFitted() {
        switch (this.stage) {
            case OBJ_STAND:
                return new int[] { BASE, STAND };
            case OBJ_BARRELS:
                return new int[] { BASE, STAND, BARRELS };
            case OBJ_READY:
                return new int[] { BASE, STAND, BARRELS, FURNACE };
            default:
                return new int[] { BASE };
        }
    }

    /** Take it out of the world without giving anything back. */
    private void remove() {
        this.stopFiring();
        if (this.object != null) {
            World.getWorld().unregisterGameObject(this.object);
            this.object = null;
        }
        byOwner.remove(this.owner);
    }

    /**
     * Players whose cannon was taken off them rather than picked up.
     *
     * The warranty draws the line precisely: "If your cannon is stolen or lost,
     * after or during being set up, the dwarf engineer will happily replace the
     * parts / However cannon parts that were given away or dropped will not be
     * replaced for free". So the claim is not "do you have a cannon?" -- it is
     * "did you lose one that was standing?", and only the server knows that.
     *
     * Held by username hash and not saved. A restart forgets outstanding
     * claims, which is the wrong way round for a player who is owed one, but it
     * is the safe way round: the alternative is a claim that survives into a
     * database it was never designed for and pays out twice.
     */
    private static final java.util.Set<Long> owed = new java.util.HashSet<Long>();

    public static boolean isOwedReplacement(Player p) {
        return owed.contains(Long.valueOf(p.getUsernameHash()));
    }

    public static void replacementGiven(Player p) {
        owed.remove(Long.valueOf(p.getUsernameHash()));
    }

    /**
     * The owner has gone.
     *
     * A cannon left standing after its owner logs out belongs to nobody -- no
     * one else can lift it -- so it goes with them, and they are owed the free
     * replacement the warranty promises for one lost while it was set up.
     */
    public static void ownerLeft(Player p) {
        Multicannon c = byOwner.get(p);
        if (c != null) {
            c.remove();
            owed.add(Long.valueOf(p.getUsernameHash()));
        }
    }
}
