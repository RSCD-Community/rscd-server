/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packetbuilder;

import org.rscdaemon.server.net.Packet;
import org.rscdaemon.server.util.Logger;

/**
 * Builds one outbound packet: a growable payload with typed appends, plus
 * RSC's bit-packed writes (addBits) used by the position/appearance update
 * packets, where mob deltas are packed a few bits at a time rather than
 * byte-aligned. toPacket() snapshots the payload; setBare marks the two
 * pre-protocol frames that go out without an id/length header.
 */
public class StaticPacketBuilder {
    protected static final int DEFAULT_SIZE = 32;
    protected byte[] payload;
    protected int curLength;
    protected int bitPosition = 0;
    protected boolean bare = false;
    protected static int[] bitmasks = new int[]{0, 1, 3, 7, 15, 31, 63, 127, 255, 511, 1023, 2047, 4095, 8191, 16383, Short.MAX_VALUE, 65535, 131071, 262143, 524287, 1048575, 0x1FFFFF, 0x3FFFFF, 0x7FFFFF, 0xFFFFFF, 0x1FFFFFF, 0x3FFFFFF, 0x7FFFFFF, 0xFFFFFFF, 0x1FFFFFFF, 0x3FFFFFFF, Integer.MAX_VALUE, -1};

    public StaticPacketBuilder() {
        this(32);
    }

    public StaticPacketBuilder(int capacity) {
        this.payload = new byte[capacity];
    }

    private void ensureCapacity(int minimumCapacity) {
        if (minimumCapacity >= this.payload.length) {
            this.expandCapacity(minimumCapacity);
        }
    }

    private void expandCapacity(int minimumCapacity) {
        int newCapacity = (this.payload.length + 1) * 2;
        if (newCapacity < 0) {
            newCapacity = Integer.MAX_VALUE;
        } else if (minimumCapacity > newCapacity) {
            newCapacity = minimumCapacity;
        }
        int oldLength = this.curLength;
        if (oldLength > this.payload.length) {
            oldLength = this.payload.length;
        }
        byte[] newPayload = new byte[newCapacity];
        try {
            System.arraycopy(this.payload, 0, newPayload, 0, oldLength);
        }
        catch (Exception e) {
            Logger.error(e);
        }
        this.payload = newPayload;
    }

    public StaticPacketBuilder setBare(boolean bare) {
        this.bare = bare;
        return this;
    }

    public StaticPacketBuilder addBits(int value, int numBits) {
        int bytePos = this.bitPosition >> 3;
        int bitOffset = 8 - (this.bitPosition & 7);
        this.bitPosition += numBits;
        this.curLength = (this.bitPosition + 7) / 8;
        this.ensureCapacity(this.curLength);
        while (numBits > bitOffset) {
            int n = bytePos;
            this.payload[n] = (byte)(this.payload[n] & ~bitmasks[bitOffset]);
            int n2 = bytePos++;
            this.payload[n2] = (byte)(this.payload[n2] | value >> numBits - bitOffset & bitmasks[bitOffset]);
            numBits -= bitOffset;
            bitOffset = 8;
        }
        if (numBits == bitOffset) {
            int n = bytePos;
            this.payload[n] = (byte)(this.payload[n] & ~bitmasks[bitOffset]);
            int n3 = bytePos;
            this.payload[n3] = (byte)(this.payload[n3] | value & bitmasks[bitOffset]);
        } else {
            int n = bytePos;
            this.payload[n] = (byte)(this.payload[n] & ~(bitmasks[numBits] << bitOffset - numBits));
            int n4 = bytePos;
            this.payload[n4] = (byte)(this.payload[n4] | (value & bitmasks[numBits]) << bitOffset - numBits);
        }
        return this;
    }

    public StaticPacketBuilder addBytes(byte[] data) {
        return this.addBytes(data, 0, data.length);
    }

    public StaticPacketBuilder addBytes(byte[] data, int offset, int len) {
        int newLength = this.curLength + len;
        this.ensureCapacity(newLength);
        System.arraycopy(data, offset, this.payload, this.curLength, len);
        this.curLength = newLength;
        return this;
    }

    public StaticPacketBuilder addByte(byte val) {
        return this.addByte(val, true);
    }

    private StaticPacketBuilder addByte(byte val, boolean checkCapacity) {
        if (checkCapacity) {
            this.ensureCapacity(this.curLength + 1);
        }
        this.payload[this.curLength++] = val;
        return this;
    }

    public StaticPacketBuilder addShort(int val) {
        this.ensureCapacity(this.curLength + 2);
        this.addByte((byte)(val >> 8), false);
        this.addByte((byte)val, false);
        return this;
    }

    public StaticPacketBuilder addInt(int val) {
        this.ensureCapacity(this.curLength + 4);
        this.addByte((byte)(val >> 24), false);
        this.addByte((byte)(val >> 16), false);
        this.addByte((byte)(val >> 8), false);
        this.addByte((byte)val, false);
        return this;
    }

    public StaticPacketBuilder addLong(long val) {
        this.addInt((int)(val >> 32));
        this.addInt((int)(val & 0xFFFFFFFFFFFFFFFFL));
        return this;
    }

    public Packet toPacket() {
        byte[] data = new byte[this.curLength];
        System.arraycopy(this.payload, 0, data, 0, this.curLength);
        return new Packet(null, data, this.bare);
    }
}

