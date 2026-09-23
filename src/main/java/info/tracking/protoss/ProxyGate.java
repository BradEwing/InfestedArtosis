package info.tracking.protoss;

import bwapi.Position;
import bwapi.UnitType;
import info.tracking.ObservedUnitTracker;
import info.tracking.StrategyDetectionContext;
import util.Time;

import java.util.function.Predicate;

/**
 * Detects Gateways built away from the enemy's main and natural. Two arms of evidence:
 * <ul>
 *     <li>GATEWAY_AWAY: a Gateway first observed no later than {@link #GATEWAY_CUTOFF} that stands outside the
 *     BWEM Areas of the enemy main and natural, or on our side of the map by ground distance;</li>
 *     <li>MAIN_EMPTY: our scouting reached the enemy main no later than {@link #MAIN_SCOUT_WINDOW_END}, and by
 *     then no Gateway, Cybernetics Core, Forge, Stargate or Robotics Facility had been observed anywhere. A
 *     Gateway seen away from the enemy's home is GATEWAY_AWAY's evidence, so counting tech anywhere loses no
 *     proxy.</li>
 * </ul>
 * While the enemy main is unknown a Gateway counts as away only when it is on our side, and MAIN_EMPTY never
 * fires, since a main we never looked at is no evidence. ProxyGate is the specific label for a Gateway rush,
 * and supersedes 2Gate in StrategyTracker.
 */
public class ProxyGate extends ProtossBaseStrategy {

    public static final String NAME = "ProxyGate";

    static final Time GATEWAY_CUTOFF = new Time(5, 0);

    /**
     * Matches TwoGate's cutoff for observing two Gateways.
     */
    static final Time MAIN_SCOUT_WINDOW_END = new Time(3, 0);

    static final String GATEWAY_AWAY_EVIDENCE = "GATEWAY_AWAY";
    static final String MAIN_EMPTY_EVIDENCE = "MAIN_EMPTY";

    static final UnitType[] MAIN_TECH = {
        UnitType.Protoss_Gateway,
        UnitType.Protoss_Cybernetics_Core,
        UnitType.Protoss_Forge,
        UnitType.Protoss_Stargate,
        UnitType.Protoss_Robotics_Facility
    };

    private String evidence = "";

    public ProxyGate() {
        super(NAME);
    }

    @Override
    public boolean isDetected(StrategyDetectionContext context) {
        ObservedUnitTracker tracker = context.getTracker();
        boolean enemyMainKnown = context.isEnemyMainKnown();
        boolean gatewayAway = hasGatewayAway(tracker, position -> isAway(enemyMainKnown,
                context.isInEnemyMainOrNatural(position.toTilePosition()), context.isOnOurSide(position)));
        boolean mainEmpty = isMainEmpty(context.getTime(), context.enemyMainReachedFrame(),
                isMainTechObserved(tracker));
        return recordEvidence(gatewayAway, mainEmpty);
    }

    /**
     * Whether a Gateway first observed no later than {@link #GATEWAY_CUTOFF} stands at a position the test
     * calls away.
     */
    static boolean hasGatewayAway(ObservedUnitTracker tracker, Predicate<Position> away) {
        return tracker.getLastKnownPositionsObservedBeforeTime(UnitType.Protoss_Gateway, GATEWAY_CUTOFF)
                .stream()
                .anyMatch(away);
    }

    /**
     * Whether a Gateway position is away from the enemy's home: on our side of the map, or, once the enemy
     * main is known, outside the Areas of its main and natural.
     */
    static boolean isAway(boolean enemyMainKnown, boolean inEnemyMainOrNatural, boolean onOurSide) {
        return onOurSide || enemyMainKnown && !inEnemyMainOrNatural;
    }

    /**
     * Whether a {@link #MAIN_TECH} building was first observed, anywhere, no later than
     * {@link #MAIN_SCOUT_WINDOW_END}.
     */
    static boolean isMainTechObserved(ObservedUnitTracker tracker) {
        return tracker.hasObservedAnyBeforeTime(MAIN_SCOUT_WINDOW_END, MAIN_TECH);
    }

    /**
     * Whether the scouted enemy main held no tech: the window has closed, our scouting reached the main within
     * it, and no tech building was observed by its end.
     *
     * @param now the current time
     * @param mainReached when our scouting first reached the enemy main, or null if it never did
     * @param techObserved whether a {@link #MAIN_TECH} building was first observed by the window end
     */
    static boolean isMainEmpty(Time now, Time mainReached, boolean techObserved) {
        if (mainReached == null || techObserved) {
            return false;
        }
        return MAIN_SCOUT_WINDOW_END.lessThanOrEqual(now) && mainReached.lessThanOrEqual(MAIN_SCOUT_WINDOW_END);
    }

    /**
     * Records which arms fired, for {@link #getDetectionLabel()}.
     *
     * @return whether any arm fired
     */
    boolean recordEvidence(boolean gatewayAway, boolean mainEmpty) {
        evidence = evidence(gatewayAway, mainEmpty);
        return !evidence.isEmpty();
    }

    /**
     * The name, followed by the evidence arms that fired, e.g. ProxyGate:MAIN_EMPTY.
     */
    @Override
    public String getDetectionLabel() {
        if (evidence.isEmpty()) {
            return getName();
        }
        return getName() + ":" + evidence;
    }

    /**
     * The arms that fired: GATEWAY_AWAY, MAIN_EMPTY or GATEWAY_AWAY+MAIN_EMPTY, or empty when neither did.
     */
    static String evidence(boolean gatewayAway, boolean mainEmpty) {
        if (gatewayAway && mainEmpty) {
            return GATEWAY_AWAY_EVIDENCE + "+" + MAIN_EMPTY_EVIDENCE;
        }
        if (gatewayAway) {
            return GATEWAY_AWAY_EVIDENCE;
        }
        if (mainEmpty) {
            return MAIN_EMPTY_EVIDENCE;
        }
        return "";
    }
}
