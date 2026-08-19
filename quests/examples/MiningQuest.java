import org.rscdaemon.server.quest.*;
import org.rscdaemon.server.model.*;
import org.rscdaemon.server.event.*;




public class MiningQuest extends Quest
{
	/**
	 * This is the quest's UniqueID. IT MUST BE UNIQUE, as it's the
	 * database identifier. Conflicting quest UIDs will result
	 * in many angry people, do not mess this up.
	 */
	/* 1000+, not 1: ids 0-49 are Jagex's, fixed by the client's quest tab
	   (see Quests.java). This is an example quest, so it lives above
	   Quests.FIRST_CUSTOM where it cannot shadow a real one. */
	public final static int UID = 1000;
	
	/**
	 * The MiningQuest constructor, all quests need one,
	 * it defines the basics of the quest (its owner and UID).
	 */
public MiningQuest(Player owner, Integer uid)
	{
		super(owner, UID);
	}

	
	
	/**
	 * This is an abstract Method, and as such
	 * must be overwritten by every subclass of 
	 * Quest. It defines the unique features of
	 * each quest - its name, its 'completion stage',
	 * any items, npcs, objects that are associated
	 * with it.
	 */
	public void define()
	{
		setName("Miner Willabii"); // Sets the name of this quest.
		setFinalStage(100); // The stage at which this quest ends.
		associateNpc(400); // Willabii
		associateNpc(3); // Chicken
		associateItem(10); // Coins
		associateItem(1261); // addy pic
		associateObject(0); // Tree
	}
	
	/**
	 * Also an abstract Method that must be overwritten.
	 * This is called when the getFinalStage() is met.
	 */
	public void completeQuest()
	{
		getOwner().getInventory().add(new InvItem(10, 10000)); // Give the player 500 coins
		getOwner().getActionSender().sendInventory();
			
		getOwner().getInventory().add(new InvItem(1261, 1)); // Give the player Addy pic
		getOwner().getActionSender().sendInventory();
		
		getOwner().getActionSender().sendMessage(" Willabii hands you 10,000 coins.");
		getOwner().incExp(14, 10000, false); // 10,000 mining exp
		sleep(2500);
		
		getOwner().getActionSender().sendMessage("You have completed " + getName() + "!");
		getOwner().getActionSender().sendMessage("@gre@You just gained 1 quest point!"); // Yet to code client-side, so display a dummy for now.
	}
	
	/**
	 * This is my own method I made for organization's sake, 
	 * and has nothing to do with the Quest superclass.
	 * It handles all the main quest line's stage progression.
	 */
	private void handleNpc(final Npc npc)
	{
		if(npc.getID() == 400) // Miner Johnson
		{
			if(!questStarted()) // Say start quest dialogue.
			{
				sayNpcMessage("Hi there, traveller. Care to make some phat loot????");
				sleep(1500);
				
				new QuestMenu(getOwner(), new String[] { "Yeah, for sure.", "No thanks, I'm rich." }) // Creates a new option menu.
				{
					public void handleReply(final int option, final String reply) {
						if(owner.isBusy()) 
						{
						return;
						}
						if(option == 0)
						{
							sayMessage("Yeah, for sure.");
							sleep(1200);
							sayNpcMessage("If you collect 25 copper for me, I'll pay you cash");
							sleep(3000);
							sayNpcMessage("Maybe I'll teach you a thing or two");
							sleep(3000);
							sayNpcMessage("I'm afraid you'll have to find your own pic axe, hurry along now");
							sleep(3000);

							new QuestMenu(getOwner(), new String[] { "Sorry, I don't like the sound of that.", "No problem, ill get right on it." })
							{
								public void handleReply(final int option, final String reply) {
									if(owner.isBusy()) {
									return;
									}
									if(option == 0)
									{
										sayMessage("Sorry, I don't like the sound of that.");
										sleep(1200);
										sayNpcMessage("Suit yourself. Good day, then.");
										sleep(1000);
									} else
									{
										setStage(1); // The user has begun the quest - set its stage to 1.
										
										sayMessage("No problem, ill get right on it.");
										sleep(1200);
										sayNpcMessage("Great! Come back and see me when you're done.");
										sleep(1000);
									}
								}
							};
						} else // The player declined the quest.
						{
							sayMessage("No thanks, I'm good.");
							sleep(1200);
							sayNpcMessage("Suit yourself. Come and see me if you change your mind.");
							sleep(1000);
							
						}
						
						stopTalking(); // Reset talking vars and MobState.
						getOwner().resetMenuHandler(); // Remove this menu handler.
					}
				};
			} else
			if(getStage() == 1) // We've already started this quest.
			{
				sayNpcMessage("Ahh, you've returned! Do you have my copper?");
				sleep(1200);
				
				new QuestMenu(getOwner(), new String[] { "Yes, I do.", "I'm afraid not." })
				{
					public void handleReply(final int option, final String reply) {
						if(owner.isBusy()) 
						{
						return;
						}
						if(option == 0)
						{
							sayMessage("Yes, I do.");
							sleep(1200);
							if(owner.getInventory().countId(150) < 25) // Check if we actually have the copper
							{
								sayNpcMessage("Um, no you don't. Get back to me when you do. The reward still stands!");
								stopTalking();
								getOwner().resetMenuHandler();
							} else
							{
								sayNpcMessage("Execelent! Hand them over, then.");
								sleep(1200);
								
								owner.getActionSender().sendMessage("You hand over 25 copper to " + npc.getDef().getName() + ".");
								sleep(1200);
								
								for(int i = 0; i < 10; i++) // Remove the copper.
									owner.getInventory().remove(owner.getInventory().remove(150, 25));
									
								owner.getActionSender().sendInventory();	
									
								sleep(1200);
								sayNpcMessage("Thank you very much! As promised, here's your reward.");
								sleep(1200);
								setStage(getFinalStage()); // Complete the quest.
									
								stopTalking();	
								getOwner().resetMenuHandler();		
							}
						} else
						{
							sayMessage("I'm afraid not.");
							sleep(1200);
							sayNpcMessage("Well, come and see me when you do. The offer still stands");
							stopTalking();
							getOwner().resetMenuHandler();
						}
					}
				};
			} else
			{
				if(completed()) // We've already finished this NPC's quest, so just say a polite hello.
					sayNpcMessage("Hey there, " + getOwner().getUsername() + ". Thank you for your help with the copper!");
					
				stopTalking();
			}
		}
	}

	/**
	 * This is an optional trigger. If an entity that has been associated
	 * (associateNpc(), associateItem(), associateObject() etc.) has been
	 * interacted with in any way by the player, this will be called, along
	 * with the QuestTrigger enum to define the action.
	 */
	public void triggerEntity(QuestTrigger trigger, Entity entity)
	{
		if(entity instanceof Npc) // This entity is an NPC.
		{
			Npc npc = (Npc)entity;
			
			if(trigger == QuestTrigger.NPC_KILLED) // We killed the NPC.
			{
				if(npc.getID() == 3) // chicken
				{
					getOwner().getActionSender().sendMessage(getName() + " You killed a chicken.");
					setStage(1);
				}
			} else
			if(trigger == QuestTrigger.NPC_TALK) // We're talking to the NPC (For Willabii, not the chicken).
			{
				handleNpc(npc);
			}
		} else
		if(entity instanceof InvItem) // This entity is an InvItem.
		{
			InvItem item = (InvItem)entity;
			
			if(trigger == QuestTrigger.ITEM_PICKUP) // We picked the item up.
			{
				getOwner().getActionSender().sendMessage(getName() + " - You picked up a " + item.getDef().getName());
			} else
			if(trigger == QuestTrigger.ITEM_DROP) // We dropped the item.
			{
				getOwner().getActionSender().sendMessage(getName() + " - You dropped a " + item.getDef().getName());
			}
		} 
	}
}