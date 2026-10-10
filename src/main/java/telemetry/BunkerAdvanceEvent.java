package telemetry;

import lombok.Getter;

/**
 * A repeat-advance gate decision about a squad and a Bunker in range, as one BUNKER_ADVANCE row reports it.
 */
@Getter
public final class BunkerAdvanceEvent {

    /**
     * The Bunker a decision concerned.
     */
    @Getter
    public static final class Bunker {

        /**
         * Stands for a decision that concerned no known Bunker.
         */
        public static final Bunker NONE = new Bunker(-1, -1, -1, -1);

        private final int id;
        private final int x;
        private final int y;
        private final int hitPoints;

        /**
         * @param id the Bunker's unit id
         * @param x its x position in pixels
         * @param y its y position in pixels
         * @param hitPoints its hit points as last seen
         */
        public Bunker(int id, int x, int y, int hitPoints) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.hitPoints = hitPoints;
        }
    }

    /**
     * The squad a decision concerned.
     */
    @Getter
    public static final class Squad {
        private final String id;
        private final int size;
        private final int lingCount;
        private final int x;
        private final int y;

        /**
         * @param id the squad's id
         * @param size units in the squad
         * @param lingCount Zerglings in the squad
         * @param x the squad centre's x in pixels
         * @param y the squad centre's y in pixels
         */
        public Squad(String id, int size, int lingCount, int x, int y) {
            this.id = id;
            this.size = size;
            this.lingCount = lingCount;
            this.x = x;
            this.y = y;
        }
    }

    private final int frame;
    private final Squad squad;
    private final BunkerAdvanceReason reason;
    private final double ownStrength;
    private final double bunkerPrice;
    private final double releaseRatio;
    private final BunkerAdvanceEntry entry;
    private final Bunker bunker;

    /**
     * @param frame the frame of the decision
     * @param squad the squad
     * @param reason what the gate decided and why
     * @param ownStrength the squad's priced strength
     * @param bunkerPrice the enemy strength the recorded loss was priced against, 0 when no loss is on record
     * @param releaseRatio the strength ratio that releases the record, 0 when no loss is on record
     * @param entry the branch where the gate read the squad
     * @param bunker the Bunker the decision concerned
     */
    public BunkerAdvanceEvent(int frame, Squad squad, BunkerAdvanceReason reason, double ownStrength,
                              double bunkerPrice, double releaseRatio, BunkerAdvanceEntry entry, Bunker bunker) {
        this.frame = frame;
        this.squad = squad;
        this.reason = reason;
        this.ownStrength = ownStrength;
        this.bunkerPrice = bunkerPrice;
        this.releaseRatio = releaseRatio;
        this.entry = entry;
        this.bunker = bunker;
    }
}
