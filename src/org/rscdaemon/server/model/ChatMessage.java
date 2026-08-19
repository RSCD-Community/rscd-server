/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.model;

import org.rscdaemon.server.model.Mob;
import org.rscdaemon.server.util.DataConversions;

public class ChatMessage {
    private Mob sender;
    private byte[] message;
    private Mob recipient = null;

    public ChatMessage(Mob sender, byte[] message) {
        this.sender = sender;
        this.message = message;
    }

    public ChatMessage(Mob sender, String message, Mob recipient) {
        this.sender = sender;
        this.message = DataConversions.stringToByteArray(message);
        this.recipient = recipient;
    }

    public Mob getRecipient() {
        return this.recipient;
    }

    public Mob getSender() {
        return this.sender;
    }

    public byte[] getMessage() {
        return this.message;
    }

    public int getLength() {
        return this.message.length;
    }
}

