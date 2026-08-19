/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.model;

import org.rscdaemon.server.model.Player;

public abstract class MenuHandler {
    protected Player owner;
    protected String[] options;

    public MenuHandler(String[] options) {
        this.options = options;
    }

    public final void setOwner(Player owner) {
        this.owner = owner;
    }

    public final String getOption(int index) {
        if (index < 0 || index >= this.options.length) {
            return null;
        }
        return this.options[index];
    }

    public final String[] getOptions() {
        return this.options;
    }

    public abstract void handleReply(int var1, String var2);
}

