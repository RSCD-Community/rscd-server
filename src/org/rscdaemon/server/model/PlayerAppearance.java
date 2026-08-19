/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.model;

import org.rscdaemon.server.util.DataConversions;
import org.rscdaemon.server.util.Formulae;

public class PlayerAppearance {
    private byte hairColour;
    private byte topColour;
    private byte trouserColour;
    private byte skinColour;
    private int head;
    private int body;

    public PlayerAppearance(int hairColour, int topColour, int trouserColour, int skinColour, int head, int body) {
        this.hairColour = (byte)hairColour;
        this.topColour = (byte)topColour;
        this.trouserColour = (byte)trouserColour;
        this.skinColour = (byte)skinColour;
        this.head = head;
        this.body = body;
    }

    public int getSprite(int pos) {
        switch (pos) {
            case 0: {
                return this.head;
            }
            case 1: {
                return this.body;
            }
            case 2: {
                return 3;
            }
        }
        return 0;
    }

    public int[] getSprites() {
        return new int[]{this.head, this.body, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    }

    public byte getHairColour() {
        return this.hairColour;
    }

    public byte getTopColour() {
        return this.topColour;
    }

    public byte getTrouserColour() {
        return this.trouserColour;
    }

    public byte getSkinColour() {
        return this.skinColour;
    }

    public boolean isValid() {
        if (!DataConversions.inArray(Formulae.headSprites, this.head) || !DataConversions.inArray(Formulae.bodySprites, this.body)) {
            return false;
        }
        if (this.hairColour < 0 || this.topColour < 0 || this.trouserColour < 0 || this.skinColour < 0) {
            return false;
        }
        return this.hairColour <= 9 && this.topColour <= 14 && this.trouserColour <= 14 && this.skinColour <= 4;
    }
}

