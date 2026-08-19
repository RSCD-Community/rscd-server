/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.event;

import java.util.ArrayList;
import org.rscdaemon.server.event.DelayedEvent;
import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Item;
import org.rscdaemon.server.model.Mob;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.Projectile;
import org.rscdaemon.server.util.DataConversions;
import org.rscdaemon.server.util.Formulae;

public class RangeEvent
extends DelayedEvent {
    private Mob affectedMob;
    private boolean firstRun = true;

    public RangeEvent(Player owner, Mob affectedMob) {
        super(owner, 2000);
        this.affectedMob = affectedMob;
    }

    private Item getArrows(int id) {
        for (Item i : world.getTile(this.affectedMob.getLocation()).getItems()) {
            if (i.getID() != id || !i.visibleTo(this.owner) || i.isRemoved()) continue;
            return i;
        }
        return null;
    }

    public void run() {
        int bowID = this.owner.getRangeEquip();
        if (!this.owner.loggedIn() || this.affectedMob instanceof Player && !((Player)this.affectedMob).loggedIn() || this.affectedMob.getHits() <= 0 || !this.owner.checkAttack(this.affectedMob, true) || bowID < 0) {
            this.owner.resetRange();
            return;
        }
        if (this.owner.withinRange(this.affectedMob, Formulae.bowRange(bowID))) {
            if (this.owner.isFollowing()) {
                this.owner.resetFollowing();
            }
            if (!this.owner.finishedPath()) {
                this.owner.resetPath();
            }
        } else {
            this.owner.setFollowing(this.affectedMob);
            return;
        }
        boolean xbow = DataConversions.inArray(Formulae.xbowIDs, bowID);
        int arrowID = -1;
        for (int aID : xbow ? Formulae.boltIDs : Formulae.arrowIDs) {
            InvItem arrow;
            int slot = this.owner.getInventory().getLastIndexById(aID);
            if (slot < 0 || (arrow = this.owner.getInventory().get(slot)) == null) continue;
            arrowID = aID;
            int newAmount = arrow.getAmount() - 1;
            if (newAmount <= 0) {
                this.owner.getInventory().remove(slot);
                this.owner.getActionSender().sendInventory();
                break;
            }
            arrow.setAmount(newAmount);
            this.owner.getActionSender().sendUpdateItem(slot);
            break;
        }
        if (arrowID < 0) {
            this.owner.getActionSender().sendMessage("@pnk@ You have run out of " + (xbow ? "bolts" : "arrows") + ".");
            this.owner.resetRange();
            return;
        }
        if (this.affectedMob.isPrayerActivated(13)) {
            this.owner.getActionSender().sendMessage("@pnk@ Your missles have mystically been blocked.");
            this.owner.resetRange();
            return;
        }
        int damage = Formulae.calcRangeHit(this.owner, this.affectedMob, arrowID);
        if (!Formulae.looseArrow(damage)) {
            Item arrows = this.getArrows(arrowID);
            if (arrows == null) {
                world.registerItem(new Item(arrowID, this.affectedMob.getX(), this.affectedMob.getY(), 1, this.owner));
            } else {
                arrows.setAmount(arrows.getAmount() + 1);
            }
        }
        if (this.firstRun) {
            this.firstRun = false;
            if (this.affectedMob instanceof Player) {
                ((Player)this.affectedMob).getActionSender().sendMessage("@pnk@ " + this.owner.getUsername() + " is shooting at you!");
            }
        }
        Projectile projectile = new Projectile(this.owner, this.affectedMob, 2);
        this.affectedMob.setLastDamage(damage);
        int newHp = this.affectedMob.getHits() - damage;
        this.affectedMob.setHits(newHp);
        org.rscdaemon.server.model.PrayerEffects.applySmite(this.owner, this.affectedMob, damage);
        ArrayList<Player> playersToInform = new ArrayList<Player>();
        playersToInform.addAll(this.owner.getViewArea().getPlayersInView());
        playersToInform.addAll(this.affectedMob.getViewArea().getPlayersInView());
        for (Player p : playersToInform) {
            p.informOfProjectile(projectile);
            p.informOfModifiedHits(this.affectedMob);
        }
        if (this.affectedMob instanceof Player) {
            Player affectedPlayer = (Player)this.affectedMob;
            affectedPlayer.getActionSender().sendStat(3);
        }
        this.owner.getActionSender().sendSound("shoot");
        this.owner.setArrowFired();
        // Told after the damage and before the death, so a quest counting what
        // has hit an npc can answer refusesKill() on this same shot. The Fire
        // warrior of lesarkus needs the ice arrow that opens him up counted
        // before the arrow that finishes him is allowed to.
        if (this.affectedMob instanceof Npc) {
            this.owner.getQuestManager().triggerRanged((Npc)this.affectedMob, arrowID, damage);
        }
        if (newHp <= 0) {
            this.affectedMob.killedBy(this.owner, false);
            // A ranged kill takes all four shares, and none of it goes to hits.
            this.owner.incExpQuarters(4, Formulae.combatExperienceQuarters(this.affectedMob) * 4, true);
            this.owner.getActionSender().sendStat(4);
            this.owner.resetRange();
        } else if (this.affectedMob instanceof Npc) {
            ((Npc) this.affectedMob).retaliate(this.owner);
        }
    }

    public Mob getAffectedMob() {
        return this.affectedMob;
    }

    public boolean equals(Object o) {
        if (o instanceof RangeEvent) {
            RangeEvent e = (RangeEvent)o;
            return e.belongsTo(this.owner);
        }
        return false;
    }
}

