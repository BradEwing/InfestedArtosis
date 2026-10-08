package unit.squad;

import bwapi.Position;
import telemetry.BunkerAdvanceReason;

import java.util.Collection;
import java.util.List;

/**
 * Whether a squad may advance into a Bunker it has been priced as unable to break.
 *
 * <p>A mostly melee squad that retreated from a priced Bunker leaves a loss on record, see {@link BunkerLossLedger}.
 * While the record stands, an advance of a mostly melee squad toward that Bunker is held. The record ends, and the
 * advance is allowed again, when the squad's strength reaches the strength the retreat was priced against times the
 * ratio the sim needs plus {@link #RELEASE_HYSTERESIS}, when the Bunker is seen damaged by
 * {@link #DAMAGE_RELEASE_HIT_POINTS} or more, when the Bunker is no longer a living observed Bunker, or when
 * {@link #HOLD_TIMEOUT_FRAMES} pass since the retreat. The gate never applies to the first attack on a Bunker, which
 * has no loss on record, and never while the sim reads the squad as breaking the Bunker.
 */
public final class BunkerAdvanceGate {

    /**
     * Added to the ratio the sim needs before a squad is priced as breaking a Bunker it lost to, so a squad that only
     * just reaches the ratio does not march back in and retreat again.
     */
    static final double RELEASE_HYSTERESIS = 0.25;

    /**
     * Hit points a Bunker must have lost since the loss was first recorded to count as damaged.
     */
    static final int DAMAGE_RELEASE_HIT_POINTS = 40;

    /**
     * Frames after the latest retreat at which a loss on record ends whatever else holds: two minutes.
     */
    static final int HOLD_TIMEOUT_FRAMES = 2880;

    /**
     * The farthest a Bunker may stand from the squad's centre, in pixels, for the gate to concern the squad.
     */
    static final double RELEVANT_RANGE = 1280;

    private BunkerAdvanceGate() {
    }

    /**
     * A living observed Bunker.
     */
    public static final class Bunker {
        private final int id;
        private final Position position;
        private final int hitPoints;

        /**
         * @param id the Bunker's unit id
         * @param position where it stands or was last seen
         * @param hitPoints its hit points as last seen
         */
        public Bunker(int id, Position position, int hitPoints) {
            this.id = id;
            this.position = position;
            this.hitPoints = hitPoints;
        }

        public int getId() {
            return id;
        }

        public Position getPosition() {
            return position;
        }

        public int getHitPoints() {
            return hitPoints;
        }
    }

    /**
     * What the gate decided and what it decided on.
     */
    public static final class Verdict {
        private final BunkerAdvanceReason reason;
        private final Bunker bunker;
        private final double price;
        private final double releaseRatio;

        Verdict(BunkerAdvanceReason reason, Bunker bunker, double price, double releaseRatio) {
            this.reason = reason;
            this.bunker = bunker;
            this.price = price;
            this.releaseRatio = releaseRatio;
        }

        public BunkerAdvanceReason getReason() {
            return reason;
        }

        public boolean isHeld() {
            return reason.isHeld();
        }

        /**
         * @return the Bunker the decision concerned, or null when none stood in range or the Bunker is dead
         */
        public Bunker getBunker() {
            return bunker;
        }

        /**
         * @return the enemy strength the loss was priced against, 0 when no loss is on record
         */
        public double getPrice() {
            return price;
        }

        /**
         * @return the strength ratio the squad needs to be priced as breaking the Bunker, 0 when no loss is on record
         */
        public double getReleaseRatio() {
            return releaseRatio;
        }
    }

    /**
     * Decides whether a squad may advance, ending the records of the Bunkers it released. With the gate switched off
     * no advance is ever held and no record is read or ended.
     *
     * @param gateOn whether the Bunker gates are switched on, see Config.bunkerGate
     * @param ledger the losses on record
     * @param living the living observed Bunkers
     * @param squadCenter the squad's centre
     * @param ownStrength the squad's priced strength
     * @param melee whether the squad is mostly melee, see {@link ContainmentGate#isMostlyMelee}
     * @param simBreaks whether the sim reads the squad as breaking the Bunker it faces
     * @param frame the current frame
     * @return the verdict
     */
    public static Verdict evaluate(boolean gateOn, BunkerLossLedger ledger, Collection<Bunker> living,
                                   Position squadCenter, double ownStrength, boolean melee, boolean simBreaks,
                                   int frame) {
        if (!gateOn) {
            return new Verdict(BunkerAdvanceReason.NO_BUNKER, null, 0, 0);
        }
        List<BunkerLossLedger.Entry> relevant = ledger.near(squadCenter, RELEVANT_RANGE);
        Bunker nearestLiving = nearest(living, squadCenter);
        if (relevant.isEmpty()) {
            return nearestLiving == null
                    ? new Verdict(BunkerAdvanceReason.NO_BUNKER, null, 0, 0)
                    : new Verdict(BunkerAdvanceReason.NO_LOSS, nearestLiving, 0, 0);
        }
        BunkerLossLedger.Entry first = relevant.get(0);
        if (!melee) {
            return verdict(BunkerAdvanceReason.NOT_MELEE, find(living, first), first);
        }
        if (simBreaks) {
            return verdict(BunkerAdvanceReason.SIM_BREAKS, find(living, first), first);
        }
        Verdict released = null;
        for (BunkerLossLedger.Entry entry : relevant) {
            Bunker bunker = find(living, entry);
            BunkerAdvanceReason reason = releaseReason(entry, bunker, ownStrength, frame);
            if (reason == null) {
                return verdict(BunkerAdvanceReason.HELD_LOSS, bunker, entry);
            }
            ledger.remove(entry);
            if (released == null) {
                released = verdict(reason, bunker, entry);
            }
        }
        return released;
    }

    private static BunkerAdvanceReason releaseReason(BunkerLossLedger.Entry entry, Bunker bunker, double ownStrength,
                                                     int frame) {
        if (bunker == null) {
            return BunkerAdvanceReason.RELEASED_DEAD;
        }
        if (bunker.getHitPoints() <= entry.getBaselineHitPoints() - DAMAGE_RELEASE_HIT_POINTS) {
            return BunkerAdvanceReason.RELEASED_DAMAGE;
        }
        if (frame - entry.getFrame() >= HOLD_TIMEOUT_FRAMES) {
            return BunkerAdvanceReason.RELEASED_TIMEOUT;
        }
        if (ownStrength >= entry.getPrice() * entry.getReleaseRatio()) {
            return BunkerAdvanceReason.RELEASED_STRENGTH;
        }
        return null;
    }

    private static Verdict verdict(BunkerAdvanceReason reason, Bunker bunker, BunkerLossLedger.Entry entry) {
        return new Verdict(reason, bunker, entry.getPrice(), entry.getReleaseRatio());
    }

    private static Bunker find(Collection<Bunker> living, BunkerLossLedger.Entry entry) {
        for (Bunker bunker : living) {
            if (bunker.getPosition().equals(entry.getPosition())) {
                return bunker;
            }
        }
        return null;
    }

    private static Bunker nearest(Collection<Bunker> living, Position center) {
        Bunker nearest = null;
        double best = RELEVANT_RANGE;
        for (Bunker bunker : living) {
            double distance = center.getDistance(bunker.getPosition());
            if (distance <= best) {
                best = distance;
                nearest = bunker;
            }
        }
        return nearest;
    }
}
