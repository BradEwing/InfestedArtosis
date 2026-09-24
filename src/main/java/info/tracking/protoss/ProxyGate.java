package info.tracking.protoss;

import bwapi.Position;
import bwapi.UnitType;
import info.tracking.ObservedUnitTracker;
import info.tracking.StrategyDetectionContext;
import util.Time;

import java.util.Collection;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Detects Gateways built on our side of the map. Two arms of evidence:
 * <ul>
 *     <li>GATEWAY_AWAY: while the clock is within {@link #GATEWAY_CUTOFF}, a Gateway first observed within it
 *     stands on our side of the map by ground distance. A forward or third-base Gateway on the enemy's side
 *     never counts;</li>
 *     <li>MAIN_EMPTY: decided once, on the first frame at or after {@link #MAIN_SCOUT_WINDOW_END}. Our vision
 *     covered the enemy main, including the ground around its depot where a Gateway would stand, within the
 *     window; no {@link #MAIN_TECH} building had been observed anywhere by the window end, destroyed ones
 *     included; and a Zealot stands on our side of the map.</li>
 * </ul>
 * A main we never covered is no evidence, and an empty main alone is not either: the scout may have missed a
 * Gateway placed after it passed or in ground it never saw, so the arm also needs the Zealot a proxy Gateway
 * delivers to our side by the window end. ProxyGate is the specific label for a Gateway rush, and supersedes
 * 2Gate in StrategyTracker.
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

    private boolean mainEmptyDecided;

    public ProxyGate() {
        super(NAME);
    }

    @Override
    public boolean isDetected(StrategyDetectionContext context) {
        ObservedUnitTracker tracker = context.getTracker();
        Time now = context.getTime();
        boolean gatewayAway = hasGatewayAway(now, tracker, context::isOnOurSide);
        boolean mainEmpty = decideMainEmpty(now, () -> isMainEmpty(context.enemyMainScoutedFrame(),
                isTechObserved(tracker),
                hasZealotOnOurSide(tracker.getLastKnownPositionsOfLivingUnits(UnitType.Protoss_Zealot),
                        context::isOnOurSide)));
        return recordEvidence(gatewayAway, mainEmpty);
    }

    /**
     * Whether, with now no later than {@link #GATEWAY_CUTOFF}, a Gateway first observed no later than the cutoff
     * stands at a position on our side of the map.
     */
    static boolean hasGatewayAway(Time now, ObservedUnitTracker tracker, Predicate<Position> onOurSide) {
        if (!now.lessThanOrEqual(GATEWAY_CUTOFF)) {
            return false;
        }
        return tracker.getLastKnownPositionsObservedBeforeTime(UnitType.Protoss_Gateway, GATEWAY_CUTOFF)
                .stream()
                .anyMatch(onOurSide);
    }

    /**
     * Evaluates the MAIN_EMPTY verdict on the first call at or after {@link #MAIN_SCOUT_WINDOW_END} and returns
     * it. Every other call returns false, so the arm never fires after the window.
     */
    boolean decideMainEmpty(Time now, BooleanSupplier verdict) {
        if (mainEmptyDecided || !MAIN_SCOUT_WINDOW_END.lessThanOrEqual(now)) {
            return false;
        }
        mainEmptyDecided = true;
        return verdict.getAsBoolean();
    }

    /**
     * Whether a {@link #MAIN_TECH} building was first observed no later than {@link #MAIN_SCOUT_WINDOW_END},
     * wherever it stood and whether or not it has since been destroyed. A Gateway on our side vetoes too, and is
     * GATEWAY_AWAY evidence instead.
     */
    static boolean isTechObserved(ObservedUnitTracker tracker) {
        return tracker.hasObservedAnyBeforeTime(MAIN_SCOUT_WINDOW_END, MAIN_TECH);
    }

    static boolean hasZealotOnOurSide(Collection<Position> zealotPositions, Predicate<Position> onOurSide) {
        return zealotPositions.stream().anyMatch(onOurSide);
    }

    /**
     * Whether the scouted enemy main held no tech while a proxy Gateway's Zealot reached our side.
     *
     * @param mainScouted when our vision first covered the enemy main and the ground around its depot, or null
     *     if it never did
     * @param techObserved whether a {@link #MAIN_TECH} building was observed by the window end
     * @param zealotOnOurSide whether a Zealot stands on our side of the map
     */
    static boolean isMainEmpty(Time mainScouted, boolean techObserved, boolean zealotOnOurSide) {
        return mainScouted != null && mainScouted.lessThanOrEqual(MAIN_SCOUT_WINDOW_END) && !techObserved
                && zealotOnOurSide;
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
