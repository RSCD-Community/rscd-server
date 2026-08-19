/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.entityhandling.defs;

import org.rscdaemon.server.entityhandling.defs.EntityDef;

public class ItemDef
extends EntityDef {
    public String command;
    public int basePrice;
    public int sprite;
    public boolean stackable;
    public boolean wieldable;
    public int pictureMask;

    public String getCommand() {
        return this.command;
    }

    public int getSprite() {
        return this.sprite;
    }

    public int getBasePrice() {
        return this.basePrice;
    }

    public boolean isStackable() {
        return this.stackable;
    }

    public boolean isWieldable() {
        return this.wieldable;
    }

    public int getPictureMask() {
        return this.pictureMask;
    }
}

