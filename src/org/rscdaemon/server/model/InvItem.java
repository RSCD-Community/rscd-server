/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.model;

import org.rscdaemon.server.entityhandling.EntityHandler;
import org.rscdaemon.server.entityhandling.defs.ItemDef;
import org.rscdaemon.server.entityhandling.defs.extras.ItemCookingDef;
import org.rscdaemon.server.entityhandling.defs.extras.ItemSmeltingDef;
import org.rscdaemon.server.entityhandling.defs.extras.ItemUnIdentHerbDef;
import org.rscdaemon.server.entityhandling.defs.extras.ItemWieldableDef;
import org.rscdaemon.server.model.Entity;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
public class InvItem
extends Entity
implements Comparable<InvItem> {
    private int amount;
    private boolean wielded = false;

    /*
     * For the definition reader (util/XmlObjects), which fills the fields
     * afterwards. XStream never called a constructor at all -- it allocated
     * through sun.reflect.ReflectionFactory, which is exactly the JDK internal
     * this project is getting off -- so a constructor that does nothing
     * reproduces the old behaviour precisely: every field starts at its default
     * and the document sets it.
     */
    public InvItem() {
    }

    public InvItem(int id) {
        this.setID(id);
        this.setAmount(1);
    }

    public InvItem(int id, int amount) {
        this.setID(id);
        this.setAmount(amount);
    }

    public ItemSmeltingDef getSmeltingDef() {
        return EntityHandler.getItemSmeltingDef(this.id);
    }

    public ItemCookingDef getCookingDef() {
        return EntityHandler.getItemCookingDef(this.id);
    }

    public ItemUnIdentHerbDef getUnIdentHerbDef() {
        return EntityHandler.getItemUnIdentHerbDef(this.id);
    }

    public ItemWieldableDef getWieldableDef() {
        return EntityHandler.getItemWieldableDef(this.id);
    }

    public ItemDef getDef() {
        return EntityHandler.getItemDef(this.id);
    }

    public boolean isWieldable() {
        return EntityHandler.getItemWieldableDef(this.id) != null;
    }

    public boolean isEdible() {
        return EntityHandler.getItemEdibleHeals(this.id) > 0;
    }

    public boolean isWielded() {
        return this.wielded;
    }

    public void setWield(boolean wielded) {
        this.wielded = wielded;
    }

    public void setAmount(int amount) {
        if (amount < 0) {
            amount = 0;
        }
        this.amount = amount;
    }

    public int getAmount() {
        return this.amount;
    }

    public boolean wieldingAffectsItem(InvItem i) {
        if (!i.isWieldable() || !this.isWieldable()) {
            return false;
        }
        for (int affected : this.getWieldableDef().getAffectedTypes()) {
            if (i.getWieldableDef().getType() != affected) continue;
            return true;
        }
        return false;
    }

    public int eatingHeals() {
        if (!this.isEdible()) {
            return 0;
        }
        return EntityHandler.getItemEdibleHeals(this.id);
    }

    public boolean equals(Object o) {
        if (o instanceof InvItem) {
            InvItem item = (InvItem)o;
            return item.getID() == this.getID();
        }
        return false;
    }

    @Override
    public int compareTo(InvItem item) {
        if (item.getDef().isStackable()) {
            return -1;
        }
        if (this.getDef().isStackable()) {
            return 1;
        }
        return item.getDef().getBasePrice() - this.getDef().getBasePrice();
    }
}

