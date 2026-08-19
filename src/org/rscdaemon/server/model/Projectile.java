/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.model;

import org.rscdaemon.server.model.Mob;

public class Projectile {
    private Mob caster;
    private Mob victim;
    private int type;

    public Projectile(Mob caster, Mob victim, int type) {
        this.caster = caster;
        this.victim = victim;
        this.type = type;
    }

    public Mob getCaster() {
        return this.caster;
    }

    public Mob getVictim() {
        return this.victim;
    }

    public int getType() {
        return this.type;
    }
}

