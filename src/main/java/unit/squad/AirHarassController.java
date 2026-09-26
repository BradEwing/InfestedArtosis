package unit.squad;

import bwapi.Game;
import bwapi.Position;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwem.Base;
import info.GameState;
import info.map.HarassHeatMap;
import info.tracking.ObservedUnit;
import telemetry.HarassRow;
import telemetry.HarassTelemetry;
import unit.managed.ManagedUnit;
import unit.managed.UnitRole;
import util.Filter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Runs the air harass in a live game: reads the enemy and the harassment heat map, asks
 * {@link AirHarassEvaluator} and {@link AirHarassTargeting} for every decision, turns those into orders for the
 * Mutalisks, and records telemetry_harass.csv. {@link SquadManager} owns the squad's status and calls in through
 * {@link #checkEntry}, {@link #start}, {@link #tick}, {@link #stop} and {@link #onUnitDestroy}.
 */
public class AirHarassController {

    /** Tuning value: frames between two steps of the harassment heat map. */
    static final int HEAT_INTERVAL = 24;
    /**
     * Tuning value: heat a base's strike point must hold before a harass starts on it, so a squad that just left a
     * base it had cleared does not turn straight back to it.
     */
    static final double MIN_ENTRY_HEAT = 60;
    /** Tuning value: pixels past a Mutalisk's range within which a death near a harassing Mutalisk is credited. */
    static final int KILL_CREDIT_MARGIN = 32;

    private final Game game;
    private final GameState gameState;
    private final Map<Base, Set<TilePosition>> resourceTiles = new HashMap<>();

    public AirHarassController(Game game, GameState gameState) {
        this.game = game;
        this.gameState = gameState;
    }

    /**
     * The outcome of an entry check: the verdict, and the base and strike point a harass would start on.
     */
    public static final class Entry {
        private final AirHarassEvaluator.EntryVerdict verdict;
        private final AirHarassEvaluator.BaseOption<Base> option;

        Entry(AirHarassEvaluator.EntryVerdict verdict, AirHarassEvaluator.BaseOption<Base> option) {
            this.verdict = verdict;
            this.option = option;
        }

        public boolean enters() {
            return verdict == AirHarassEvaluator.EntryVerdict.ENTER && option != null;
        }
    }

    /**
     * Steps the harassment heat map every {@link #HEAT_INTERVAL} frames: every known enemy base heats up, and the
     * ground within sight of each harassing Mutalisk is zeroed.
     *
     * @param now current frame
     * @param squads fight squads
     */
    public void onFrame(int now, Collection<Squad> squads) {
        if (now % HEAT_INTERVAL != 0) {
            return;
        }
        HarassHeatMap heatMap = gameState.getGameMap().getHarassHeatMap();
        List<Position> centers = new ArrayList<>();
        Set<TilePosition> resources = new HashSet<>();
        for (Base base : gameState.getBaseData().getEnemyBases()) {
            centers.add(base.getCenter());
            resources.addAll(resourceTilesOf(base));
        }
        heatMap.accumulate(centers, resources::contains);
        int sight = UnitType.Zerg_Mutalisk.sightRange() / 32;
        for (Squad squad : squads) {
            if (squad.getStatus() != SquadStatus.HARASS) {
                continue;
            }
            for (ManagedUnit member : squad.getMembers()) {
                heatMap.visit(member.getPosition(), sight);
            }
        }
    }

    /**
     * Runs the entry gates for an air squad and records the verdict. The base options are only read for a squad
     * that passes the cheap gates.
     *
     * @param squad air squad
     * @param now current frame
     * @param basesUnderAttack true when a base of ours is under attack
     * @param containPoints midpoints of the containment arcs held
     * @return the verdict and the base a harass would start on
     */
    public Entry checkEntry(Squad squad, int now, boolean basesUnderAttack, List<Position> containPoints) {
        Flock flock = flock(squad);
        double tolerance = AirHarassEvaluator.tolerance(flock.healthy);
        boolean cheapGatesPass = AirHarassEvaluator.harassMatchup(gameState.getOpponentRace())
                && AirHarassEvaluator.mutalisksOnly(squad.getComposition())
                && flock.healthy >= AirHarassEvaluator.MIN_HEALTHY_MUTAS
                && !basesUnderAttack;
        List<AirHarassEvaluator.BaseOption<Base>> options = new ArrayList<>();
        List<AirHarassTargeting.AirThreat> threats = new ArrayList<>();
        if (cheapGatesPass) {
            threats = view(now).threats;
            options = options(threats, tolerance, containPoints, gameState.getBaseData().getEnemyBases(),
                    MIN_ENTRY_HEAT);
        }
        AirHarassEvaluator.EntryVerdict verdict = AirHarassEvaluator.entryVerdict(
                AirHarassEvaluator.EntryInput.builder()
                        .opponentRace(gameState.getOpponentRace())
                        .composition(squad.getComposition())
                        .healthyMutas(flock.healthy)
                        .basesUnderAttack(basesUnderAttack)
                        .options(new ArrayList<>(options))
                        .build());
        AirHarassEvaluator.BaseOption<Base> chosen = verdict == AirHarassEvaluator.EntryVerdict.ENTER
                ? AirHarassEvaluator.chooseBase(options)
                : null;
        HarassTelemetry.row(HarassRow.builder()
                .frame(now)
                .squadId(squad.getId())
                .event(HarassRow.Event.ENTRY_CHECK)
                .verdict(verdict)
                .base(chosen == null ? null : chosen.getBase().getCenter())
                .strikePoint(chosen == null ? null : chosen.getStrikePoint())
                .center(squad.getCenter())
                .mutas(flock.mutas)
                .healthyMutas(flock.healthy)
                .flockHitPoints(flock.hitPoints)
                .tolerance(tolerance)
                .airDefense(chosen == null ? -1 : AirHarassTargeting.defenseAt(threats, chosen.getStrikePoint(),
                        AirHarassEvaluator.STRIKE_RADIUS))
                .containDistance(nearestDistance(squad.getCenter(), containPoints))
                .basesUnderAttack(basesUnderAttack ? 1 : 0)
                .build());
        return new Entry(verdict, chosen);
    }

    /**
     * Starts a harass on the entry's base and hands every Mutalisk the HARASS role.
     *
     * @param squad squad entering HARASS
     * @param entry the entry that passed
     * @param now current frame
     */
    public void start(Squad squad, Entry entry, int now) {
        Flock flock = flock(squad);
        AirHarassState state = new AirHarassState(now, flock.hitPoints);
        state.target(entry.option.getBase(), entry.option.getStrikePoint(), now);
        state.setLastTickFrame(now - AirHarassEvaluator.HARASS_TICK);
        squad.setHarassState(state);
        for (ManagedUnit member : squad.getMembers()) {
            member.setRole(UnitRole.HARASS);
            member.setFightTarget(null);
            member.setContainPosition(null);
            member.setRetreatTarget(null);
            member.setHarassDestination(entry.option.getStrikePoint());
        }
        HarassTelemetry.row(row(squad, state, HarassRow.Event.ENTER, now)
                .mutas(flock.mutas)
                .healthyMutas(flock.healthy)
                .flockHitPoints(flock.hitPoints)
                .tolerance(AirHarassEvaluator.tolerance(flock.healthy))
                .build());
    }

    /**
     * Runs one frame of a harassing squad: a squad decision every {@link AirHarassEvaluator#HARASS_TICK} frames,
     * then an order for every Mutalisk.
     *
     * @param squad harassing squad
     * @param now current frame
     * @param basesUnderAttack true when a base of ours is under attack
     * @param containPoints midpoints of the containment arcs held
     * @return why the harass ends this frame, or null to keep harassing
     */
    public AirHarassEvaluator.ExitReason tick(Squad squad, int now, boolean basesUnderAttack,
                                              List<Position> containPoints) {
        AirHarassState state = squad.getHarassState();
        if (squad.size() == 0) {
            return null;
        }
        if (state == null || state.getTargetBase() == null) {
            return AirHarassEvaluator.ExitReason.NO_TARGET;
        }
        View view = view(now);
        Flock flock = flock(squad);
        double tolerance = AirHarassEvaluator.tolerance(flock.healthy);
        if (AirHarassEvaluator.decisionTickDue(now, state.getLastTickFrame())) {
            state.setLastTickFrame(now);
            AirHarassEvaluator.ExitReason reason = decisionTick(squad, state, view, flock, tolerance,
                    basesUnderAttack, containPoints, now);
            if (reason != null) {
                return reason;
            }
        }
        assignOrders(squad, state, view, AirHarassTargeting.avoided(view.threats, tolerance), flock.mutas, now);
        return null;
    }

    /**
     * Ends a harass: records the EXIT row and clears every Mutalisk's harass order. The squad's status is left to
     * the caller.
     *
     * @param squad harassing squad
     * @param reason why the harass ended
     * @param now current frame
     */
    public void stop(Squad squad, AirHarassEvaluator.ExitReason reason, int now) {
        AirHarassState state = squad.getHarassState();
        Flock flock = flock(squad);
        HarassRow.HarassRowBuilder row = state == null
                ? HarassRow.builder().frame(now).squadId(squad.getId()).event(HarassRow.Event.EXIT)
                : row(squad, state, HarassRow.Event.EXIT, now)
                        .hpLossFraction(AirHarassEvaluator.hpLossFraction(state.getStartHitPoints(), flock.hitPoints));
        HarassTelemetry.row(row
                .exitReason(reason)
                .center(squad.getCenter())
                .mutas(flock.mutas)
                .healthyMutas(flock.healthy)
                .flockHitPoints(flock.hitPoints)
                .build());
        for (ManagedUnit member : squad.getMembers()) {
            member.setHarassDestination(null);
            member.setFightTarget(null);
        }
    }

    /**
     * Books a death against a harassing squad: a member's death as a Mutalisk lost, and an enemy's death as a kill
     * when a member was attacking it or stood within a Mutalisk's range plus {@link #KILL_CREDIT_MARGIN} of it. Must
     * run before a dead member is removed from its squad.
     *
     * @param unit destroyed unit
     * @param squads fight squads
     */
    public void onUnitDestroy(Unit unit, Collection<Squad> squads) {
        int now = game.getFrameCount();
        boolean ours = unit.getPlayer() == game.self();
        if (!ours && !game.self().isEnemy(unit.getPlayer())) {
            return;
        }
        int creditRadius = UnitType.Zerg_Mutalisk.groundWeapon().maxRange() + KILL_CREDIT_MARGIN;
        Position position = unit.getPosition();
        for (Squad squad : squads) {
            AirHarassState state = squad.getHarassState();
            if (squad.getStatus() != SquadStatus.HARASS || state == null) {
                continue;
            }
            for (ManagedUnit member : squad.getMembers()) {
                if (ours && member.getUnit() == unit) {
                    state.creditLoss();
                    HarassTelemetry.row(row(squad, state, HarassRow.Event.MUTA_LOST, now)
                            .center(position)
                            .build());
                    return;
                }
                if (!ours && (member.fightTarget == unit
                        || member.getPosition().getDistance(position) <= creditRadius)) {
                    state.creditKill(killKind(unit.getType()));
                    HarassTelemetry.row(row(squad, state, HarassRow.Event.KILL, now)
                            .center(position)
                            .killedType(unit.getType())
                            .build());
                    return;
                }
            }
        }
    }

    /**
     * What a credited kill counts as.
     *
     * @param type killed type
     * @return worker, building or other
     */
    static AirHarassState.KillKind killKind(UnitType type) {
        if (Filter.isWorkerType(type)) {
            return AirHarassState.KillKind.WORKER;
        }
        return type.isBuilding() ? AirHarassState.KillKind.BUILDING : AirHarassState.KillKind.OTHER;
    }

    private AirHarassEvaluator.ExitReason decisionTick(Squad squad, AirHarassState state, View view, Flock flock,
                                                       double tolerance, boolean basesUnderAttack,
                                                       List<Position> containPoints, int now) {
        HarassHeatMap heatMap = gameState.getGameMap().getHarassHeatMap();
        Base base = state.getTargetBase();
        boolean targetGone = !gameState.getBaseData().getEnemyBases().contains(base);
        Position strike = targetGone ? null : heatMap.hottestNear(base.getCenter(), tolerated(view.threats, tolerance));
        boolean heated = !targetGone && heatMap.hottestNear(base.getCenter(), point -> true) != null;
        double hpLoss = AirHarassEvaluator.hpLossFraction(state.getStartHitPoints(), flock.hitPoints);
        double flockDefense = AirHarassTargeting.defenseAt(view.threats, squad.getCenter(), 0);
        AirHarassEvaluator.ExitReason reason = AirHarassEvaluator.exitReason(AirHarassEvaluator.ExitInput.builder()
                .basesUnderAttack(basesUnderAttack)
                .healthyMutas(flock.healthy)
                .hpLossFraction(hpLoss)
                .strikeDefended(heated && strike == null)
                .flockDefense(flockDefense)
                .tolerance(tolerance)
                .build());
        if (reason != null) {
            return reason;
        }
        if (strike != null) {
            state.setStrikePoint(strike);
        }
        if (!state.hasArrived() && AirHarassEvaluator.arrived(squad.getCenter(), state.getStrikePoint())) {
            state.arrive(now);
        }
        if (AirHarassEvaluator.shouldRetarget(targetGone, heated, state.hasArrived(), now,
                state.getLastProgressFrame()) && !retarget(squad, state, view.threats, tolerance, containPoints, now)) {
            return AirHarassEvaluator.ExitReason.NO_TARGET;
        }
        HarassTelemetry.row(row(squad, state, HarassRow.Event.TICK, now)
                .center(squad.getCenter())
                .mutas(flock.mutas)
                .healthyMutas(flock.healthy)
                .flockHitPoints(flock.hitPoints)
                .hpLossFraction(hpLoss)
                .tolerance(tolerance)
                .airDefense(AirHarassTargeting.defenseAt(view.threats, state.getStrikePoint(),
                        AirHarassEvaluator.STRIKE_RADIUS))
                .avoidedZones(AirHarassTargeting.avoided(view.threats, tolerance).size())
                .containDistance(nearestDistance(squad.getCenter(), containPoints))
                .basesUnderAttack(basesUnderAttack ? 1 : 0)
                .build());
        return null;
    }

    /**
     * Moves a harass on to the best known enemy base it has not raided yet this episode.
     *
     * @return true when a base was found
     */
    private boolean retarget(Squad squad, AirHarassState state, List<AirHarassTargeting.AirThreat> threats,
                             double tolerance, List<Position> containPoints, int now) {
        Set<Base> candidates = new HashSet<>(gameState.getBaseData().getEnemyBases());
        candidates.removeAll(state.getVisitedBases());
        AirHarassEvaluator.BaseOption<Base> next = AirHarassEvaluator.chooseBase(
                options(threats, tolerance, containPoints, candidates, 0));
        if (next == null) {
            return false;
        }
        state.target(next.getBase(), next.getStrikePoint(), now);
        HarassTelemetry.row(row(squad, state, HarassRow.Event.RETARGET, now).center(squad.getCenter()).build());
        return true;
    }

    private List<AirHarassEvaluator.BaseOption<Base>> options(List<AirHarassTargeting.AirThreat> threats,
                                                              double tolerance, List<Position> containPoints,
                                                              Collection<Base> bases, double minHeat) {
        HarassHeatMap heatMap = gameState.getGameMap().getHarassHeatMap();
        Predicate<Position> tolerated = tolerated(threats, tolerance);
        List<AirHarassEvaluator.BaseOption<Base>> options = new ArrayList<>();
        for (Base base : bases) {
            Position center = base.getCenter();
            Position hottest = heatMap.hottestNear(center, point -> true);
            boolean heated = hottest != null && heatMap.heatAt(hottest.toTilePosition()) >= minHeat;
            Position strike = heated ? heatMap.hottestNear(center, tolerated) : null;
            double heat = strike == null ? 0 : heatMap.heatAt(strike.toTilePosition());
            if (heat < minHeat) {
                strike = null;
            }
            options.add(new AirHarassEvaluator.BaseOption<>(base, strike, heat, heated,
                    nearestDistance(center, containPoints)));
        }
        return options;
    }

    private static Predicate<Position> tolerated(List<AirHarassTargeting.AirThreat> threats, double tolerance) {
        return point -> AirHarassTargeting.defenseAt(threats, point, AirHarassEvaluator.STRIKE_RADIUS) <= tolerance;
    }

    /**
     * Gives every Mutalisk of a harassing squad that acts this frame its order. A Mutalisk still waiting on its
     * last order keeps it. Only enemies around the target base are sought out; an enemy anywhere else is taken only
     * when it is close to the Mutalisk.
     */
    private void assignOrders(Squad squad, AirHarassState state, View view,
                              List<AirHarassTargeting.AirThreat> avoided, int mutas, int now) {
        Position baseCenter = state.getTargetBase().getCenter();
        int baseRadius = HarassHeatMap.RADIUS_TILES * 32;
        int mapWidth = game.mapWidth() * 32;
        int mapHeight = game.mapHeight() * 32;
        AirHarassTargeting.Situation situation = AirHarassTargeting.Situation.builder()
                .contacts(view.contacts)
                .avoided(avoided)
                .flockSize(mutas)
                .seekPoint(state.getStrikePoint())
                .targetAllowed(point -> point.getDistance(baseCenter) <= baseRadius)
                .pointAllowed(point -> point.getX() >= 0 && point.getY() >= 0 && point.getX() < mapWidth
                        && point.getY() < mapHeight)
                .now(now)
                .build();
        for (ManagedUnit member : squad.getMembers()) {
            if (member.getRole() != UnitRole.HARASS) {
                member.setRole(UnitRole.HARASS);
            }
            if (!member.isReady() && now < member.getUnreadyUntilFrame()) {
                continue;
            }
            AirHarassTargeting.Decision decision = AirHarassTargeting.choose(
                    new AirHarassTargeting.Muta(member.getUnitID(), member.getPosition()), situation,
                    state.memoryFor(member.getUnitID()));
            Unit target = decision.getKind() == AirHarassTargeting.Kind.ATTACK
                    ? view.units.get(decision.getTargetId())
                    : null;
            if (target != null) {
                member.setHarassDestination(null);
                member.setFightTarget(target);
                state.setLastProgressFrame(now);
                continue;
            }
            member.setFightTarget(null);
            member.setHarassDestination(decision.getPoint() != null ? decision.getPoint() : state.getStrikePoint());
        }
    }

    /**
     * Reads the enemy once for a harass frame: every visible enemy a Mutalisk could attack, and every known
     * anti-air threat. Structures count wherever they were last seen; a mobile unit only while its observation is
     * fresh.
     */
    private View view(int now) {
        View view = new View();
        for (Unit enemy : gameState.getVisibleEnemyUnits()) {
            UnitType type = enemy.getType();
            if (!enemy.isDetected() || !enemy.isTargetable() || Filter.isLowPriorityCombatTarget(type)) {
                continue;
            }
            int pool = type.maxHitPoints() + type.maxShields();
            int hitPoints = enemy.getHitPoints() + enemy.getShields();
            double fraction = pool <= 0 ? 1.0 : (double) hitPoints / pool;
            view.contacts.add(new AirHarassTargeting.Contact(enemy.getID(), type, enemy.getPosition(), hitPoints,
                    fraction));
            view.units.put(enemy.getID(), enemy);
        }
        for (ObservedUnit observed : gameState.getObservedUnitTracker().getLivingObservedUnits()) {
            UnitType type = observed.getUnitType();
            Position position = observed.getCurrentOrLastKnownPosition();
            if (!AirHarassTargeting.isAntiAir(type) || position == null) {
                continue;
            }
            Unit unit = observed.getUnit();
            boolean visible = unit.isVisible();
            if (!type.isBuilding()
                    && !RunbyEvaluator.isFresh(visible, observed.getLastObservedFrame().getFrames(), now)) {
                continue;
            }
            int range = AirHarassTargeting.airRange(type,
                    weapon -> visible ? unit.getPlayer().weaponMaxRange(weapon) : weapon.maxRange());
            view.threats.add(AirHarassTargeting.AirThreat.of(unit.getID(), type, position, range));
        }
        return view;
    }

    private Set<TilePosition> resourceTilesOf(Base base) {
        return resourceTiles.computeIfAbsent(base, b -> {
            Set<TilePosition> tiles = new HashSet<>();
            for (bwem.Mineral mineral : b.getMinerals()) {
                addFootprint(tiles, mineral.getTopLeft(), mineral.getBottomRight());
            }
            for (bwem.Geyser geyser : b.getGeysers()) {
                addFootprint(tiles, geyser.getTopLeft(), geyser.getBottomRight());
            }
            return tiles;
        });
    }

    private static void addFootprint(Set<TilePosition> tiles, TilePosition topLeft, TilePosition bottomRight) {
        for (int x = topLeft.getX(); x <= bottomRight.getX(); x++) {
            for (int y = topLeft.getY(); y <= bottomRight.getY(); y++) {
                tiles.add(new TilePosition(x, y));
            }
        }
    }

    private static double nearestDistance(Position from, List<Position> points) {
        if (from == null || points.isEmpty()) {
            return -1;
        }
        double nearest = Double.MAX_VALUE;
        for (Position point : points) {
            nearest = Math.min(nearest, from.getDistance(point));
        }
        return nearest;
    }

    private static HarassRow.HarassRowBuilder row(Squad squad, AirHarassState state, HarassRow.Event event, int now) {
        return HarassRow.builder()
                .frame(now)
                .squadId(squad.getId())
                .event(event)
                .phase(state.getPhase())
                .base(state.getTargetBase() == null ? null : state.getTargetBase().getCenter())
                .strikePoint(state.getStrikePoint())
                .workersKilled(state.getWorkersKilled())
                .buildingsKilled(state.getBuildingsKilled())
                .otherKilled(state.getOtherKilled())
                .mutasLost(state.getMutasLost());
    }

    private static Flock flock(Squad squad) {
        Flock flock = new Flock();
        for (ManagedUnit member : squad.getMembers()) {
            if (member.getUnitType() != UnitType.Zerg_Mutalisk) {
                continue;
            }
            int hitPoints = member.getUnit().getHitPoints();
            flock.mutas++;
            flock.hitPoints += hitPoints;
            if (AirHarassEvaluator.isHealthy(hitPoints, UnitType.Zerg_Mutalisk.maxHitPoints())) {
                flock.healthy++;
            }
        }
        return flock;
    }

    /**
     * The squad's Mutalisks on one frame.
     */
    private static final class Flock {
        private int mutas;
        private int healthy;
        private int hitPoints;
    }

    /**
     * The enemy as a harass reads it on one frame.
     */
    private static final class View {
        private final List<AirHarassTargeting.Contact> contacts = new ArrayList<>();
        private final Map<Integer, Unit> units = new HashMap<>();
        private final List<AirHarassTargeting.AirThreat> threats = new ArrayList<>();
    }
}
