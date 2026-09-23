package info.tracking.protoss;

import bwapi.UnitType;
import info.BaseData;
import info.tracking.ObservedUnitTracker;
import info.tracking.StrategyDetectionContext;
import util.Time;

/**
 * Detects Gateways built near our bases. Two arms of evidence, each dated no later than
 * {@link #DETECTION_CUTOFF}:
 * <ul>
 *     <li>GATEWAY: a Gateway stamped proxied, i.e. first shown near one of our bases or our inferred natural;</li>
 *     <li>ZEALOTS: at least {@link #MIN_LOCAL_ZEALOTS} distinct Zealots whose enemy onUnitComplete callback
 *     fired on our main or natural defence tiles.</li>
 * </ul>
 * BWAPI also raises onUnitComplete when it first shows an enemy unit that is already complete, so a single
 * Zealot walking onto our tiles unseen reports a local completion. The ZEALOTS arm therefore asks for the same
 * two attackers EarlyRush does, and a single walk-in never detects. A proxied Probe alone is not evidence.
 * ProxyGate is the specific label for a Gateway rush, and supersedes 2Gate in StrategyTracker.
 */
public class ProxyGate extends ProtossBaseStrategy {

    public static final String NAME = "ProxyGate";

    static final Time DETECTION_CUTOFF = new Time(4, 30);
    static final int MIN_LOCAL_ZEALOTS = 2;

    static final String GATEWAY_EVIDENCE = "GATEWAY";
    static final String ZEALOTS_EVIDENCE = "ZEALOTS";

    private String evidence = "";

    public ProxyGate() {
        super(NAME);
    }

    @Override
    public boolean isDetected(StrategyDetectionContext context) {
        ObservedUnitTracker tracker = context.getTracker();
        int proxiedGateways = tracker.getProxiedCountByTypeBeforeTime(UnitType.Protoss_Gateway, DETECTION_CUTOFF);
        int localZealots = tracker.countCompletedWhileObservedOnTiles(UnitType.Protoss_Zealot,
                context.ourBaseTiles(BaseData.NATURAL_DEFENSE_TILE_RADIUS), DETECTION_CUTOFF);
        return recordEvidence(proxiedGateways, localZealots);
    }

    /**
     * Records which arms the counts satisfy, for {@link #getDetectionLabel()}.
     *
     * @return whether any arm is satisfied
     */
    boolean recordEvidence(int proxiedGateways, int localZealots) {
        evidence = evidence(proxiedGateways, localZealots);
        return !evidence.isEmpty();
    }

    /**
     * The name, followed by the evidence arms that fired, e.g. ProxyGate:ZEALOTS.
     */
    @Override
    public String getDetectionLabel() {
        if (evidence.isEmpty()) {
            return getName();
        }
        return getName() + ":" + evidence;
    }

    static boolean matches(int proxiedGateways, int localZealots) {
        return !evidence(proxiedGateways, localZealots).isEmpty();
    }

    /**
     * The arms the evidence satisfies: GATEWAY, ZEALOTS or GATEWAY+ZEALOTS, or empty when neither does.
     */
    static String evidence(int proxiedGateways, int localZealots) {
        boolean gateway = proxiedGateways > 0;
        boolean zealots = localZealots >= MIN_LOCAL_ZEALOTS;
        if (gateway && zealots) {
            return GATEWAY_EVIDENCE + "+" + ZEALOTS_EVIDENCE;
        }
        if (gateway) {
            return GATEWAY_EVIDENCE;
        }
        if (zealots) {
            return ZEALOTS_EVIDENCE;
        }
        return "";
    }
}
