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

    private final int frame;
    private final String squadId;
    private final BunkerAdvanceReason reason;
    private final double ownStrength;
    private final double bunkerPrice;
    private final double releaseRatio;
    private final int lingCount;
    private final Bunker bunker;

    /**
     * @param frame the frame of the decision
     * @param squadId the squad's id
     * @param reason what the gate decided and why
     * @param ownStrength the squad's priced strength
     * @param bunkerPrice the enemy strength the recorded loss was priced against, 0 when no loss is on record
     * @param releaseRatio the strength ratio that releases the record, 0 when no loss is on record
     * @param lingCount Zerglings in the squad
     * @param bunker the Bunker the decision concerned
     */
    public BunkerAdvanceEvent(int frame, String squadId, BunkerAdvanceReason reason, double ownStrength,
                              double bunkerPrice, double releaseRatio, int lingCount, Bunker bunker) {
        this.frame = frame;
        this.squadId = squadId;
        this.reason = reason;
        this.ownStrength = ownStrength;
        this.bunkerPrice = bunkerPrice;
        this.releaseRatio = releaseRatio;
        this.lingCount = lingCount;
        this.bunker = bunker;
    }
}
