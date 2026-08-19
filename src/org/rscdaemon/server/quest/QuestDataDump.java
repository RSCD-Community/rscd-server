package org.rscdaemon.server.quest;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Vector;

/**
 * Prints every quest's declared metadata as JSON, for the website: the
 * Beastiary's "featured in quests" line and the manual's generated quest
 * pages (description, start point, requirements, rewards).
 *
 * Goes through QuestLoader and define() exactly the way QuestManager does on
 * every login (null owner is fine: load() guards it), so the output can never
 * disagree with what the running server does -- grantRewards() pays out of the
 * same declarations this prints.
 *
 * Skill indexes are printed raw; the website maps them through the same
 * 18-entry order as Formulae.statArray. Quest points and the members flag
 * come from the Quests registry, which is where the server reads them too.
 *
 * Usage: java -Drscd.quests=<quests dir> org.rscdaemon.server.quest.QuestDataDump
 */
public class QuestDataDump {

    public static void main(String[] args) throws Exception {
        QuestLoader.loadClasses();

        String[] fields = {
            "name", "associatedNpcs", "commandNpcs", "description", "startPoint",
            "speakTo", "missionLength", "requiredLevels", "requiredQuests",
            "requiredOther", "rewardItems", "rewardExp", "rewardOther"
        };
        java.util.HashMap<String, Field> f = new java.util.HashMap<String, Field>();
        for (String fieldName : fields) {
            Field field = Quest.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            f.put(fieldName, field);
        }

        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (Class<?> c : QuestLoader.getClasses()) {
            Quest quest;
            try {
                quest = (Quest) c.getConstructor(
                    org.rscdaemon.server.model.Player.class, Integer.class)
                    .newInstance(null, -1);
                quest.define();
            } catch (Throwable t) {
                System.err.println("skip " + c.getName() + ": " + t);
                continue;
            }

            Vector<Integer> npcs = new Vector<Integer>((List<Integer>) f.get("associatedNpcs").get(quest));
            for (Integer id : (List<Integer>) f.get("commandNpcs").get(quest)) {
                if (!npcs.contains(id)) {
                    npcs.add(id);
                }
            }

            int uid = quest.getUID();
            /* The registry name carries Jagex's "(members)" suffix; the class
               name is the clean display form. Membership comes from the
               registry, display from the class. */
            boolean members = Quests.isVanilla(uid) && Quests.name(uid).contains("(members)");

            if (!first) {
                json.append(",");
            }
            first = false;
            json.append("\n  {");
            json.append("\"quest\": ").append(str((String) f.get("name").get(quest)));
            json.append(", \"uid\": ").append(uid);
            json.append(", \"vanilla\": ").append(Quests.isVanilla(uid));
            json.append(", \"points\": ").append(Quests.points(uid));
            json.append(", \"members\": ").append(members);
            json.append(", \"description\": ").append(str((String) f.get("description").get(quest)));
            json.append(", \"startPoint\": ").append(str((String) f.get("startPoint").get(quest)));
            json.append(", \"speakTo\": ").append(str((String) f.get("speakTo").get(quest)));
            json.append(", \"missionLength\": ").append(str((String) f.get("missionLength").get(quest)));
            json.append(", \"requiredLevels\": ").append(ints((List<int[]>) f.get("requiredLevels").get(quest)));
            json.append(", \"requiredQuests\": ").append(f.get("requiredQuests").get(quest));
            json.append(", \"requiredOther\": ").append(strs((List<String>) f.get("requiredOther").get(quest)));
            json.append(", \"rewardItems\": ").append(ints((List<int[]>) f.get("rewardItems").get(quest)));
            json.append(", \"rewardExp\": ").append(ints((List<int[]>) f.get("rewardExp").get(quest)));
            json.append(", \"rewardOther\": ").append(strs((List<String>) f.get("rewardOther").get(quest)));
            json.append(", \"npcs\": ").append(npcs);
            json.append("}");
        }
        json.append("\n]");
        System.out.println(json);
    }

    private static String str(String s) {
        if (s == null) {
            s = "";
        }
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String strs(List<String> list) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < list.size(); ++i) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(str(list.get(i)));
        }
        return out.append("]").toString();
    }

    private static String ints(List<int[]> list) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < list.size(); ++i) {
            if (i > 0) {
                out.append(", ");
            }
            out.append("[");
            int[] row = list.get(i);
            for (int j = 0; j < row.length; ++j) {
                if (j > 0) {
                    out.append(", ");
                }
                out.append(row[j]);
            }
            out.append("]");
        }
        return out.append("]").toString();
    }
}
