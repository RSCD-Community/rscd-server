package org.rscdaemon.server.entityhandling.defs.extras;

/**
 * One certificate exchange stall: Giles, Miles, Niles and the seven others.
 *
 * The four wording fields exist because Jagex's certers do not read from one
 * template. Each stall's lines were written by hand and the grammar between
 * them is irregular, so the document carries the exact words rather than the
 * handler guessing at a plural:
 *
 * <pre>
 *   type      "Welcome to my ORE exchange stall"
 *   goods     "I have some ORE to trade in"
 *             "what sort of ORE do you wish to trade in?"
 *             "You exchange your ORE for certificates"
 *   counted   "How many ORES do you wish to trade in?"
 *   shortfall "You don't have that MUCH ORE"
 *   plural    "You may exchange your ORES here"
 * </pre>
 *
 * ore/ore/ores/much ore/ores, bar/bars/bars/many bars/bars,
 * fish/fish/fishs/much fish/fish, log/logs/logs/many logs/logs. Jagex's
 * "fishs" is not a typo of ours; the wiki marks it {@literal {{sic}}} against
 * the original menu. It is also the one place where the counted form and the
 * explanation's plural part company, which is why they are separate fields.
 *
 * The three added fields fall back to {@link #getType()} so a certer document
 * written before they existed still loads and still reads sensibly.
 */
public class CerterDef {
    private String type;
    private String goods;
    private String counted;
    private String shortfall;
    private String plural;
    private CertDef[] certs;

    /** The word in "Welcome to my ... exchange stall". */
    public String getType() {
        return this.type;
    }

    /** The word the player and the stall both use for the goods themselves. */
    public String getGoods() {
        return this.goods == null ? this.type : this.goods;
    }

    /** The word used when a number precedes it: "How many ores ...". */
    public String getCounted() {
        return this.counted == null ? getGoods() : this.counted;
    }

    /** The tail of "You don't have that ...": "much ore", "many bars". */
    public String getShortfall() {
        return this.shortfall == null ? "many " + getGoods() : this.shortfall;
    }

    /** The word the explanation uses throughout: "5 ORES will give you one". */
    public String getPlural() {
        return this.plural == null ? getCounted() : this.plural;
    }

    /**
     * "What is AN ore exchange stall?" against "What is A bar exchange stall?".
     * The only certer types are ore, bar, fish and log, so the first letter
     * settles it.
     */
    public String getArticle() {
        switch (getType().charAt(0)) {
            case 'a': case 'e': case 'i': case 'o': case 'u': {
                return "an";
            }
        }
        return "a";
    }

    public String[] getCertNames() {
        String[] names = new String[this.certs.length];
        for (int i = 0; i < this.certs.length; ++i) {
            names[i] = this.certs[i].getName();
        }
        return names;
    }

    public int getCertCount() {
        return this.certs.length;
    }

    public int getCertID(int index) {
        if (index < 0 || index >= this.certs.length) {
            return -1;
        }
        return this.certs[index].getCertID();
    }

    public int getItemID(int index) {
        if (index < 0 || index >= this.certs.length) {
            return -1;
        }
        return this.certs[index].getItemID();
    }
}
