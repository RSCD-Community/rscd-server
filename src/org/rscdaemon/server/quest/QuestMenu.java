/*
 * Decompiled with CFR 0.152.
 */
package org.rscdaemon.server.quest;

import org.rscdaemon.server.model.MenuHandler;
import org.rscdaemon.server.model.Player;

public abstract class QuestMenu {
    public QuestMenu(Player owner, String[] s) {
        final QuestMenu qmenu = this;
        owner.setQuestMenuHandler(new MenuHandler(s){

            public void handleReply(int option, String response) {
                qmenu.handleReply(option, response);
            }
        });
    }

    public abstract void handleReply(int var1, String var2);
}

