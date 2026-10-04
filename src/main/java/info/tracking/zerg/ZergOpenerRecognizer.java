package info.tracking.zerg;

import info.tracking.StrategyDetectionContext;

/**
 * Detects one Zerg opener. The recognizers of every opener share one {@link ZergOpenerReading}, so they are
 * mutually exclusive and at most one fires per game.
 */
public class ZergOpenerRecognizer extends ZergBaseStrategy {

    private final ZergOpener opener;
    private final ZergOpenerReading reading;

    public ZergOpenerRecognizer(ZergOpener opener, ZergOpenerReading reading) {
        super(opener.getStrategyName());
        this.opener = opener;
        this.reading = reading;
    }

    @Override
    public boolean isDetected(StrategyDetectionContext context) {
        return reading.read(context) == opener;
    }

    @Override
    public String getDetectionLabel() {
        String evidenceLabel = reading.getEvidenceLabel();
        return evidenceLabel == null ? getName() : evidenceLabel;
    }
}
