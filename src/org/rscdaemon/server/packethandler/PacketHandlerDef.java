/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.packethandler;

public class PacketHandlerDef {
    public int[] ids;
    public String className;

    /*
     * For the definition reader (util/XmlObjects), which fills the fields
     * afterwards. XStream never called a constructor at all -- it allocated
     * through sun.reflect.ReflectionFactory, which is exactly the JDK internal
     * this project is getting off -- so a constructor that does nothing
     * reproduces the old behaviour precisely: every field starts at its default
     * and the document sets it.
     */
    public PacketHandlerDef() {
    }

    public PacketHandlerDef(int[] ids, String className) {
        this.ids = ids;
        this.className = className;
    }

    public String getClassName() {
        return this.className;
    }

    public int[] getAssociatedPackets() {
        return this.ids;
    }
}

