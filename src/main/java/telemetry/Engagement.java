package telemetry;

import bwapi.Position;
import bwapi.UnitType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One spatially clustered fight.
 *
 * Identity is the cluster, never a Squad. SquadManager.mergeSquads builds a brand new Squad with a
 * fresh id whenever two squads combine, so squad identity does not survive a fight, and trickling
 * is by definition several squads converging on one place.
 *
 * The engagement emits facts only. Whether a fight was piecemeal, won or lost is left to an
 * analysis script, where the definitions can be retuned without a rebuild and a fresh batch.
 */
class Engagement {

    private static final String NONE = "NONE";

    private final int id;
    private final Position anchor;
    private final Map<Integer, EngagementUnit> units = new LinkedHashMap<>();
    private final Set<String> squadIds = new HashSet<>();

    private int startFrame;
    private int lastContactFrame;
    private Position centroid;

    private int supplyOpen = -1;
    private int supplyPeak;
    private int supplyLastSample;
    private int armySupplyOpen = -1;
    private int enemySupplyPeak;

    private int enemyUnitsKilled;
    private int enemySupplyKilled;
    private boolean killsAmbiguous;

    private String statusOpen = NONE;
    private String statusLast = NONE;
    private int statusChanges;

    private double simRatioOpen = -1;
    private double simThresholdOpen = -1;
    private String simVerdictOpen = NONE;

    private int unitsLost;
    private int supplyLost;

    Engagement(int id, int frame, Position anchor) {
        this.id = id;
        this.anchor = anchor;
        this.startFrame = frame;
        this.lastContactFrame = frame;
        this.centroid = anchor;
    }

    int getId() {
        return id;
    }

    int getStartFrame() {
        return startFrame;
    }

    int getLastContactFrame() {
        return lastContactFrame;
    }

    Position getCentroid() {
        return centroid;
    }

    int getUnitsLost() {
        return unitsLost;
    }

    int getSupplyLost() {
        return supplyLost;
    }

    boolean hasOpened() {
        return supplyOpen >= 0;
    }

    void noteContact(int frame, Position sampleCentroid) {
        this.lastContactFrame = frame;
        if (sampleCentroid != null) {
            this.centroid = sampleCentroid;
        }
    }

    void noteUnit(int frame, int unitId, UnitType unitType, int hitPoints, String role, String squadId) {
        squadIds.add(squadId);
        EngagementUnit unit = units.get(unitId);
        if (unit == null) {
            units.put(unitId, new EngagementUnit(unitId, unitType, frame, hitPoints, role, squadId));
            return;
        }
        unit.observe(frame, hitPoints);
    }

    void noteSupply(int sampleSupply, int armySupply, int enemySupply) {
        if (supplyOpen < 0) {
            supplyOpen = sampleSupply;
            armySupplyOpen = armySupply;
        }
        supplyLastSample = sampleSupply;
        supplyPeak = Math.max(supplyPeak, sampleSupply);
        enemySupplyPeak = Math.max(enemySupplyPeak, enemySupply);
    }

    void noteStatus(String status) {
        if (NONE.equals(statusLast)) {
            statusOpen = status;
        } else if (!statusLast.equals(status)) {
            statusChanges++;
        }
        statusLast = status;
    }

    void noteSim(double ratio, double threshold, String verdict) {
        this.simRatioOpen = ratio;
        this.simThresholdOpen = threshold;
        this.simVerdictOpen = verdict;
    }

    void addKills(int killedUnits, int killedSupply, boolean ambiguous) {
        enemyUnitsKilled += killedUnits;
        enemySupplyKilled += killedSupply;
        if (ambiguous) {
            killsAmbiguous = true;
        }
    }

    void recordDeath(int unitId, int frame, Position position) {
        EngagementUnit unit = units.get(unitId);
        if (unit == null || unit.isDied()) {
            return;
        }
        unit.markDied(frame, position);
        unitsLost++;
        supplyLost += unit.getSupply();
    }

    /**
     * Folds another open engagement into this one after their centroids converged. This engagement
     * is the older of the two, so its anchor and start frame are the ones that survive.
     */
    void absorb(Engagement other) {
        startFrame = Math.min(startFrame, other.startFrame);
        lastContactFrame = Math.max(lastContactFrame, other.lastContactFrame);
        squadIds.addAll(other.squadIds);

        for (Map.Entry<Integer, EngagementUnit> entry : other.units.entrySet()) {
            EngagementUnit existing = units.get(entry.getKey());
            if (existing == null || entry.getValue().getArrivalFrame() < existing.getArrivalFrame()) {
                units.put(entry.getKey(), entry.getValue());
            }
        }

        if (supplyOpen < 0) {
            supplyOpen = other.supplyOpen;
            armySupplyOpen = other.armySupplyOpen;
        }
        supplyPeak = Math.max(supplyPeak, other.supplyPeak);
        supplyLastSample = Math.max(supplyLastSample, other.supplyLastSample);
        enemySupplyPeak = Math.max(enemySupplyPeak, other.enemySupplyPeak);

        enemyUnitsKilled += other.enemyUnitsKilled;
        enemySupplyKilled += other.enemySupplyKilled;
        killsAmbiguous = killsAmbiguous || other.killsAmbiguous;

        statusChanges += other.statusChanges;
        unitsLost += other.unitsLost;
        supplyLost += other.supplyLost;

        if (NONE.equals(simVerdictOpen)) {
            simRatioOpen = other.simRatioOpen;
            simThresholdOpen = other.simThresholdOpen;
            simVerdictOpen = other.simVerdictOpen;
        }
    }

    String toRow(String gameId) {
        List<int[]> arrivals = new ArrayList<>();
        for (EngagementUnit unit : units.values()) {
            arrivals.add(new int[] {unit.getArrivalFrame() - startFrame, unit.getSupply()});
        }

        List<String> fields = new ArrayList<>();
        fields.add(gameId);
        fields.add(String.valueOf(id));
        fields.add(String.valueOf(startFrame));
        fields.add(String.valueOf(lastContactFrame));
        fields.add(String.valueOf(anchor.getX()));
        fields.add(String.valueOf(anchor.getY()));
        fields.add(String.valueOf(Math.max(supplyOpen, 0)));
        fields.add(String.valueOf(supplyPeak));
        fields.add(String.valueOf(supplyLastSample));
        fields.add(String.valueOf(armySupplyOpen));
        fields.add(String.valueOf(unitsLost));
        fields.add(String.valueOf(supplyLost));
        fields.add(String.valueOf(SupplyQuantiles.weighted(arrivals, 0.25)));
        fields.add(String.valueOf(SupplyQuantiles.weighted(arrivals, 0.50)));
        fields.add(String.valueOf(SupplyQuantiles.weighted(arrivals, 0.75)));
        fields.add(String.valueOf(enemySupplyPeak));
        fields.add(String.valueOf(enemyUnitsKilled));
        fields.add(String.valueOf(enemySupplyKilled));
        fields.add(killsAmbiguous ? "1" : "0");
        fields.add(String.valueOf(squadIds.size()));
        fields.add(statusOpen);
        fields.add(statusLast);
        fields.add(String.valueOf(statusChanges));
        fields.add(Csv.format(simRatioOpen));
        fields.add(Csv.format(simThresholdOpen));
        fields.add(simVerdictOpen);
        return String.join(",", fields);
    }

    List<String> toUnitRows(String gameId) {
        List<String> rows = new ArrayList<>();
        for (EngagementUnit unit : units.values()) {
            List<String> fields = new ArrayList<>();
            fields.add(gameId);
            fields.add(String.valueOf(id));
            fields.add(String.valueOf(unit.getUnitId()));
            fields.add(Csv.name(unit.getUnitType()));
            fields.add(String.valueOf(unit.getSupply()));
            fields.add(String.valueOf(unit.getArrivalFrame()));
            fields.add(String.valueOf(unit.getArrivalFrame() - startFrame));
            fields.add(String.valueOf(unit.getExitFrame()));
            fields.add(unit.isDied() ? "1" : "0");
            fields.add(String.valueOf(unit.getDeathFrame()));
            fields.add(String.valueOf(unit.getDeathX()));
            fields.add(String.valueOf(unit.getDeathY()));
            fields.add(String.valueOf(unit.getHitPointsAtArrival()));
            fields.add(String.valueOf(unit.getHitPointsAtExit()));
            fields.add(unit.getRoleAtArrival());
            fields.add(unit.getSquadAtArrival());
            rows.add(String.join(",", fields));
        }
        return rows;
    }
}
