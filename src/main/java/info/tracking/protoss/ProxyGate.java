package info.tracking.protoss;

import bwapi.Position;
import bwapi.TilePosition;
import bwapi.UnitType;
import info.tracking.ObservedUnitTracker;
import info.tracking.StrategyDetectionContext;
import util.Time;

import java.util.function.Predicate;

/**
 * Detects Gateways built away from the enemy's home. The enemy's home is the BWEM Areas of the enemy main and
 * natural plus the natural's wall ground, within {@link #NATURAL_WALL_TILE_RADIUS} of its depot or chokepoints.
 * Two arms of evidence:
 * <ul>
 *     <li>GATEWAY_AWAY: a Gateway first observed no later than {@link #GATEWAY_CUTOFF} that stands on our side
 *     of the map by ground distance, or, once the enemy main and natural are known, outside the enemy's
 *     home;</li>
 *     <li>MAIN_EMPTY: our vision covered enough of the enemy main to count it as scouted no later than
 *     {@link #MAIN_SCOUT_WINDOW_END}, and by then no Gateway, Cybernetics Core, Forge, Stargate or Robotics
 *     Facility had been observed at the enemy's home.</li>
 * </ul>
 * A main we never covered is no evidence, so MAIN_EMPTY never fires on it, nor while the enemy natural is
 * unknown and tech there could not be ruled out. ProxyGate is the specific label for
 * a Gateway rush, and supersedes 2Gate in StrategyTracker.
 */
public class ProxyGate extends ProtossBaseStrategy {

    public static final String NAME = "ProxyGate";

    static final Time GATEWAY_CUTOFF = new Time(5, 0);

    /**
     * Matches TwoGate's cutoff for observing two Gateways.
     */
    static final Time MAIN_SCOUT_WINDOW_END = new Time(3, 0);

    /**
     * Matches FFE's reach around the enemy natural's depot and chokepoints for the buildings of a natural wall.
     */
    static final int NATURAL_WALL_TILE_RADIUS = FFE.PROXIMITY_TILE_RADIUS;

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
        boolean enemyHomeKnown = context.isEnemyHomeKnown();
        Predicate<TilePosition> atEnemyHome = tile -> context.isAtEnemyHome(tile, NATURAL_WALL_TILE_RADIUS);
        boolean gatewayAway = hasGatewayAway(tracker, position -> isAway(enemyHomeKnown,
                atEnemyHome.test(position.toTilePosition()), context.isOnOurSide(position)));
        boolean mainEmpty = enemyHomeKnown && isMainEmpty(context.getTime(), context.enemyMainScoutedFrame(),
                isHomeTechObserved(tracker, atEnemyHome));
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
     * Whether a Gateway position is away from the enemy's home: on our side of the map, or, once the enemy main
     * and natural are known, not at the enemy's home.
     */
    static boolean isAway(boolean enemyHomeKnown, boolean atEnemyHome, boolean onOurSide) {
        return onOurSide || enemyHomeKnown && !atEnemyHome;
    }

    /**
     * Whether a {@link #MAIN_TECH} building was first observed at the enemy's home no later than
     * {@link #MAIN_SCOUT_WINDOW_END}. The natural and its wall count as home because a Forge or Gateway
     * expansion puts its first tech there rather than in the main.
     */
    static boolean isHomeTechObserved(ObservedUnitTracker tracker, Predicate<TilePosition> atEnemyHome) {
        return tracker.hasObservedAnyBeforeTimeAt(MAIN_SCOUT_WINDOW_END, atEnemyHome, MAIN_TECH);
    }

    /**
     * Whether the scouted enemy main held no tech: the window has closed, our vision covered the main within
     * it, and no tech building was observed at the enemy's home by its end.
     *
     * @param now the current time
     * @param mainScouted when our vision first covered the enemy main, or null if it never did
     * @param techObserved whether a {@link #MAIN_TECH} building was first observed at the enemy's home by the
     *     window end
     */
    static boolean isMainEmpty(Time now, Time mainScouted, boolean techObserved) {
        if (mainScouted == null || techObserved) {
            return false;
        }
        return MAIN_SCOUT_WINDOW_END.lessThanOrEqual(now) && mainScouted.lessThanOrEqual(MAIN_SCOUT_WINDOW_END);
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
