package info.tracking.protoss;

import bwapi.TilePosition;
import bwapi.UnitType;
import info.BaseData;
import info.tracking.ObservedUnitTracker;
import info.tracking.StrategyDetectionContext;
import util.Time;

import java.util.Set;

/**
 * Detects Gateways built near our bases. Two arms of evidence, each dated no later than
 * {@link #DETECTION_CUTOFF}:
 * <ul>
 *     <li>a Gateway stamped proxied, i.e. first shown near one of our bases or our inferred natural;</li>
 *     <li>a Zealot whose enemy onUnitComplete callback fired on our main or natural defence tiles.</li>
 * </ul>
 * A proxied Probe alone is not evidence. ProxyGate is the specific label for a Gateway rush, and supersedes
 * 2Gate in StrategyTracker.
 */
public class ProxyGate extends ProtossBaseStrategy {

    public static final String NAME = "ProxyGate";

    static final Time DETECTION_CUTOFF = new Time(4, 30);

    public ProxyGate() {
        super(NAME);
    }

    @Override
    public boolean isDetected(StrategyDetectionContext context) {
        return matches(context.getTracker(), context.ourBaseTiles(BaseData.NATURAL_DEFENSE_TILE_RADIUS));
    }

    static boolean matches(ObservedUnitTracker tracker, Set<TilePosition> ourBaseTiles) {
        return proxiedGatewaySeen(tracker) || zealotCompletedAtOurBases(tracker, ourBaseTiles);
    }

    private static boolean proxiedGatewaySeen(ObservedUnitTracker tracker) {
        return tracker.getProxiedCountByTypeBeforeTime(UnitType.Protoss_Gateway, DETECTION_CUTOFF) > 0;
    }

    private static boolean zealotCompletedAtOurBases(ObservedUnitTracker tracker, Set<TilePosition> ourBaseTiles) {
        return tracker.hasCompletedWhileObservedOnTiles(UnitType.Protoss_Zealot, ourBaseTiles, DETECTION_CUTOFF);
    }
}
