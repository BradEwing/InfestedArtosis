package telemetry;

import bwapi.Game;
import bwapi.Player;
import bwapi.Position;
import bwapi.Race;
import bwapi.Unit;
import bwapi.UnitType;
import info.GameState;
import strategy.buildorder.BuildOrder;
import unit.managed.ManagedUnit;
import unit.squad.CombatSimulator;
import unit.squad.Squad;
import unit.squad.SquadManager;
import unit.squad.horizon.HorizonCombatSimulator;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Emits engagement telemetry so the trickle pattern - engage, lose a third of the squad, retreat -
 * can be measured across a batch instead of watched in a replay.
 *
 * Read-only against combat code. Squad statuses and combat sim verdicts are sampled from state the
 * bot already computes; HorizonCombatSimulator builds its DebugSnapshot on every evaluate call
 * regardless of any debug flag, so reading it costs nothing and changes no decision.
 *
 * Disabled by default. The disabled path returns before any BWAPI accessor, allocation or file
 * handle, because every JBWAPI accessor is at minimum a shared-memory read.
 */
public class CombatTelemetry {

    static final int SAMPLE_INTERVAL_FRAMES = 8;
    static final int CONTACT_RADIUS = 320;
    static final int MERGE_RADIUS = 512;
    static final int CLOSE_COOLDOWN_FRAMES = 240;

    private static final int FLUSH_INTERVAL_FRAMES = 480;
    private static final String WRITE_DIRECTORY = "bwapi-data/write";
    private static final Set<UnitType> IGNORED_ENEMY_TYPES = EnumSet.of(
            UnitType.Zerg_Larva,
            UnitType.Zerg_Egg,
            UnitType.Zerg_Lurker_Egg,
            UnitType.Zerg_Cocoon);

    private final boolean enabled;
    private final Game game;
    private final GameState gameState;
    private final SquadManager squadManager;
    private final TelemetryLog log;
    private final String gameId;
    private final List<UnitType> killCandidates;
    private final List<Engagement> openEngagements = new ArrayList<>();

    private boolean failed;
    private int nextEngagementId = 1;
    private int engagementCount;
    private int totalUnitsLost;
    private int totalSupplyLost;
    private int killedUnits;
    private int killedSupply;
    private int lastKilledUnits;
    private int lastKilledSupply;

    public CombatTelemetry(Game game, GameState gameState, SquadManager squadManager) {
        this.game = game;
        this.gameState = gameState;
        this.squadManager = squadManager;
        this.enabled = gameState.getConfig().telemetryCombat;
        this.gameId = enabled ? UUID.randomUUID().toString() : "";
        this.log = new TelemetryLog(enabled, Paths.get(WRITE_DIRECTORY));
        this.killCandidates = enabled ? killCandidates(gameState.getOpponentRace()) : Collections.<UnitType>emptyList();
    }

    /** Joins the other telemetry files for this game to the combat game row. Empty when disabled. */
    public String getGameId() {
        return gameId;
    }

    public void onFrame() {
        if (!enabled || failed) {
            return;
        }

        try {
            int frame = game.getFrameCount();
            if (frame % SAMPLE_INTERVAL_FRAMES == 0) {
                sample(frame);
            }
            if (frame % FLUSH_INTERVAL_FRAMES == 0) {
                log.flush();
            }
        } catch (RuntimeException e) {
            disable();
        }
    }

    public void onUnitDestroy(Unit unit) {
        if (!enabled || failed || openEngagements.isEmpty()) {
            return;
        }

        try {
            Player owner = unit.getPlayer();
            if (owner == null || owner.getID() != gameState.getSelf().getID()) {
                return;
            }

            int frame = game.getFrameCount();
            Position position = unit.getPosition();
            int unitId = unit.getID();
            for (Engagement engagement : openEngagements) {
                engagement.recordDeath(unitId, frame, position);
            }
        } catch (RuntimeException e) {
            disable();
        }
    }

    public void onEnd(boolean isWinner) {
        if (!enabled || failed) {
            return;
        }

        try {
            for (Engagement engagement : openEngagements) {
                emit(engagement);
            }
            openEngagements.clear();

            scanKills();
            log.appendGame(gameRow(isWinner, game.getFrameCount()));
            log.flush();
        } catch (RuntimeException e) {
            disable();
        }
    }

    /**
     * Telemetry never gets to end a game. An exception escaping onFrame kills the JVM and the batch
     * harness scores that as a crash, so a broken sampler stops sampling instead of propagating.
     */
    private void disable() {
        failed = true;
        openEngagements.clear();
    }

    private void sample(int frame) {
        mergeOverlappingEngagements();
        scanKills();
        int killedUnitsDelta = killedUnits - lastKilledUnits;
        int killedSupplyDelta = killedSupply - lastKilledSupply;
        lastKilledUnits = killedUnits;
        lastKilledSupply = killedSupply;

        List<EnemyThreat> threats = enemyThreats();
        Map<Engagement, List<Contact>> grouped = assignContacts(collectContacts(threats), frame);
        int armySupply = armySupply();
        boolean ambiguous = grouped.size() > 1;

        for (Map.Entry<Engagement, List<Contact>> entry : grouped.entrySet()) {
            updateEngagement(entry.getKey(), entry.getValue(), frame, armySupply, threats);
            if (killedUnitsDelta > 0 || killedSupplyDelta > 0) {
                entry.getKey().addKills(killedUnitsDelta, killedSupplyDelta, ambiguous);
            }
        }

        closeStaleEngagements(frame);
    }

    private List<EnemyThreat> enemyThreats() {
        List<EnemyThreat> threats = new ArrayList<>();
        for (Unit enemy : gameState.getVisibleEnemyUnits()) {
            UnitType type = enemy.getType();
            if (IGNORED_ENEMY_TYPES.contains(type)) {
                continue;
            }
            if (type.isBuilding() && !type.canAttack()) {
                continue;
            }
            Position position = enemy.getPosition();
            if (position == null) {
                continue;
            }
            threats.add(new EnemyThreat(position, type.supplyRequired()));
        }
        return threats;
    }

    private List<Contact> collectContacts(List<EnemyThreat> threats) {
        List<Contact> contacts = new ArrayList<>();
        if (threats.isEmpty()) {
            return contacts;
        }

        for (Squad squad : squadManager.fightSquads) {
            for (ManagedUnit member : squad.getMembers()) {
                if (member.getUnitType() == UnitType.Zerg_Overlord) {
                    continue;
                }
                Position position = member.getUnit().getPosition();
                if (position == null || !isInContact(position, threats)) {
                    continue;
                }
                contacts.add(new Contact(member, squad, position));
            }
        }
        return contacts;
    }

    private boolean isInContact(Position position, List<EnemyThreat> threats) {
        for (EnemyThreat threat : threats) {
            if (position.getDistance(threat.getPosition()) <= CONTACT_RADIUS) {
                return true;
            }
        }
        return false;
    }

    private Map<Engagement, List<Contact>> assignContacts(List<Contact> contacts, int frame) {
        Map<Engagement, List<Contact>> grouped = new LinkedHashMap<>();
        for (Contact contact : contacts) {
            Engagement engagement = nearestEngagement(contact.getPosition());
            if (engagement == null) {
                engagement = new Engagement(nextEngagementId++, frame, contact.getPosition());
                openEngagements.add(engagement);
            }
            List<Contact> members = grouped.get(engagement);
            if (members == null) {
                members = new ArrayList<>();
                grouped.put(engagement, members);
            }
            members.add(contact);
        }
        return grouped;
    }

    /**
     * An engagement still inside its close cooldown remains a candidate, so wave after wave into
     * the same place stays one engagement rather than one per wave.
     */
    private Engagement nearestEngagement(Position position) {
        Engagement best = null;
        double bestDistance = MERGE_RADIUS;
        for (Engagement engagement : openEngagements) {
            double distance = position.getDistance(engagement.getCentroid());
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = engagement;
            }
        }
        return best;
    }

    private void mergeOverlappingEngagements() {
        for (int i = 0; i < openEngagements.size(); i++) {
            Engagement first = openEngagements.get(i);
            for (int j = i + 1; j < openEngagements.size(); j++) {
                Engagement second = openEngagements.get(j);
                if (first.getCentroid().getDistance(second.getCentroid()) > MERGE_RADIUS) {
                    continue;
                }
                first.absorb(second);
                openEngagements.remove(j);
                j--;
            }
        }
    }

    private void updateEngagement(Engagement engagement, List<Contact> contacts, int frame, int armySupply,
                                  List<EnemyThreat> threats) {
        boolean opening = !engagement.hasOpened();
        Position centroid = centroidOf(contacts);
        engagement.noteContact(frame, centroid);

        int supply = 0;
        for (Contact contact : contacts) {
            ManagedUnit member = contact.getManagedUnit();
            UnitType type = member.getUnitType();
            engagement.noteUnit(frame, member.getUnitID(), type, member.getUnit().getHitPoints(),
                    Csv.name(member.getRole()), contact.getSquad().getId());
            supply += type.supplyRequired();
        }

        engagement.noteSupply(supply, armySupply, enemySupplyNear(centroid, threats));

        Squad dominant = dominantSquad(contacts);
        if (dominant == null) {
            return;
        }

        engagement.noteStatus(Csv.name(dominant.getStatus()));
        if (opening) {
            noteCombatSim(engagement, dominant, frame);
        }
    }

    /**
     * Reads the snapshot HorizonCombatSimulator already stashed for this squad this frame. Telemetry
     * samples after UnitManager.onFrame, so the snapshot reflects the decision the squad just made.
     */
    private void noteCombatSim(Engagement engagement, Squad squad, int frame) {
        CombatSimulator simulator = squad.getCombatSimulator();
        if (!(simulator instanceof HorizonCombatSimulator)) {
            return;
        }

        HorizonCombatSimulator.DebugSnapshot snapshot =
                ((HorizonCombatSimulator) simulator).getLastSnapshots().get(squad.getId());
        if (snapshot == null || frame - snapshot.getCapturedFrame() > SAMPLE_INTERVAL_FRAMES) {
            return;
        }

        engagement.noteSim(snapshot.getOverallRatio(), snapshot.getEngageThreshold(), Csv.name(snapshot.getResult()));
    }

    private Squad dominantSquad(List<Contact> contacts) {
        Map<Squad, Integer> supplyBySquad = new HashMap<>();
        for (Contact contact : contacts) {
            Squad squad = contact.getSquad();
            int supply = contact.getManagedUnit().getUnitType().supplyRequired();
            supplyBySquad.put(squad, supplyBySquad.getOrDefault(squad, 0) + supply);
        }

        Squad best = null;
        int bestSupply = -1;
        for (Map.Entry<Squad, Integer> entry : supplyBySquad.entrySet()) {
            Squad squad = entry.getKey();
            int supply = entry.getValue();
            if (supply > bestSupply || supply == bestSupply && squad.getId().compareTo(best.getId()) < 0) {
                bestSupply = supply;
                best = squad;
            }
        }
        return best;
    }

    private Position centroidOf(List<Contact> contacts) {
        long x = 0;
        long y = 0;
        for (Contact contact : contacts) {
            x += contact.getPosition().getX();
            y += contact.getPosition().getY();
        }
        return new Position((int) (x / contacts.size()), (int) (y / contacts.size()));
    }

    private int enemySupplyNear(Position centroid, List<EnemyThreat> threats) {
        int supply = 0;
        for (EnemyThreat threat : threats) {
            if (centroid.getDistance(threat.getPosition()) <= CONTACT_RADIUS) {
                supply += threat.getSupply();
            }
        }
        return supply;
    }

    private int armySupply() {
        int supply = 0;
        for (Squad squad : squadManager.fightSquads) {
            supply += squad.getSupply();
        }
        return supply;
    }

    private void closeStaleEngagements(int frame) {
        Iterator<Engagement> engagements = openEngagements.iterator();
        while (engagements.hasNext()) {
            Engagement engagement = engagements.next();
            if (frame - engagement.getLastContactFrame() < CLOSE_COOLDOWN_FRAMES) {
                continue;
            }
            engagements.remove();
            emit(engagement);
        }
    }

    private void emit(Engagement engagement) {
        engagementCount++;
        totalUnitsLost += engagement.getUnitsLost();
        totalSupplyLost += engagement.getSupplyLost();
        log.appendEngagement(engagement.toRow(gameId));
        for (String row : engagement.toUnitRows(gameId)) {
            log.appendEngagementUnit(row);
        }
    }

    /**
     * Enemy losses come from our own kill counters rather than from observed damage. BWEventListener
     * has no damage event and enemy hit points are readable only while the unit is visible, so
     * damage dealt is not obtainable and is deliberately absent from the schema.
     */
    private void scanKills() {
        int units = 0;
        int supply = 0;
        Player self = gameState.getSelf();
        for (UnitType type : killCandidates) {
            int count = self.killedUnitCount(type);
            if (count <= 0) {
                continue;
            }
            units += count;
            supply += count * type.supplyRequired();
        }
        killedUnits = units;
        killedSupply = supply;
    }

    /**
     * Restricts the per frame kill scan to types the opponent can actually field. A random opponent
     * still reports Race.Unknown at onStart, so all three races stay in the list.
     */
    private static List<UnitType> killCandidates(Race opponentRace) {
        boolean raceKnown = opponentRace == Race.Terran || opponentRace == Race.Protoss || opponentRace == Race.Zerg;

        List<UnitType> candidates = new ArrayList<>();
        for (UnitType type : UnitType.values()) {
            if (type.isBuilding() || type.supplyRequired() <= 0) {
                continue;
            }
            if (raceKnown && type.getRace() != opponentRace) {
                continue;
            }
            candidates.add(type);
        }
        return candidates;
    }

    private String gameRow(boolean isWinner, int endFrame) {
        BuildOrder buildOrder = gameState.getActiveBuildOrder();

        List<String> fields = new ArrayList<>();
        fields.add(gameId);
        fields.add(Csv.sanitize(game.mapFileName()));
        fields.add(Csv.sanitize(game.enemy().getName()));
        fields.add(Csv.name(gameState.getOpponentRace()));
        fields.add(buildOrder != null ? Csv.sanitize(buildOrder.getName()) : "NONE");
        fields.add(isWinner ? "1" : "0");
        fields.add(String.valueOf(endFrame));
        fields.add(Csv.format(game.getAverageFPS()));
        fields.add(String.valueOf(engagementCount));
        fields.add(String.valueOf(totalUnitsLost));
        fields.add(String.valueOf(totalSupplyLost));
        fields.add(String.valueOf(killedUnits));
        fields.add(String.valueOf(killedSupply));
        fields.add(String.valueOf(SAMPLE_INTERVAL_FRAMES));
        fields.add(String.valueOf(CONTACT_RADIUS));
        fields.add(String.valueOf(MERGE_RADIUS));
        fields.add(String.valueOf(CLOSE_COOLDOWN_FRAMES));
        return String.join(",", fields);
    }
}
