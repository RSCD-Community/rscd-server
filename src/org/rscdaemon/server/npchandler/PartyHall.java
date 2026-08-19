package org.rscdaemon.server.npchandler;

import java.util.ArrayList;
import java.util.List;

import org.rscdaemon.server.model.InvItem;
import org.rscdaemon.server.model.Npc;
import org.rscdaemon.server.model.PartySchedule;
import org.rscdaemon.server.model.Player;
import org.rscdaemon.server.model.World;
import org.rscdaemon.server.quest.dialogue.Choice;
import org.rscdaemon.server.quest.dialogue.Conversation;
import org.rscdaemon.server.quest.dialogue.Effect;

/**
 * The Party Hall Guards (794) in Seers' Village.
 *
 * Two of them, at (494,462) and (497,462), stood in front of the hall. They are
 * ours rather than Jagex's -- RSC had no party hall -- and they started out
 * with one job: explaining that telekinetic grab does not work inside the
 * building (the ward is Point.inPartyHall(), enforced in SpellHandler case 16).
 *
 * They also keep the calendar now. Booking costs PartySchedule.FEE and buys
 * the announcements, nothing else -- the hall and its cannons work for anyone
 * at any time, so an unannounced party is just a party nobody was told about.
 * One booking per website account at a time, and one party per half-hour
 * window across everybody -- a start overlapping an existing party's 30
 * minutes is refused as a taken slot. Both rules are enforced where the
 * account table lives (the ls-side PartyScheduleHandler); the booking reply
 * comes back asynchronously as chat, refunding the fee if the calendar
 * refused it. Sole ownership of the window is also what the Party Animals
 * hiscore leans on: items the cannons fire during a scheduled party count
 * toward its host's total, and only then.
 *
 * The Magical Party Schedule (1290) is free for the asking, one per carry.
 * Reading it fetches the calendar fresh -- see InvActionHandler.
 *
 * The two explanation topics keep their original one-shot behaviour: each
 * drops out of the menu once asked. The two service options are the guards'
 * job and never drop out.
 */
public class PartyHall implements NpcHandler {

    public static final int PARTY_HALL_GUARD = 794;

    public static final int MAGICAL_PARTY_SCHEDULE = 1290;

    private static final int COINS = 10;

    /* Menu labels. The menu is rebuilt from whichever topics are still
       unasked, so options arrays differ in length and order between passes
       and branches match on the label, never the index. */
    private static final String ASK_PARTY = "What party?";
    private static final String ASK_FUN = "How do you keep it fun?";
    private static final String ASK_BOOK = "I'd like to schedule a party";
    private static final String ASK_COPY = "Could I get a party schedule?";
    private static final String LEAVE = "Ok thanks";

    /* The bookable slots, hours from the moment of asking. */
    private static final String[] SLOT_NAMES = {
        "In 1 hour", "In 2 hours", "In 4 hours", "In 8 hours",
        "In 12 hours", "This time tomorrow", "Actually, never mind"
    };
    private static final int[] SLOT_HOURS = { 1, 2, 4, 8, 12, 24 };

    public void handleNpc(Npc npc, Player player) throws Exception {
        Conversation c = new Conversation(player, npc)
                .player("What do you do here?")
                .npc("It's our job to keep the party fun!");
        offer(c, true, true);
        c.start();
    }

    /**
     * Queue the topic menu, carrying which one-shot topics are still worth
     * offering.
     */
    private static void offer(Conversation c, final boolean party, final boolean fun) {
        List<String> opts = new ArrayList<String>();
        if (party) {
            opts.add(ASK_PARTY);
        }
        if (fun) {
            opts.add(ASK_FUN);
        }
        opts.add(ASK_BOOK);
        opts.add(ASK_COPY);
        opts.add(LEAVE);
        c.options(new Choice(opts.toArray(new String[opts.size()])) {
            public void picked(int option, Conversation c) {
                String picked = this.getOptions()[option];
                if (ASK_PARTY.equals(picked)) {
                    c.npc("Any party! This is the designated party hall, players..")
                     .npc("..come from all over to host and attend parties here");
                    offer(c, false, fun);
                } else if (ASK_FUN.equals(picked)) {
                    c.npc("We simply keep the party fair with our powerful magic")
                     .npc("We maintain a spell here preventing any players from..")
                     .npc("..using magic spells like telekinetic grab at the party");
                    offer(c, party, false);
                } else if (ASK_BOOK.equals(picked)) {
                    c.npc("Wonderful! A booking puts your party on the schedule..")
                     .npc("..and we announce it to the whole world as it begins")
                     .npc("The booking fee is 50,000 gold coins");
                    slots(c, party, fun);
                } else if (ASK_COPY.equals(picked)) {
                    if (c.getPlayer().getInventory().countId(MAGICAL_PARTY_SCHEDULE) > 0) {
                        c.npc("You've already got one right there!");
                    } else {
                        c.npc("Of course, here you are")
                         .give(new InvItem(MAGICAL_PARTY_SCHEDULE, 1))
                         .npc("It keeps itself up to date, that's the magical part");
                    }
                    offer(c, party, fun);
                }
                // LEAVE queues nothing: the echoed "Ok thanks" is the last line.
            }
        });
    }

    /** The when-menu. Payment happens here, refunds happen in the reply. */
    private static void slots(Conversation c, final boolean party, final boolean fun) {
        c.npc("When shall I put you down for?");
        c.options(new Choice(SLOT_NAMES) {
            public void picked(int option, Conversation c) {
                if (option >= SLOT_HOURS.length) {
                    c.npc("Any time, you know where to find us");
                    offer(c, party, fun);
                    return;
                }
                if (c.getPlayer().getInventory().countId(COINS) < PartySchedule.FEE) {
                    c.npc("The fee is 50,000 gold, come back when you have the coin");
                    offer(c, party, fun);
                    return;
                }
                final long start = System.currentTimeMillis() / 1000L
                    + SLOT_HOURS[option] * 3600L;
                c.take(COINS, PartySchedule.FEE)
                 .then(new Effect() {
                     public void run(Conversation c) {
                         World.getWorld().getServer().getLoginConnector()
                             .getActionSender().partyBook(c.getPlayer(), start);
                     }
                 })
                 .npc("Very good, I'll see to the announcements myself");
            }
        });
    }
}
