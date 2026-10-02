package unit.squad;

import bwapi.Game;
import bwapi.Position;
import bwapi.Race;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwapi.WeaponType;
import bwapi.WalkPosition;
import bwem.Base;
import bwem.CPPath;
import info.GameState;
import info.ScoutData;
import info.map.BaseArea;
import info.tracking.DarkSwarm;
import info.tracking.EnemyReachMemory;
import info.tracking.ObservedUnit;
import info.tracking.ObservedUnitTracker;
import info.tracking.PsiStormTracker;
import info.tracking.StrategyTracker;
import info.tracking.protoss.ProxyGate;
import lombok.Getter;

import org.bk.ass.sim.Agent;
import org.bk.ass.sim.BWMirrorAgentFactory;
import org.bk.ass.sim.Simulator;
import telemetry.DecisionPath;
import telemetry.DefenseEvent;
import telemetry.FixedFireTelemetry;
import telemetry.RallyReason;
import telemetry.RallyRelease;
import telemetry.RetreatRoute;
import telemetry.RunbyTelemetry;
import telemetry.RunbyTick;
import telemetry.SquadDecisions;
import telemetry.SquadLock;
import telemetry.SwarmEvent;
import telemetry.TargetChoices;
import unit.managed.Lurker;
import unit.managed.ManagedUnit;
import unit.squad.horizon.HorizonCombatSimulator;
import unit.managed.UnitRole;
import util.Arc;
import util.Filter;
import util.StaticDefenseZone;
import util.MeleeOverflowGate;
import util.TargetLedger;
import util.Vec2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import util.TargetScorer;

import static java.lang.Math.min;
import static util.Distance.manhattanTileDistance;

public class SquadManager {

    private Game game;
    private GameState gameState;

    private BWMirrorAgentFactory agentFactory;
    private ContainmentEvaluator containmentEvaluator;
    private final ContainmentEscalation containmentEscalation = new ContainmentEscalation();

    private Squad overlords = new Squad();

    public HashSet<Squad> fightSquads = new HashSet<>();

    @Getter
    private HashMap<Base, Squad> defenseSquads = new HashMap<>();

    private HashMap<Base, Integer> defenseAbandonedUntilFrame = new HashMap<>();

    @Getter
    private List<Arc> activeContainmentArcs = new ArrayList<>();

    private HashSet<ManagedUnit> disbanded = new HashSet<>();
    private HashSet<ManagedUnit> irradiatedUnits = new HashSet<>();

    public static final double AIR_JOIN_DISTANCE = 128;
    public static final double SQUAD_MERGE_DISTANCE = 256.0;
    private static final double ENEMY_DETECTION_RADIUS = 512.0;

    private static final Set<UnitType> GROUND_SQUAD_TYPES = new HashSet<>();

    static {
        GROUND_SQUAD_TYPES.add(UnitType.Zerg_Zergling);
        GROUND_SQUAD_TYPES.add(UnitType.Zerg_Hydralisk);
        GROUND_SQUAD_TYPES.add(UnitType.Zerg_Lurker);
        GROUND_SQUAD_TYPES.add(UnitType.Zerg_Ultralisk);
        GROUND_SQUAD_TYPES.add(UnitType.Zerg_Defiler);
    }

    private static final Set<UnitType> AIR_SQUAD_TYPES = new HashSet<>();

    static {
        AIR_SQUAD_TYPES.add(UnitType.Zerg_Mutalisk);
        AIR_SQUAD_TYPES.add(UnitType.Zerg_Scourge);
        AIR_SQUAD_TYPES.add(UnitType.Zerg_Guardian);
        AIR_SQUAD_TYPES.add(UnitType.Zerg_Devourer);
    }

    /** Tuning value: air combat units a squad needs to move out against Protoss, Terran or an unknown race. */
    static final int AIR_MOVE_OUT_UNITS = 5;
    /** Tuning value: air combat units a squad needs to move out against Zerg. */
    static final int AIR_MOVE_OUT_UNITS_VS_ZERG = 2;
    /** Tuning value: Scourge a Scourge only squad needs to move out, against any race. */
    static final int SCOURGE_MOVE_OUT_UNITS = 2;
    /**
     * Tuning value: ground path length in pixels from the squad centre to the closest base held within which an air
     * squad under its move out threshold still simulates close threats. Air distance stands in when no base is
     * reachable on the ground from the centre.
     */
    static final int AIR_HOME_DEFENSE_RADIUS = 480;

    private static final int RETREAT_VECTOR_MAGNITUDE = 192;
    /**
     * Tuning value: frames a ground retreat plan is kept before it is made again, which bounds the path searches a
     * retreating squad costs to one per this many frames.
     */
    static final int RETREAT_REPLAN_FRAMES = 8;
    private static final int COMBAT_SIM_DURATION_FRAMES = 150;
    private static final double DEFENSE_WIN_THRESHOLD = 0.50;
    private static final double SCV_RUSH_DEFENSE_CLEAR_THRESHOLD = 0.75;
    private static final int MERGE_CHECK_INTERVAL = 50;
    private static final int DEFENSE_SIM_RANGE = 256;
    private static final int CONTAINMENT_REEVALUATE_INTERVAL = 48;
    private static final int MAX_MOVE_OUT_THRESHOLD = 40;
    static final int CONTAINMENT_TIMEOUT_FRAMES = 1400;
    private static final int CONTAINMENT_ENGAGE_RADIUS = 256;
    private static final int ARC_DEGREES = 90;
    private static final int ARC_RADIUS = 160;
    private static final int MAX_ARC_DEGREES = 180;
    private static final int MIN_ARC_POINTS = 4;
    private static final int MAX_SPACED_RADIUS = ContainmentPushback.MAX_RADIUS - ContainmentPushback.RADIUS_STEP;
    private static final int CONTAIN_DEFENSE_MARGIN = 32;
    private static final double REINFORCEMENT_RADIUS = 384.0;
    private static final int TARGETING_RADIUS = 256;
    private static final int WIDENED_TARGETING_RADIUS = 2 * TARGETING_RADIUS;
    /**
     * Stands in for the id of a fight target when a unit holds none that still exists.
     */
    static final int NO_TARGET_ID = -1;
    /**
     * Distance within which a melee attacker's pick counts as within reach, so an unsaturated re-target lets an
     * attacker in overflow attack it directly at once. It matches the radius inside which a Zergling fighting its
     * target attacks it instead of walking to it.
     */
    static final int OVERFLOW_EXIT_REACH = 64;
    public static final int GROUND_SPLIT_DISTANCE = 256;
    public static final int AIR_SPLIT_DISTANCE = 768;
    private static final int COMMITMENT_RELEASE_DISTANCE = 512;
    /**
     * Multiple of the engage threshold an air squad's ENGAGE read must reach to break its retreat lock.
     */
    static final double RETREAT_LOCK_ENGAGE_BREAK_MULTIPLIER = 2.0;
    private static final int RUNBY_AREA_PROXIMITY_TILES = 4;
    private static final int RUNBY_AREA_TILES = 24;
    private static final int LIKELY_SPOT_SEARCH_TILES = 2;
    private static final int RUNBY_KILL_CREDIT_RADIUS = 64;
    /** Frames between the SWARM_ACTIVE rows sampled for each melee squad near one of our active Dark Swarms. */
    private static final int SWARM_SAMPLE_INTERVAL_FRAMES = 24;
    /** Tuning value: pixels from a squad's center to the nearest arc point within which it takes the arc. */
    static final int CONTAIN_ARRIVAL_DISTANCE = 320;
    /** Tuning value: ratio an ENGAGE must reach, never below the matchup threshold, to break an attrition lock. */
    static final double STRONG_ENGAGE_RATIO = 1.5;
    /** Tuning value: share of the engage threshold a measured read needs for a HOME_CONTESTED squad to defend. */
    static final double CONTESTED_HOME_DEFEND_FRACTION = 0.9;

    private final Map<Base, RunbyTarget> runbyTargets = new HashMap<>();
    private Set<ManagedUnit> outrangedHits = new HashSet<>();
    private final FixedFire fixedFire = new FixedFire();
    private List<StaticDefenseZone> fixedFireZones = Collections.emptyList();
    private final Set<Squad> wholeSquadCommits = new HashSet<>();
    private Set<Lurker> holdingLurkers = new HashSet<>();

    /**
     * Pixels a Lurker's hold point must lie clear of every fixed fire zone that outranges it for the Lurker to let
     * go of the point: the fire that sent it out has moved away or is gone. Two evade rings, so a point just
     * outside a zone is never let go of while that zone stands.
     */
    static final int HOLD_RELEASE_MARGIN = 256;
    private final Map<Integer, List<Unit>> swarmCoveredEnemies = new HashMap<>();
    private int swarmCoverFrame = -1;
    /** The swarm-priced sim read {@link #evaluateSwarmLock} took for the squad it last evaluated, or null. */
    private CombatSimulator.CombatResult swarmSimResult;
    /** The last frame {@link #evaluateSwarmLock} saw a base threat, or -1; see {@link SwarmLock#baseThreatStands}. */
    private int swarmBaseThreatFrame = -1;

    private final ScoutChase scoutChase = new ScoutChase();
    private final AirHarassController airHarass;
    private final AirReinforcer airReinforcer;

    private TargetLedger fightTargetLedger = TargetLedger.empty();
    private int fightTargetLedgerFrame = -1;

    private GroundRetreatRouter groundRetreatRouter;

    public SquadManager(Game game, GameState gameState) {
        this.game = game;
        this.gameState = gameState;
        this.agentFactory = new BWMirrorAgentFactory();
        this.containmentEvaluator = new ContainmentEvaluator(gameState);
        this.airHarass = new AirHarassController(game, gameState);
        this.airReinforcer = new AirReinforcer(game, gameState);
    }

    public void updateFightSquads() {
        disbanded.clear();
        activeContainmentArcs.clear();
        gameState.getEndgameHunt().update(gameState.getObservedUnitTracker().getLivingObservedUnits(),
                game.getFrameCount(), gameState.getSupply());
        removeEmptySquads();
        mergeSquads();
        splitSquads();
        airReinforcer.prune(fightSquads);
        rebuildScoutChase();
        evictIrradiatedUnits();
        updateIrradiatedUnits();
        assignOverlordsToSquads();

        int now = game.getFrameCount();
        outrangedHits = findOutrangedHits(now);
        fixedFireZones = FixedFire.fixedFireZones(gameState.getGroundThreatZones(now));
        recordFixedFireHurts(now);
        airHarass.onFrame(now, fightSquads);
        updateStalemateCommit(now);
        Set<Squad> removed = new HashSet<>();
        for (Squad fightSquad: fightSquads) {
            fightSquad.onFrame();
            if (fightSquad.shouldDisband()) {
                disbanded.addAll(disbandSquad(fightSquad));
                removed.add(fightSquad);
            }

            evaluateSquadRole(fightSquad);
            sampleSwarm(fightSquad, now);

            for (ManagedUnit mu : fightSquad.getMembers()) {
                if (mu.getUnitType() == UnitType.Zerg_Overlord) {
                    if (gameState.getTechProgression().isOverlordSpeed()) {
                        mu.setRallyPoint(fightSquad.getCenter());
                        mu.setRole(UnitRole.RALLY);
                    } else {
                        fightSquad.removeUnit(mu);
                        overlords.addUnit(mu);
                        mu.setRole(UnitRole.IDLE);
                    }
                }
            }
        }

        fightSquads.removeAll(removed);
        evadeOutrangedHits(now);
        updateLurkerFixedFire();
        holdLurkersOutOfFire(now);
        gameState.getContainHeldTimer().update(now, anyGroundSquadContaining(fightSquads));
    }

    /**
     * Cools every fixed fire zone a ground member of a fight squad was hurt inside this frame, among those that
     * outrange it, see {@link FixedFire#appliesTo}. A member losing hit points to a Psionic Storm or irradiation is
     * not read as hurt by the zone.
     *
     * @param now current frame
     */
    private void recordFixedFireHurts(int now) {
        fixedFire.expire(now);
        if (fixedFireZones.isEmpty()) {
            return;
        }
        for (Squad squad : fightSquads) {
            for (ManagedUnit member : squad.getMembers()) {
                UnitType type = member.getUnitType();
                if (!FixedFire.appliesTo(type) || !member.wasHitOn(now) || gameState.isTakingNonWeaponDamage(member)) {
                    continue;
                }
                List<StaticDefenseZone> zones = ContainmentPushback.outrangingZones(fixedFireZones,
                        EnemyReachMemory.baseGroundRange(type));
                Position position = member.getPosition();
                for (StaticDefenseZone started : fixedFire.recordHurt(position, zones,
                        containmentDefensePadding(Collections.singletonList(type)), now)) {
                    FixedFireTelemetry.cooldownStarted(now, member.getUnitID(), type, position, started);
                }
            }
        }
    }

    /**
     * Whether a squad is committing to the fight, so that no target of a fighter other than a Lurker is skipped for
     * standing in cooling fixed fire: it is fighting on an ENGAGE verdict or under its fight lock. Lurkers commit on
     * their own rule, see {@link LurkerHold#lurkersCommit}.
     *
     * @param status the squad's status
     * @param fightLocked whether the squad's fight lock is active
     * @param lastVerdict the sim's last verdict for the squad, or null
     * @return true when the squad is committing
     */
    static boolean isCommitting(SquadStatus status, boolean fightLocked, CombatSimulator.CombatResult lastVerdict) {
        return status == SquadStatus.FIGHT
                && (fightLocked || lastVerdict == CombatSimulator.CombatResult.ENGAGE);
    }

    private boolean isCommitting(Squad squad, int now) {
        HorizonCombatSimulator.DebugSnapshot snapshot = lastSnapshot(squad);
        return isCommitting(squad.getStatus(), squad.isFightLocked(now), snapshot == null ? null : snapshot.getResult());
    }

    /**
     * Whether a squad is committed as a whole, breaking its contain, collapsing or under a stalemate commit: it was
     * marked so on the break, the collapse or the stalemate commit and {@link #wholeSquadCommitHolds} still holds.
     *
     * @param squad the squad
     * @param now current frame
     * @return true while the whole squad is committed
     */
    private boolean wholeSquadCommit(Squad squad, int now) {
        ContainmentStalemate stalemate = gameState.getContainmentStalemate();
        return wholeSquadCommits.contains(squad) && wholeSquadCommitHolds(squad, now,
                ContainmentStalemate.takesOver(squad.isGroundSquad(), squad.getStatus(), stalemate.isCommitting(),
                        stalemate.isCommitPaused()));
    }

    /**
     * Whether a squad marked on a contain break, a collapse or a stalemate commit is still committed as a whole: it
     * is in FIGHT and under its fight lock, in a collapse's wrap, held by a committed collapse, see
     * {@link Squad#isCollapseCommitHeld}, or run by a stalemate commit. A wrap that outlasts the fight lock it armed
     * keeps the mark, and so does a stalemate commit that outlasts it.
     *
     * @param squad the squad
     * @param now current frame
     * @param stalemateCommit whether a running, unpaused stalemate commit runs the squad this frame
     * @return true while the whole-squad commit holds
     */
    static boolean wholeSquadCommitHolds(Squad squad, int now, boolean stalemateCommit) {
        return squad.getStatus() == SquadStatus.FIGHT
                && (squad.isFightLocked(now) || squad.getCollapse() != null || squad.isCollapseCommitHeld(now)
                || stalemateCommit);
    }

    /**
     * @param squad the squad
     * @param now current frame
     * @return the sim's snapshot for the squad when it was read this frame, or null
     */
    private HorizonCombatSimulator.DebugSnapshot freshSnapshot(Squad squad, int now) {
        HorizonCombatSimulator.DebugSnapshot snapshot = lastSnapshot(squad);
        return snapshot != null && snapshot.getCapturedFrame() == now ? snapshot : null;
    }

    /**
     * Whether a squad's Lurkers commit with it this frame, see {@link LurkerHold#lurkersCommit}.
     *
     * @param squad the squad
     * @param now current frame
     * @return true when its Lurkers commit
     */
    private boolean lurkersCommit(Squad squad, int now) {
        return lurkersCommit(squad.getStatus(), wholeSquadCommit(squad, now), freshSnapshot(squad, now));
    }

    /**
     * Whether a squad's Lurkers commit on this frame's sim read, see {@link LurkerHold#lurkersCommit}.
     *
     * @param status the squad's status
     * @param wholeSquadCommit whether the squad is committed as a whole
     * @param snapshot the sim's snapshot read this frame, or null when it was not read this frame
     * @return true when the squad's Lurkers commit
     */
    static boolean lurkersCommit(SquadStatus status, boolean wholeSquadCommit,
                                 HorizonCombatSimulator.DebugSnapshot snapshot) {
        return LurkerHold.lurkersCommit(status, wholeSquadCommit, snapshot == null ? null : snapshot.getResult());
    }

    /**
     * The zones among {@code zones} a squad's Lurkers keep out of this frame, see {@link LurkerHold#keptOut}.
     *
     * @param squad the squad
     * @param zones fixed fire zones that outrange a Lurker
     * @param now current frame
     * @return the zones its Lurkers keep out of
     */
    private List<StaticDefenseZone> lurkerKeptOutZones(Squad squad, List<StaticDefenseZone> zones, int now) {
        return LurkerHold.keptOut(zones, lurkersCommit(squad, now), wholeSquadCommit(squad, now),
                pricedSiegedTanks(freshSnapshot(squad, now)));
    }

    /**
     * @param snapshot the sim's snapshot, or null
     * @return positions of the sieged tanks the snapshot priced with a strength above zero
     */
    private static List<Position> pricedSiegedTanks(HorizonCombatSimulator.DebugSnapshot snapshot) {
        if (snapshot == null) {
            return Collections.emptyList();
        }
        List<Position> priced = new ArrayList<>();
        for (HorizonCombatSimulator.UnitDebugEntry entry : snapshot.getEnemyUnits()) {
            if (entry.getType() == UnitType.Terran_Siege_Tank_Siege_Mode && entry.getStrength() > 0) {
                priced.add(entry.getPosition());
            }
        }
        return priced;
    }

    /**
     * Pulls Lurkers back out of fixed fire and holds them there. A Lurker in a fighting or retreating ground squad is
     * given a point clear of every zone its squad's Lurkers keep out of, see {@link #lurkerKeptOutZones}, when it is
     * hurt inside a sieged tank's reach among them, or when it retreats inside one, see {@link FixedFire#holdPoint}.
     * It then holds that point in its RETREAT role, see {@link Lurker#holdStep}. The hold is let go of: CLEAR when
     * the point lies more than {@link #HOLD_RELEASE_MARGIN} clear of every fixed fire zone that outranges it; COMMIT
     * when its squad's Lurkers commit and the point lies that far clear of every zone they still keep out of; STATUS
     * when its squad leaves FIGHT and RETREAT. A point the fire has moved onto is found again, and the Lurker is moved
     * to it only when it stands {@link LurkerHold#MOVE_GAIN} further out, see {@link LurkerHold#worthMoving}. A Lurker
     * listed by two fight squads is visited once, for the first of them. A Lurker that held a point last frame and is
     * no longer in any fight squad lets go of it (STATUS), see {@link LurkerHold#leftBehind}.
     *
     * @param now current frame
     */
    private void holdLurkersOutOfFire(int now) {
        wholeSquadCommits.removeIf(squad -> !fightSquads.contains(squad) || !wholeSquadCommit(squad, now));
        Predicate<Position> allowed = walkablePoints();
        int padding = containmentDefensePadding(Collections.singletonList(UnitType.Zerg_Lurker));
        List<StaticDefenseZone> zones = ContainmentPushback.outrangingZones(fixedFireZones,
                EnemyReachMemory.baseGroundRange(UnitType.Zerg_Lurker));
        Map<ManagedUnit, Squad> owners = LurkerHold.firstSquadOf(fightSquads, Squad::getMembers);
        Set<Lurker> visited = new HashSet<>();
        for (Map.Entry<ManagedUnit, Squad> entry : owners.entrySet()) {
            if (!(entry.getKey() instanceof Lurker)) {
                continue;
            }
            Lurker lurker = (Lurker) entry.getKey();
            visited.add(lurker);
            Squad squad = entry.getValue();
            boolean holding = squad.isGroundSquad()
                    && (squad.getStatus() == SquadStatus.FIGHT || squad.getStatus() == SquadStatus.RETREAT);
            holdLurker(lurker, holding, lurkersCommit(squad, now), zones, lurkerKeptOutZones(squad, zones, now),
                    padding, allowed, now);
        }
        for (Lurker lurker : LurkerHold.leftBehind(holdingLurkers, visited, held -> held.getUnit().exists())) {
            Position hold = lurker.getHoldPosition();
            if (hold != null) {
                lurker.clearHold();
                FixedFireTelemetry.lurkerHoldReleased(now, lurker.getUnitID(), lurker.getPosition(), hold,
                        LurkerHold.RELEASE_STATUS);
            }
        }
        holdingLurkers = visited.stream().filter(lurker -> lurker.getHoldPosition() != null)
                .collect(Collectors.toSet());
    }

    private void holdLurker(Lurker lurker, boolean holding, boolean commit, List<StaticDefenseZone> zones,
                            List<StaticDefenseZone> keptOut, int padding, Predicate<Position> allowed, int now) {
        Position position = lurker.getPosition();
        Position hold = lurker.getHoldPosition();
        String release = holding ? null : LurkerHold.RELEASE_STATUS;
        if (release == null && hold != null && RunbyTargeting.zoneMargin(hold, zones, padding) > HOLD_RELEASE_MARGIN) {
            release = LurkerHold.RELEASE_CLEAR;
        }
        if (release == null && hold != null && commit
                && RunbyTargeting.zoneMargin(hold, keptOut, padding) > HOLD_RELEASE_MARGIN) {
            release = LurkerHold.RELEASE_COMMIT;
        }
        if (release != null) {
            if (hold != null) {
                lurker.clearHold();
                FixedFireTelemetry.lurkerHoldReleased(now, lurker.getUnitID(), position, hold, release);
            }
            return;
        }
        String reason = LurkerHold.reason(hold != null && FixedFire.coveringZone(hold, keptOut, padding) != null,
                hitInsideSiegedTankReach(lurker, keptOut, now, padding),
                lurker.getRole() == UnitRole.RETREAT && FixedFire.coveringZone(position, keptOut, padding) != null,
                hold != null);
        if (reason != null) {
            Position point = FixedFire.holdPoint(position, keptOut, padding, allowed);
            if (point != null && (hold == null || LurkerHold.worthMoving(RunbyTargeting.zoneMargin(hold, keptOut,
                    padding), RunbyTargeting.zoneMargin(point, keptOut, padding)))) {
                lurker.holdAt(point);
                FixedFireTelemetry.lurkerHold(now, lurker.getUnitID(), position, point,
                        FixedFire.coveringZone(LurkerHold.MOVED.equals(reason) ? hold : position, keptOut, padding),
                        reason);
            }
        }
        if (lurker.getHoldPosition() != null) {
            lurker.setRole(UnitRole.RETREAT);
        }
    }

    private boolean hitInsideSiegedTankReach(ManagedUnit member, List<StaticDefenseZone> zones, int now,
                                             int padding) {
        if (!member.wasHitOn(now) || gameState.isTakingNonWeaponDamage(member)) {
            return false;
        }
        for (StaticDefenseZone zone : zones) {
            if (zone.getStructure() == UnitType.Terran_Siege_Tank_Siege_Mode
                    && zone.covers(member.getPosition(), padding)) {
                return true;
            }
        }
        return false;
    }

    private Predicate<Position> walkablePoints() {
        Set<WalkPosition> accessible = gameState.getGameMap().getAccessibleWalkPositions();
        int mapPixelWidth = game.mapWidth() * 32;
        int mapPixelHeight = game.mapHeight() * 32;
        return point -> isWalkable(point, accessible, mapPixelWidth, mapPixelHeight);
    }

    /**
     * @param squads the fight squads
     * @return true when any ground squad is in CONTAIN
     */
    static boolean anyGroundSquadContaining(Collection<Squad> squads) {
        for (Squad squad : squads) {
            if (squad.isGroundSquad() && squad.getStatus() == SquadStatus.CONTAIN) {
                return true;
            }
        }
        return false;
    }

    /**
     * Members of every fight squad hit this frame by something they cannot answer, read before any squad decides,
     * so a containing squad and the hit unit both react on the frame the hit is seen. A member standing in an active
     * Psionic Storm or irradiated may be losing hit points to the effect, and its drop is not read as a hit.
     *
     * @param now current frame
     * @return the members with an outranged hit
     */
    private Set<ManagedUnit> findOutrangedHits(int now) {
        Set<ManagedUnit> hit = new HashSet<>();
        for (Squad squad : fightSquads) {
            for (ManagedUnit member : squad.getMembers()) {
                if (!member.wasHitOn(now) || gameState.isTakingNonWeaponDamage(member)) {
                    continue;
                }
                if (ManagedUnit.isOutrangedHit(member.getHitPointsBefore(), member.getUnit().getHitPoints(),
                        member.getRole(), member.hasEnemyWithinReach(ManagedUnit.MELEE_MARGIN))) {
                    hit.add(member);
                }
            }
        }
        return hit;
    }

    /**
     * Moves every member with an outranged hit this frame to the point, within a short ring around it, farthest
     * outside every zone that outranges it. The move is issued this frame, ahead of the unit's ready gate. A
     * burrowed member, or one whose type cannot move, is left to keep attacking from its role, and so is a member
     * fighting or wrapping in a collapse, or held by its commit: it was sent into that fire by the collapse test.
     *
     * @param now current frame
     */
    private void evadeOutrangedHits(int now) {
        if (outrangedHits.isEmpty()) {
            return;
        }
        List<StaticDefenseZone> threats = gameState.getGroundThreatZones(now);
        Predicate<Position> allowed = walkablePoints();
        Set<ManagedUnit> collapsing = collapsingMembers(now);
        for (ManagedUnit member : outrangedHits) {
            if (collapsing.contains(member) || !member.canStepOutNow() && !member.canWithdrawNow()
                    || !ManagedUnit.evadesOutrangedHit(member.getRole(), member.isClosingOnTarget())) {
                continue;
            }
            UnitType type = member.getUnitType();
            List<StaticDefenseZone> zones = ContainmentPushback.outrangingZones(threats,
                    EnemyReachMemory.baseGroundRange(type));
            Position seek = member.getRole() == UnitRole.CONTAIN ? member.getContainPosition() : null;
            Position point = RunbyTargeting.findEvadePoint(member.getPosition(), zones,
                    containmentDefensePadding(Collections.singletonList(type)), allowed, seek);
            if (point != null) {
                member.evade(point, now);
            }
        }
    }

    /**
     * Gives every containing Lurker the ground the enemy fires on from where it stands, so a burrowed one stays put
     * when its contain point moves a short way, see {@link Lurker#staysBurrowed}.
     */
    private void updateLurkerFixedFire() {
        int padding = containmentDefensePadding(Collections.singletonList(UnitType.Zerg_Lurker));
        for (Squad squad : fightSquads) {
            for (ManagedUnit member : squad.getMembers()) {
                if (!(member instanceof Lurker) || member.getRole() != UnitRole.CONTAIN) {
                    continue;
                }
                ((Lurker) member).setFixedFireZones(fixedFireZones, padding);
            }
        }
    }

    private static boolean isWalkable(Position point, Set<WalkPosition> accessible, int mapPixelWidth,
                                      int mapPixelHeight) {
        if (point.getX() < 0 || point.getY() < 0 || point.getX() >= mapPixelWidth || point.getY() >= mapPixelHeight) {
            return false;
        }
        return accessible.isEmpty() || accessible.contains(new WalkPosition(point));
    }

    public void updateOverlordSquad() {
        TilePosition mainBaseLocation = gameState.getBaseData().mainBasePosition();
        if (overlords.getRallyPoint() == null) {
            overlords.setRallyPoint(mainBaseLocation.toPosition());
        }

        for (ManagedUnit managedUnit: overlords.getMembers()) {
            if (managedUnit.getUnit().getDistance(mainBaseLocation.toPosition()) < 16) {
                managedUnit.setRole(UnitRole.IDLE);
                continue;
            }

            managedUnit.setRole(UnitRole.RALLY);
            managedUnit.setRallyPoint(mainBaseLocation.toPosition());
        }
    }



    public void updateDefenseSquads() {
        ensureDefenderSquadsHaveTargets();
    }

    private void evictIrradiatedUnits() {
        for (Squad squad : fightSquads) {
            evictIrradiatedFromSquad(squad);
        }
        for (Squad defenseSquad : defenseSquads.values()) {
            evictIrradiatedFromSquad(defenseSquad);
        }
    }

    private void evictIrradiatedFromSquad(Squad squad) {
        List<ManagedUnit> toEvict = new ArrayList<>();
        for (ManagedUnit mu : squad.getMembers()) {
            if (mu.isIrradiated() && !irradiatedUnits.contains(mu)) {
                toEvict.add(mu);
            }
        }
        for (ManagedUnit mu : toEvict) {
            squad.removeUnit(mu);
            mu.setRole(UnitRole.FIGHT);
            mu.setDefendTarget(null);
            mu.setRallyPoint(gameState.getBaseData().mainBasePosition().toPosition());
            irradiatedUnits.add(mu);
            assignIrradiatedTarget(mu);
        }
    }

    private void updateIrradiatedUnits() {
        List<ManagedUnit> recovered = new ArrayList<>();
        for (ManagedUnit mu : irradiatedUnits) {
            if (!mu.isIrradiated()) {
                recovered.add(mu);
                continue;
            }
            assignIrradiatedTarget(mu);
        }
        for (ManagedUnit mu : recovered) {
            irradiatedUnits.remove(mu);
            addManagedFighter(mu);
        }
    }

    private void assignIrradiatedTarget(ManagedUnit managedUnit) {
        Unit unit = managedUnit.getUnit();
        List<Unit> enemyUnits = new ArrayList<>(gameState.getVisibleEnemyUnits());

        List<Unit> filtered = new ArrayList<>();
        for (Unit enemyUnit : enemyUnits) {
            if (unit.getType() == UnitType.Zerg_Lurker && !enemyUnit.isFlying() && enemyUnit.isDetected()) {
                filtered.add(enemyUnit);
                continue;
            }
            if (unit.canAttack(enemyUnit) && enemyUnit.isDetected()
                    && !Filter.isLowPriorityCombatTarget(enemyUnit.getType())) {
                filtered.add(enemyUnit);
            }
        }

        List<Unit> uncapped = ScoutChase.withoutCappedScouts(filtered, enemy -> isCappedScoutFor(unit, enemy));
        if (uncapped.isEmpty() && !filtered.isEmpty()) {
            rallyToDefensePosition(managedUnit, gameState.defensePosition());
            return;
        }
        filtered = uncapped;
        if (filtered.isEmpty()) {
            scoutChase.release(unit.getID());
            managedUnit.setRole(UnitRole.FIGHT);
            Base enemyBase = gameState.getBaseData().getMainEnemyBase();
            if (enemyBase != null) {
                managedUnit.setMovementTargetPosition(enemyBase.getLocation());
            }
            return;
        }

        filtered = filterByProximity(filtered, unit::getDistance);

        TargetScorer.Selection selection = TargetScorer.selectTarget(unit, filtered, managedUnit.fightTarget);
        if (selection != null) {
            managedUnit.setRole(UnitRole.FIGHT);
            managedUnit.setFightTarget(selection.getTarget());
            recordScoutClaim(unit, selection.getTarget());
        }
    }

    /**
     * Re-evaluates the worker defence of a base, then pulls the gatherers it still needs.
     *
     * <p>Outside a cannon rush the defence is simulated with every assigned defender and every candidate. If
     * that full commitment loses, no candidate is pulled, every assigned defender is released and the base
     * may not pull again for {@link WorkerDefense#ABANDON_HOLD_FRAMES}. Otherwise candidates are added until
     * the defence clears the threat.
     *
     * @param base base to defend
     * @param candidates gatherers that may be pulled, in pull order
     * @param hostileUnits units threatening this base
     * @return gatherers pulled, to be removed from the WorkerManager, and defenders released, to be returned
     */
    public WorkerDefense.Outcome<ManagedUnit> assignGatherersToDefend(Base base, List<ManagedUnit> candidates,
                                                                      List<Unit> hostileUnits) {
        ensureDefenseSquad(base);
        Squad defenseSquad = defenseSquads.get(base);

        if (gameState.isCannonRushed()) {
            return assignCannonRushDefenders(defenseSquad, candidates, hostileUnits);
        }

        int frame = game.getFrameCount();
        if (WorkerDefense.abandonHeld(defenseAbandonedUntilFrame.get(base), frame)) {
            return new WorkerDefense.Outcome<>(false, Collections.emptyList(), Collections.emptyList());
        }

        final double clearThreshold = gameState.getStrategyTracker().isDetectedStrategy("SCVRush")
                ? SCV_RUSH_DEFENSE_CLEAR_THRESHOLD
                : DEFENSE_WIN_THRESHOLD;
        List<DefenseSim> sims = new ArrayList<>();
        List<ManagedUnit> members = new ArrayList<>(defenseSquad.getMembers());
        WorkerDefense.Outcome<ManagedUnit> outcome = WorkerDefense.decide(members, candidates,
                defenders -> {
                    DefenseSim sim = simulateDefense(base, defenders, hostileUnits, DEFENSE_WIN_THRESHOLD);
                    sims.add(sim);
                    return sim.wins();
                },
                defenders -> simulateDefense(base, defenders, hostileUnits, clearThreshold).wins());
        DefenseSim fullCommitment = sims.isEmpty() ? null : sims.get(0);

        if (outcome.isAbandoned()) {
            releaseDefenders(defenseSquad);
            defenseAbandonedUntilFrame.put(base, frame + WorkerDefense.ABANDON_HOLD_FRAMES);
            SquadDecisions.defenseEvaluated(defenseSquad, DefenseEvent.ABANDON, candidates.size(),
                    Collections.emptyList(), outcome.getReleased(), fullCommitment);
            return outcome;
        }

        for (ManagedUnit gatherer : outcome.getPulled()) {
            defenseSquad.addUnit(gatherer);
            gatherer.setRole(UnitRole.DEFEND);
            assignDefenderTarget(gatherer, hostileUnits);
        }
        if (!outcome.getPulled().isEmpty()) {
            SquadDecisions.defenseEvaluated(defenseSquad, DefenseEvent.PULL, candidates.size(),
                    outcome.getPulled(), Collections.emptyList(), fullCommitment);
        }
        return outcome;
    }

    private WorkerDefense.Outcome<ManagedUnit> assignCannonRushDefenders(Squad defenseSquad,
                                                                         List<ManagedUnit> candidates,
                                                                         List<Unit> hostileUnits) {
        List<ManagedUnit> pulled = new ArrayList<>();
        int totalGatherers = gameState.getGatherersAssignedToBase().values().stream()
                .mapToInt(HashSet::size)
                .sum();
        int existingDefenders = defenseSquads.values().stream()
                .mapToInt(s -> s.getMembers().size())
                .sum();
        int maxToAssign = totalGatherers / 2 - existingDefenders;
        for (ManagedUnit gatherer : candidates) {
            if (pulled.size() >= maxToAssign) {
                break;
            }
            defenseSquad.addUnit(gatherer);
            gatherer.setRole(UnitRole.DEFEND);
            assignDefenderTarget(gatherer, hostileUnits);
            pulled.add(gatherer);
        }
        if (!pulled.isEmpty()) {
            SquadDecisions.defenseEvaluated(defenseSquad, DefenseEvent.PULL, candidates.size(), pulled,
                    Collections.emptyList(), null);
        }
        return new WorkerDefense.Outcome<>(false, pulled, Collections.emptyList());
    }

    public List<ManagedUnit> disbandDefendSquad(Base base) {
        ensureDefenseSquad(base);
        Squad defenseSquad = defenseSquads.get(base);

        List<ManagedUnit> reassignedDefenders = releaseDefenders(defenseSquad);
        if (!reassignedDefenders.isEmpty()) {
            SquadDecisions.defenseEvaluated(defenseSquad, DefenseEvent.RELEASE, 0, Collections.emptyList(),
                    reassignedDefenders, null);
        }
        return reassignedDefenders;
    }

    private List<ManagedUnit> releaseDefenders(Squad defenseSquad) {
        List<ManagedUnit> reassignedDefenders = new ArrayList<>(defenseSquad.getMembers());

        for (ManagedUnit defender: reassignedDefenders) {
            defenseSquad.removeUnit(defender);
            defender.setDefendTarget(null);
            defender.setMovementTargetPosition(null);
        }

        return reassignedDefenders;
    }

    /**
     * Disbands a squad and returns all its members for reassignment.
     * Removes the squad from the fight squads collection.
     * Overlords are returned to the overlord squad instead of being added to disbanded list.
     *
     * @param squad Squad to disband
     * @return List of managed units that were in the squad (excluding overlords)
     */
    private List<ManagedUnit> disbandSquad(Squad squad) {
        List<ManagedUnit> members = new ArrayList<>(squad.getMembers());
        List<ManagedUnit> nonOverlordMembers = new ArrayList<>();

        for (ManagedUnit member : members) {
            squad.removeUnit(member);
            
            if (member.getUnitType() == UnitType.Zerg_Overlord) {
                overlords.addUnit(member);
                member.setRole(UnitRole.IDLE);
            } else {
                nonOverlordMembers.add(member);
            }
        }

        return nonOverlordMembers;
    }

    public Set<Base> getDefenseSquadBases() {
        return defenseSquads.keySet();
    }

    private void ensureDefenseSquad(Base base) {
        if (!defenseSquads.containsKey(base)) {
            Squad squad = new Squad();
            squad.setCenter(base.getCenter());
            squad.setRallyPoint(base.getLocation().toPosition());
            defenseSquads.put(base, squad);
        }
    }

    private void assignDefenderTarget(ManagedUnit defender, List<Unit> threats) {
        if (defender.getDefendTarget() != null) {
            return;
        }
        ObservedUnitTracker tracker = gameState.getObservedUnitTracker();
        TilePosition defenderTile = defender.getUnit().getTilePosition();

        Unit bestTarget = null;
        int bestHp = Integer.MAX_VALUE;
        int bestDistance = Integer.MAX_VALUE;
        for (Unit threat : threats) {
            Position threatPos = tracker.getLastKnownPosition(threat);
            if (threatPos == null) {
                continue;
            }
            int hp = threat.getHitPoints();
            int distance = manhattanTileDistance(defenderTile, threatPos.toTilePosition());
            if (hp < bestHp || hp == bestHp && distance < bestDistance) {
                bestHp = hp;
                bestDistance = distance;
                bestTarget = threat;
            }
        }

        if (bestTarget != null) {
            defender.setDefendTarget(bestTarget);
            Position lastKnown = tracker.getLastKnownPosition(bestTarget);
            if (lastKnown != null) {
                defender.setMovementTargetPosition(lastKnown.toTilePosition());
            }
        }
    }

    private void ensureDefenderSquadsHaveTargets() {
        for (Base base: defenseSquads.keySet()) {
            ensureDefenseSquad(base);
            Squad squad = defenseSquads.get(base);
            if (squad.size() == 0) {
                continue;
            }

            HashSet<Unit> baseThreats = gameState.getBaseToThreatLookup().get(base);
            if (baseThreats == null || baseThreats.isEmpty()) {
                continue;
            }
            for (ManagedUnit defender: squad.getMembers()) {
                ensureDefenderHasTarget(defender, baseThreats);
            }
        }
    }

    private void ensureDefenderHasTarget(ManagedUnit defender, HashSet<Unit> baseThreats) {
        if (defender.getDefendTarget() != null) {
            ObservedUnitTracker tracker = gameState.getObservedUnitTracker();
            Position lastKnown = tracker.getLastKnownPosition(defender.getDefendTarget());
            if (lastKnown == null) {
                defender.setDefendTarget(null);
                defender.setMovementTargetPosition(null);
            } else {
                return;
            }
        }
        assignDefenderTarget(defender, new ArrayList<>(baseThreats));
    }

    private void removeEmptySquads() {
        List<Squad> emptySquads = new ArrayList<>();
        for (Squad squad: fightSquads) {
            if (squad.size() == 0) {
                emptySquads.add(squad);
            }
        }

        for (Squad squad: emptySquads) {
            if (squad.getStatus() == SquadStatus.CONTAIN) {
                containEndedOtherwise();
                endContainment(squad);
            }
            AirHarassEvaluator.ExitReason harassExit = AirHarassEvaluator.removalExit(squad.getStatus(), squad.size());
            if (harassExit != null) {
                airHarass.stop(squad, harassExit, game.getFrameCount());
            }
            fightSquads.remove(squad);
        }
    }

    /**
     * Combines pairs of nearby fight squads into a single squad.
     *
     * <p>Merge sets are unordered, so the surviving state is folded by precedence in
     * {@link Squad#inheritStateFrom(java.util.Collection)} rather than being taken from whichever squad happens to be
     * iterated last.
     */
    private void mergeSquads() {
        if (game.getFrameCount() % MERGE_CHECK_INTERVAL != 0) {
            return;
        }

        int currentFrame = game.getFrameCount();
        List<Set<Squad>> toMerge = new ArrayList<>();
        Set<Squad> considered = new HashSet<>();
        for (Squad squad1: fightSquads) {
            for (Squad squad2: fightSquads) {
                if (squad1 == squad2) continue;
                if (considered.contains(squad1) || considered.contains(squad2)) continue;
                if (!squad1.isMergeEligible(currentFrame) || !squad2.isMergeEligible(currentFrame)) continue;
                if (!mayMerge(squad1.getStatus()) || !mayMerge(squad2.getStatus())) continue;
                boolean bothGround = squad1.isGroundSquad() && squad2.isGroundSquad();
                boolean bothAir = squad1.isAirSquad() && squad2.isAirSquad();
                if (!bothGround && !bothAir) continue;
                boolean scourge1 = holdsOnlyScourge(squad1.getComposition());
                boolean scourge2 = holdsOnlyScourge(squad2.getComposition());
                if (bothAir && !mayMergeAirSquads(scourge1, scourge2)) continue;
                if (squad1.distance(squad2) < SQUAD_MERGE_DISTANCE) {
                    Set<Squad> mergeSet = new HashSet<>();
                    mergeSet.add(squad1);
                    mergeSet.add(squad2);
                    considered.add(squad1);
                    considered.add(squad2);
                    toMerge.add(mergeSet);
                }
            }
        }

        for (Set<Squad> mergeSet: toMerge) {
            Squad first = mergeSet.iterator().next();
            Squad newSquad;
            if (first.isGroundSquad()) {
                newSquad = newFightSquad(UnitType.Zerg_Zergling);
            } else {
                newSquad = newFightSquad(UnitType.Zerg_Mutalisk);
            }
            newSquad.inheritStateFrom(mergeSet);
            SquadDecisions.pathTaken(newSquad, DecisionPath.MERGE_INHERIT);
            for (Squad mergingSquad: mergeSet) {
                SwarmLock dropped = mergingSquad.getSwarmLock();
                if (SwarmLock.droppedByMerge(dropped, newSquad.getSwarmLock())) {
                    SquadDecisions.swarmEvaluated(mergingSquad, SwarmEvent.SWARM_EXPIRED, dropped.getSwarmId(),
                            gameState.getDarkSwarmTracker().getRemainingFrames(dropped.getSwarmId()),
                            SwarmLock.Release.MERGED);
                }
                if (mergeEndsContainment(mergingSquad.getStatus(), newSquad.getStatus())) {
                    endContainment(mergingSquad);
                }
                for (ManagedUnit mu : new ArrayList<>(mergingSquad.getMembers())) {
                    newSquad.addUnit(mu);
                }
                fightSquads.remove(mergingSquad);
            }
            newSquad.setSplitFrame(currentFrame);
            fightSquads.add(newSquad);
        }
    }

    private void splitSquads() {
        if (game.getFrameCount() % MERGE_CHECK_INTERVAL != 0) {
            return;
        }

        int currentFrame = game.getFrameCount();
        List<Squad> toAdd = new ArrayList<>();

        for (Squad squad : fightSquads) {
            if (!maySplit(squad.getStatus())) continue;
            if (squad.size() < 4) continue;

            int threshold = squad.isAirSquad() ? AIR_SPLIT_DISTANCE : GROUND_SPLIT_DISTANCE;
            List<ManagedUnit> outliers = squad.findOutliers(threshold);
            if (outliers.isEmpty()) continue;

            int moveOutThreshold = calculateMoveOutThreshold(squad);
            int totalStrength = squadStrength(squad);
            int outlierStrength = strengthOf(squad, outliers);
            if (!splitKeepsBothSidesCommittable(totalStrength, outlierStrength, moveOutThreshold)) {
                SquadDecisions.splitSuppressed(squad, moveOutThreshold, totalStrength, outlierStrength);
                continue;
            }

            Squad child = squad.createSibling();
            child.inheritStateFrom(squad);
            SquadDecisions.pathTaken(child, DecisionPath.SPLIT_INHERIT);
            child.setSplitFrame(currentFrame);
            squad.setSplitFrame(currentFrame);
            for (ManagedUnit mu : outliers) {
                squad.removeUnit(mu);
                child.addUnit(mu);
            }
            if (squad.isAirSquad()) {
                ((AirSquad) squad).rebaseEngageCommitment();
            }
            toAdd.add(child);
        }

        fightSquads.addAll(toAdd);
    }

    /**
     * Whether a merge closes a source squad's contain episode: the source was containing and the merged squad is
     * not. A merge that stays in CONTAIN carries the episode on in the merged squad.
     *
     * @param source status of a squad being merged
     * @param merged status the merged squad took
     * @return true when the source's episode ends with the merge
     */
    static boolean mergeEndsContainment(SquadStatus source, SquadStatus merged) {
        return source == SquadStatus.CONTAIN && merged != SquadStatus.CONTAIN;
    }

    /**
     * Whether a squad holding a status may merge with a neighbour. A runby or harass squad is kept apart: a merge
     * would fold a squad at home into the enemy base, or hand the raid to a squad that recalls it.
     *
     * @param status the squad's status
     * @return true when the squad may merge
     */
    static boolean mayMerge(SquadStatus status) {
        return status != SquadStatus.RUNBY && status != SquadStatus.HARASS;
    }

    /**
     * Whether a squad holding a status may split off its outliers. A runby or harass squad spreads out over the
     * enemy base on purpose, so it is never split.
     *
     * @param status the squad's status
     * @return true when the squad may split
     */
    static boolean maySplit(SquadStatus status) {
        return status != SquadStatus.CONTAIN && status != SquadStatus.RALLY && status != SquadStatus.RETREAT
                && status != SquadStatus.RUNBY && status != SquadStatus.HARASS;
    }

    /**
     * Whether a new or re-homed ground unit or Overlord may join a squad holding a status. A runby or harass squad
     * takes none: joining one would re-simulate it and overwrite its status. Mutalisks join a harassing squad
     * through {@link #mayJoinAirSquadAt(SquadStatus, double, boolean)} and {@link #joinHarass}, which keep its
     * status.
     *
     * @param status the squad's status
     * @return true when the squad may take the unit
     */
    static boolean mayJoin(SquadStatus status) {
        return status != SquadStatus.RUNBY && status != SquadStatus.HARASS;
    }

    /**
     * Returns true when both sides of a proposed split would still clear the move out threshold.
     *
     * <p>splitSquads runs before evaluateSquadRole in the same frame, so a split that drops either side under
     * the threshold hands both fragments to the launch gate on the tick they are created and abandons an attack
     * the whole squad was cleared to make. Splitting is a formation fix, not a reason to give up ground.
     *
     * @param squadStrength strength of the squad before the split
     * @param outlierStrength strength of the units leaving for the sibling squad
     * @param moveOutThreshold strength each side needs to stay cleared to move out
     * @return true if the split is safe to perform
     */
    static boolean splitKeepsBothSidesCommittable(int squadStrength, int outlierStrength, int moveOutThreshold) {
        return outlierStrength >= moveOutThreshold && squadStrength - outlierStrength >= moveOutThreshold;
    }

    private int strengthOf(Squad squad, List<ManagedUnit> units) {
        if (squad.isAirSquad()) {
            Map<UnitType, Integer> composition = new HashMap<>();
            for (ManagedUnit managedUnit : units) {
                composition.merge(managedUnit.getUnitType(), 1, Integer::sum);
            }
            return airMoveOutUnits(composition);
        }

        int supply = 0;
        for (ManagedUnit managedUnit : units) {
            supply += managedUnit.getUnitType().supplyRequired();
        }
        return supply;
    }

    private int squadStrength(Squad squad) {
        if (squad.isAirSquad()) {
            return airMoveOutUnits(squad.getComposition());
        }
        return squad.getSupply();
    }

    /**
     * Counts the air combat units in a composition, the strength an air squad's move out threshold is measured in.
     * Overlords escorting the squad are not counted.
     *
     * @param composition unit counts by type
     * @return number of Mutalisks, Scourge, Guardians and Devourers
     */
    static int airMoveOutUnits(Map<UnitType, Integer> composition) {
        int units = 0;
        for (Map.Entry<UnitType, Integer> entry : composition.entrySet()) {
            if (AIR_SQUAD_TYPES.contains(entry.getKey())) {
                units += entry.getValue();
            }
        }
        return units;
    }

    private List<Unit> enemyUnitsNearSquad(Squad squad) {
        Set<Unit> enemyUnits = gameState.getVisibleEnemyUnits();

        List<Unit> enemies = new ArrayList<>();

        for (Unit u: enemyUnits) {
            final double d = u.getPosition().getDistance(squad.getCenter());
            if (d > ENEMY_DETECTION_RADIUS) {
                continue;
            }
            boolean relevant = squad.getMembers().stream()
                    .anyMatch(member -> member.getUnit().canAttack(u) || u.canAttack(member.getUnit()));
            if (!relevant) {
                continue;
            }
            enemies.add(u);
        }

        return enemies;
    }

    private void rallySquad(Squad squad, RallyReason reason) {
        boolean defilersOnly = squad.isGroundSquad() && squad.hasOnly(UnitType.Zerg_Defiler);
        SquadDecisions.rallied(squad, rallyReasonFor(defilersOnly, reason));
        SquadDecisions.pathTaken(squad, DecisionPath.RALLY);
        squad.setStatus(SquadStatus.RALLY);
        squad.clearCommitment();
        Position rallyPoint = gameState.getSquadRallyPoint();
        for (ManagedUnit managedUnit: squad.getMembers()) {
            managedUnit.setRallyPoint(rallyPoint);
            managedUnit.setRole(UnitRole.RALLY);
        }
    }

    /**
     * Names why a squad is at the rally point, from the composition rather than the branch alone.
     *
     * <p>A ground squad of Defilers only reaches the rally point two ways. SquadManager has a
     * branch that rallies it instead of simulating a fight, but that branch sits inside
     * {@link #simulateFightSquad}, which a squad under the move out threshold never reaches:
     * {@link #chooseSquadAction} returns RALLY first and {@link #evaluateSquadRole} returns. The
     * ground threshold carries a Lurker term and no Defiler term, so which of the two fires is a
     * question of the squad's supply against a threshold that moves with the matchup.
     *
     * <p>Reading the branch alone therefore filed the same squad under BELOW_MOVE_OUT on most
     * frames, which is the bucket an analyst keeps. Composing the reason from the composition keeps
     * the Defiler episodes separable however they were rallied, which is the whole point of
     * recording the reason.
     *
     * @param defilersOnly true for a ground squad whose composition is Defilers and nothing else
     * @param branchReason reason the calling branch would have recorded
     * @return the reason to log
     */
    static RallyReason rallyReasonFor(boolean defilersOnly, RallyReason branchReason) {
        return defilersOnly ? RallyReason.DEFILER_ONLY : branchReason;
    }

    /**
     * Determines whether a squad should continue or change its current role.
     * @param squad Squad to evaluate
     */
    private void evaluateSquadRole(Squad squad) {
        SwarmLock.Verdict swarmVerdict = evaluateSwarmLock(squad);
        switch (SwarmLock.route(squad.getStatus(), swarmVerdict)) {
            case RUNBY:
                evaluateRunbySquad(squad);
                return;
            case SWARM:
                fightUnderSwarm(squad, swarmVerdict);
                return;
            default:
                break;
        }
        if (squad.getStatus() == SquadStatus.HARASS) {
            evaluateHarassSquad(squad);
            return;
        }

        ContainmentStalemate stalemate = gameState.getContainmentStalemate();
        if (ContainmentStalemate.takesOver(squad.isGroundSquad(), squad.getStatus(), stalemate.isCommitting(),
                stalemate.isCommitPaused())) {
            clearCombatSimSnapshot(squad);
            commitSquad(squad, game.getFrameCount());
            return;
        }

        final boolean closeThreats = !enemyUnitsNearSquad(squad).isEmpty();

        SquadStatus squadStatus = squad.getStatus();
        if (squadStatus == SquadStatus.CONTAIN) {
            clearCombatSimSnapshot(squad);
            evaluateContainingSquad(squad);
            return;
        }

        boolean activeAirSquad = AirReinforcement.seeksReinforcementTarget(squadStatus, squad.isAirSquad(),
                squad.size()) && AirReinforcer.hasActiveAirSquad(squad, fightSquads);
        if (activeAirSquad && reinforceActiveAirSquad(squad, closeThreats)) {
            return;
        }
        airReinforcer.forget(squad);

        int strength = squadStrength(squad);
        int moveOutThreshold = AirReinforcement.launchThreshold(calculateMoveOutThreshold(squad), squadStatus,
                activeAirSquad);
        SquadDecisions.moveOutEvaluated(squad, moveOutThreshold, strength);
        boolean holdAway = holdsAwayFromHome(squad, closeThreats, strength, moveOutThreshold);
        SquadAction action = chooseSquadAction(holdAway, closeThreats, strength, moveOutThreshold,
                squadStatus, squad.isCommitted(), distanceFromRallyPoint(squad));

        if (squadStatus == SquadStatus.RALLY) {
            SquadDecisions.rallyReleased(squad, releaseFor(action, closeThreats));
        }

        if (action == SquadAction.RALLY) {
            clearCombatSimSnapshot(squad);
            if (keepsJoiningContain(squadStatus, squad.isCommitted()) && joinActiveContain(squad)) {
                return;
            }
            rallySquad(squad, holdAway ? RallyReason.AIR_BELOW_MOVE_OUT_AWAY : RallyReason.BELOW_MOVE_OUT);
            return;
        }

        squad.commit(game.getFrameCount());
        if (action == SquadAction.LAUNCH && launchOffersContain(squad, game.getFrameCount())
                && tryEnterContainment(squad)) {
            return;
        }
        if (tryEnterHarass(squad)) {
            return;
        }

        simulateFightSquad(squad);
        holdCollapseWrap(squad);
    }

    /**
     * Whether a squad the move out gate launches is offered a containment arc. A squad wrapping in a collapse or
     * holding a fight lock is not: taking the arc would drop the collapse and the fight it committed to on the
     * frame after it left the arc. A squad holding a retreat lock is not either: it has just left an arc, and taking
     * one again at once would undo the exit before the lock expires.
     *
     * @param squad squad being launched
     * @param now current frame
     * @return true when the squad may take an arc on launch
     */
    static boolean launchOffersContain(Squad squad, int now) {
        return squad.getCollapse() == null && !squad.isFightLocked(now) && !squad.isRetreatLocked(now);
    }

    /**
     * Sends a rallying air squad to the nearest active air squad it may merge with while a path outside known
     * anti-air reaches it, and hands its members over on arrival. A squad at home with close threats defends
     * instead, and a squad with no safe path takes the rally branch, where its move out threshold is suspended by
     * {@link AirReinforcement#launchThreshold}.
     *
     * @param squad rallying air squad
     * @param closeThreats true when enemies sit inside the squad detection radius
     * @return true when the squad is reinforcing or has joined its target this frame
     */
    private boolean reinforceActiveAirSquad(Squad squad, boolean closeThreats) {
        if (closeThreats && isNearHome(squad.getCenter())) {
            return false;
        }
        AirReinforcer.Outcome outcome = airReinforcer.reinforce(squad, fightSquads, game.getFrameCount());
        if (outcome == AirReinforcer.Outcome.REFUSED) {
            return false;
        }
        clearCombatSimSnapshot(squad);
        SquadDecisions.rallied(squad, RallyReason.AIR_REINFORCE);
        SquadDecisions.pathTaken(squad, DecisionPath.AIR_REINFORCE);
        if (outcome == AirReinforcer.Outcome.ARRIVED) {
            joinAirSquad(squad, airReinforcer.targetOf(squad));
            airReinforcer.forget(squad);
        }
        return true;
    }

    /**
     * Moves every member of a reinforcing squad into the active air squad it reached. A harassing target takes
     * them through {@link #joinHarass}, and escorting Overlords go back to the Overlord squad as they do on a
     * harass entry; any other target is simulated with its new members, as a unit joining it on hatching is. The
     * emptied squad is removed on the next frame.
     *
     * @param source the reinforcing squad
     * @param target the active air squad
     */
    private void joinAirSquad(Squad source, Squad target) {
        boolean harass = target.getStatus() == SquadStatus.HARASS;
        List<ManagedUnit> joined = new ArrayList<>();
        for (ManagedUnit member : new ArrayList<>(source.getMembers())) {
            source.removeUnit(member);
            if (harass && member.getUnitType() == UnitType.Zerg_Overlord) {
                overlords.addUnit(member);
                member.setRole(UnitRole.IDLE);
                continue;
            }
            target.addUnit(member);
            joined.add(member);
        }
        if (harass) {
            joinHarass(target, joined);
            return;
        }
        simulateFightSquad(target);
    }

    /**
     * Hands Mutalisks joining a harassing squad the HARASS role and its strike point, or its hold point while the
     * harass probes, see {@link AirHarassController#destinationOf}, and adds their hit points to
     * the ones the harass started with, so the reinforcement is not read as hit points regained.
     *
     * @param squad harassing squad
     * @param joined units that just joined it
     */
    private void joinHarass(Squad squad, Collection<ManagedUnit> joined) {
        AirHarassState state = squad.getHarassState();
        int hitPoints = 0;
        for (ManagedUnit member : joined) {
            member.setRole(UnitRole.HARASS);
            member.setFightTarget(null);
            member.setContainPosition(null);
            member.setRetreatTarget(null);
            member.setHarassDestination(state == null ? null : AirHarassController.destinationOf(state, member));
            if (member.getUnitType() == UnitType.Zerg_Mutalisk) {
                hitPoints += member.getUnit().getHitPoints();
            }
        }
        if (state != null) {
            state.addStartHitPoints(hitPoints);
        }
    }

    /**
     * Offers an air squad a harass on every {@link AirHarassEvaluator#HARASS_TICK}, outside its retreat and fight
     * locks and outside the hold that follows a broken harass exit lock, see {@link AirHarassEvaluator#holdsReentry}.
     * Overlords escorting the squad go back to the Overlord squad, since they would trail the Mutalisks
     * into the enemy base.
     *
     * @param squad fight squad cleared to act
     * @return true when the squad entered HARASS
     */
    private boolean tryEnterHarass(Squad squad) {
        int now = game.getFrameCount();
        if (!AirHarassEvaluator.entryCheckDue(squad.isAirSquad(), squad.isRetreatLocked(now),
                squad.isFightLocked(now), now)
                || AirHarassEvaluator.holdsReentry(squad.getHarassExitEngageFrame(), now)) {
            return false;
        }
        AirHarassController.Entry entry = airHarass.checkEntry(squad, now, basesUnderAttack(), containPoints());
        if (!entry.enters()) {
            return false;
        }
        for (ManagedUnit member : new ArrayList<>(squad.getMembers())) {
            if (member.getUnitType() == UnitType.Zerg_Overlord) {
                squad.removeUnit(member);
                overlords.addUnit(member);
                member.setRole(UnitRole.IDLE);
            }
        }
        clearCombatSimSnapshot(squad);
        squad.setStatus(SquadStatus.HARASS);
        squad.commit(now);
        SquadDecisions.pathTaken(squad, DecisionPath.HARASS_ENTER);
        airHarass.start(squad, entry, now);
        return true;
    }

    /**
     * Runs one frame of a harassing squad. The combat sim, the locks and the containment branches never see it; it
     * leaves HARASS only through the harass exit, and then retreats, the whole flock to one exit point when anti-air
     * is near it.
     *
     * @param squad harassing squad
     */
    private void evaluateHarassSquad(Squad squad) {
        int now = game.getFrameCount();
        AirHarassEvaluator.ExitReason reason = airHarass.tick(squad, now, basesUnderAttack(), containPoints());
        if (reason == null) {
            return;
        }
        airHarass.stop(squad, reason, now);
        squad.setHarassState(null);
        squad.setHarassExitFrame(now);
        squad.setStatus(SquadStatus.RETREAT);
        SquadDecisions.pathTaken(squad, DecisionPath.HARASS_EXIT);
        assignRetreatTargets(squad, squad.getMembers());
        airHarass.leaveTogether(squad, now);
        squad.startRetreatLock(now);
    }

    private List<Position> containPoints() {
        List<Position> points = new ArrayList<>();
        for (Squad squad : fightSquads) {
            Arc arc = squad.getStatus() == SquadStatus.CONTAIN ? squad.getContainmentArc() : null;
            if (arc != null && !arc.isEmpty()) {
                points.add(arc.getMidpoint());
            }
        }
        return points;
    }

    /**
     * Takes, holds or drops a squad's swarm lock for this frame. See {@link SwarmLock}.
     *
     * <p>When nothing else stands against the lock, the combat sim runs, pricing the swarm's cover, and a RETREAT read
     * refuses the commit, and one under the release hysteresis releases the held lock, see
     * {@link SwarmLock#releasesOnRead}. A squad released that way commits to no swarm for the cooldown, see
     * {@link SwarmLock#mayCommit}. A commit on a read with no cover for the squad needs a margin over the engage
     * threshold, see {@link SwarmLock#commitsOnRead}. The sim's result is kept for {@link #fightUnderSwarm}, so it runs
     * once a frame.
     *
     * @param squad squad to evaluate
     * @return this frame's swarm lock verdict
     */
    private SwarmLock.Verdict evaluateSwarmLock(Squad squad) {
        swarmSimResult = null;
        SwarmLock held = squad.getSwarmLock();
        List<DarkSwarm> swarms = gameState.getDarkSwarmTracker().getActiveSwarms();
        if (held == null && swarms.isEmpty() || squad.getStatus() == SquadStatus.RUNBY) {
            return SwarmLock.Verdict.NONE;
        }

        Position center = squad.getCenter();
        int now = game.getFrameCount();
        boolean melee = !squad.isAirSquad() && SwarmLock.isMeleeSquad(squad.getComposition());
        DarkSwarm eligible = held == null && melee && center != null
                && SwarmLock.mayCommit(now, squad.getSimRetreatReleaseFrame())
                ? SwarmLock.choose(swarms, center, swarmEligibility(swarms, center))
                : null;
        DarkSwarm swarm = held != null ? gameState.getDarkSwarmTracker().getSwarm(held.getSwarmId()) : eligible;
        if (swarm == null && held == null) {
            return SwarmLock.Verdict.NONE;
        }
        int remaining = swarm != null ? swarm.getRemainingFrames() : 0;
        boolean threatenedNow = baseThreatensContainment();
        boolean baseThreatened = SwarmLock.baseThreatStands(threatenedNow, now, swarmBaseThreatFrame);
        if (threatenedNow) {
            swarmBaseThreatFrame = now;
        }
        boolean inStorm = anyMemberInStorm(squad);
        SwarmLock.Release reason = SwarmLock.releaseReason(melee, swarm == null, remaining, baseThreatened, inStorm,
                false);
        boolean commits = eligible != null;
        if (SwarmLock.simDecides(held != null, commits, reason)) {
            swarmSimResult = squad.getCombatSimulator()
                    .evaluate(squad, getAdjacentSquads(squad, REINFORCEMENT_RADIUS), gameState);
            HorizonCombatSimulator.DebugSnapshot snapshot = lastSnapshot(squad);
            HorizonCombatSimulator.DebugSnapshot read = snapshot != null && snapshot.getCapturedFrame() == now
                    ? snapshot : null;
            reason = SwarmLock.releaseReason(melee, false, remaining, baseThreatened, inStorm,
                    SwarmLock.releasesOnRead(held != null, swarmSimResult == CombatSimulator.CombatResult.RETREAT,
                            read != null ? read.getOverallRatio() : SwarmLock.NO_READ,
                            read != null ? read.getEngageThreshold() : 0));
            commits = read != null && SwarmLock.commitsOnRead(commits, read.getSwarmCover(), read.getOverallRatio(),
                    read.getEngageThreshold());
        }
        SwarmLock.Verdict verdict = SwarmLock.verdict(held != null, commits, reason);

        if (verdict == SwarmLock.Verdict.COMMIT) {
            squad.setSwarmLock(new SwarmLock(swarm.getId(), now));
            SquadDecisions.swarmEvaluated(squad, SwarmEvent.SWARM_COMMIT, swarm.getId(), remaining,
                    SwarmLock.Release.NONE);
        } else if (verdict == SwarmLock.Verdict.RELEASE) {
            squad.setSwarmLock(null);
            if (reason == SwarmLock.Release.SIM_RETREAT) {
                squad.setSimRetreatReleaseFrame(now);
            }
            SquadDecisions.pathTaken(squad, DecisionPath.SWARM_EXPIRED);
            SquadDecisions.swarmEvaluated(squad, SwarmEvent.SWARM_EXPIRED, held.getSwarmId(), remaining, reason);
        }
        return verdict;
    }

    /**
     * Every {@link #SWARM_SAMPLE_INTERVAL_FRAMES}, writes a SWARM_ACTIVE row for a melee squad near one of our active
     * Dark Swarms, after the squad has decided its status for the frame. A squad holding a lock is sampled on its
     * swarm; any other melee squad on the nearest swarm it would be eligible for, whatever time that swarm has left.
     *
     * @param squad squad that has just been evaluated
     * @param now current frame
     */
    private void sampleSwarm(Squad squad, int now) {
        List<DarkSwarm> swarms = gameState.getDarkSwarmTracker().getActiveSwarms();
        Position center = squad.getCenter();
        if (now % SWARM_SAMPLE_INTERVAL_FRAMES != 0 || swarms.isEmpty() || center == null
                || squad.getStatus() == SquadStatus.RUNBY || squad.isAirSquad()
                || !SwarmLock.isMeleeSquad(squad.getComposition())) {
            return;
        }
        SwarmLock held = squad.getSwarmLock();
        DarkSwarm sampled = held != null ? gameState.getDarkSwarmTracker().getSwarm(held.getSwarmId()) : null;
        if (sampled == null) {
            sampled = SwarmLock.nearest(swarms, center, swarmEligibility(swarms, center), 1);
        }
        if (sampled != null) {
            SquadDecisions.swarmEvaluated(squad, SwarmEvent.SWARM_ACTIVE, sampled.getId(),
                    sampled.getRemainingFrames(), SwarmLock.Release.NONE);
        }
    }

    /**
     * For each swarm, whether a squad centred here may commit to it: its footprint lies within
     * {@link SwarmLock#COMMIT_RADIUS} and it covers an enemy worth committing to, see {@link SwarmLock#isCommitTarget}.
     */
    private List<Boolean> swarmEligibility(List<DarkSwarm> swarms, Position center) {
        List<Boolean> eligibility = new ArrayList<>();
        for (DarkSwarm swarm : swarms) {
            boolean coversTarget = false;
            for (Unit enemy : enemiesCoveredBy(swarm)) {
                if (SwarmLock.isCommitTarget(enemy.getType())) {
                    coversTarget = true;
                    break;
                }
            }
            eligibility.add(SwarmLock.isEligible(swarm, center, coversTarget));
        }
        return eligibility;
    }

    /**
     * Detected enemy ground units and buildings within {@link SwarmLock#COVER_MARGIN} of a swarm's footprint.
     * Computed once per swarm per frame.
     */
    private List<Unit> enemiesCoveredBy(DarkSwarm swarm) {
        int now = game.getFrameCount();
        if (now != swarmCoverFrame) {
            swarmCoverFrame = now;
            swarmCoveredEnemies.clear();
        }
        List<Unit> covered = swarmCoveredEnemies.get(swarm.getId());
        if (covered != null) {
            return covered;
        }
        covered = new ArrayList<>();
        for (Unit enemy : gameState.getVisibleEnemyUnits()) {
            UnitType type = enemy.getType();
            if (!enemy.isDetected() || type.isFlyer() || Filter.isLowPriorityCombatTarget(type)) {
                continue;
            }
            if (SwarmLock.coversEnemy(swarm, enemy.getPosition(), type)) {
                covered.add(enemy);
            }
        }
        swarmCoveredEnemies.put(swarm.getId(), covered);
        return covered;
    }

    private boolean anyMemberInStorm(Squad squad) {
        for (ManagedUnit member : squad.getMembers()) {
            if (gameState.isPositionInStorm(member.getUnit().getPosition(), 0)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Runs one tick of a squad holding a swarm lock: it leaves any containment arc, drops its retreat lock and
     * fights. The swarm-priced sim read that let the lock hold, see {@link #evaluateSwarmLock}, is the frame's sim
     * verdict.
     *
     * <p>A melee member attacks an enemy the swarm covers, see {@link SwarmLock#coversEnemy}; with none to attack it
     * moves to the footprint centre rather than chase an enemy out of the swarm. Every other member takes its target
     * as in any fight.
     *
     * @param squad squad holding the lock
     * @param verdict COMMIT on the frame the lock is taken, HOLD after
     */
    private void fightUnderSwarm(Squad squad, SwarmLock.Verdict verdict) {
        int now = game.getFrameCount();
        DarkSwarm swarm = gameState.getDarkSwarmTracker().getSwarm(squad.getSwarmLock().getSwarmId());
        if (squad.getStatus() == SquadStatus.CONTAIN) {
            endContainment(squad);
        }
        squad.setCollapse(null);
        squad.clearRetreatLock();
        squad.commit(now);

        SquadDecisions.simEvaluated(squad, swarmSimResult, false, squad.isFightLocked(now));
        squad.setStatus(SquadStatus.FIGHT);
        SquadDecisions.pathTaken(squad, verdict == SwarmLock.Verdict.COMMIT
                ? DecisionPath.SWARM_COMMIT : DecisionPath.SWARM_ACTIVE);

        List<Unit> covered = enemiesCoveredBy(swarm);
        for (ManagedUnit managedUnit : new ArrayList<>(squad.getMembers())) {
            managedUnit.clearRetreatStart();
            if (!SwarmLock.isMelee(managedUnit.getUnitType())) {
                managedUnit.setRole(UnitRole.FIGHT);
                assignEnemyTarget(managedUnit, squad, fightTargetLedger());
                continue;
            }
            assignSwarmTarget(managedUnit, squad, swarm, covered);
        }
    }

    /**
     * Gives a melee member of a swarm-locked squad a target among the enemies its swarm covers, picked against the
     * frame's shared melee ledger, see {@link #swarmPick}. With no covered enemy it can attack, the member moves to
     * the footprint centre.
     */
    private void assignSwarmTarget(ManagedUnit managedUnit, Squad squad, DarkSwarm swarm, List<Unit> covered) {
        Unit unit = managedUnit.getUnit();
        List<Unit> attackable = new ArrayList<>();
        for (Unit enemy : covered) {
            if (unit.canAttack(enemy)) {
                attackable.add(enemy);
            }
        }
        TargetScorer.Selection issued = swarmPick(unit, attackable, managedUnit.fightTarget, fightTargetLedger(),
                squad.getId(), managedUnit.getOverflowGate(), game.getFrameCount());
        if (issued == null) {
            rallyToDefensePosition(managedUnit, swarm.getCenter());
            return;
        }
        managedUnit.setRole(UnitRole.FIGHT);
        TargetChoices.chosen(managedUnit, managedUnit.fightTarget, managedUnit.isAttackMoving(), issued, false);
        managedUnit.setFightTarget(issued.getTarget(), issued.isAttackMove());
        recordScoutClaim(unit, issued.getTarget());
    }

    /**
     * Picks a melee attacker's target under a swarm the way {@link #assignEnemyTarget} does in any fight: the
     * attacker's entry is dropped from the frame's ledger, the pick is scored against the melee load every fight
     * squad has put on each candidate, and it is committed through {@link #commitPick}, so it is recorded in the
     * ledger unless the attacker attack-moves in overflow.
     *
     * @param attacker the melee attacker
     * @param attackable the enemies the swarm covers that the attacker can attack
     * @param heldTarget the fight target the attacker held before this pick, or null
     * @param ledger the frame's melee assignments across every fight squad
     * @param squadId id of the attacker's squad, carried on the selection for telemetry
     * @param gate the attacker's overflow gate
     * @param frame the current frame
     * @return the pick as it is issued, or null when there is nothing to attack
     */
    static TargetScorer.Selection swarmPick(Unit attacker, List<Unit> attackable, Unit heldTarget,
                                            TargetLedger ledger, String squadId, MeleeOverflowGate gate,
                                            int frame) {
        ledger.release(attacker.getID());
        TargetScorer.Selection selection = TargetScorer.selectTarget(attacker, attackable, heldTarget, ledger,
                squadId);
        return selection == null ? null : commitPick(ledger, gate, attacker, selection, heldTarget, frame);
    }

    /**
     * Branch {@link #evaluateSquadRole} takes for a fight squad that is not already containing.
     */
    enum SquadAction {
        SIMULATE,
        LAUNCH,
        RALLY
    }

    /**
     * Picks the branch for a fight squad that is not already containing.
     *
     * <p>The move out threshold is a launch permission, not a recall rule. A squad already cleared to move out
     * keeps acting while it is committed and still out on the map, so losses or a split dropping it under the
     * threshold do not send it back to the global rally point with no enemy anywhere near it. Commitment is
     * released by {@link #rallySquad} and, for a squad that has walked itself home, by the release distance.
     *
     * @param closeThreats true when enemies sit inside the squad detection radius
     * @param squadStrength supply of a ground squad, or air combat unit count of an air squad
     * @param moveOutThreshold strength the squad needs to be cleared to move out
     * @param status status the squad held entering the tick
     * @param committed true when the squad has been cleared to act and has not been recalled since
     * @param distanceFromRallyPoint pixels between the squad center and the global rally point
     * @return branch to take
     */
    static SquadAction chooseSquadAction(boolean closeThreats, int squadStrength, int moveOutThreshold,
                                         SquadStatus status, boolean committed, double distanceFromRallyPoint) {
        if (closeThreats) {
            return SquadAction.SIMULATE;
        }
        if (squadStrength >= moveOutThreshold) {
            return SquadAction.LAUNCH;
        }
        if (status == SquadStatus.FIGHT) {
            return SquadAction.SIMULATE;
        }
        if (committed && distanceFromRallyPoint > COMMITMENT_RELEASE_DISTANCE) {
            return SquadAction.SIMULATE;
        }
        return SquadAction.RALLY;
    }

    /**
     * Picks the branch for a fight squad that is not already containing, holding a sub-threshold air squad at the
     * rally point when its close threats are away from our bases. Every other squad takes
     * {@link #chooseSquadAction(boolean, int, int, SquadStatus, boolean, double)}.
     *
     * @param holdAwayFromHome result of {@link #holdsAwayFromHome(boolean, boolean, boolean, int, int, boolean)}
     * @param closeThreats true when enemies sit inside the squad detection radius
     * @param squadStrength supply of a ground squad, or air combat unit count of an air squad
     * @param moveOutThreshold strength the squad needs to be cleared to move out
     * @param status status the squad held entering the tick
     * @param committed true when the squad has been cleared to act and has not been recalled since
     * @param distanceFromRallyPoint pixels between the squad center and the global rally point
     * @return branch to take
     */
    static SquadAction chooseSquadAction(boolean holdAwayFromHome, boolean closeThreats, int squadStrength,
                                         int moveOutThreshold, SquadStatus status, boolean committed,
                                         double distanceFromRallyPoint) {
        if (holdAwayFromHome) {
            return SquadAction.RALLY;
        }
        return chooseSquadAction(closeThreats, squadStrength, moveOutThreshold, status, committed,
                distanceFromRallyPoint);
    }

    /**
     * Whether a squad's close threats are refused because it is an air squad under its move out threshold, not
     * committed, and outside {@link #AIR_HOME_DEFENSE_RADIUS} of every base we hold. Such a squad simulates
     * close threats only at home, where it is defending Overlords or a drone line; away from home the move out
     * threshold decides. Ground squads and committed air squads are never held.
     *
     * @param airSquad true for an air squad
     * @param closeThreats true when enemies sit inside the squad detection radius
     * @param nearHome true when the squad centre is inside the home defence radius of a base we hold
     * @param squadStrength air combat unit count of the squad
     * @param moveOutThreshold air combat units the squad needs to be cleared to move out
     * @param committed true when the squad has been cleared to act and has not been recalled since
     * @return true when the squad rallies instead of simulating its close threats
     */
    static boolean holdsAwayFromHome(boolean airSquad, boolean closeThreats, boolean nearHome, int squadStrength,
                                     int moveOutThreshold, boolean committed) {
        return mayHoldAwayFromHome(airSquad, squadStrength, moveOutThreshold, committed) && closeThreats && !nearHome;
    }

    /**
     * The terms of {@link #holdsAwayFromHome(boolean, boolean, boolean, int, int, boolean)} that need no enemy scan
     * or path search: an air squad under its move out threshold and not committed. A squad failing them is never
     * held, so the costlier terms are measured only for a squad that passes.
     *
     * @param airSquad true for an air squad
     * @param squadStrength air combat unit count of the squad
     * @param moveOutThreshold air combat units the squad needs to be cleared to move out
     * @param committed true when the squad has been cleared to act and has not been recalled since
     * @return true when the squad can be held
     */
    static boolean mayHoldAwayFromHome(boolean airSquad, int squadStrength, int moveOutThreshold, boolean committed) {
        return airSquad && squadStrength < moveOutThreshold && !committed;
    }

    private boolean holdsAwayFromHome(Squad squad, boolean closeThreats, int squadStrength, int moveOutThreshold) {
        if (!closeThreats
                || !mayHoldAwayFromHome(squad.isAirSquad(), squadStrength, moveOutThreshold, squad.isCommitted())) {
            return false;
        }
        return !isNearHome(squad.getCenter());
    }

    private boolean isNearHome(Position center) {
        if (center == null) {
            return true;
        }
        Set<Position> basePositions = gameState.getBaseData().getMyBasePositions();
        int groundDistance = -1;
        double airDistance = Double.MAX_VALUE;
        for (Position basePosition : basePositions) {
            int length = gameState.getBwem().getMap().getPathLength(center, basePosition);
            if (length >= 0 && (groundDistance < 0 || length < groundDistance)) {
                groundDistance = length;
            }
            airDistance = Math.min(airDistance, center.getDistance(basePosition));
        }
        return isInsideHomeDefenseRadius(groundDistance, airDistance);
    }

    /**
     * Whether a squad centre is inside {@link #AIR_HOME_DEFENSE_RADIUS} of the bases we hold. The ground path
     * length is the measure, the same one squad decision telemetry records as ground_distance_to_base; the air
     * distance is read only when no base is reachable on the ground.
     *
     * @param groundDistance shortest ground path length to a base held, or negative when none is reachable
     * @param airDistance shortest air distance to a base held, or Double.MAX_VALUE when we hold none
     * @return true when the centre is inside the radius
     */
    static boolean isInsideHomeDefenseRadius(int groundDistance, double airDistance) {
        if (groundDistance >= 0) {
            return groundDistance <= AIR_HOME_DEFENSE_RADIUS;
        }
        return airDistance <= AIR_HOME_DEFENSE_RADIUS;
    }

    /**
     * Names the term that let a rallying squad stop rallying, for the row that closes the episode.
     *
     * <p>Reads the branch {@link #chooseSquadAction} already picked rather than re-testing its
     * thresholds, so the two cannot drift apart. Only three of its branches are reachable from
     * RALLY: the FIGHT branch requires the squad to already be fighting, which leaves close threats,
     * the move out threshold, and a committed squad that has walked past the release distance.
     *
     * @param action branch chooseSquadAction returned this frame
     * @param closeThreats true when enemies sit inside the squad detection radius
     * @return the release, or NONE while the squad keeps rallying
     */
    static RallyRelease releaseFor(SquadAction action, boolean closeThreats) {
        if (action == SquadAction.RALLY) {
            return RallyRelease.NONE;
        }
        if (closeThreats) {
            return RallyRelease.CLOSE_THREATS;
        }
        if (action == SquadAction.LAUNCH) {
            return RallyRelease.MOVE_OUT_THRESHOLD;
        }
        return RallyRelease.COMMITTED_DOWNFIELD;
    }

    private double distanceFromRallyPoint(Squad squad) {
        Position rallyPoint = gameState.getSquadRallyPoint();
        Position center = squad.getCenter();
        if (rallyPoint == null || center == null) {
            return 0;
        }
        return center.getDistance(rallyPoint);
    }

    private int calculateMoveOutThreshold(Squad squad) {
        if (squad.isAirSquad()) {
            return calculateAirSquadMoveOutThreshold(squad);
        }

        if (squad.isGroundSquad()) {
            return calculateGroundSquadMoveOutThreshold(squad);
        }

        return defaultMoveOutThreshold();
    }

    private int calculateAirSquadMoveOutThreshold(Squad squad) {
        return airMoveOutThreshold(holdsOnlyScourge(squad.getComposition()), gameState.getOpponentRace());
    }

    /**
     * Whether a composition's air combat units are all Scourge. Escorting Overlords are ignored, and a
     * composition with no air combat units is not a Scourge squad.
     *
     * @param composition unit counts by type
     * @return true when the composition holds Scourge and no other air combat unit
     */
    static boolean holdsOnlyScourge(Map<UnitType, Integer> composition) {
        int units = airMoveOutUnits(composition);
        return units > 0 && units == composition.getOrDefault(UnitType.Zerg_Scourge, 0);
    }

    /**
     * Whether an air unit may join an air squad. Scourge keep to squads of Scourge so a pair moves out on
     * {@link #SCOURGE_MOVE_OUT_UNITS}, and every other air unit keeps out of them.
     *
     * @param type the joining unit's type
     * @param squadHoldsOnlyScourge true when the squad's air combat units are all Scourge
     * @return true when the unit may join the squad
     */
    static boolean mayJoinAirSquad(UnitType type, boolean squadHoldsOnlyScourge) {
        boolean scourge = type == UnitType.Zerg_Scourge;
        return scourge == squadHoldsOnlyScourge;
    }

    /**
     * Whether two air squads may merge. A Scourge squad merges only with another Scourge squad.
     *
     * @param firstHoldsOnlyScourge true when the first squad's air combat units are all Scourge
     * @param secondHoldsOnlyScourge true when the second squad's air combat units are all Scourge
     * @return true when the squads may merge
     */
    static boolean mayMergeAirSquads(boolean firstHoldsOnlyScourge, boolean secondHoldsOnlyScourge) {
        return firstHoldsOnlyScourge == secondHoldsOnlyScourge;
    }

    /**
     * Air combat units an air squad needs before it is cleared to move out, compared against
     * {@link #airMoveOutUnits}.
     *
     * @param scourgeOnly true when the squad holds Scourge and nothing else
     * @param opponentRace the opponent's race
     * @return threshold in units
     */
    static int airMoveOutThreshold(boolean scourgeOnly, Race opponentRace) {
        if (scourgeOnly) {
            return SCOURGE_MOVE_OUT_UNITS;
        }
        if (opponentRace == Race.Zerg) {
            return AIR_MOVE_OUT_UNITS_VS_ZERG;
        }
        return AIR_MOVE_OUT_UNITS;
    }

    private int defaultMoveOutThreshold() {
        int staticDefensePenalty = min(gameState.getObservedUnitTracker().getHostileToGroundBuildings().size(), 6);
        int moveOutThreshold = 8 * (1 + staticDefensePenalty);
        StrategyTracker strategyTracker = gameState.getStrategyTracker();
        if (strategyTracker.isAnyDetectedStrategy("2Gate", ProxyGate.NAME)) {
            final int zealots = gameState.enemyUnitCount(UnitType.Protoss_Zealot);
            moveOutThreshold += zealots * 2;
        }

        return Math.min(moveOutThreshold, MAX_MOVE_OUT_THRESHOLD);
    }

    private int calculateGroundSquadMoveOutThreshold(Squad squad) {
        if (gameState.isLingFloodHold()) {
            return MAX_MOVE_OUT_THRESHOLD;
        }
        StrategyTracker strategyTracker = gameState.getStrategyTracker();
        final boolean isActivelyCannonRushed = gameState.isCannonRushed();
        final boolean isCannonRushed = strategyTracker.isDetectedStrategy("CannonRush");

        if (isCannonRushed) {
            if (isActivelyCannonRushed) {
                Set<Position> basePositions = gameState.getBaseData().getMyBasePositions();
                ObservedUnitTracker tracker = gameState.getObservedUnitTracker();
                int completedCannons = tracker.getCompletedBuildingCountNearPositions(UnitType.Protoss_Photon_Cannon, basePositions, 512);
                if (completedCannons == 0) {
                    return 1;
                }
                return Math.max(6, completedCannons * 3);
            }
            final int zealots = gameState.enemyUnitCount(UnitType.Protoss_Zealot);
            if (zealots < 1) {
                return 2;
            }
        }

        int threshold = 4;

        int rushThresholdIncrease = 0;
        if (gameState.isEarlyRushed()) {
            rushThresholdIncrease = gameState.visibleEnemyMobileGroundCombatUnitsAtOurBases() * 2;
        }
        if (strategyTracker.isAnyDetectedStrategy("2Gate", ProxyGate.NAME)) {
            final int zealots = gameState.enemyUnitCount(UnitType.Protoss_Zealot);
            rushThresholdIncrease = Math.max(rushThresholdIncrease, zealots * 2);
        }
        threshold += rushThresholdIncrease;

        return Math.min(threshold, MAX_MOVE_OUT_THRESHOLD);
    }

    /**
     * Runs one tick of a fight squad that is not holding a containment arc.
     *
     * <p>The composition and hazard branches answer first, before anything is measured: a Defiler
     * only squad, and a squad standing in a psionic storm. Every other status
     * is decided at or below the lock reads, so the retreat lock gates it. A branch placed above
     * those reads returns before the simulator runs and neither lock can see it. The one exception is the retreat
     * lock a harass exit armed, which a measured ENGAGE breaks (see {@link AirHarassEvaluator#breaksExitLock}).
     *
     * <p>A squad with nothing detected anywhere still attacks: the sim has no enemy to weigh, so it
     * returns ADVANCE, and the fighters take the remembered enemy building through
     * {@link #assignFallbackMovementTarget}.
     *
     * <p>While another ground squad holds a containment arc, a ground squad's ADVANCE goes to that arc instead,
     * through {@link #joinActiveContain}, unless one of our bases is threatened. ADVANCE is the verdict for a squad
     * whose sim found no enemy strength to weigh, so the squad would otherwise march blind on a building behind the
     * contained choke.
     *
     * <p>An air squad commits to a fight on a sim-backed ENGAGE: the commitment holds it in FIGHT through RETREAT
     * verdicts until it expires or the flock loses enough hit points (see {@link AirSquad#engageCommitmentHolds}).
     * A RETREAT verdict far below the threshold, or one that sampled static anti-air, is let through (see
     * {@link #commitmentMayHold}). An air squad's retreat lock yields to an ENGAGE of twice the threshold once it
     * has held over a fight hysteresis window (see {@link #retreatLockYieldsToEngage}), and the FIGHT episode that
     * yield opens arms no commitment.
     *
     * @param squad fight squad to tick
     */
    private void simulateFightSquad(Squad squad) {
        HashSet<ManagedUnit> managedFighters = squad.getMembers();

        if (squad.isGroundSquad() && squad.hasOnly(UnitType.Zerg_Defiler)) {
            rallySquad(squad, RallyReason.DEFILER_ONLY);
            return;
        }

        if (stormRetreat(squad)) {
            return;
        }

        Set<Position> enemyBuildingPositions = gameState.getLastKnownPositionsOfBuildings();
        Set<Unit> enemyUnits = gameState.getDetectedEnemyUnits();

        if (enemyUnits.isEmpty() && enemyBuildingPositions.isEmpty()) {
            ScoutData scoutData = gameState.getScoutData();
            if (!scoutData.isEnemyBuildingLocationKnown()) {
                squad.setShouldDisband(true);
                return;
            }
        }

        boolean noVisionMarch = enemyUnits.isEmpty() && !enemyBuildingPositions.isEmpty();

        int now = game.getFrameCount();
        boolean retreatLocked = squad.isRetreatLocked(now);
        boolean fightLocked = squad.isFightLocked(now);

        Map<Squad, Double> adjacentSquads = getAdjacentSquads(squad, REINFORCEMENT_RADIUS);
        CombatSimulator.CombatResult result = squad.getCombatSimulator()
                .evaluate(squad, adjacentSquads, gameState);
        HorizonCombatSimulator.DebugSnapshot snapshot = lastSnapshot(squad);
        boolean enemyMeasured = snapshot == null || snapshot.isEnemyMeasured();
        boolean threatBeyondRadius = snapshot != null && snapshot.isThreatBeyondRadius();
        double ratio = snapshot != null ? snapshot.getOverallRatio() : 0;
        double engageThreshold = snapshot != null ? snapshot.getEngageThreshold() : 0;

        boolean exitLockBroken = AirHarassEvaluator.breaksExitLock(squad.isHarassExitLocked(now), result,
                enemyMeasured);
        if (exitLockBroken) {
            squad.clearRetreatLock();
            squad.setHarassExitEngageFrame(now);
            retreatLocked = false;
        }
        SquadDecisions.simEvaluated(squad, result, retreatLocked, fightLocked);
        SquadDecisions.pathTaken(squad, exitLockBroken
                ? DecisionPath.HARASS_EXIT_ENGAGE
                : requestPath(noVisionMarch, result));

        DecisionPath turnPath = retreatTurnPath(squad.getStatus(), squad.getRetreatRoute(), result, enemyMeasured,
                ratio, engageThreshold);
        if (squad.isGroundSquad() && squad.corneredEngagePersisted(turnPath != null, now)) {
            turnCorneredSquadToFight(squad, result, now);
            SquadDecisions.pathTaken(squad, turnPath);
            assignFightTargets(squad, managedFighters, true);
            return;
        }
        if (squad.getStatus() == SquadStatus.RETREAT && retreatLocked) {
            boolean attritionLock = squad.isAttritionRetreatLock()
                    && ContainmentCollapse.appliesAgainst(gameState.getOpponentRace());
            boolean airYield = retreatLockYieldsToEngage(squad.isAirSquad(), result, enemyMeasured, ratio,
                    engageThreshold);
            if (squad.strongEngagePersisted(airYield || strongEngageBreaksRetreatLock(attritionLock, result,
                    enemyMeasured, ratio, engageThreshold), now)) {
                squad.clearRetreatLock();
                retreatLocked = false;
                if (airYield) {
                    ((AirSquad) squad).barEngageCommitment();
                }
                SquadDecisions.pathTaken(squad, airYield
                        ? DecisionPath.AIR_RETREAT_LOCK_YIELD
                        : DecisionPath.RETREAT_LOCK_BROKEN);
            } else {
                assignRetreatTargets(squad, managedFighters);
                SquadDecisions.lockSuppressed(squad, SquadLock.RETREAT);
                SquadDecisions.pathTaken(squad, DecisionPath.RETREAT_LOCK);
                return;
            }
        }
        if (fightHeld(squad, now, fightLockHolds(fightLocked, result, enemyMeasured, ratio, engageThreshold))) {
            SquadDecisions.lockSuppressed(squad, SquadLock.FIGHT);
            SquadDecisions.pathTaken(squad, DecisionPath.FIGHT_LOCK);
            assignFightTargets(squad, collapseFighters(managedFighters, squad.getCollapse()), false);
            return;
        }
        boolean staticAntiAir = samplesStaticAntiAir(snapshot);
        if (commitmentMayHold(squad.getStatus(), result, squad.isAirSquad(), ratio, engageThreshold, staticAntiAir)
                && ((AirSquad) squad).engageCommitmentHolds(now, flockHitPoints(managedFighters))) {
            SquadDecisions.pathTaken(squad, DecisionPath.AIR_COMMITMENT);
            SquadDecisions.lockSuppressed(squad, SquadLock.FIGHT);
            assignFightTargets(squad, managedFighters, false);
            return;
        }
        if (squad.isAirSquad() && squad.getStatus() == SquadStatus.FIGHT
                && result == CombatSimulator.CombatResult.RETREAT) {
            SquadDecisions.commitmentReleased(squad,
                    ((AirSquad) squad).commitmentRelease(now, flockHitPoints(managedFighters), ratio,
                            engageThreshold, staticAntiAir));
        }

        switch (result) {
            case ADVANCE:
                boolean baseThreatened = squad.getStatus() != SquadStatus.FIGHT && baseThreatened();
                if (!baseThreatened && joinActiveContain(squad)) {
                    break;
                }
                if (blindAdvanceHeld(squad.getStatus(), enemyMeasured, threatBeyondRadius, baseThreatened)) {
                    holdSquad(squad, managedFighters);
                    break;
                }
                if (AirHarassEvaluator.holdsBlindAdvance(squad.getHarassExitFrame(), now, enemyMeasured,
                        squad.getStatus())) {
                    holdSquad(squad, managedFighters);
                    SquadDecisions.pathTaken(squad, DecisionPath.HARASS_HOLD);
                    break;
                }
                squad.setStatus(SquadStatus.FIGHT);
                assignFightTargets(squad, managedFighters, true);
                break;

            case RETREAT:
                boolean enteredContain = tryEnterContainment(squad);
                if (!enteredContain) {
                    squad.setStatus(SquadStatus.RETREAT);
                    assignRetreatTargets(squad, managedFighters);
                    squad.startRetreatLock(now);
                }
                break;

            case ENGAGE:
                squad.setStatus(SquadStatus.FIGHT);
                assignFightTargets(squad, managedFighters, true);
                updateFightLock(squad, result, retreatLocked, now);
                if (squad.isAirSquad() && snapshot != null && enemyMeasured) {
                    ((AirSquad) squad).armEngageCommitment(now, flockHitPoints(managedFighters));
                }
                break;

            default:
                break;
        }
    }

    /**
     * Turns a cornered squad whose ENGAGE persisted, or a HOME_CONTESTED squad whose defend read persisted, to FIGHT:
     * drops its retreat lock and route, holds it in FIGHT for one fight hysteresis window, see
     * {@link Squad#holdCorneredFight}, and arms the fight lock on an ENGAGE.
     *
     * @param squad the cornered or contested squad
     * @param result this frame's combat sim verdict
     * @param now current frame
     */
    static void turnCorneredSquadToFight(Squad squad, CombatSimulator.CombatResult result, int now) {
        squad.setStatus(SquadStatus.FIGHT);
        squad.setRetreatRoute(RetreatRoute.NONE);
        squad.clearRetreatLock();
        squad.holdCorneredFight(now);
        updateFightLock(squad, result, false, now);
    }

    /**
     * Pulls the whole squad out of a Psionic Storm when any member stands in one, under a retreat lock.
     *
     * @param squad fight squad
     * @return true when the squad retreated from a storm this frame
     */
    private boolean stormRetreat(Squad squad) {
        Set<Position> stormPositions = gameState.getActiveStormPositions();
        if (stormPositions.isEmpty()) {
            return false;
        }
        HashSet<ManagedUnit> managedFighters = squad.getMembers();
        boolean anyUnitInStorm = false;
        for (ManagedUnit managedUnit : managedFighters) {
            Position unitPos = managedUnit.getUnit().getPosition();
            for (Position stormPos : stormPositions) {
                if (unitPos.getDistance(stormPos) <= PsiStormTracker.STORM_RADIUS) {
                    anyUnitInStorm = true;
                    break;
                }
            }
            if (anyUnitInStorm) break;
        }
        if (!anyUnitInStorm) {
            return false;
        }
        squad.setStatus(SquadStatus.RETREAT);
        squad.setRetreatRoute(RetreatRoute.NONE);
        SquadDecisions.pathTaken(squad, DecisionPath.STORM_RETREAT);
        int now = game.getFrameCount();
        for (ManagedUnit managedUnit : managedFighters) {
            managedUnit.setRole(UnitRole.RETREAT);
            managedUnit.markRetreatStart(now);
            Position retreatTarget = calculateStormRetreatPosition(managedUnit.getUnit().getPosition(), stormPositions);
            managedUnit.setRetreatTarget(retreatTarget);
        }
        squad.startRetreatLock(now);
        return true;
    }

    /**
     * Arms or renews the fight lock on an ENGAGE verdict.
     *
     * <p>A retreat lock blocks the fight lock, and so does a squad that has lost supply since the
     * lock it would be renewing was armed (see {@link Squad#canRenewFightLock}).
     *
     * @param squad squad whose verdict this is
     * @param result this frame's combat sim verdict
     * @param retreatLocked whether the squad's retreat lock is currently active
     * @param currentFrame current frame
     */
    static void updateFightLock(Squad squad, CombatSimulator.CombatResult result,
                                boolean retreatLocked, int currentFrame) {
        if (result == CombatSimulator.CombatResult.ENGAGE && !retreatLocked
                && squad.canRenewFightLock(currentFrame)) {
            squad.startFightLock(currentFrame);
        }
    }

    /**
     * Whether this frame's verdict breaks an active retreat lock.
     *
     * <p>Only the lock a contain's attrition exit armed can be broken, and only by an ENGAGE measured against a real
     * enemy at or above {@link #strongEngageThreshold}. Any other retreat lock holds for its full window. The lock
     * breaks only once such a read has held over a fight hysteresis window, see {@link Squad#strongEngagePersisted},
     * and never against Protoss, see {@link ContainmentCollapse#appliesAgainst}.
     *
     * @param attritionLock true when the lock was armed by a contain's attrition exit against an opponent the
     *     matchup gate admits
     * @param result this frame's combat sim verdict
     * @param enemyMeasured whether the sim measured a real enemy this frame
     * @param ratio the sim's overall strength ratio this frame
     * @param engageThreshold the engage threshold the sim judged this frame's ratio against
     * @return true when the lock is dropped and the verdict acts
     */
    static boolean strongEngageBreaksRetreatLock(boolean attritionLock, CombatSimulator.CombatResult result,
                                                 boolean enemyMeasured, double ratio, double engageThreshold) {
        return attritionLock && result == CombatSimulator.CombatResult.ENGAGE && enemyMeasured
                && ratio >= strongEngageThreshold(engageThreshold);
    }

    /**
     * Ratio an ENGAGE must reach to break an attrition retreat lock: {@link #STRONG_ENGAGE_RATIO}, or the matchup
     * engage threshold when that is higher.
     *
     * @param engageThreshold the matchup engage threshold
     * @return the strong engage threshold
     */
    static double strongEngageThreshold(double engageThreshold) {
        return Math.max(engageThreshold, STRONG_ENGAGE_RATIO);
    }

    /**
     * Whether a FIGHT squad stays in FIGHT whatever this frame's verdict: a collapse is wrapping, a committed collapse
     * still holds it, see {@link Squad#isCollapseCommitHeld}, a cornered squad that turned to fight is still held, see
     * {@link Squad#isCorneredFightHeld}, or its fight lock holds against the verdict, see {@link #fightLockHolds}. A
     * collapse was judged on the enemies inside the arc's sector, so a whole-squad RETREAT read around the squad's
     * center does not undo it, and a cornered squad has no path home to retreat along.
     *
     * @param squad fight squad
     * @param now current frame
     * @param fightLockHolds whether the squad's fight lock holds against this frame's verdict
     * @return true when the squad stays in FIGHT and this frame's verdict is suppressed
     */
    static boolean fightHeld(Squad squad, int now, boolean fightLockHolds) {
        return squad.getStatus() == SquadStatus.FIGHT
                && (fightLockHolds || collapseHoldsMembers(squad, now) || squad.isCorneredFightHeld(now));
    }

    /**
     * Whether this frame's verdict is a strong enough ENGAGE to count toward breaking an air squad's retreat lock.
     *
     * <p>An air squad's retreat lock yields to an ENGAGE measured against a real enemy at
     * {@link #RETREAT_LOCK_ENGAGE_BREAK_MULTIPLIER} times the engage threshold or more, so a flock that
     * fell back on a weak read turns around on a decisive one instead of waiting out the lock. The lock
     * breaks only once such reads have held over a fight hysteresis window, see {@link Squad#strongEngagePersisted},
     * so a single spiking read does not break it. Ground squads keep their retreat lock, and a verdict with no
     * threshold (no snapshot) never breaks it.
     *
     * @param airSquad whether the squad is an air squad
     * @param result this frame's combat sim verdict
     * @param enemyMeasured whether the sim measured a real enemy this frame
     * @param ratio the sim's overall strength ratio this frame
     * @param engageThreshold the engage threshold the sim judged this frame's ratio against
     * @return true if this frame's read counts toward breaking the retreat lock
     */
    static boolean retreatLockYieldsToEngage(boolean airSquad, CombatSimulator.CombatResult result,
                                             boolean enemyMeasured, double ratio, double engageThreshold) {
        return airSquad && result == CombatSimulator.CombatResult.ENGAGE && enemyMeasured && engageThreshold > 0
                && ratio >= engageThreshold * RETREAT_LOCK_ENGAGE_BREAK_MULTIPLIER;
    }

    /**
     * Whether this frame is one an air squad's engage commitment is consulted on: an air squad in FIGHT given a
     * RETREAT verdict that does not release the commitment outright (see
     * {@link AirSquad#retreatReleasesCommitment}). The commitment itself decides whether it still holds (see
     * {@link AirSquad#engageCommitmentHolds}).
     *
     * @param status the squad's status before this frame's verdict
     * @param result this frame's combat sim verdict
     * @param airSquad whether the squad is an air squad
     * @param ratio the sim's overall strength ratio this frame
     * @param engageThreshold the engage threshold the sim judged this frame's ratio against
     * @param staticAntiAir whether this frame's verdict sampled a building that can attack air
     * @return true if the commitment should be asked to hold the squad in FIGHT
     */
    static boolean commitmentMayHold(SquadStatus status, CombatSimulator.CombatResult result, boolean airSquad,
                                     double ratio, double engageThreshold, boolean staticAntiAir) {
        return airSquad && status == SquadStatus.FIGHT && result == CombatSimulator.CombatResult.RETREAT
                && !AirSquad.retreatReleasesCommitment(ratio, engageThreshold, staticAntiAir);
    }

    /**
     * Whether a verdict sampled static anti-air. An air squad's snapshot records each enemy at its anti-air
     * strength, so a building entry with strength above zero is a building that can shoot the flock.
     *
     * @param snapshot the air squad's snapshot for this frame, or null when the sim left none
     * @return true if the snapshot holds a building with anti-air strength
     */
    static boolean samplesStaticAntiAir(HorizonCombatSimulator.DebugSnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }
        for (HorizonCombatSimulator.UnitDebugEntry entry : snapshot.getEnemyUnits()) {
            if (entry.getType().isBuilding() && entry.getStrength() > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param fighters members of the squad
     * @return summed hit points of the members that count toward the flock, see {@link #countsTowardFlockHitPoints}
     */
    private static int flockHitPoints(Collection<ManagedUnit> fighters) {
        int hitPoints = 0;
        for (ManagedUnit fighter : fighters) {
            if (countsTowardFlockHitPoints(fighter.getUnitType())) {
                hitPoints += fighter.getUnit().getHitPoints();
            }
        }
        return hitPoints;
    }

    /**
     * Whether a member's hit points count toward the flock an engage commitment measures its loss against. An
     * escorting Overlord does not, as the air sim leaves it out of the friendly force.
     *
     * @param type the member's type
     * @return true if the member's hit points count
     */
    static boolean countsTowardFlockHitPoints(UnitType type) {
        return type != UnitType.Zerg_Overlord;
    }

    /**
     * Whether an active fight lock still holds against this frame's verdict.
     *
     * <p>The lock holds a squad in FIGHT against ENGAGE and ADVANCE verdicts, and breaks for a
     * RETREAT that was measured against a real enemy below the engage threshold the sim judged it
     * by. Every RETREAT from the Horizon sim is measured and below that threshold by construction,
     * so the lock never discards one. A simulator that leaves no snapshot reports a ratio and a
     * threshold of 0, which holds.
     *
     * @param fightLocked whether the squad's fight lock is currently active
     * @param result this frame's combat sim verdict
     * @param enemyMeasured whether the sim measured a real enemy this frame
     * @param ratio the sim's overall strength ratio this frame
     * @param engageThreshold the engage threshold the sim judged this frame's ratio against
     * @return true if the lock should still suppress this frame's verdict
     */
    static boolean fightLockHolds(boolean fightLocked, CombatSimulator.CombatResult result,
                                  boolean enemyMeasured, double ratio, double engageThreshold) {
        if (!fightLocked) return false;
        if (result != CombatSimulator.CombatResult.RETREAT) return true;
        return !enemyMeasured || ratio >= engageThreshold;
    }

    /**
     * Names the branch asking for this frame's status, read before either lock is consulted.
     *
     * <p>A squad with no detected enemy anywhere cannot be given a fight target by
     * {@link #assignEnemyTarget}, so every member falls through to the remembered building march
     * whatever the verdict says. The march therefore outranks the verdict as the description of
     * what the squad is doing, and the sim columns on the row still carry the verdict itself.
     *
     * @param noVisionMarch true when no enemy unit is detected and an enemy building is remembered
     * @param result this frame's combat sim verdict
     * @return the branch the row should name
     */
    static DecisionPath requestPath(boolean noVisionMarch, CombatSimulator.CombatResult result) {
        if (noVisionMarch) {
            return DecisionPath.NO_VISION_MARCH;
        }
        if (result == null) {
            return DecisionPath.NONE;
        }
        switch (result) {
            case ADVANCE:
                return DecisionPath.SIM_ADVANCE;
            case ENGAGE:
                return DecisionPath.SIM_ENGAGE;
            case RETREAT:
                return DecisionPath.SIM_RETREAT;
            default:
                return DecisionPath.NONE;
        }
    }

    /**
     * Whether a blind ADVANCE verdict should be held rather than committed as a transition into FIGHT.
     *
     * <p>A squad already in FIGHT is mid-approach and must keep going so the sim can measure the
     * enemy at close range and return a real verdict; only a transition into FIGHT is blocked.
     * An unmeasured ADVANCE is held when the sim itself saw a fresh, attack-capable, non-worker
     * enemy just beyond its engagement radius, or when a mobile ground combat unit is standing on
     * one of our bases.
     *
     * @param status the squad's status entering this tick
     * @param enemyMeasured whether the sim measured a real enemy this frame
     * @param threatBeyondRadius whether the sim saw a nearby but unmeasured threat this frame
     * @param baseThreatened whether a mobile ground combat unit is on one of our bases
     * @return true if the blind ADVANCE should be held rather than acted on
     */
    static boolean blindAdvanceHeld(SquadStatus status, boolean enemyMeasured,
                                    boolean threatBeyondRadius, boolean baseThreatened) {
        if (status == SquadStatus.FIGHT || enemyMeasured) return false;
        return threatBeyondRadius || baseThreatened;
    }

    private HorizonCombatSimulator.DebugSnapshot lastSnapshot(Squad squad) {
        CombatSimulator sim = squad.getCombatSimulator();
        if (!(sim instanceof HorizonCombatSimulator)) {
            return null;
        }
        return ((HorizonCombatSimulator) sim).getLastSnapshots().get(squad.getId());
    }

    private boolean baseThreatened() {
        return gameState.visibleEnemyMobileGroundCombatUnitsAtOurBases() > 0;
    }

    private void holdSquad(Squad squad, HashSet<ManagedUnit> managedFighters) {
        if (squad.getStatus() == SquadStatus.RETREAT) {
            assignRetreatTargets(squad, managedFighters);
        } else {
            rallySquad(squad, RallyReason.HOLD);
        }
    }

    private void clearCombatSimSnapshot(Squad squad) {
        CombatSimulator sim = squad.getCombatSimulator();
        if (sim instanceof HorizonCombatSimulator) {
            ((HorizonCombatSimulator) sim).getLastSnapshots().remove(squad.getId());
        }
    }

    private void assignRetreatTargets(Squad squad, HashSet<ManagedUnit> managedFighters) {
        Position rallyPoint = gameState.getSquadRallyPoint();
        int now = game.getFrameCount();
        boolean keepPlan = squad.isGroundSquad()
                && retreatPlanFresh(squad.getRetreatRoute(), squad.getRetreatPlanFrame(), now);
        Map<ManagedUnit, Position> retreatTargets = squad.isGroundSquad() && !keepPlan
                ? planGroundRetreat(squad, rallyPoint, now)
                : null;
        if (keepPlan) {
            SquadDecisions.retreatRouted(squad, squad.getRetreatRoute());
        }
        for (ManagedUnit managedUnit : managedFighters) {
            if (managedUnit.getRole() != UnitRole.RETREAT) {
                managedUnit.setReady(true);
            }
            managedUnit.setRole(UnitRole.RETREAT);
            managedUnit.setRallyPoint(rallyPoint);
            if (keepPlan) {
                continue;
            }
            if (retreatTargets != null) {
                managedUnit.setRetreatTarget(retreatTargets.get(managedUnit));
            } else {
                managedUnit.setRetreatTarget(managedUnit.getRetreatPosition());
            }
        }
    }

    /**
     * Puts every fighter in FIGHT and picks its target. Fighters are targeted in unit id order against the frame's
     * {@link TargetLedger}, shared by every fight squad, so each melee pick counts toward the load every later
     * fighter sees, whichever squad it is in.
     */
    private void assignFightTargets(Squad squad, HashSet<ManagedUnit> managedFighters, boolean clearRetreat) {
        TargetLedger ledger = fightTargetLedger();
        List<ManagedUnit> ordered = new ArrayList<>(managedFighters);
        ordered.sort(Comparator.comparingInt(ManagedUnit::getUnitID));
        for (ManagedUnit managedUnit : ordered) {
            managedUnit.setRole(UnitRole.FIGHT);
            if (clearRetreat) {
                managedUnit.clearRetreatStart();
            }
            assignEnemyTarget(managedUnit, squad, ledger);
        }
    }

    /**
     * Runs the containment entry decision and reports the verdict that produced it.
     *
     * <p>The evaluator calls stay short circuited in their original order: canBreakContainment is
     * consulted only when shouldContain holds, and enterContainment only when both allow it. No squad takes an arc
     * while a contain escalation or a contain stalemate bars entry, see {@link #mayTakeArc}.
     *
     * @param squad squad offered an arc
     * @return true if the squad took the arc and is now containing
     */
    private boolean tryEnterContainment(Squad squad) {
        int now = game.getFrameCount();
        boolean underAttack = baseThreatensContainment();
        boolean shouldContain = containmentEvaluator.shouldContain(squad);
        boolean canBreak = shouldContain && containmentEvaluator.canBreakContainment(fightSquads, now);
        boolean entered = mayTakeArc(containmentEscalation, gameState.getContainmentStalemate(), now, underAttack,
                shouldContain, canBreak) && enterContainment(squad);
        SquadDecisions.containmentEvaluated(squad, shouldContain, canBreak, entered);
        return entered;
    }

    /**
     * Whether a squad offered an arc may take it: neither a contain escalation's hold nor a contain stalemate's hold
     * or commit bars entry, see {@link ContainmentStalemate#barsEntry}, and {@link #mayEnterContainment} allows it.
     *
     * @param escalation the army's run of timeout re-entries
     * @param stalemate the army's stalemate state
     * @param now current frame
     * @param basesUnderAttack true when a combat unit threatens one of our bases
     * @param shouldContain true when containment applies to the squad
     * @param canBreak true when the strength gate clears the army to push in
     * @return true when the squad may take the arc
     */
    static boolean mayTakeArc(ContainmentEscalation escalation, ContainmentStalemate stalemate, int now,
                              boolean basesUnderAttack, boolean shouldContain, boolean canBreak) {
        return !escalation.holdsEntry(now) && !stalemate.barsEntry(now)
                && mayEnterContainment(basesUnderAttack, shouldContain, canBreak);
    }

    /**
     * Whether a squad may take a containment arc.
     *
     * <p>A contain is never entered while a base is under attack. A containing squad breaks on that threat on the
     * next frame, so an arc taken under it is dropped again and re-taken on the following sim RETREAT, and the
     * squad alternates CONTAIN and FIGHT instead of retreating.
     *
     * @param basesUnderAttack true when a combat unit threatens one of our bases, see {@link #threatensContainment}
     * @param shouldContain true when containment applies to the squad
     * @param canBreak true when the strength gate clears the army to push in
     * @return true when the squad may enter containment
     */
    static boolean mayEnterContainment(boolean basesUnderAttack, boolean shouldContain, boolean canBreak) {
        return !basesUnderAttack && shouldContain && !canBreak;
    }

    /**
     * Puts a squad on the arc it is offered once it has arrived there. A squad farther than
     * {@link #CONTAIN_ARRIVAL_DISTANCE} from every arc point stays out of CONTAIN and keeps running the sim on its
     * way, so it answers the enemies it meets in transit rather than walking through them to a line at the choke. A
     * squad wrapping in a collapse never takes an arc: the collapse ends only in its commit or in the squad leaving
     * FIGHT, see {@link #holdCollapseWrap}. Nor does a squad a committed collapse still holds, see
     * {@link Squad#isCollapseCommitHeld}.
     *
     * @param squad squad offered an arc
     * @return true if the squad took the arc
     */
    private boolean enterContainment(Squad squad) {
        if (squad.getCollapse() != null || squad.isCollapseCommitHeld(game.getFrameCount())) return false;
        Arc arc = containmentArc(squad);
        if (arc == null) return false;
        double arcDistance = squad.getCenter().getDistance(arc.closestPosition(squad.getCenter()));
        SquadDecisions.containArcMeasured(squad, (int) arcDistance);
        if (!arrivedAtArc(arcDistance)) return false;
        squad.setStatus(SquadStatus.CONTAIN);
        SquadDecisions.pathTaken(squad, DecisionPath.CONTAIN_ENTER);
        squad.startContainLock(game.getFrameCount());
        containmentEscalation.onEntered(game.getFrameCount());
        assignContainmentPositions(squad, arc);
        return true;
    }

    /**
     * Whether a squad is close enough to the arc it is offered to take it.
     *
     * @param arcDistance pixels from the squad's center to the nearest arc point
     * @return true within {@link #CONTAIN_ARRIVAL_DISTANCE}
     */
    static boolean arrivedAtArc(double arcDistance) {
        return arcDistance <= CONTAIN_ARRIVAL_DISTANCE;
    }

    /**
     * Verdicts available to a squad that is already holding a containment arc.
     */
    enum ContainmentVerdict {
        BREAK_ALL,
        ESCALATE,
        COLLAPSE,
        RETREAT,
        STALEMATE,
        PUSH_BACK,
        HOLD,
        REPOSITION
    }

    /**
     * Whether a member of a containing squad was hit this frame by something it cannot answer, and if so whether
     * any arc point on the choke is left out of every known reach.
     */
    enum OutrangedHit {
        NONE,
        ARC_KEPT,
        ARC_LOST
    }

    /**
     * Picks what a squad holding a containment arc does this frame.
     *
     * <p>A containing squad always sits within the squad detection radius of the units it contains, so mere
     * proximity carries no information and never ends an episode. Only the strength gate, the timeout, a base
     * under attack, attrition, losing every arc point to outranging fire, or containment ceasing to apply end
     * one. An enemy that has reached the arc holds the squad in place instead of moving it: each unit already
     * returns fire inside its own weapon range, so the squad trades on the line rather than charging a position it
     * has been measured as unable to break.
     *
     * <p>A member hit by something it cannot answer moves the arc out of every known reach, overriding both the
     * throttle and an enemy on the arc; with no arc point left out of reach the squad retreats.
     *
     * <p>A collapse, see {@link ContainmentCollapse}, is ranked over this verdict by {@link #rankCollapse}: below a
     * base under attack and above everything else, on any frame.
     *
     * <p>Bases under attack, attrition, an outranged hit and a lost arc outrank the re-evaluation throttle and are
     * the only verdicts reachable on a throttled frame. A squad being ground down, hit from out of its reach, or with
     * nowhere left to stand out of reach, acts on the frame it happens rather than at the next re-evaluation tick.
     *
     * <p>Only a base under attack and the strength gate move the whole army; they are the two signals that are
     * true for every squad at once. A squad that has run out its own containment clock disengages by itself
     * rather than committing squads whose gate has not fired. A timeout RETREAT can still become ESCALATE afterwards,
     * see {@link #escalatedVerdict}, or STALEMATE, see {@link #stalemateVerdict}.
     *
     * @param basesUnderAttack true when a combat unit threatens one of our bases, see {@link #threatensContainment}
     * @param bleeding true when the squad is losing supply within the attrition window while killing little
     * @param outrangedHit whether a member was hit this frame with no enemy in its own range, and whether an arc
     *     point on the choke stays out of reach of every enemy that outranges the squad
     * @param throttled true when the contain lock holds and this frame is not a re-evaluation tick
     * @param engaged true when a mobile enemy is within contact range of a member
     * @param timedOut true when the episode has run past the containment timeout
     * @param canBreak true when the strength gate clears the army to push in
     * @param shouldContain true when containment still applies to this squad
     * @return verdict for this frame
     */
    static ContainmentVerdict containmentVerdict(boolean basesUnderAttack, boolean bleeding,
                                                 OutrangedHit outrangedHit, boolean throttled, boolean engaged,
                                                 boolean timedOut, boolean canBreak, boolean shouldContain) {
        if (basesUnderAttack) {
            return ContainmentVerdict.BREAK_ALL;
        }
        if (bleeding || outrangedHit == OutrangedHit.ARC_LOST) {
            return ContainmentVerdict.RETREAT;
        }
        if (outrangedHit == OutrangedHit.ARC_KEPT) {
            return ContainmentVerdict.PUSH_BACK;
        }
        if (throttled) {
            return ContainmentVerdict.HOLD;
        }
        if (canBreak) {
            return ContainmentVerdict.BREAK_ALL;
        }
        if (timedOut) {
            return ContainmentVerdict.RETREAT;
        }
        if (!shouldContain) {
            return ContainmentVerdict.RETREAT;
        }
        if (engaged) {
            return ContainmentVerdict.HOLD;
        }
        return ContainmentVerdict.REPOSITION;
    }

    /**
     * Ranks a passed collapse test against the verdict {@link #containmentVerdict} picked: below a base under
     * attack, above every other verdict, so a squad bleeding or hit from out of its reach collapses on enemies it can
     * beat inside its arc instead of retreating or pushing back.
     *
     * @param basesUnderAttack true when a combat unit threatens one of our bases, see {@link #threatensContainment}
     * @param collapse true when the collapse test on the enemies inside the arc's sector passed
     * @param verdict the verdict without the collapse
     * @return COLLAPSE when the test passed and no base is under attack, else the verdict
     */
    static ContainmentVerdict rankCollapse(boolean basesUnderAttack, boolean collapse, ContainmentVerdict verdict) {
        return !basesUnderAttack && collapse ? ContainmentVerdict.COLLAPSE : verdict;
    }

    private void evaluateContainingSquad(Squad squad) {
        int now = game.getFrameCount();
        if (now % RunbyEvaluator.RUNBY_TICK == 0 && tryEnterRunby(squad, now)) {
            containEndedOtherwise();
            return;
        }
        HashSet<ManagedUnit> members = squad.getMembers();

        boolean basesUnderAttack = baseThreatensContainment();
        ContainmentCollapse.Read collapseTest = basesUnderAttack ? null : readCollapse(squad, now);
        ContainmentCollapse.UnderFire collapseUnderFire = collapseTest != null
                && collapseTest.getOutcome() == ContainmentCollapse.Outcome.COLLAPSE
                ? collapseUnderFire(squad, now)
                : ContainmentCollapse.UnderFire.NONE;
        ContainmentCollapse.Read collapseRead = gateCollapse(squad, collapseTest, collapseUnderFire, now);
        boolean collapse = collapseRead != null && collapseRead.getOutcome() == ContainmentCollapse.Outcome.COLLAPSE;
        boolean bleeding = !basesUnderAttack && squad.getContainmentAttrition().isBleeding(now, squad.getSupply());
        boolean outrangedHit = !basesUnderAttack && !collapse && !bleeding && hasOutrangedHit(squad);
        List<StaticDefenseZone> zones = outrangedHit ? containmentZones(squad, now) : Collections.emptyList();
        Arc underFire = outrangedHit ? arcUnderFire(squad, zones) : null;
        boolean arcLost = outrangedHit && underFire == null;
        OutrangedHit hit = outrangedHitVerdict(outrangedHit, arcLost);
        boolean throttled = isContainmentThrottled(squad, now);
        boolean evaluate = !basesUnderAttack && !collapse && !bleeding && !outrangedHit && !throttled;
        boolean timedOut = evaluate && containmentTimedOut(squad, now);
        ContainmentEvaluator.BreakMeasure breakMeasure = evaluate
                ? containmentEvaluator.measureBreak(fightSquads, now) : null;
        boolean canBreak = breakMeasure != null && breakMeasure.breaks();
        boolean shouldContain = !evaluate || containmentEvaluator.shouldContain(squad);
        boolean engaged = evaluate && enemiesOnContainmentArc(squad);

        SquadDecisions.outrangedHit(squad, outrangedHit);
        ContainmentVerdict evaluated = containmentVerdict(basesUnderAttack, bleeding, hit, throttled, engaged,
                timedOut, canBreak, shouldContain);
        boolean onTimeout = timeoutRetreat(evaluated, timedOut, shouldContain);
        boolean staticOnly = onTimeout && containmentEvaluator.enemyDefenceIsStaticOnly(now);
        int reentries = containmentEscalation.getReentries();
        if (onTimeout) {
            SquadDecisions.containmentTimedOut(squad, reentries, staticOnly);
        }
        ContainmentVerdict escalated = escalatedVerdict(evaluated, onTimeout, containmentEscalation,
                () -> staticOnly, now);
        boolean breakUnreachable = onTimeout && breakMeasure.unreachable();
        ContainmentVerdict stalemated = stalemateVerdict(escalated, onTimeout, reentries, staticOnly,
                breakUnreachable, gameState.getContainmentStalemate(), now);
        if (onTimeout) {
            SquadDecisions.containmentStalemateRead(squad, breakMeasure.shortfall(), breakUnreachable,
                    stalemated == ContainmentVerdict.STALEMATE);
        }
        ContainmentVerdict verdict = rankCollapse(basesUnderAttack, collapse, stalemated);

        DecisionPath exitPath = containmentExitPath(bleeding, arcLost);
        if (breaksHeldContain(verdict, exitPath)) {
            gameState.getContainHeldTimer().broken();
        }
        switch (verdict) {
            case BREAK_ALL:
                containEndedOtherwise();
                breakAllContainment(now, DecisionPath.CONTAIN_BREAK);
                break;
            case ESCALATE:
                gameState.getContainmentStalemate().onEndedOtherwise();
                breakAllContainment(now, DecisionPath.CONTAIN_ESCALATE);
                break;
            case COLLAPSE:
                containEndedOtherwise();
                collapseContainingSquad(squad, collapseRead, now);
                break;
            case RETREAT:
                if (!onTimeout) {
                    containEndedOtherwise();
                }
                retreatFromContainment(squad, members, now, exitPath);
                break;
            case STALEMATE:
                retreatFromContainment(squad, members, now, DecisionPath.CONTAIN_STALEMATE);
                break;
            case PUSH_BACK:
                pushBackContainingSquad(squad, zones, underFire);
                break;
            case REPOSITION:
                repositionContainingSquad(squad, members, now);
                break;
            default:
                break;
        }
    }

    /**
     * Whether a containing squad's verdict is a retreat on the containment timeout alone: a RETREAT on a frame its
     * clock ran out while containment still applied to it. A squad that has also stopped qualifying to contain on
     * that frame retreats because containment ceased to apply, which ends the run of re-entries rather than
     * counting toward it.
     *
     * @param verdict verdict from {@link #containmentVerdict}
     * @param timedOut true when the episode ran past the containment timeout this frame
     * @param shouldContain true when containment still applies to the squad
     * @return true for a timeout retreat
     */
    static boolean timeoutRetreat(ContainmentVerdict verdict, boolean timedOut, boolean shouldContain) {
        return verdict == ContainmentVerdict.RETREAT && timedOut && shouldContain;
    }

    /**
     * Turns the timeout retreat into an escalation when {@link ContainmentEscalation} says the run of re-entries has
     * reached its limit against a static-only defence, and records every other timeout toward that run.
     *
     * <p>The static-only test is read only on a timeout.
     *
     * @param verdict verdict from {@link #containmentVerdict}
     * @param timedOut true when the episode ran past the containment timeout this frame
     * @param escalation the army's run of timeout re-entries
     * @param staticOnly whether the enemy has no known army outside its static defence
     * @param now current frame
     * @return ESCALATE for an escalating timeout, else the verdict unchanged
     */
    static ContainmentVerdict escalatedVerdict(ContainmentVerdict verdict, boolean timedOut,
                                               ContainmentEscalation escalation, BooleanSupplier staticOnly,
                                               int now) {
        if (verdict != ContainmentVerdict.RETREAT || !timedOut) {
            return verdict;
        }
        return escalation.onTimedOut(staticOnly.getAsBoolean(), now) ? ContainmentVerdict.ESCALATE : verdict;
    }

    /**
     * Turns a timeout retreat that did not escalate into a stalemate exit when {@link ContainmentStalemate} reads
     * the contain as one that can neither break nor escalate. The squad still retreats, and no squad may take an
     * arc for the stalemate's hold window.
     *
     * @param verdict verdict after {@link #escalatedVerdict}
     * @param onTimeout true for a timeout retreat, see {@link #timeoutRetreat}
     * @param reentries re-entries after a timeout in the current run, read before this timeout was recorded
     * @param staticOnly whether the enemy has no known army outside its static defence
     * @param breakUnreachable whether the break needs more than the supply cap
     * @param stalemate the army's stalemate state
     * @param now current frame
     * @return STALEMATE for a stalemate timeout, else the verdict unchanged
     */
    static ContainmentVerdict stalemateVerdict(ContainmentVerdict verdict, boolean onTimeout, int reentries,
                                               boolean staticOnly, boolean breakUnreachable,
                                               ContainmentStalemate stalemate, int now) {
        if (verdict != ContainmentVerdict.RETREAT || !onTimeout) {
            return verdict;
        }
        return stalemate.onTimedOut(reentries, staticOnly, breakUnreachable, now)
                ? ContainmentVerdict.STALEMATE : verdict;
    }

    /**
     * Records that a contain ended some other way than by timing out, clearing both the run of timeout re-entries
     * and a detected stalemate.
     */
    private void containEndedOtherwise() {
        containmentEscalation.onEndedOtherwise();
        gameState.getContainmentStalemate().onEndedOtherwise();
    }

    /**
     * Starts, pauses, resumes or releases the maxed-army commit of a detected stalemate, see
     * {@link ContainmentStalemate#onFrame}. A base is threatened on the predicate containment entry and the break
     * read, {@link #baseThreatensContainment}. On a start or a resume every squad the commit takes over, see
     * {@link ContainmentStalemate#takesOver}, drops its retreat lock and any collapse under way; on a pause or a
     * release the squads are handed back to the normal rules. Each change is logged on every ground squad.
     *
     * @param now current frame
     */
    private void updateStalemateCommit(int now) {
        ContainmentStalemate stalemate = gameState.getContainmentStalemate();
        int armySupply = groundArmySupply(fightSquads);
        boolean targetKnown = !gameState.getLastKnownPositionsOfBuildings().isEmpty()
                || gameState.getBaseData().getMainEnemyBase() != null;
        ContainmentStalemate.CommitChange change = stalemate.onFrame(game.self().supplyUsed(), armySupply,
                targetKnown, baseThreatensContainment());
        if (change == ContainmentStalemate.CommitChange.NONE) {
            return;
        }
        DecisionPath path = commitChangePath(change);
        for (Squad squad : fightSquads) {
            if (!squad.isGroundSquad()) {
                continue;
            }
            SquadDecisions.stalemateCommit(squad, stalemate.getCommittedSupply(), armySupply);
            SquadDecisions.pathTaken(squad, path);
            if (ContainmentStalemate.takesOver(true, squad.getStatus(), stalemate.isCommitting(),
                    stalemate.isCommitPaused())) {
                squad.clearRetreatLock();
                if (squad.getCollapse() != null) {
                    squad.endCollapse(now);
                }
            }
        }
    }

    /**
     * The decision path a change to the stalemate commit is logged on.
     *
     * @param change a change other than NONE
     * @return STALEMATE_COMMIT for a start, STALEMATE_COMMIT_PAUSE, STALEMATE_COMMIT_RESUME, or
     *     STALEMATE_COMMIT_RELEASE
     */
    static DecisionPath commitChangePath(ContainmentStalemate.CommitChange change) {
        switch (change) {
            case PAUSED:
                return DecisionPath.STALEMATE_COMMIT_PAUSE;
            case RESUMED:
                return DecisionPath.STALEMATE_COMMIT_RESUME;
            case RELEASED:
                return DecisionPath.STALEMATE_COMMIT_RELEASE;
            default:
                return DecisionPath.STALEMATE_COMMIT;
        }
    }

    /**
     * Supply of the ground fight squads, the army a stalemate commit sends in.
     *
     * @param squads the fight squads
     * @return summed supply of the ground squads, in BWAPI half-supply
     */
    static int groundArmySupply(Collection<Squad> squads) {
        int supply = 0;
        for (Squad squad : squads) {
            if (squad.isGroundSquad()) {
                supply += squad.getSupply();
            }
        }
        return supply;
    }

    /**
     * Runs one frame of a ground squad under a stalemate commit: the whole-squad storm retreat still pulls it out of
     * a Psionic Storm and holds it back while that retreat lock lasts, see
     * {@link ContainmentStalemate#stormRetreatHolds}; otherwise it drops any collapse under way, leaves any arc,
     * fights under a fight lock and marches on the enemy, whatever the combat sim would read, and is marked committed
     * as a whole, so its Lurkers go in with it, see {@link #wholeSquadCommit}.
     *
     * @param squad ground squad
     * @param now current frame
     */
    private void commitSquad(Squad squad, int now) {
        if (stormRetreat(squad)) {
            return;
        }
        if (ContainmentStalemate.stormRetreatHolds(squad.getStatus(), squad.isRetreatLocked(now))) {
            SquadDecisions.lockSuppressed(squad, SquadLock.RETREAT);
            assignRetreatTargets(squad, squad.getMembers());
            return;
        }
        if (squad.getCollapse() != null) {
            squad.endCollapse(now);
        }
        if (squad.getStatus() == SquadStatus.CONTAIN) {
            endContainment(squad);
        }
        if (squad.getStatus() != SquadStatus.FIGHT) {
            squad.setStatus(SquadStatus.FIGHT);
            squad.startFightLock(now);
        }
        squad.commit(now);
        wholeSquadCommits.add(squad);
        assignFightTargets(squad, squad.getMembers(), true);
    }

    /**
     * Names the branch that sent a containing squad back, so attrition and outranging exits are separable from the
     * timeout and the size floor.
     *
     * @param bleeding true when the attrition rule fired
     * @param arcLost true when no arc point stayed out of reach of an outranging enemy
     * @return the decision path of the retreat
     */
    static DecisionPath containmentExitPath(boolean bleeding, boolean arcLost) {
        if (bleeding) {
            return DecisionPath.CONTAIN_ATTRITION;
        }
        if (arcLost) {
            return DecisionPath.CONTAIN_OUTRANGED;
        }
        return DecisionPath.CONTAIN_RETREAT;
    }

    /**
     * Whether a containing squad's verdict ends the held contain at once rather than letting
     * {@link ContainHeldTimer} bridge it: every BREAK_ALL, whether a base is under attack or the strength
     * gate sends the army in, an ESCALATE, which sends it in too, and a retreat the enemy forced by attrition or an
     * outranged arc. A retreat on the timeout, a stalemate exit, a retreat on containment ceasing to apply, or one
     * with no arc left clear of static defence is bridged.
     *
     * @param verdict what the containing squad does this frame
     * @param retreatPath the decision path a RETREAT verdict retreats on
     * @return true when the held contain is broken
     */
    static boolean breaksHeldContain(ContainmentVerdict verdict, DecisionPath retreatPath) {
        if (verdict == ContainmentVerdict.BREAK_ALL || verdict == ContainmentVerdict.ESCALATE) {
            return true;
        }
        return verdict == ContainmentVerdict.RETREAT
                && (retreatPath == DecisionPath.CONTAIN_ATTRITION || retreatPath == DecisionPath.CONTAIN_OUTRANGED);
    }

    private void retreatFromContainment(Squad squad, HashSet<ManagedUnit> members, int now, DecisionPath path) {
        endContainment(squad);
        squad.setStatus(SquadStatus.RETREAT);
        SquadDecisions.pathTaken(squad, path);
        assignRetreatTargets(squad, members);
        if (path == DecisionPath.CONTAIN_ATTRITION) {
            squad.startAttritionRetreatLock(now);
        } else {
            squad.startRetreatLock(now);
        }
    }

    /**
     * Applies the collapse hysteresis gate, see {@link ContainmentCollapse#gate}, to this evaluation's collapse test.
     * The test is recorded on the squad's entry run first, see {@link CollapseEntryRun#record} and
     * {@link CollapseEntryRun#recordFavourable}. A squad under fire
     * commits on its first pass outside the cooldown.
     *
     * @param squad containing squad
     * @param read this evaluation's collapse test, or null when none ran or no armed enemy stood in the sector
     * @param underFire whether the enemy is already engaging the squad, NONE when the test did not pass
     * @param now current frame
     * @return the read with the gated outcome, the under fire reason and the frames the entry run and the streak of
     *     favourable reads started, or null when the read was null
     */
    static ContainmentCollapse.Read gateCollapse(Squad squad, ContainmentCollapse.Read read,
                                                 ContainmentCollapse.UnderFire underFire, int now) {
        boolean coolingDown = squad.isCollapseLocked(now);
        int passes = squad.recordCollapseTest(read == null ? null : read.getOutcome(), coolingDown, now);
        squad.recordCollapseRead(read != null && read.isFavourable(), now);
        if (read == null) {
            return null;
        }
        return read.gated(ContainmentCollapse.gate(read.getOutcome(), coolingDown, passes, underFire), underFire,
                squad.getCollapseEntryFrames());
    }

    /**
     * Whether the enemy is already engaging a containing squad: a collapse member lost hit points within
     * {@link ContainmentCollapse#UNDER_FIRE_FRAMES} while not standing in a Psionic Storm or irradiated and within
     * reach of an armed enemy inside the arc's sector, see {@link ContainmentCollapse#inSectorReach}, or an armed
     * enemy stands within {@link ContainmentCollapse#MELEE_CONTACT_DISTANCE} of one.
     *
     * @param squad containing squad
     * @param now current frame
     * @return the reason, NONE when the enemy is not engaging the squad
     */
    private ContainmentCollapse.UnderFire collapseUnderFire(Squad squad, int now) {
        List<ManagedUnit> members = collapseMembers(squad);
        List<Unit> armedInSector = new ArrayList<>();
        for (Unit enemy : sectorEnemies(squad.getContainmentArc())) {
            if (canPressTheArc(enemy)) {
                armedInSector.add(enemy);
            }
        }
        boolean hit = false;
        for (ManagedUnit member : members) {
            if (member.wasHitSince(now - ContainmentCollapse.UNDER_FIRE_FRAMES)
                    && !gameState.isTakingNonWeaponDamage(member) && inSectorReach(member, armedInSector)) {
                hit = true;
                break;
            }
        }
        return ContainmentCollapse.UnderFire.of(hit, armedEnemyInMeleeContact(members));
    }

    private boolean inSectorReach(ManagedUnit member, List<Unit> armedInSector) {
        for (Unit enemy : armedInSector) {
            int reach = gameState.getReachMemory().groundReach(enemy.getType());
            if (ContainmentCollapse.inSectorReach(enemy.getDistance(member.getUnit()), reach)) {
                return true;
            }
        }
        return false;
    }

    private boolean armedEnemyInMeleeContact(List<ManagedUnit> members) {
        for (Unit enemy : gameState.getVisibleEnemyUnits()) {
            if (!canPressTheArc(enemy)) {
                continue;
            }
            for (ManagedUnit member : members) {
                if (member.getUnit().getDistance(enemy) <= ContainmentCollapse.MELEE_CONTACT_DISTANCE) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Tests whether a containing squad collapses on the armed enemies inside its arc's sector, see
     * {@link ContainmentCollapse}. The sim runs over exactly the mobile enemies in the sector, never over a sample
     * around the squad's center. The enemy centroid must be clear of every zone that fires from where it stands, see
     * {@link ContainmentCollapse#fixedFireZones}. No test runs against an opponent the matchup gate excludes.
     *
     * @param squad containing squad
     * @param now current frame
     * @return the read, or null when no armed enemy stands inside the sector or the matchup gate excludes the
     *     opponent
     */
    private ContainmentCollapse.Read readCollapse(Squad squad, int now) {
        Arc arc = squad.getContainmentArc();
        if (arc == null || arc.isEmpty() || !ContainmentCollapse.appliesAgainst(gameState.getOpponentRace())) {
            return null;
        }
        List<Unit> inSector = sectorEnemies(arc);
        List<Position> armed = new ArrayList<>();
        for (Unit enemy : inSector) {
            if (canPressTheArc(enemy)) {
                armed.add(enemy.getPosition());
            }
        }
        CombatSimulator sim = squad.getCombatSimulator();
        DoubleSupplier sectorSim = () -> sim instanceof HorizonCombatSimulator
                ? ((HorizonCombatSimulator) sim).sectorRatio(squad, inSector, ContainmentCollapse.centroid(armed),
                gameState)
                : ContainmentCollapse.NOT_SIMULATED;
        return ContainmentCollapse.read(armed, ContainmentCollapse.fixedFireZones(gameState.getGroundThreatZones(now)),
                containmentDefensePadding(squad.getComposition().keySet()), sectorSim,
                HorizonCombatSimulator.engageThreshold(gameState.getOpponentRace()), squad.canRenewFightLock(now),
                collapseMembers(squad).size());
    }

    /**
     * Visible mobile enemies inside an arc's sector, workers excluded, see {@link ContainmentCollapse#inSector}.
     *
     * @param arc the containing squad's arc
     * @return the enemies in the sector
     */
    private List<Unit> sectorEnemies(Arc arc) {
        List<Unit> inSector = new ArrayList<>();
        for (Unit enemy : gameState.getVisibleEnemyUnits()) {
            UnitType type = enemy.getType();
            if (type.canMove() && !type.isBuilding() && !type.isWorker()
                    && ContainmentCollapse.inSector(arc.getCenter(), arc.getPositions(), enemy.getPosition())) {
                inSector.add(enemy);
            }
        }
        return inSector;
    }

    /**
     * Takes a containing squad off its arc and onto the enemies inside it: FIGHT under a fight lock. A squad under
     * fire, see {@link ContainmentCollapse.UnderFire}, skips the wrap, commits, see {@link Squad#commitCollapse}, and
     * every member fights from this frame; the commit holds it in FIGHT whatever the sim around its center reads
     * until the commit's fight lock expires. Its Lurkers commit with it while it fights under that lock, see
     * {@link LurkerHold#lurkersCommit}.
     * Otherwise the centre fights from this frame while the flanks attack-move past the enemy centroid, see
     * {@link #holdCollapseWrap}. The collapse test admits only a squad large enough to flank, see
     * {@link ContainmentCollapse#MIN_COLLAPSE_MEMBERS}, and only once it has passed the hysteresis gate, see
     * {@link #gateCollapse}. The collapse cooldown is armed on this frame, and again when the wrap ends.
     *
     * @param squad containing squad
     * @param read the collapse test that passed
     * @param now current frame
     */
    private void collapseContainingSquad(Squad squad, ContainmentCollapse.Read read, int now) {
        ContainmentCollapse.Maneuver maneuver = planCollapse(squad, read, now);
        endContainment(squad);
        squad.setStatus(SquadStatus.FIGHT);
        if (maneuver == null) {
            SquadDecisions.collapseWrapEnded(squad, ContainmentCollapse.WrapEnd.unplanned(read.getUnderFire()));
            SquadDecisions.pathTaken(squad, DecisionPath.CONTAIN_COLLAPSE_COMMIT);
        }
        SquadDecisions.pathTaken(squad, DecisionPath.CONTAIN_COLLAPSE);
        squad.startCollapseLock(now);
        wholeSquadCommits.add(squad);
        if (maneuver == null) {
            squad.commitCollapse(now);
            assignFightTargets(squad, squad.getMembers(), true);
            return;
        }
        squad.startFightLock(now);
        squad.setCollapse(maneuver);
        assignFightTargets(squad, collapseFighters(squad.getMembers(), maneuver), true);
        holdCollapseWrap(squad);
    }

    /**
     * Members of a squad that fight during a collapse's wrap: every member but the flanks still wrapping.
     *
     * @param members the squad's members
     * @param maneuver the collapse under way, or null
     * @return the members that take fight targets
     */
    static HashSet<ManagedUnit> collapseFighters(Set<ManagedUnit> members, ContainmentCollapse.Maneuver maneuver) {
        HashSet<ManagedUnit> fighters = new HashSet<>(members);
        if (maneuver != null) {
            fighters.removeIf(member -> maneuver.wrapFor(member) != null);
        }
        return fighters;
    }

    /**
     * Plans the wrap of a collapse, see {@link ContainmentCollapse#memberOrders}: the outer third of the members on
     * each side by bearing around the choke attack-move to a point past the enemy centroid on their side, pulled back
     * to the centroid when a zone that fires from where it stands covers it, see
     * {@link ContainmentCollapse#fixedFireZones}, and every other member fights. A squad under fire plans no wrap.
     *
     * @param squad containing squad, still holding its arc
     * @param read the collapse test that passed
     * @param now current frame
     * @return the maneuver, or null when no member wraps
     */
    private ContainmentCollapse.Maneuver planCollapse(Squad squad, ContainmentCollapse.Read read, int now) {
        Arc arc = squad.getContainmentArc();
        List<ManagedUnit> members = collapseMembers(squad);
        List<Position> positions = new ArrayList<>();
        boolean[] attackMoves = new boolean[members.size()];
        for (int i = 0; i < members.size(); i++) {
            positions.add(members.get(i).getPosition());
            attackMoves[i] = ContainmentCollapse.attackMovesToWrap(members.get(i).getUnitType());
        }
        int[] sides = ContainmentCollapse.flankSides(arc.getCenter(), arc.getMidpoint(), positions);
        ContainmentCollapse.MemberOrder[] memberOrders = ContainmentCollapse.memberOrders(sides, attackMoves,
                read.getUnderFire());
        Map<Integer, List<Position>> flankPositions = new HashMap<>();
        for (int i = 0; i < sides.length; i++) {
            if (memberOrders[i] == ContainmentCollapse.MemberOrder.WRAP) {
                flankPositions.computeIfAbsent(sides[i], side -> new ArrayList<>()).add(positions.get(i));
            }
        }
        if (flankPositions.isEmpty()) {
            return null;
        }
        List<StaticDefenseZone> staticZones = ContainmentCollapse.fixedFireZones(gameState.getGroundThreatZones(now));
        int padding = containmentDefensePadding(squad.getComposition().keySet());
        Map<Integer, Position> wraps = new HashMap<>();
        for (Map.Entry<Integer, List<Position>> flank : flankPositions.entrySet()) {
            Position wrap = ContainmentCollapse.wrapPoint(arc.getCenter(), read.getEnemyCentroid(), arc.getMidpoint(),
                    ContainmentCollapse.centroid(flank.getValue()));
            if (!ContainmentCollapse.clearOfStaticDefence(wrap, staticZones, padding)) {
                wrap = read.getEnemyCentroid();
            }
            wraps.put(flank.getKey(), wrap);
        }
        Map<ManagedUnit, Position> flankWraps = new HashMap<>();
        for (int i = 0; i < members.size(); i++) {
            if (memberOrders[i] == ContainmentCollapse.MemberOrder.WRAP) {
                flankWraps.put(members.get(i), wraps.get(sides[i]));
            }
        }
        return new ContainmentCollapse.Maneuver(flankWraps, new HashSet<>(members), now);
    }

    /**
     * Members that take part in a collapse: every member but an escorting Overlord.
     */
    private static List<ManagedUnit> collapseMembers(Squad squad) {
        List<ManagedUnit> members = new ArrayList<>();
        for (ManagedUnit member : squad.getMembers()) {
            if (member.getUnitType() != UnitType.Zerg_Overlord) {
                members.add(member);
            }
        }
        return members;
    }

    /**
     * Carries a collapse's wrap on for one more frame. While it runs, each flank attack-moves to its wrap point,
     * fighting what it meets on the way, while every other member fights the targets the fight tick gave it, and the
     * fight tick holds the squad in FIGHT whatever the sim around its center reads: the collapse was judged on the
     * enemies inside the arc. The wrap ends when every flank has arrived or it has run out its frames, see
     * {@link ContainmentCollapse#wrapEnd}: the flanks then fight too and, when the squad has not lost supply since
     * the collapse, the collapse commits, see {@link Squad#commitCollapse}. A squad that has left FIGHT drops the
     * wrap. Either way the collapse ends and its cooldown runs from this frame, see {@link Squad#endCollapse}.
     *
     * @param squad fight squad
     */
    private void holdCollapseWrap(Squad squad) {
        ContainmentCollapse.Maneuver maneuver = squad.getCollapse();
        if (maneuver == null) {
            return;
        }
        int now = game.getFrameCount();
        if (squad.getStatus() != SquadStatus.FIGHT) {
            squad.endCollapse(now);
            return;
        }
        ContainmentCollapse.WrapEnd wrapEnd = ContainmentCollapse.wrapEnd(now - maneuver.getStartFrame(),
                maneuver.flankDistances(squad.getMembers()));
        if (wrapEnd != null) {
            squad.endCollapse(now);
            SquadDecisions.collapseWrapEnded(squad, wrapEnd);
            SquadDecisions.pathTaken(squad, DecisionPath.CONTAIN_COLLAPSE_COMMIT);
            if (squad.canRenewFightLock(now)) {
                squad.commitCollapse(now);
            }
            assignFightTargets(squad, squad.getMembers(), false);
            return;
        }
        for (ManagedUnit member : squad.getMembers()) {
            Position wrap = maneuver.wrapFor(member);
            if (wrap == null) {
                continue;
            }
            member.setRole(UnitRole.CONTAIN);
            member.setFightTarget(null);
            member.attackMoveToContainPosition(wrap);
        }
    }

    /**
     * Members of fight squads taking part in a collapse this frame: the members of a collapse under way, and every
     * member of a squad a committed collapse still holds, see {@link Squad#isCollapseCommitHeld}.
     *
     * @param now current frame
     * @return the members exempt from evading an outranged hit
     */
    private Set<ManagedUnit> collapsingMembers(int now) {
        Set<ManagedUnit> collapsing = new HashSet<>();
        for (Squad squad : fightSquads) {
            if (!collapseHoldsMembers(squad, now)) {
                continue;
            }
            ContainmentCollapse.Maneuver maneuver = squad.getCollapse();
            collapsing.addAll(maneuver != null ? maneuver.getMembers() : squad.getMembers());
        }
        return collapsing;
    }

    /**
     * Whether a squad's members are taking part in a collapse, and so hold their ground under outranging fire: a
     * FIGHT squad with a collapse under way or a committed collapse still holding it.
     *
     * @param squad fight squad
     * @param now current frame
     * @return true while the collapse or its commit holds the squad
     */
    static boolean collapseHoldsMembers(Squad squad, int now) {
        return squad.getStatus() == SquadStatus.FIGHT
                && (squad.getCollapse() != null || squad.isCollapseCommitHeld(now));
    }

    private void endContainment(Squad squad) {
        SquadDecisions.containmentEnded(squad, squad.getContainmentAttrition().getTotalLost());
        squad.clearContainStart();
    }

    /**
     * Folds an outranged hit and whether the recomputed arc kept a point into one verdict input.
     *
     * @param outrangedHit true when a member was hit this frame with no enemy in its own range
     * @param arcLost true when no arc point on the choke stays out of every known reach
     * @return NONE without a hit, else ARC_KEPT or ARC_LOST
     */
    static OutrangedHit outrangedHitVerdict(boolean outrangedHit, boolean arcLost) {
        if (!outrangedHit) {
            return OutrangedHit.NONE;
        }
        return arcLost ? OutrangedHit.ARC_LOST : OutrangedHit.ARC_KEPT;
    }

    private boolean hasOutrangedHit(Squad squad) {
        for (ManagedUnit member : squad.getMembers()) {
            if (outrangedHits.contains(member)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The arc a squad hit from out of its reach holds next: its current arc recomputed against every zone that
     * outranges it, at learned reach, and pushed back when that loses points.
     *
     * @param squad containing squad
     * @param zones zones the arc must stay out of
     * @return the arc, or null when no point on the choke is left clear
     */
    private Arc arcUnderFire(Squad squad, List<StaticDefenseZone> zones) {
        Arc current = squad.getContainmentArc();
        if (current == null || current.isEmpty()) {
            return containmentArc(squad, zones);
        }
        return ContainmentPushback.recompute(current, zones, containmentDefensePadding(squad.getComposition().keySet()),
                gameState.getGameMap().getAccessibleWalkPositions(), game.mapWidth() * 32, game.mapHeight() * 32);
    }

    /**
     * Puts a squad hit from out of its reach on the recomputed arc, reassigning every member, not only the one that
     * was hit, and reports it on every hit that changed something, see {@link #pushbackChanged}. When the recomputed
     * arc left every point in place no member is reassigned, so a squad still on its way to the arc is not
     * re-ordered onto the same line on every hit.
     *
     * @param squad containing squad
     * @param zones zones the arc was recomputed against
     * @param arc recomputed arc
     */
    private void pushBackContainingSquad(Squad squad, List<StaticDefenseZone> zones, Arc arc) {
        Arc current = squad.getContainmentArc();
        Position from = current == null ? null : current.getMidpoint();
        UnitType enemyType = current == null ? null
                : ContainmentPushback.coveringType(current, zones,
                containmentDefensePadding(squad.getComposition().keySet()));
        squad.setContainRadius(Math.max(squad.getContainRadius(), arc.getRadius()));
        int moved = ContainmentPushback.moved(current, arc) ? assignContainmentPositions(squad, arc) : 0;
        if (pushbackChanged(from, arc.getMidpoint(), moved)) {
            SquadDecisions.containmentPushedBack(squad, from, arc.getMidpoint(), enemyType, moved);
        }
    }

    /**
     * Whether a push back changed anything worth a row: the arc's midpoint moved or a member was reassigned.
     *
     * @param from midpoint of the arc before the push back, null when the squad held none
     * @param to midpoint of the recomputed arc
     * @param membersMoved members given a new arc point
     * @return true when the push back is reported
     */
    static boolean pushbackChanged(Position from, Position to, int membersMoved) {
        return membersMoved > 0 || !Objects.equals(from, to);
    }

    /**
     * Moves a containing squad onto a freshly computed arc, or retreats it when no arc point is left clear of
     * enemy static defence.
     *
     * @param squad containing squad
     * @param members squad members
     * @param now current frame
     */
    private void repositionContainingSquad(Squad squad, HashSet<ManagedUnit> members, int now) {
        Arc arc = containmentArc(squad);
        if (arc == null) {
            containEndedOtherwise();
            retreatFromContainment(squad, members, now, DecisionPath.CONTAIN_RETREAT);
            return;
        }
        assignContainmentPositions(squad, arc);
    }

    /**
     * Reports whether the contain lock suppresses re-evaluation this frame.
     *
     * <p>The re-evaluation phase is anchored to the frame the episode started rather than to the global frame
     * count, so a squad that takes an arc is throttled for a full interval whatever frame it entered on.
     *
     * @param squad containing squad
     * @param now current frame
     * @return true when the squad keeps its arc without further checks
     */
    static boolean isContainmentThrottled(Squad squad, int now) {
        if (!squad.isContainLocked(now)) {
            return false;
        }
        int elapsed = now - squad.getContainStartFrame();
        return elapsed <= 0 || elapsed % CONTAINMENT_REEVALUATE_INTERVAL != 0;
    }

    private boolean containmentTimedOut(Squad squad, int now) {
        return squad.getContainStartFrame() > 0
                && now - squad.getContainStartFrame() >= CONTAINMENT_TIMEOUT_FRAMES;
    }

    /**
     * Reports whether an enemy has closed onto the arc the squad is holding.
     *
     * <p>Distance is measured from each member rather than from the squad center, so an enemy near one flank
     * reads the same as one in the middle of the squad. Only enemies that can move count: a sieged tank or a
     * static defence emplacement never closed onto anything, and treating one as contact would send the squad
     * into the position it is holding a line against.
     *
     * @param squad containing squad
     * @return true when an enemy that could contest the arc is within contact range of any member
     */
    private boolean enemiesOnContainmentArc(Squad squad) {
        for (Unit enemy : gameState.getVisibleEnemyUnits()) {
            if (!canPressTheArc(enemy)) {
                continue;
            }
            for (ManagedUnit member : squad.getMembers()) {
                Unit memberUnit = member.getUnit();
                if (memberUnit.getDistance(enemy) > CONTAINMENT_ENGAGE_RADIUS) {
                    continue;
                }
                if (memberUnit.canAttack(enemy)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether an enemy is the kind of unit that takes ground away from a containment arc: one that can move to
     * it and shoot the ground squad holding it. A drifting overlord or a passing worker is neither.
     *
     * @param enemy visible enemy unit
     * @return true when the unit could contest the arc
     */
    private boolean canPressTheArc(Unit enemy) {
        UnitType type = enemy.getType();
        return type.canMove() && type.groundWeapon() != WeaponType.None;
    }

    private void breakAllContainment(int now, DecisionPath path) {
        for (Squad s : fightSquads) {
            if (s.getStatus() == SquadStatus.CONTAIN) {
                endContainment(s);
                s.setStatus(SquadStatus.FIGHT);
                SquadDecisions.pathTaken(s, path);
                s.startFightLock(now);
                wholeSquadCommits.add(s);
                assignFightTargets(s, s.getMembers(), true);
            }
        }
    }

    private boolean basesUnderAttack() {
        Set<Base> morphingBases = gameState.getBaseData().morphingBases();
        Set<Base> heldBases = gameState.getBaseData().getMyBases();
        boolean cannonRushed = gameState.isCannonRushed();
        for (Map.Entry<Base, HashSet<Unit>> entry : gameState.getBaseToThreatLookup().entrySet()) {
            Base base = entry.getKey();
            boolean morphingOnly = !cannonRushed && morphingBases.contains(base) && !heldBases.contains(base);
            List<UnitType> threatTypes = entry.getValue().stream().map(Unit::getType).collect(Collectors.toList());
            if (baseUnderAttack(morphingOnly, threatTypes)) {
                return true;
            }
        }
        return false;
    }

    private boolean baseThreatensContainment() {
        return threatensContainment(gameState.getBaseToThreatLookup().values().stream()
                .flatMap(Collection::stream)
                .map(Unit::getType)
                .collect(Collectors.toList()));
    }

    /**
     * Whether the threats tracked at our bases put a base under attack for containment purposes. Entry and the
     * break both read this one predicate, so a squad is never let onto an arc by a threat that would pull it off
     * again, and a scout circling a base, visible or last known, neither blocks a contain nor breaks one.
     *
     * @param threatTypes types of every enemy unit tracked as a threat to one of our bases
     * @return true when any of them is a combat threat
     */
    static boolean threatensContainment(Collection<UnitType> threatTypes) {
        return threatTypes.stream().anyMatch(SquadManager::isCombatThreat);
    }

    /**
     * Whether a base threat of this type is one a containing squad leaves its arc for: a mobile unit that can
     * fight, on the ground or in the air. A scouting worker, an Overlord, an Observer or a building is not.
     *
     * @param type type of the enemy unit tracked as a threat to one of our bases
     * @return true when the threat ends contains and refuses new ones
     */
    static boolean isCombatThreat(UnitType type) {
        return Filter.isMobileGroundCombatUnit(type) || Filter.isAirCombatUnit(type);
    }

    /**
     * Returns true if a base's tracked threats count as an attack. Any threat does at a base we hold. At a base
     * where our hatchery is still morphing only a mobile ground combat unit does, so a scouting worker or
     * Overlord watching the morph does not break contains or hold advances.
     *
     * @param morphingOnly true if our only hatchery at the base is still morphing
     * @param threatTypes types of the enemy units tracked as threats to the base
     */
    static boolean baseUnderAttack(boolean morphingOnly, Collection<UnitType> threatTypes) {
        if (!morphingOnly) {
            return !threatTypes.isEmpty();
        }
        return threatTypes.stream().anyMatch(Filter::isMobileGroundCombatUnit);
    }

    /**
     * Offers a containing squad a runby into the base it contains.
     *
     * <p>Runs ahead of every containment exit, a base under attack included: a contain that has lost a ling or
     * two, or whose army is needed at home, still runs by when the gates pass, and trades bases instead of
     * walking home. The cheap gates are read first so the target base is only resolved for a squad that could go.
     *
     * @param squad containing squad
     * @param now current frame
     * @return true when the squad entered RUNBY
     */
    private boolean tryEnterRunby(Squad squad, int now) {
        boolean zerglingsOnly = squad.hasOnly(UnitType.Zerg_Zergling);
        boolean metabolicBoost = gameState.getTechProgression().isMetabolicBoost();
        Base base = null;
        RunbyTarget target = null;
        if (zerglingsOnly && metabolicBoost && squad.size() >= RunbyEvaluator.MIN_LINGS) {
            base = runbyBaseNear(squad.getCenter(), Collections.emptySet());
            target = base == null ? null : runbyTarget(base);
        }
        RunbyView view = target == null ? new RunbyView() : runbyView(now, target.area);
        Position anchor = target == null ? null : target.anchor;
        RunbyEvaluator.EntryVerdict verdict = RunbyEvaluator.entryVerdict(RunbyEvaluator.EntryInput.builder()
                .zerglingsOnly(zerglingsOnly)
                .metabolicBoost(metabolicBoost)
                .size(squad.size())
                .squadCenter(squad.getCenter())
                .anchor(anchor)
                .army(view.army)
                .zones(view.zones)
                .build());
        boolean underAttack = basesUnderAttack();
        RunbyTick.RunbyTickBuilder row = RunbyTick.builder()
                .frame(now)
                .squadId(squad.getId())
                .event(RunbyTick.Event.ENTRY_CHECK)
                .verdict(verdict)
                .anchor(anchor)
                .lings(squad.size())
                .basesUnderAttack(underAttack ? 1 : 0);
        if (anchor != null) {
            row.enemyTally(RunbyEvaluator.enemyTally(view.army, view.zones, anchor))
                    .ourTally(RunbyEvaluator.ourTally(squad.size()))
                    .pathTally(RunbyEvaluator.pathTally(view.army, view.zones, squad.getCenter(), anchor));
        }
        RunbyTelemetry.tick(row.build());
        if (verdict != RunbyEvaluator.EntryVerdict.ENTER) {
            return false;
        }
        enterRunby(squad, base, target, now);
        return true;
    }

    private void enterRunby(Squad squad, Base base, RunbyTarget target, int now) {
        endContainment(squad);
        squad.setStatus(SquadStatus.RUNBY);
        squad.commit(now);
        SquadDecisions.pathTaken(squad, DecisionPath.RUNBY_ENTER);
        RunbyState state = new RunbyState(now);
        state.target(base, target.area, target.anchor, target.spots, runbyBudget(squad, target.anchor), now);
        state.setLastTickFrame(now - RunbyEvaluator.RUNBY_TICK);
        squad.setRunbyState(state);
        for (ManagedUnit member : squad.getMembers()) {
            member.setRole(UnitRole.RUNBY);
            member.setContainPosition(null);
            member.setFightTarget(null);
            member.setRunbyDestination(null);
            state.getLastHitPoints().put(member.getUnitID(), member.getUnit().getHitPoints());
        }
    }

    /**
     * Runs one frame of a runby squad: a squad decision every {@link RunbyEvaluator#RUNBY_TICK} frames, then an
     * order for every ling. The contain throttle, the combat sim branch and the containment exits never see a
     * runby squad; it leaves RUNBY only by aborting, by running out of targets, or by emptying.
     *
     * @param squad runby squad
     */
    private void evaluateRunbySquad(Squad squad) {
        int now = game.getFrameCount();
        RunbyState state = squad.getRunbyState();
        if (squad.size() == 0) {
            return;
        }
        if (state == null || state.getTargetArea() == null) {
            exitRunby(squad, DecisionPath.RUNBY_EXIT_NO_TARGETS, now);
            return;
        }

        RunbyView view = runbyView(now, state.getTargetArea());
        boolean inside = state.getTargetArea().contains(squad.getCenter().toTilePosition());
        if (inside && state.getArrivedFrame() < 0) {
            state.setArrivedFrame(now);
        }

        if (RunbyEvaluator.decisionTickDue(now, state.getLastTickFrame())) {
            state.setLastTickFrame(now);
            if (runbyDecisionTick(squad, state, view, inside, now)) {
                return;
            }
        }

        assignRunbyOrders(squad, state, view, now);
    }

    /**
     * One squad decision of a runby: abort while the abort window is open, refresh the winnable fight verdict
     * in HARASS, refresh the seek point, end PENETRATE, and retarget once the base has nothing left.
     *
     * @return true when the squad left RUNBY
     */
    private boolean runbyDecisionTick(Squad squad, RunbyState state, RunbyView view, boolean inside, int now) {
        boolean windowOpen = RunbyEvaluator.abortWindowOpen(state.isAbortWindowClosed(), state.getArrivedFrame(), now);
        if (!windowOpen) {
            state.setAbortWindowClosed(true);
        }

        boolean simDue = windowOpen && inside
                || state.getPhase() == RunbyState.Phase.HARASS
                && RunbyEvaluator.winnableRefreshDue(now, state.getWinnableCheckedFrame());
        CombatSimulator.CombatResult simResult = simDue ? runbySim(squad) : null;
        HorizonCombatSimulator.DebugSnapshot snapshot = simResult == null ? null : lastSnapshot(squad);
        boolean measured = snapshot != null && snapshot.isEnemyMeasured();
        double ratio = snapshot != null ? snapshot.getOverallRatio() : 0;
        double threshold = snapshot != null ? snapshot.getEngageThreshold() : 0;

        if (state.getPhase() == RunbyState.Phase.HARASS && simResult != null) {
            state.setWinnable(simResult == CombatSimulator.CombatResult.ENGAGE && measured);
            state.setWinnableCheckedFrame(now);
        }

        if (windowOpen) {
            state.setEnemyTally(RunbyEvaluator.enemyTally(view.army, view.zones, state.getAnchor()));
            state.setOurTally(RunbyEvaluator.ourTally(squad.size()));
            if (RunbyEvaluator.shouldAbort(true, state.getEnemyTally(), state.getOurTally(), inside, simResult, measured,
                    ratio, threshold)) {
                logRunbyTick(squad, state, view, inside, windowOpen, now);
                exitRunby(squad, DecisionPath.RUNBY_ABORT, now);
                return true;
            }
        }

        List<Position> lings = memberPositions(squad);
        List<Position> visibleWorkers = visibleWorkersIn(view, state.getTargetArea());
        Set<Position> recentWorkers = gameState.getObservedUnitTracker().getRecentWorkerPositionsIn(
                state.getTargetArea()::contains, now, RunbyEvaluator.FRESH_FRAMES);
        RunbyTargeting.markVisited(state.getLikelySpots(), state.getVisitedSpots(), lings, visibleWorkers);
        RunbyTargeting.Goal goal = RunbyTargeting.seekGoal(squad.getCenter(), visibleWorkers, recentWorkers,
                state.getLikelySpots(), state.getVisitedSpots());
        state.setGoal(goal.getPoint());
        state.setGoalType(goal.getType());
        if (goal.getType() != RunbyState.GoalType.NONE) {
            state.setLastProgressFrame(now);
        }

        if (state.getPhase() == RunbyState.Phase.PENETRATE && RunbyEvaluator.penetrateEnds(now,
                state.getPhaseStartFrame(), state.getPenetrateBudgetFrames(), inside,
                safeWorkerInReach(squad, view, state.getTargetArea()))) {
            state.startHarass(now);
            SquadDecisions.runbyPhaseStarted(squad, RunbyState.Phase.PENETRATE, RunbyState.Phase.HARASS,
                    DecisionPath.RUNBY_PHASE);
        }

        boolean targetGone = !gameState.getBaseData().getEnemyBases().contains(state.getTargetBase());
        if (targetGone || RunbyEvaluator.noTargets(now, state.getLastProgressFrame())) {
            logRunbyTick(squad, state, view, inside, windowOpen, now);
            return retargetOrExitRunby(squad, state, now);
        }

        logRunbyTick(squad, state, view, inside, windowOpen, now);
        return false;
    }

    private CombatSimulator.CombatResult runbySim(Squad squad) {
        CombatSimulator.CombatResult result = squad.getCombatSimulator()
                .evaluate(squad, Collections.emptyMap(), gameState);
        SquadDecisions.simEvaluated(squad, result, false, false);
        return result;
    }

    /**
     * Moves a runby squad on to the next known enemy base it has not raided yet, or retreats it when there is
     * none.
     *
     * @return true when the squad left RUNBY
     */
    private boolean retargetOrExitRunby(Squad squad, RunbyState state, int now) {
        Base next = runbyBaseNear(squad.getCenter(), state.getVisitedBases());
        if (next == null) {
            exitRunby(squad, DecisionPath.RUNBY_EXIT_NO_TARGETS, now);
            return true;
        }
        RunbyTarget target = runbyTarget(next);
        RunbyState.Phase from = state.getPhase();
        state.target(next, target.area, target.anchor, target.spots, runbyBudget(squad, target.anchor), now);
        SquadDecisions.runbyPhaseStarted(squad, from, RunbyState.Phase.PENETRATE, DecisionPath.RUNBY_RETARGET);
        return false;
    }

    private void exitRunby(Squad squad, DecisionPath path, int now) {
        for (ManagedUnit member : squad.getMembers()) {
            member.setRunbyDestination(null);
            member.setFightTarget(null);
        }
        squad.setRunbyState(null);
        squad.setStatus(SquadStatus.RETREAT);
        SquadDecisions.pathTaken(squad, path);
        assignRetreatTargets(squad, squad.getMembers());
        squad.startRetreatLock(now);
    }

    /**
     * Gives every ling of a runby squad its order for this frame. Orders are recomputed on every frame and each
     * ling acts on its latest order whenever it is ready.
     */
    private void assignRunbyOrders(Squad squad, RunbyState state, RunbyView view, int now) {
        BaseArea area = state.getTargetArea();
        RunbyTargeting.Situation situation = RunbyTargeting.Situation.builder()
                .phase(state.getPhase())
                .winnable(state.isWinnable())
                .contacts(view.contacts)
                .threats(view.threats)
                .zones(view.zones)
                .seekPoint(state.getGoal())
                .evadeAllowed(point -> isRunbyEvadePoint(area, point))
                .workerAllowed(point -> area.contains(point.toTilePosition()))
                .now(now)
                .build();
        for (ManagedUnit member : squad.getMembers()) {
            if (member.getRole() != UnitRole.RUNBY) {
                member.setRole(UnitRole.RUNBY);
            }
            RunbyTargeting.Ling ling = new RunbyTargeting.Ling(member.getUnitID(), member.getPosition(),
                    RunbyTargeting.reach(member.getUnitType()));
            RunbyTargeting.Decision decision = RunbyTargeting.choose(ling, situation, state.memoryFor(member.getUnitID()));
            if (applyRunbyDecision(member, decision, view, state, squad.getId())) {
                state.setLastProgressFrame(now);
            }
        }
    }

    /**
     * Turns a ling's decision into its order.
     *
     * @param squadId id of the runby squad, recorded with a fight pick
     * @return true when the ling was given an enemy to hit, which counts as progress at the target base
     */
    private boolean applyRunbyDecision(ManagedUnit member, RunbyTargeting.Decision decision, RunbyView view,
                                       RunbyState state, String squadId) {
        switch (decision.getKind()) {
            case EVADE:
            case SEEK:
                member.setFightTarget(null);
                member.setRunbyDestination(decision.getPoint());
                return false;
            case WORKER:
            case BUILDING:
                Unit target = view.units.get(decision.getTargetId());
                if (target == null) {
                    seekOrHold(member, state);
                    return false;
                }
                member.setRunbyDestination(null);
                member.setFightTarget(target);
                return true;
            case FIGHT:
                if (!assignRunbyFightTarget(member, view, squadId)) {
                    seekOrHold(member, state);
                    return false;
                }
                member.setRunbyDestination(null);
                return true;
            default:
                seekOrHold(member, state);
                return false;
        }
    }

    /**
     * Sends a ling with nothing to hit to the squad's seek point, or to the anchor when there is none.
     */
    private void seekOrHold(ManagedUnit member, RunbyState state) {
        member.setFightTarget(null);
        member.setRunbyDestination(state.getGoal() != null ? state.getGoal() : state.getAnchor());
    }

    /**
     * Picks a winnable fight target with TargetScorer among the visible enemies the ling can attack, against the
     * frame's shared melee ledger, and commits it as a fight pick is (see {@link #commitPick}): a direct attack is
     * recorded in the ledger, and a ling the overflow gate holds attack-moves past a saturated pick instead.
     *
     * @param squadId id of the runby squad
     * @return true when a target was set, false when no candidate survived the attack filter
     */
    private boolean assignRunbyFightTarget(ManagedUnit member, RunbyView view, String squadId) {
        Unit unit = member.getUnit();
        TargetLedger ledger = fightTargetLedger();
        ledger.release(unit.getID());
        List<Unit> candidates = new ArrayList<>();
        for (Unit enemy : view.units.values()) {
            if (RunbyTargeting.isFightTarget(enemy.getType()) && unit.canAttack(enemy)) {
                candidates.add(enemy);
            }
        }
        if (candidates.isEmpty()) {
            return false;
        }
        TargetScorer.Selection selection = TargetScorer.selectTarget(unit,
                filterByProximity(candidates, unit::getDistance), member.fightTarget, ledger, squadId);
        if (selection == null) {
            return false;
        }
        TargetScorer.Selection issued = commitPick(ledger, member.getOverflowGate(), unit, selection,
                member.fightTarget, game.getFrameCount());
        TargetChoices.chosen(member, member.fightTarget, member.isAttackMoving(), issued, false);
        member.setFightTarget(issued.getTarget(), issued.isAttackMove());
        return true;
    }

    private boolean isRunbyEvadePoint(BaseArea area, Position point) {
        int maxX = game.mapWidth() * 32 - 1;
        int maxY = game.mapHeight() * 32 - 1;
        if (point.getX() < 0 || point.getY() < 0 || point.getX() > maxX || point.getY() > maxY) {
            return false;
        }
        return area.contains(point.toTilePosition()) && game.isWalkable(new WalkPosition(point));
    }

    private boolean safeWorkerInReach(Squad squad, RunbyView view, BaseArea area) {
        for (RunbyTargeting.Contact contact : view.contacts) {
            if (!contact.isWorker() || !area.contains(contact.getPosition().toTilePosition())
                    || !RunbyTargeting.isSafe(contact.getPosition(), view.threats)) {
                continue;
            }
            for (ManagedUnit member : squad.getMembers()) {
                if (member.getPosition().getDistance(contact.getPosition())
                        <= RunbyTargeting.reach(member.getUnitType())) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<Position> visibleWorkersIn(RunbyView view, BaseArea area) {
        List<Position> workers = new ArrayList<>();
        for (RunbyTargeting.Contact contact : view.contacts) {
            if (contact.isWorker() && area.contains(contact.getPosition().toTilePosition())) {
                workers.add(contact.getPosition());
            }
        }
        return workers;
    }

    private List<Position> memberPositions(Squad squad) {
        List<Position> positions = new ArrayList<>();
        for (ManagedUnit member : squad.getMembers()) {
            positions.add(member.getPosition());
        }
        return positions;
    }

    /**
     * Records a runby decision tick, measuring the hit points the squad lost since the previous tick. A ling
     * that died since then counts its whole remaining pool.
     */
    private void logRunbyTick(Squad squad, RunbyState state, RunbyView view, boolean inside, boolean windowOpen,
                              int now) {
        Map<Integer, Integer> previous = state.getLastHitPoints();
        Map<Integer, Integer> current = new HashMap<>();
        int hpLost = 0;
        int hpLostExposed = 0;
        int exposed = 0;
        for (ManagedUnit member : squad.getMembers()) {
            int hp = member.getUnit().getHitPoints();
            current.put(member.getUnitID(), hp);
            boolean inReach = RunbyTargeting.minMargin(member.getPosition(), view.threats) <= 0;
            if (inReach) {
                exposed++;
            }
            Integer before = previous.get(member.getUnitID());
            int lost = before == null ? 0 : Math.max(0, before - hp);
            hpLost += lost;
            if (inReach) {
                hpLostExposed += lost;
            }
        }
        for (Map.Entry<Integer, Integer> entry : previous.entrySet()) {
            if (!current.containsKey(entry.getKey())) {
                hpLost += entry.getValue();
            }
        }
        previous.clear();
        previous.putAll(current);

        RunbyTelemetry.tick(RunbyTick.builder()
                .frame(now)
                .squadId(squad.getId())
                .event(RunbyTick.Event.TICK)
                .phase(state.getPhase())
                .goalType(state.getGoalType())
                .seekPoint(state.getGoal())
                .anchor(state.getAnchor())
                .abortWindowOpen(windowOpen ? 1 : 0)
                .enemyTally(windowOpen ? state.getEnemyTally() : -1)
                .ourTally(windowOpen ? state.getOurTally() : -1)
                .inBaseArea(inside ? 1 : 0)
                .lings(squad.size())
                .workersVisible(visibleWorkersIn(view, state.getTargetArea()).size())
                .exposedLings(exposed)
                .hpLost(hpLost)
                .hpLostNonWorkerInReach(hpLostExposed)
                .winnable(winnableCell(state))
                .basesUnderAttack(basesUnderAttack() ? 1 : 0)
                .workersKilled(state.getWorkersKilled())
                .buildingsKilled(state.getBuildingsKilled())
                .build());
    }

    private static int winnableCell(RunbyState state) {
        if (state.getPhase() != RunbyState.Phase.HARASS) {
            return -1;
        }
        return state.isWinnable() ? 1 : 0;
    }

    private int runbyBudget(Squad squad, Position anchor) {
        Position center = squad.getCenter();
        int length = gameState.getBwem().getMap().getPathLength(center, anchor);
        double distance = length >= 0 ? length : center.getDistance(anchor);
        return RunbyEvaluator.penetrateBudget(distance);
    }

    /**
     * The known enemy base a runby from here would raid: the nearest by ground, skipping bases already raided.
     *
     * @param from squad center
     * @param excluded bases already raided
     * @return the base, or null when none is left
     */
    private Base runbyBaseNear(Position from, Set<Base> excluded) {
        Set<Base> candidates = new HashSet<>(gameState.getBaseData().getEnemyBases());
        candidates.removeAll(excluded);
        if (candidates.isEmpty()) {
            return null;
        }
        return closestBaseTo(from, candidates);
    }

    /**
     * Resolves the ground a runby raids at a base, and the walkable spots its workers are likely to be at:
     * the mineral line side between the depot and the mineral centroid first, then the geyser side. Each raw
     * spot is snapped to the walkable tile nearest the depot by ground, so a spot under a mineral field is never
     * sent as an order. The anchor is the first spot, or the depot when no spot resolves.
     *
     * @param base the base
     * @return the runby target, cached per base
     */
    private RunbyTarget runbyTarget(Base base) {
        return runbyTargets.computeIfAbsent(base, b -> {
            BaseArea area = BaseArea.from(b, gameState.getBwem().getMap(), RUNBY_AREA_PROXIMITY_TILES,
                    RUNBY_AREA_TILES);
            List<Position> spots = likelyWorkerSpots(b);
            Position anchor = spots.isEmpty() ? b.getCenter() : spots.get(0);
            return new RunbyTarget(area, anchor, spots);
        });
    }

    private List<Position> likelyWorkerSpots(Base base) {
        Position depot = base.getCenter();
        List<Position> raw = new ArrayList<>();
        if (!base.getMinerals().isEmpty()) {
            int sumX = 0;
            int sumY = 0;
            for (bwem.Mineral mineral : base.getMinerals()) {
                sumX += mineral.getCenter().getX();
                sumY += mineral.getCenter().getY();
            }
            int count = base.getMinerals().size();
            raw.add(midpoint(depot, new Position(sumX / count, sumY / count)));
        }
        for (bwem.Geyser geyser : base.getGeysers()) {
            raw.add(midpoint(depot, geyser.getCenter()));
        }

        Set<TilePosition> blocked = new HashSet<>();
        for (bwem.Mineral mineral : base.getMinerals()) {
            blocked.addAll(footprint(mineral.getTopLeft(), mineral.getBottomRight()));
        }
        for (bwem.Geyser geyser : base.getGeysers()) {
            blocked.addAll(footprint(geyser.getTopLeft(), geyser.getBottomRight()));
        }

        List<Position> spots = new ArrayList<>();
        for (Position spot : raw) {
            Map<TilePosition, Position> candidates = new HashMap<>();
            TilePosition center = spot.toTilePosition();
            for (int dx = -LIKELY_SPOT_SEARCH_TILES; dx <= LIKELY_SPOT_SEARCH_TILES; dx++) {
                for (int dy = -LIKELY_SPOT_SEARCH_TILES; dy <= LIKELY_SPOT_SEARCH_TILES; dy++) {
                    TilePosition tile = new TilePosition(center.getX() + dx, center.getY() + dy);
                    if (!blocked.contains(tile) && gameState.getGameMap().isValidTile(tile)) {
                        candidates.put(tile, tile.toPosition().add(new Position(16, 16)));
                    }
                }
            }
            Position snapped = gameState.getGameMap().findNearestByGround(base.getLocation(), candidates, blocked);
            if (snapped != null && !spots.contains(snapped)) {
                spots.add(snapped);
            }
        }
        return spots;
    }

    private static Position midpoint(Position a, Position b) {
        return new Position((a.getX() + b.getX()) / 2, (a.getY() + b.getY()) / 2);
    }

    private static Set<TilePosition> footprint(TilePosition topLeft, TilePosition bottomRight) {
        Set<TilePosition> tiles = new HashSet<>();
        for (int x = topLeft.getX(); x <= bottomRight.getX(); x++) {
            for (int y = topLeft.getY(); y <= bottomRight.getY(); y++) {
                tiles.add(new TilePosition(x, y));
            }
        }
        return tiles;
    }

    /**
     * Reads the enemy once for a runby frame: visible ground targets for the lings, the tracked army with the
     * freshness of each observation, and everything that can hurt a ling with its reach. An army unit threatens
     * while its observation is fresh, or for as long as it may be burrowed where it was last seen inside the
     * target base; a structure that shoots ground threatens wherever it was last seen.
     *
     * @param now current frame
     * @param targetArea ground of the base being raided or offered
     */
    private RunbyView runbyView(int now, BaseArea targetArea) {
        RunbyView view = new RunbyView();
        view.zones = gameState.getStaticDefenseZones();
        EnemyReachMemory reachMemory = gameState.getReachMemory();
        for (EnemyReachMemory.HurtMark mark : reachMemory.liveHurtMarks(now)) {
            view.threats.add(RunbyTargeting.Threat.of(mark));
        }
        for (Unit enemy : gameState.getVisibleEnemyUnits()) {
            UnitType type = enemy.getType();
            if (!enemy.isDetected() || enemy.isFlying() || !enemy.isTargetable()
                    || Filter.isLowPriorityCombatTarget(type)) {
                continue;
            }
            int maxPool = type.maxHitPoints() + type.maxShields();
            double hpFraction = maxPool <= 0 ? 1.0 : (double) (enemy.getHitPoints() + enemy.getShields()) / maxPool;
            view.contacts.add(new RunbyTargeting.Contact(enemy.getID(), type, enemy.getPosition(), hpFraction,
                    Filter.isMeanWorker(enemy)));
            view.units.put(enemy.getID(), enemy);
        }
        for (ObservedUnit observed : gameState.getObservedUnitTracker().getLivingObservedUnits()) {
            UnitType type = observed.getUnitType();
            boolean visible = observed.getUnit().isVisible();
            Position position = observed.getCurrentOrLastKnownPosition();
            if (RunbyEvaluator.isArmyType(type)) {
                boolean fresh = RunbyEvaluator.isFresh(visible, observed.getLastObservedFrame().getFrames(), now);
                Position lastKnown = observed.getLastKnownLocation();
                boolean lurking = RunbyEvaluator.isLurking(visible, type.isBurrowable(),
                        lastKnown != null && targetArea.contains(lastKnown.toTilePosition()));
                boolean cleared = RunbyEvaluator.isCleared(visible,
                        lastKnown != null && game.isVisible(lastKnown.toTilePosition()), lurking);
                view.army.add(new RunbyEvaluator.ArmyUnit(type, position, fresh, cleared));
                if ((fresh || lurking) && position != null) {
                    view.threats.add(RunbyTargeting.Threat.of(type, position, reachMemory.groundReach(type)));
                }
            } else if (Filter.isHostileBuildingToGround(type) && position != null) {
                view.threats.add(RunbyTargeting.Threat.of(type, position, reachMemory.groundReach(type)));
            }
        }
        return view;
    }

    /**
     * The enemy as a runby reads it on one frame.
     */
    private static final class RunbyView {
        private final List<RunbyTargeting.Contact> contacts = new ArrayList<>();
        private final Map<Integer, Unit> units = new HashMap<>();
        private final List<RunbyEvaluator.ArmyUnit> army = new ArrayList<>();
        private final List<RunbyTargeting.Threat> threats = new ArrayList<>();
        private List<StaticDefenseZone> zones = Collections.emptyList();
    }

    /**
     * The ground a runby raids at one base, the point it is measured at, and where its workers are likely to be.
     */
    private static final class RunbyTarget {
        private final BaseArea area;
        private final Position anchor;
        private final List<Position> spots;

        private RunbyTarget(BaseArea area, Position anchor, List<Position> spots) {
            this.area = area;
            this.anchor = anchor;
            this.spots = spots;
        }
    }

    /**
     * Builds the arc a squad would hold at the choke in front of the enemy base closest to it, one point per member,
     * at no less than the radius the episode has been pushed back to and wide enough that consecutive points sit at
     * least the widest member's width apart, clear of every zone that outranges the squad at the reach learned over the
     * game.
     *
     * @param squad squad offered the arc
     * @return the computed arc, or null when there is no enemy base or choke, or no arc point is clear
     */
    private Arc containmentArc(Squad squad) {
        return containmentArc(squad, containmentZones(squad, game.getFrameCount()));
    }

    private Arc containmentArc(Squad squad, List<StaticDefenseZone> zones) {
        HashSet<Base> enemyBases = gameState.getBaseData().getEnemyBases();
        if (enemyBases.isEmpty()) return null;
        Base containBase = closestBaseTo(squad.getCenter(), enemyBases);
        Position chokePosition = findContainmentChoke(squad.getCenter(), containBase);
        if (chokePosition == null) return null;

        Position enemyBasePosition = containBase.getCenter();
        Position faceTarget = new Position(
                2 * chokePosition.getX() - enemyBasePosition.getX(),
                2 * chokePosition.getY() - enemyBasePosition.getY()
        );

        int points = containmentPoints(squad);
        int spacing = containmentSpacing(squad.getComposition().keySet());
        int radius = containmentRadius(squad.getContainRadius(), points, spacing);
        Arc arc = new Arc(chokePosition, faceTarget, radius, containmentDegrees(radius, points, spacing), points);
        return computeContainmentArc(arc, zones, containmentDefensePadding(squad.getComposition().keySet()),
                gameState.getGameMap().getAccessibleWalkPositions(), game.mapWidth() * 32, game.mapHeight() * 32);
    }

    /**
     * Radius a squad's arc is drawn at, from the radius its episode has been pushed back to, its point count and the
     * width of its widest member, see {@link #containmentRadius(int, int, int)}.
     *
     * @param squad squad offered the arc
     * @return radius in pixels
     */
    static int containmentRadius(Squad squad) {
        return containmentRadius(squad.getContainRadius(), containmentPoints(squad),
                containmentSpacing(squad.getComposition().keySet()));
    }

    /**
     * Radius an arc of the given points is drawn at: the largest of the default radius, the radius the episode has
     * been pushed back to, and the radius that keeps consecutive points the spacing apart over the default span,
     * the last capped one {@link ContainmentPushback#RADIUS_STEP} short of {@link ContainmentPushback#MAX_RADIUS} so
     * an arc sized for its squad can still be pushed back.
     *
     * @param pushbackRadius radius the episode has been pushed back to, 0 when it has not
     * @param points points on the arc
     * @param spacing pixels wanted between consecutive points
     * @return radius in pixels
     */
    static int containmentRadius(int pushbackRadius, int points, int spacing) {
        int spaced = Math.min(MAX_SPACED_RADIUS, Arc.radiusForSpacing(points, ARC_DEGREES, spacing));
        return Math.max(Math.max(ARC_RADIUS, pushbackRadius), spaced);
    }

    /**
     * Span of an arc of the given points at the radius: the default span, widened up to {@link #MAX_ARC_DEGREES}
     * when the radius alone cannot keep consecutive points the spacing apart.
     *
     * @param radius radius of the arc in pixels
     * @param points points on the arc
     * @param spacing pixels wanted between consecutive points
     * @return span in degrees
     */
    static int containmentDegrees(int radius, int points, int spacing) {
        return Math.max(ARC_DEGREES, Math.min(MAX_ARC_DEGREES, Arc.degreesForSpacing(points, radius, spacing)));
    }

    /**
     * Points on the arc offered to a squad: one per member, and never fewer than four.
     *
     * @param squad squad offered the arc
     * @return point count
     */
    static int containmentPoints(Squad squad) {
        return Math.max(squad.size(), MIN_ARC_POINTS);
    }

    /**
     * Pixels kept between consecutive arc points: the width of the widest unit type in the squad.
     *
     * @param memberTypes unit types in the squad
     * @return spacing in pixels, 0 when the squad is empty
     */
    static int containmentSpacing(Collection<UnitType> memberTypes) {
        int widest = 0;
        for (UnitType type : memberTypes) {
            widest = Math.max(widest, type.dimensionLeft() + type.dimensionRight());
        }
        return widest;
    }

    /**
     * Places an arc's points clear of the zones.
     *
     * @param arc uncomputed arc
     * @param zones zones the points must stay out of
     * @param padding pixels added to every zone's reach
     * @param accessible walkable positions, or empty to treat the whole map as walkable
     * @param mapPixelWidth map width in pixels
     * @param mapPixelHeight map height in pixels
     * @return the computed arc, or null when no point is clear
     */
    static Arc computeContainmentArc(Arc arc, Collection<StaticDefenseZone> zones, int padding,
                                     Set<WalkPosition> accessible, int mapPixelWidth, int mapPixelHeight) {
        arc.compute(accessible, zones, padding, mapPixelWidth, mapPixelHeight);
        return arc.isEmpty() ? null : arc;
    }

    /**
     * The ground threat zones a containing squad's arc stays out of, at the reach learned over the game.
     *
     * @param squad containing squad
     * @param now current frame
     * @return every static defence zone, and every other zone that may move the arc, see
     *     {@link ContainmentPushback#arcZones}, and outranges the squad's shortest ranged member
     */
    private List<StaticDefenseZone> containmentZones(Squad squad, int now) {
        return ContainmentPushback.outrangingZones(ContainmentPushback.arcZones(gameState.getGroundThreatZones(now)),
                shortestGroundRange(squad.getComposition().keySet()));
    }

    /**
     * Shortest ground weapon range among unit types that have one.
     *
     * @param types unit types in a squad
     * @return range in pixels, or 0 when no type has a ground weapon
     */
    static int shortestGroundRange(Collection<UnitType> types) {
        int shortest = Integer.MAX_VALUE;
        for (UnitType type : types) {
            if (type.groundWeapon() == WeaponType.None) {
                continue;
            }
            shortest = Math.min(shortest, EnemyReachMemory.baseGroundRange(type));
        }
        return shortest == Integer.MAX_VALUE ? 0 : shortest;
    }

    /**
     * Pixels an arc point must keep beyond a static defence structure's reach: the largest extent of any unit
     * that may stand on the point, plus a margin.
     *
     * @param memberTypes unit types in the squad
     * @return padding in pixels
     */
    static int containmentDefensePadding(Collection<UnitType> memberTypes) {
        int largest = 0;
        for (UnitType type : memberTypes) {
            largest = Math.max(largest, extent(type));
        }
        return largest + CONTAIN_DEFENSE_MARGIN;
    }

    private static int extent(UnitType type) {
        return Math.max(Math.max(type.dimensionLeft(), type.dimensionRight()),
                Math.max(type.dimensionUp(), type.dimensionDown()));
    }

    /**
     * Puts the squad on an arc and gives every member a point of it.
     *
     * @param squad containing squad
     * @param arc computed arc
     * @return members whose contain position changed
     */
    private int assignContainmentPositions(Squad squad, Arc arc) {
        activeContainmentArcs.add(arc);
        squad.setContainmentArc(arc);

        int moved = 0;
        List<ManagedUnit> units = new ArrayList<>(squad.getMembers());
        Map<ManagedUnit, Position> assignments = arc.assignUnits(units);
        for (ManagedUnit mu : units) {
            Position assigned = assignments.get(mu);
            if (assigned == null) {
                assigned = arc.closestPosition(mu.getPosition());
            }
            if (assigned == null) {
                continue;
            }
            if (!assigned.equals(mu.getContainPosition())) {
                moved++;
            }
            mu.setRole(UnitRole.CONTAIN);
            mu.setContainPosition(assigned);
        }
        return moved;
    }

    /**
     * Puts a unit joining a containing squad on the arc the squad already holds, leaving the squad's status alone.
     * The squad's next re-evaluation redistributes the whole arc.
     *
     * @param squad containing squad the unit joined
     * @param managedUnit unit that joined
     */
    private void joinContainment(Squad squad, ManagedUnit managedUnit) {
        Arc arc = squad.getContainmentArc();
        Position assigned = arc == null ? null : arc.closestPosition(managedUnit.getPosition());
        if (assigned == null) {
            managedUnit.setRallyPoint(squad.getCenter());
            managedUnit.setRole(UnitRole.RALLY);
            return;
        }
        managedUnit.setRole(UnitRole.CONTAIN);
        managedUnit.setContainPosition(assigned);
    }

    /**
     * Sends a ground squad to the arc of the contain closest to it, each member to the arc point nearest it.
     *
     * <p>The squad takes RALLY rather than FIGHT. The merge folds it into the containing squad once the two are
     * within {@link #SQUAD_MERGE_DISTANCE}, and RALLY is below CONTAIN in merge precedence, so the merged squad
     * keeps the arc; a FIGHT reinforcement would end the contain it merged into. Commitment is kept, so a squad
     * still near the rally point stays on its way to the arc, see {@link #keepsJoiningContain}.
     *
     * @param squad squad to send
     * @return true when the squad was sent, false when it is not a ground squad or no contain holds an arc
     */
    private boolean joinActiveContain(Squad squad) {
        Arc arc = containArcToJoin(squad);
        if (arc == null) {
            return false;
        }
        SquadDecisions.rallied(squad, RallyReason.JOIN_CONTAIN);
        SquadDecisions.pathTaken(squad, DecisionPath.RALLY);
        squad.setStatus(SquadStatus.RALLY);
        for (ManagedUnit managedUnit : squad.getMembers()) {
            managedUnit.setRallyPoint(arc.closestPosition(managedUnit.getPosition()));
            managedUnit.setRole(UnitRole.RALLY);
        }
        return true;
    }

    /**
     * The arc a ground squad reinforces: the arc of the other containing ground squad closest to it.
     *
     * @param squad squad looking for a contain to join
     * @return the arc, or null when the squad is not a ground squad or no other ground squad holds an arc
     */
    private Arc containArcToJoin(Squad squad) {
        if (!squad.isGroundSquad() || squad.getStatus() == SquadStatus.CONTAIN) {
            return null;
        }
        List<Arc> arcs = new ArrayList<>();
        for (Squad other : fightSquads) {
            if (other == squad || !other.isGroundSquad() || other.getStatus() != SquadStatus.CONTAIN) {
                continue;
            }
            arcs.add(other.getContainmentArc());
        }
        return arcToJoin(squad.getCenter(), arcs);
    }

    /**
     * Picks the arc a reinforcement joins: the one whose held line sits closest to it. Missing and empty arcs are
     * skipped.
     *
     * @param from reinforcement's center
     * @param arcs arcs held by containing squads
     * @return the closest arc, or null when there is none to join
     */
    static Arc arcToJoin(Position from, Collection<Arc> arcs) {
        if (from == null) {
            return null;
        }
        Arc closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (Arc arc : arcs) {
            if (arc == null || arc.isEmpty()) {
                continue;
            }
            double distance = from.getDistance(arc.getMidpoint());
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = arc;
            }
        }
        return closest;
    }

    /**
     * Whether a squad the move out gate would send back to the rally point goes on to a contain instead.
     *
     * <p>{@link #rallySquad} clears commitment and {@link #joinActiveContain} keeps it, so a committed squad in
     * RALLY is one on its way to an arc. Below the move out threshold and still within the commitment release
     * distance of the rally point, {@link #chooseSquadAction} returns RALLY for it, and sending it home there would
     * turn back every reinforcement that left the rally point under the threshold.
     *
     * @param status status the squad held entering the tick
     * @param committed true when the squad has been cleared to act and has not been recalled since
     * @return true when the squad keeps heading for a contain
     */
    static boolean keepsJoiningContain(SquadStatus status, boolean committed) {
        return status == SquadStatus.RALLY && committed;
    }

    private Base closestBaseTo(Position pos, Set<Base> bases) {
        Map<TilePosition, Position> baseTiles = new HashMap<>();
        for (Base base : bases) {
            baseTiles.put(base.getLocation(), base.getCenter());
        }
        Position nearest = gameState.getGameMap().findNearestByGround(
                pos.toTilePosition(), baseTiles, Collections.emptySet());
        if (nearest != null) {
            for (Base base : bases) {
                if (base.getCenter().equals(nearest)) return base;
            }
        }
        Base closest = null;
        double minDist = Double.MAX_VALUE;
        for (Base base : bases) {
            double dist = pos.getDistance(base.getCenter());
            if (dist < minDist) {
                minDist = dist;
                closest = base;
            }
        }
        return closest;
    }

    private Position findContainmentChoke(Position squadPos, Base base) {
        CPPath path = gameState.getBwem().getMap().getPath(squadPos, base.getCenter());
        if (!path.isEmpty()) {
            return path.get(path.size() - 1).getCenter().toPosition();
        }
        return null;
    }

    /**
     * Plans each member's retreat target along the ground path home to the rally point, around the ground threats
     * near the squad, and records the route and the frame of the plan on the squad for the cornered fight rule, the
     * replan throttle and the telemetry row. Falls back to backing straight away from the enemy when the rally point
     * has no walkable tile near it.
     */
    private Map<ManagedUnit, Position> planGroundRetreat(Squad squad, Position rallyPoint, int now) {
        GroundRetreatRouter.Plan<ManagedUnit> plan = null;
        if (rallyPoint != null && gameState.getGameMap() != null && gameState.getGameMap().getWidth() > 0) {
            if (groundRetreatRouter == null) {
                groundRetreatRouter = new GroundRetreatRouter(gameState.getGameMap());
            }
            Map<ManagedUnit, Position> members = new HashMap<>();
            for (ManagedUnit member : squad.getMembers()) {
                members.put(member, member.getUnit().getPosition());
            }
            List<Position> threats = enemyUnitsNearSquad(squad).stream()
                    .filter(enemy -> closesRetreatPath(enemy.getType(), enemy.isAttacking()))
                    .map(Unit::getPosition)
                    .collect(Collectors.toList());
            plan = groundRetreatRouter.plan(members, rallyPoint, threats);
        }
        RetreatRoute route = plan != null ? plan.getRoute() : RetreatRoute.AWAY;
        squad.setRetreatRoute(route);
        squad.setRetreatPlanFrame(now);
        SquadDecisions.retreatRouted(squad, route);
        return plan != null ? plan.getTargets() : computeGroundRetreatTargets(squad);
    }

    /**
     * Whether a ground retreat plan made on frame planFrame still stands on frame now. A plan is made at most once per
     * {@link #RETREAT_REPLAN_FRAMES}; between plans the members keep the targets they were given.
     *
     * @param route route of the squad's last ground retreat plan, NONE when the squad has none to keep
     * @param planFrame frame that plan was made
     * @param now current frame
     * @return true when the last plan is kept
     */
    static boolean retreatPlanFresh(RetreatRoute route, int planFrame, int now) {
        return route != RetreatRoute.NONE && now - planFrame < RETREAT_REPLAN_FRAMES;
    }

    /**
     * Whether a visible enemy closes the tiles around it to a retreat path home: a type that threatens ground units,
     * except a worker that is not attacking.
     *
     * @param type the enemy's type
     * @param attacking whether the enemy is attacking
     * @return true when the retreat routes around it
     */
    static boolean closesRetreatPath(UnitType type, boolean attacking) {
        return Filter.isGroundThreat(type) && (!Filter.isWorkerType(type) || attacking);
    }

    /**
     * Whether a squad held in retreat reads a cornered ENGAGE: its last retreat plan found no path home clear of the
     * enemy, and the sim rates the fight at or above its engage threshold. The squad turns to fight once this read has
     * held over a fight hysteresis window, see {@link Squad#corneredEngagePersisted}, and then stays in FIGHT for one
     * more, see {@link Squad#holdCorneredFight}. A retreat from a HOME_CONTESTED plan is not cornered, see
     * {@link #contestedHomeDefends}.
     *
     * @param status status the squad holds
     * @param route route of the squad's last ground retreat plan
     * @param result the sim's verdict this frame
     * @return true when this frame's read counts toward the squad fighting instead of retreating
     */
    static boolean corneredSquadFights(SquadStatus status, RetreatRoute route, CombatSimulator.CombatResult result) {
        return status == SquadStatus.RETREAT && route == RetreatRoute.CORNERED
                && result == CombatSimulator.CombatResult.ENGAGE;
    }

    /**
     * The path a squad held in retreat takes if it turns to fight on this frame's read: CORNERED_ENGAGE for a cornered
     * ENGAGE, see {@link #corneredSquadFights}, HOME_CONTESTED_DEFEND for a contested home to defend, see
     * {@link #contestedHomeDefends}, and null when the read does not count toward a turn.
     *
     * @param status status the squad holds
     * @param route route of the squad's last ground retreat plan
     * @param result the sim's verdict this frame
     * @param enemyMeasured whether the sim measured a real enemy this frame
     * @param ratio the sim's overall strength ratio this frame
     * @param engageThreshold the engage threshold the sim judged this frame's ratio against
     * @return the turn's decision path, or null
     */
    static DecisionPath retreatTurnPath(SquadStatus status, RetreatRoute route, CombatSimulator.CombatResult result,
                                        boolean enemyMeasured, double ratio, double engageThreshold) {
        if (corneredSquadFights(status, route, result)) {
            return DecisionPath.CORNERED_ENGAGE;
        }
        if (contestedHomeDefends(status, route, result, enemyMeasured, ratio, engageThreshold)) {
            return DecisionPath.HOME_CONTESTED_DEFEND;
        }
        return null;
    }

    /**
     * Whether a squad held in retreat reads a contested home it should defend instead of staging at the edge of the
     * threats on it: its last retreat plan was HOME_CONTESTED, and the sim reads ENGAGE, or a RETREAT measured against
     * a real enemy at or above {@link #CONTESTED_HOME_DEFEND_FRACTION} of its engage threshold. Like a cornered
     * ENGAGE, the squad turns to fight once this read has held over a fight hysteresis window, see
     * {@link Squad#corneredEngagePersisted}, and then stays in FIGHT for one more, see {@link Squad#holdCorneredFight}.
     *
     * @param status status the squad holds
     * @param route route of the squad's last ground retreat plan
     * @param result the sim's verdict this frame
     * @param enemyMeasured whether the sim measured a real enemy this frame
     * @param ratio the sim's overall strength ratio this frame
     * @param engageThreshold the engage threshold the sim judged this frame's ratio against
     * @return true when this frame's read counts toward the squad defending its home instead of staging
     */
    static boolean contestedHomeDefends(SquadStatus status, RetreatRoute route, CombatSimulator.CombatResult result,
                                        boolean enemyMeasured, double ratio, double engageThreshold) {
        if (status != SquadStatus.RETREAT || route != RetreatRoute.HOME_CONTESTED) {
            return false;
        }
        if (result == CombatSimulator.CombatResult.ENGAGE) {
            return true;
        }
        return result == CombatSimulator.CombatResult.RETREAT && enemyMeasured && engageThreshold > 0
                && ratio >= CONTESTED_HOME_DEFEND_FRACTION * engageThreshold;
    }

    /**
     * Computes a shared retreat anchor for zerglings with perpendicular jitter per unit, for a ground retreat whose
     * rally point has no walkable tile near it.
     * Anchor: vector from squad center away from closest enemy cluster.
     * Jitter: perpendicular offsets to reduce clumping.
     */
    private HashMap<ManagedUnit, Position> computeGroundRetreatTargets(Squad squad) {
        HashMap<ManagedUnit, Position> result = new HashMap<>();
        Position center = squad.getCenter();
        List<Unit> enemiesNear = enemyUnitsNearSquad(squad);
        if (enemiesNear.isEmpty()) {
            for (ManagedUnit mu : squad.getMembers()) {
                result.put(mu, center);
            }
            return result;
        }

        double ex = 0;
        double ey = 0;
        int cnt = 0;
        for (Unit e : enemiesNear) {
            Position p = e.getPosition();
            ex += p.getX();
            ey += p.getY();
            cnt++;
        }
        ex /= cnt; ey /= cnt;

        Vec2 away = new Vec2(center.getX() - ex, center.getY() - ey);
        double len = Math.max(1.0, away.length());
        Vec2 awayUnit = away.scale(1.0 / len);
        Vec2 perp = awayUnit.perpendicular();
        Position anchor = awayUnit.scale(RETREAT_VECTOR_MAGNITUDE).clampToMap(game, center);

        int maxX = game.mapWidth() * 32 - 1;
        int maxY = game.mapHeight() * 32 - 1;

        int i = 0;
        for (ManagedUnit mu : squad.getMembers()) {
            int side = (i % 2 == 0) ? 1 : -1;
            double mag = 16 + (i / 2) * 8;
            int rx = (int) Math.round(anchor.getX() + side * perp.x * mag);
            int ry = (int) Math.round(anchor.getY() + side * perp.y * mag);
            rx = Math.max(0, Math.min(rx, maxX));
            ry = Math.max(0, Math.min(ry, maxY));

            if (!game.isWalkable(new WalkPosition(rx, ry))) {
                int bestX = rx;
                int bestY = ry;
                boolean found = false;
                for (int t = 256; t >= 0; t -= 16) {
                    int cx = (int) Math.round(rx + awayUnit.x * t);
                    int cy = (int) Math.round(ry + awayUnit.y * t);
                    cx = Math.max(0, Math.min(cx, maxX));
                    cy = Math.max(0, Math.min(cy, maxY));
                    if (game.isWalkable(new WalkPosition(cx, cy))) {
                        bestX = cx;
                        bestY = cy;
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    for (int t = 16; t <= 128; t += 16) {
                        int cx = (int) Math.round(rx - awayUnit.x * t);
                        int cy = (int) Math.round(ry - awayUnit.y * t);
                        cx = Math.max(0, Math.min(cx, maxX));
                        cy = Math.max(0, Math.min(cy, maxY));
                        if (game.isWalkable(new WalkPosition(cx, cy))) {
                            bestX = cx;
                            bestY = cy;
                            break;
                        }
                    }
                }
                rx = bestX;
                ry = bestY;
            }

            result.put(mu, new Position(rx, ry));
            i++;
        }

        return result;
    }

    private Position calculateStormRetreatPosition(Position unitPos, Set<Position> stormPositions) {
        double totalDx = 0;
        double totalDy = 0;
        double totalWeight = 0;

        for (Position stormPos : stormPositions) {
            double distance = unitPos.getDistance(stormPos);
            if (distance > 0) {
                double weight = 1.0 / (distance * distance / 10000);
                totalDx += (unitPos.getX() - stormPos.getX()) * weight;
                totalDy += (unitPos.getY() - stormPos.getY()) * weight;
                totalWeight += weight;
            }
        }

        if (totalWeight == 0) {
            return unitPos;
        }

        Vec2 weighted = new Vec2(totalDx / totalWeight, totalDy / totalWeight);
        Vec2 retreat = weighted.length() > 0
                ? weighted.normalizeToLength(RETREAT_VECTOR_MAGNITUDE)
                : new Vec2(RETREAT_VECTOR_MAGNITUDE, 0);
        return retreat.clampToMap(game, unitPos);
    }

    /**
     * Simulates the given defenders against the threats near a base.
     *
     * <p>Enemies farther than {@link #DEFENSE_SIM_RANGE} from the base center are left out. A defender that
     * far away is placed at the base center, so a drone still mining at another base is judged in the fight
     * it would walk into rather than from out of range of it.
     */
    private DefenseSim simulateDefense(Base base, List<ManagedUnit> defenders, List<Unit> enemyUnits,
                                       double threshold) {
        Position center = base.getCenter();
        Simulator simulator = new Simulator.Builder().build();

        for (ManagedUnit managedUnit: defenders) {
            Unit unit = managedUnit.getUnit();
            if (unit.getType() == UnitType.Unknown) {
                continue;
            }
            Agent agent = agentFactory.of(unit);
            if (unit.getPosition().getDistance(center) > DEFENSE_SIM_RANGE) {
                agent.setX(center.getX()).setY(center.getY());
            }
            simulator.addAgentA(agent);
        }

        for (Unit enemyUnit: enemyUnits) {
            if (enemyUnit.getType() == UnitType.Unknown) {
                continue;
            }
            if ((int) enemyUnit.getPosition().getDistance(center) > DEFENSE_SIM_RANGE) {
                continue;
            }
            try {
                simulator.addAgentB(agentFactory.of(enemyUnit));
            } catch (ArithmeticException e) {
                return DefenseSim.unsimulated(simulator.getAgentsA().size(), threshold);
            }
        }

        int defenderAgents = simulator.getAgentsA().size();
        int enemyAgents = simulator.getAgentsB().size();
        simulator.simulate(COMBAT_SIM_DURATION_FRAMES);
        return new DefenseSim(true, defenderAgents, enemyAgents, simulator.getAgentsA().size(),
                simulator.getAgentsB().size(), threshold);
    }

    /**
     * Adds a managed unit to the squad manager. Overlords are sorted into the overlord squad; all other units are
     * added to fight squads.
     * @param managedUnit
     */
    public void addManagedUnit(ManagedUnit managedUnit) {
        if (managedUnit.getUnitType() == UnitType.Zerg_Overlord) {
            addManagedOverlord(managedUnit);
            return;
        }

        addManagedFighter(managedUnit);
    }

    /**
     * Clears references to a destroyed unit and books it against any containing squad: a member's death as a
     * loss, an enemy's death near a member as a kill. Must run before the unit is removed from its squad.
     *
     * @param unit destroyed unit
     */
    public void onUnitDestroy(Unit unit) {
        int now = game.getFrameCount();
        boolean enemy = unit.getPlayer() == game.enemy();
        Map<Squad, Double> killers = new HashMap<>();
        for (Squad squad: fightSquads) {
            if (squad.getTarget() == unit) {
                squad.setTarget(null);
            }
            if (squad.getStatus() != SquadStatus.CONTAIN) {
                continue;
            }
            if (enemy) {
                double distance = closestMemberInKillReach(squad, unit);
                if (distance >= 0) {
                    killers.put(squad, distance);
                }
                continue;
            }
            for (ManagedUnit member : squad.getMembers()) {
                if (member.getUnit() == unit) {
                    squad.getContainmentAttrition().recordLoss(now, member.getUnitType().supplyRequired());
                    break;
                }
            }
        }
        creditContainKill(killers, now, unit.getType().supplyRequired());
        irradiatedUnits.removeIf(mu -> mu.getUnit() == unit);
        scoutChase.releaseScout(unit.getID());
        scoutChase.release(unit.getID());
        creditRunbyKill(unit);
        airHarass.onUnitDestroy(unit, fightSquads);
    }

    /**
     * Credits a dead enemy to the one containing squad whose member stood closest to it, among those with a member
     * that could have landed the killing blow. Squads sharing an arc would otherwise each book the same kill, and
     * the one being ground down would never read as bleeding.
     *
     * @param killers each containing squad with a member in kill reach, mapped to that member's distance
     * @param frame frame of the kill
     * @param supply supply of the dead enemy
     * @return the credited squad, or null when no squad was in reach
     */
    static Squad creditContainKill(Map<Squad, Double> killers, int frame, int supply) {
        Squad credited = killers.entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
        if (credited != null) {
            credited.getContainmentAttrition().recordKill(frame, supply);
        }
        return credited;
    }

    /**
     * Credits a dead enemy worker or building to the runby squad that killed it: a member was targeting it, or
     * stood within {@link #RUNBY_KILL_CREDIT_RADIUS} of it.
     *
     * @param unit the destroyed unit
     */
    private void creditRunbyKill(Unit unit) {
        UnitType type = unit.getType();
        boolean worker = Filter.isWorkerType(type);
        if (!worker && !type.isBuilding() || !game.self().isEnemy(unit.getPlayer())) {
            return;
        }
        Position position = unit.getPosition();
        for (Squad squad : fightSquads) {
            RunbyState state = squad.getRunbyState();
            if (squad.getStatus() != SquadStatus.RUNBY || state == null) {
                continue;
            }
            for (ManagedUnit member : squad.getMembers()) {
                if (member.fightTarget == unit || member.getPosition().getDistance(position) <= RUNBY_KILL_CREDIT_RADIUS) {
                    state.creditKill(worker);
                    return;
                }
            }
        }
    }

    private double closestMemberInKillReach(Squad squad, Unit enemy) {
        Position position = enemy.getPosition();
        double closest = -1;
        for (ManagedUnit member : squad.getMembers()) {
            UnitType memberType = member.getUnitType();
            int reach = EnemyReachMemory.groundRange(memberType, weapon -> game.self().weaponMaxRange(weapon));
            double distance = member.getPosition().getDistance(position);
            if (distance > containKillRadius(memberType, reach, enemy.getType())) {
                continue;
            }
            if (closest < 0 || distance < closest) {
                closest = distance;
            }
        }
        return closest;
    }

    /**
     * Centre to centre distance within which a member could have landed the killing blow on an enemy: the member's
     * ground reach from its edge, both units' largest extents, and the contain margin. An enemy that dies farther
     * from every member was killed by something else and is not credited to the contain.
     *
     * @param memberType member unit type
     * @param memberReach member ground reach in pixels, upgrades included
     * @param enemyType destroyed enemy's unit type
     * @return radius in pixels
     */
    static int containKillRadius(UnitType memberType, int memberReach, UnitType enemyType) {
        return memberReach + extent(memberType) + extent(enemyType) + CONTAIN_DEFENSE_MARGIN;
    }

    private void addManagedFighter(ManagedUnit managedUnit) {
        if (managedUnit.isIrradiated()) {
            managedUnit.setRole(UnitRole.FIGHT);
            managedUnit.setRallyPoint(gameState.getBaseData().mainBasePosition().toPosition());
            irradiatedUnits.add(managedUnit);
            assignIrradiatedTarget(managedUnit);
            return;
        }
        UnitType type = managedUnit.getUnitType();
        Squad squad;
        if (AIR_SQUAD_TYPES.contains(type)) {
            squad = findCloseAirSquad(managedUnit);
        } else {
            squad = findCloseGroundSquad(managedUnit);
        }
        if (squad == null) {
            squad = newFightSquad(type);
        }

        squad.addUnit(managedUnit);
        boolean stage = shouldStageSquad(squad);
        boolean holdAway = !stage && squad.getStatus() != SquadStatus.CONTAIN && reinforcementHeldAway(squad);
        switch (reinforcementPath(squad.getSwarmLock() != null, squad.getStatus(), stage, holdAway)) {
            case JOIN_SWARM:
                managedUnit.setRole(UnitRole.FIGHT);
                return;
            case STAGE:
                rallySquad(squad, RallyReason.STAGING);
                return;
            case JOIN_CONTAINMENT:
                joinContainment(squad, managedUnit);
                return;
            case HOLD_AWAY:
                clearCombatSimSnapshot(squad);
                rallySquad(squad, RallyReason.AIR_BELOW_MOVE_OUT_AWAY);
                return;
            case JOIN_HARASS:
                joinHarass(squad, Collections.singletonList(managedUnit));
                return;
            default:
                break;
        }

        RallyRelease release = reinforcementRelease(squad.getStatus());
        if (release != RallyRelease.NONE) {
            SquadDecisions.rallyReleased(squad, release);
        }

        simulateFightSquad(squad);
    }

    /**
     * Branch {@link #addManagedFighter} takes for the squad a reinforcement joined.
     */
    enum ReinforcementPath {
        STAGE,
        JOIN_CONTAINMENT,
        HOLD_AWAY,
        SIMULATE,
        JOIN_HARASS,
        JOIN_SWARM
    }

    /**
     * Picks what a reinforcement does to the squad it joined. A unit joining a squad that holds a swarm lock only
     * takes the fight role: the squad's orders come from {@link #fightUnderSwarm} on its next evaluation, so the join
     * never stages, contains, simulates or retreats a squad the lock holds under its swarm. Any other squad goes by
     * {@link #reinforcementPath(SquadStatus, boolean, boolean)}.
     *
     * @param swarmLocked true when the squad holds a swarm lock
     * @param status status the squad held as the reinforcement joined
     * @param stage true when the squad is rallying with no enemy inside its detection radius
     * @param holdAway true when the squad is an air squad held at the rally point away from home
     * @return branch to take
     */
    static ReinforcementPath reinforcementPath(boolean swarmLocked, SquadStatus status, boolean stage,
                                               boolean holdAway) {
        return swarmLocked ? ReinforcementPath.JOIN_SWARM : reinforcementPath(status, stage, holdAway);
    }

    /**
     * Picks what a reinforcement does to the squad it joined.
     *
     * <p>Apart from a merge with a squad of higher precedence, a containing squad changes status only through
     * {@link #evaluateContainingSquad}, so a unit joining one takes a point on the arc and never runs the squad
     * through {@link #simulateFightSquad}. A zergling scout can be pulled from a containing squad and handed
     * straight back every 24 frames; simulating on each return would let a blind ADVANCE flip the squad to FIGHT
     * until the next frame re-entered the arc.
     *
     * <p>A squad with close threats is otherwise simulated at once, except a squad
     * {@link #holdsAwayFromHome(boolean, boolean, boolean, int, int, boolean)} holds: it returns to the rally
     * point, the same branch {@link #evaluateSquadRole} takes for it, so a unit hatched under threat away from our
     * bases does not fight below its move out threshold.
     *
     * <p>A Mutalisk joining a harassing squad takes the harass's orders through {@link #joinHarass}; simulating the
     * squad would overwrite its status.
     *
     * @param status status the squad held as the reinforcement joined
     * @param stage true when the squad is rallying with no enemy inside its detection radius
     * @param holdAway true when the squad is an air squad held at the rally point away from home
     * @return branch to take
     */
    static ReinforcementPath reinforcementPath(SquadStatus status, boolean stage, boolean holdAway) {
        if (status == SquadStatus.CONTAIN) {
            return ReinforcementPath.JOIN_CONTAINMENT;
        }
        if (status == SquadStatus.HARASS) {
            return ReinforcementPath.JOIN_HARASS;
        }
        if (stage) {
            return ReinforcementPath.STAGE;
        }
        if (holdAway) {
            return ReinforcementPath.HOLD_AWAY;
        }
        return ReinforcementPath.SIMULATE;
    }

    private boolean reinforcementHeldAway(Squad squad) {
        if (!squad.isAirSquad()) {
            return false;
        }
        int strength = squadStrength(squad);
        int moveOutThreshold = calculateMoveOutThreshold(squad);
        if (!mayHoldAwayFromHome(true, strength, moveOutThreshold, squad.isCommitted())) {
            return false;
        }
        boolean closeThreats = !enemyUnitsNearSquad(squad).isEmpty();
        return holdsAwayFromHome(squad, closeThreats, strength, moveOutThreshold);
    }

    /**
     * Names the release for a squad a reinforcement joins outside the staging path.
     *
     * <p>A unit completing runs before the frame's squad loop, so this is the one place a squad can
     * leave RALLY without {@link #evaluateSquadRole} seeing it: {@link #simulateFightSquad} is
     * called here directly and can set FIGHT, RETREAT or CONTAIN before the release hook there ever
     * reads the status. The episode's closing row would carry NONE, which by contract says the row
     * closes nothing.
     *
     * <p>The release is always close threats. Reaching this call at all means
     * {@link #shouldStageSquad} was false, and for a squad already rallying the only term that can
     * make it false is an enemy inside the detection radius.
     *
     * @param status status the squad held as the reinforcement joined
     * @return CLOSE_THREATS for a rallying squad, NONE for any other, which closes no episode
     */
    static RallyRelease reinforcementRelease(SquadStatus status) {
        return status == SquadStatus.RALLY ? RallyRelease.CLOSE_THREATS : RallyRelease.NONE;
    }

    private Squad findCloseGroundSquad(ManagedUnit managedUnit) {
        for (Squad squad : fightSquads) {
            if (!squad.isGroundSquad()) continue;
            if (!mayJoin(squad.getStatus())) continue;
            if (squad.distance(managedUnit) < SQUAD_MERGE_DISTANCE) {
                return squad;
            }
        }
        return null;
    }

    private Squad findCloseAirSquad(ManagedUnit managedUnit) {
        Squad closestSquad = null;
        double closestDistance = Double.MAX_VALUE;

        for (Squad squad : fightSquads) {
            if (!squad.isAirSquad()) continue;
            if (!mayJoinAirSquad(managedUnit.getUnitType(), holdsOnlyScourge(squad.getComposition()))) continue;
            if (!AirReinforcement.mayReinforce(squad.getStatus(),
                    Collections.singletonMap(managedUnit.getUnitType(), 1))) {
                continue;
            }

            double distance = squad.distance(managedUnit);
            boolean reinforcing = airReinforcer.isReinforcing(squad);
            if (mayJoinAirSquadAt(squad.getStatus(), distance, reinforcing) && distance < closestDistance) {
                closestDistance = distance;
                closestSquad = squad;
            }
        }

        return closestSquad;
    }

    /**
     * Whether a new air unit may join an air squad holding a status at a distance, the squad not reinforcing.
     *
     * @param status the squad's status
     * @param distance pixels between the squad centre and the unit
     * @return true when the unit may join the squad
     * @see #mayJoinAirSquadAt(SquadStatus, double, boolean)
     */
    static boolean mayJoinAirSquadAt(SquadStatus status, double distance) {
        return mayJoinAirSquadAt(status, distance, false);
    }

    /**
     * Whether a new air unit may join an air squad holding a status at a distance. A rallying squad at home takes
     * it from anywhere; a rallying squad flying to reinforce another, and a fighting, retreating or harassing squad,
     * only within {@link #AIR_JOIN_DISTANCE}, so the hatchlings of one egg born beside a squad join it instead of
     * each starting a squad of one, and a new unit never flies across the map alone to catch a squad up.
     *
     * @param status the squad's status
     * @param distance pixels between the squad centre and the unit
     * @param reinforcing true when the squad is flying to reinforce an active air squad
     * @return true when the unit may join the squad
     */
    static boolean mayJoinAirSquadAt(SquadStatus status, double distance, boolean reinforcing) {
        if (status == SquadStatus.RALLY && !reinforcing) {
            return true;
        }
        if (status == SquadStatus.RALLY || AirReinforcement.isActive(status)) {
            return distance < AIR_JOIN_DISTANCE;
        }
        return false;
    }

    private Squad newFightSquad(UnitType type) {
        Squad newSquad;

        if (AIR_SQUAD_TYPES.contains(type)) {
            newSquad = new AirSquad();
        } else {
            newSquad = new GroundSquad();
        }

        newSquad.setStatus(SquadStatus.RALLY);
        newSquad.setRallyPoint(gameState.getSquadRallyPoint());
        fightSquads.add(newSquad);
        return newSquad;
    }

    /**
     * Returns true when a rallying squad has no enemies within detection range and should stage.
     *
     * @param squad squad the reinforcement was added to
     * @return true if the squad should rally to the staging point
     */
    private boolean shouldStageSquad(Squad squad) {
        if (squad.getStatus() != SquadStatus.RALLY) {
            return false;
        }
        return enemyUnitsNearSquad(squad).isEmpty();
    }

    private void addManagedOverlord(ManagedUnit overlord) {
        overlords.addUnit(overlord);
    }

    public void removeManagedUnit(ManagedUnit managedUnit) {
        irradiatedUnits.remove(managedUnit);
        UnitType unitType = managedUnit.getUnitType();
        if (unitType != null && unitType == UnitType.Zerg_Overlord) {
            removeManagedOverlord(managedUnit);
            return;
        }

        removeManagedFighter(managedUnit);
    }

    private void removeManagedFighter(ManagedUnit managedUnit) {
        for (Squad squad: fightSquads) {
            if (squad.containsManagedUnit(managedUnit)) {
                squad.removeUnit(managedUnit);
                if (managedUnit.getUnitType() == UnitType.Zerg_Overlord) {
                    overlords.addUnit(managedUnit);
                }
                return;
            }
        }
        for (Base base: defenseSquads.keySet()) {
            Squad squad = defenseSquads.get(base);
            squad.removeUnit(managedUnit);
        }
    }

    private void removeManagedOverlord(ManagedUnit overlord) {
        overlords.removeUnit(overlord);
        overlord.setRole(UnitRole.IDLE);
    }

    /**
     * Assign target to a fighter and squad.
     *
     * @param managedUnit unit that needs a target
     * @param squad squad that passed fight simulation
     * @param ledger the frame's melee assignments across every fight squad; the unit's entry is dropped, and the
     *     chosen target recorded in its place unless the unit attack-moves in overflow (see {@link #commitPick})
     */
    private void assignEnemyTarget(ManagedUnit managedUnit, Squad squad, TargetLedger ledger) {
        Unit unit = managedUnit.getUnit();
        ledger.release(unit.getID());
        if (managedUnit.getUnitType() == UnitType.Zerg_Overlord) {
            if (gameState.getTechProgression().isOverlordSpeed()) {
                managedUnit.setRallyPoint(squad.getCenter());
                managedUnit.setRole(UnitRole.RALLY);
            } else {
                squad.removeUnit(managedUnit);
                overlords.addUnit(managedUnit);
                managedUnit.setRole(UnitRole.IDLE);
            }
            return;
        }
        if (managedUnit.getUnitType() == UnitType.Zerg_Defiler) {
            Set<Unit> enemies = gameState.getVisibleEnemyUnits();
            Unit nearestEnemy = null;
            double nearestDistance = Double.MAX_VALUE;
            for (Unit enemyUnit : enemies) {
                if (!enemyUnit.isDetected()) continue;
                double d = unit.getDistance(enemyUnit);
                if (d < nearestDistance) {
                    nearestDistance = d;
                    nearestEnemy = enemyUnit;
                }
            }
            if (nearestEnemy != null) {
                managedUnit.setFightTarget(nearestEnemy);
            } else {
                managedUnit.setFightTarget(null);
                managedUnit.setRallyPoint(squad.getCenter());
                managedUnit.setRole(UnitRole.RALLY);
            }
            return;
        }
        List<Unit> enemyUnits = new ArrayList<>(gameState.getVisibleEnemyUnits());

        List<Unit> filtered = new ArrayList<>();
        for (Unit enemyUnit: enemyUnits) {
            if (unit.getType() == UnitType.Zerg_Lurker && !enemyUnit.isFlying() && enemyUnit.isDetected()) {
                filtered.add(enemyUnit);
                continue;
            }
            if (unit.canAttack(enemyUnit) && enemyUnit.isDetected() && 
                !Filter.isLowPriorityCombatTarget(enemyUnit.getType())) {
                filtered.add(enemyUnit);
            }
        }

        Arc joinArc = containArcToJoin(squad);
        if (filtered.isEmpty()) {
            scoutChase.release(unit.getID());
            if (joinArc != null) {
                rallyToDefensePosition(managedUnit, joinArc.closestPosition(unit.getPosition()));
                return;
            }
            assignFallbackMovementTarget(managedUnit, squad);
            return;
        }

        List<Unit> uncapped = ScoutChase.withoutCappedScouts(filtered, enemy -> isCappedScoutFor(unit, enemy));
        boolean scoutCapped = uncapped.size() < filtered.size();
        if (scoutCapped) {
            Position threatened = gameState.threatenedDefensePosition();
            ScoutChase.Excess excess = ScoutChase.excessAction(uncapped.size(), nearestDistance(unit, uncapped),
                    ENEMY_DETECTION_RADIUS, threatened != null);
            if (excess == ScoutChase.Excess.DEFEND) {
                rallyToDefensePosition(managedUnit, threatened);
                return;
            }
            if (excess == ScoutChase.Excess.FOLLOW_SQUAD) {
                rallyToDefensePosition(managedUnit, squad.getCenter());
                return;
            }
        }

        int defensePadding = containmentDefensePadding(Collections.singleton(unit.getType()));
        int now = game.getFrameCount();
        boolean lurker = managedUnit instanceof Lurker;
        boolean committing = lurker ? lurkersCommit(squad, now) : isCommitting(squad, now);
        List<StaticDefenseZone> outranging = FixedFire.appliesTo(unit.getType())
                ? ContainmentPushback.outrangingZones(fixedFireZones, EnemyReachMemory.baseGroundRange(unit.getType()))
                : Collections.emptyList();
        List<StaticDefenseZone> tankZones = lurker
                ? FixedFire.siegedTankZones(lurkerKeptOutZones(squad, outranging, now))
                : Collections.emptyList();
        List<Unit> outOfTankFire = withoutTargetsInTankFire(unit, uncapped, tankZones, defensePadding);
        if (outOfTankFire.isEmpty() && !uncapped.isEmpty()
                && holdOutOfFire(managedUnit, tankZones, defensePadding, LurkerHold.TANK_ZONE)) {
            return;
        }
        if (outOfTankFire.isEmpty()) {
            outOfTankFire = uncapped;
        }
        List<StaticDefenseZone> coolingZones = fixedFire.coolingZones(outranging, now);
        List<Unit> outOfCoolingFire = withoutTargetsInCoolingFire(managedUnit, outOfTankFire, coolingZones,
                defensePadding, committing);
        if (outOfCoolingFire.isEmpty() && !outOfTankFire.isEmpty()
                && holdOutOfFire(managedUnit, coolingZones, defensePadding, LurkerHold.COOLDOWN)) {
            return;
        }
        if (outOfCoolingFire.isEmpty()) {
            outOfCoolingFire = outOfTankFire;
        }

        List<StaticDefenseZone> defenseZones = joinArc == null
                ? Collections.emptyList()
                : gameState.getStaticDefenseZones();
        List<Unit> admissible = outOfCoolingFire;
        Predicate<Unit> admitted = enemy -> !coveredByStaticDefense(enemy.getPosition(), defenseZones, defensePadding);
        filtered = filterByProximity(admissible, unit::getDistance, admitted);
        if (filtered.isEmpty() && joinArc != null) {
            rallyToDefensePosition(managedUnit, joinArc.closestPosition(unit.getPosition()));
            return;
        }
        Function<List<Unit>, TargetScorer.Selection> select = candidates -> TargetScorer.selectTarget(unit,
                preferProxiedBuildings(candidates), managedUnit.fightTarget, ledger, squad.getId());
        TargetScorer.Selection selection = widenWhenSaturated(select.apply(filtered), filtered.size(),
                () -> widenCandidates(admissible, unit::getDistance, admitted), select);
        if (selection != null) {
            TargetScorer.Selection issued = commitPick(ledger, managedUnit.getOverflowGate(), unit, selection,
                    managedUnit.fightTarget, game.getFrameCount());
            TargetChoices.chosen(managedUnit, managedUnit.fightTarget, managedUnit.isAttackMoving(), issued,
                    scoutCapped);
            managedUnit.setFightTarget(issued.getTarget(), issued.isAttackMove());
            recordScoutClaim(unit, issued.getTarget());
        }
    }

    /**
     * Commits a live attacker's pick (see {@link #commitPick(TargetLedger, MeleeOverflowGate, int, UnitType,
     * TargetScorer.Selection, int, boolean, int)}), taking the pick to be within reach when the attacker is no
     * further than {@link #OVERFLOW_EXIT_REACH} from it.
     *
     * @param heldTarget the fight target the attacker held before this pick, or null
     * @return the pick as it is issued
     */
    private static TargetScorer.Selection commitPick(TargetLedger ledger, MeleeOverflowGate gate, Unit attacker,
                                                     TargetScorer.Selection selection, Unit heldTarget, int frame) {
        return commitPick(ledger, gate, attacker.getID(), attacker.getType(), selection, heldTargetId(heldTarget),
                attacker.getDistance(selection.getTarget()) <= OVERFLOW_EXIT_REACH, frame);
    }

    /**
     * Reports the pick to the attacker's {@link MeleeOverflowGate} and decides how it is issued. The pick is a
     * re-target when it is not the target the attacker held, so a saturated re-target enters overflow at once, and
     * an unsaturated re-target within reach leaves it at once. While the gate holds the attacker in overflow, the
     * pick comes back marked as an attack-move past the target and the ledger is left alone, so the attacker does not
     * count toward the target's load. Otherwise the pick stands as a direct attack and is recorded in the ledger.
     *
     * @param ledger the frame's melee assignments
     * @param gate the attacker's overflow gate
     * @param attackerId the attacker's unit id
     * @param attackerType the attacker's type
     * @param selection the pick made for the attacker
     * @param heldTargetId the id of the fight target the attacker held before this pick, or {@link #NO_TARGET_ID}
     *     when it held none that still exists
     * @param inReach true when the picked target is within {@link #OVERFLOW_EXIT_REACH} of the attacker
     * @param frame the current frame
     * @return the pick as it is issued
     */
    static TargetScorer.Selection commitPick(TargetLedger ledger, MeleeOverflowGate gate, int attackerId,
                                             UnitType attackerType, TargetScorer.Selection selection,
                                             int heldTargetId, boolean inReach, int frame) {
        int targetId = selection.getTarget().getID();
        if (gate.observe(selection.isSaturated(), heldTargetId != targetId, inReach, frame)) {
            return selection.asAttackMove();
        }
        ledger.record(attackerId, attackerType, targetId);
        return selection;
    }

    /**
     * @return the id of a fight target that still exists, or {@link #NO_TARGET_ID} for none
     */
    static int heldTargetId(Unit fightTarget) {
        return fightTarget != null && fightTarget.exists() ? fightTarget.getID() : NO_TARGET_ID;
    }

    /**
     * While cannon rushed, narrows the candidates to the proxied buildings among them, when there are any.
     */
    private List<Unit> preferProxiedBuildings(List<Unit> candidates) {
        if (!gameState.isCannonRushed()) {
            return candidates;
        }
        Set<Unit> proxied = gameState.getObservedUnitTracker().getProxiedBuildings();
        List<Unit> proxiedTargets = candidates.stream()
                .filter(proxied::contains)
                .collect(Collectors.toList());
        return proxiedTargets.isEmpty() ? candidates : proxiedTargets;
    }

    /**
     * The frame's melee target ledger, shared by every fight squad's targeting. The first read on a frame builds it
     * from the enemy static defence and seeds it with the fight target each FIGHT member of a fight squad already
     * holds, so a squad targeted early in the frame sees the load of a squad targeted after it. A member that has
     * left FIGHT, whose target no longer exists, or that attack-moves in overflow is not seeded, and the ledger counts
     * only melee members.
     */
    private TargetLedger fightTargetLedger() {
        int now = game.getFrameCount();
        if (fightTargetLedgerFrame != now) {
            fightTargetLedger = new TargetLedger(gameState.getStaticDefenseZones());
            fightTargetLedgerFrame = now;
            for (Squad squad : fightSquads) {
                seedFightTargets(fightTargetLedger, squad.getMembers());
            }
        }
        return fightTargetLedger;
    }

    /**
     * Records in the ledger the fight target of every member in FIGHT whose target still exists and that attacks it
     * directly.
     */
    static void seedFightTargets(TargetLedger ledger, Collection<ManagedUnit> members) {
        for (ManagedUnit member : members) {
            Unit target = member.fightTarget;
            if (seedsFightTarget(member.getRole(), target != null && target.exists(), member.isAttackMoving())) {
                ledger.record(member.getUnitID(), member.getUnitType(), target.getID());
            }
        }
    }

    /**
     * The candidate targets a Lurker keeps once those it would stand inside sieged-tank reach to fire on are dropped,
     * see {@link FixedFire#firingPointZone}.
     *
     * @param unit the Lurker
     * @param candidates its candidate targets
     * @param tankZones sieged-tank zones its squad's Lurkers keep out of, see {@link #lurkerKeptOutZones}
     * @param padding pixels added to each zone's reach
     * @return the candidates kept
     */
    private List<Unit> withoutTargetsInTankFire(Unit unit, List<Unit> candidates, List<StaticDefenseZone> tankZones,
                                                int padding) {
        if (tankZones.isEmpty()) {
            return candidates;
        }
        int ownRange = EnemyReachMemory.baseGroundRange(unit.getType());
        List<Unit> kept = new ArrayList<>();
        for (Unit enemy : candidates) {
            if (FixedFire.firingPointZone(unit.getPosition(), enemy.getPosition(), unit.getDistance(enemy), ownRange,
                    tankZones, padding) == null) {
                kept.add(enemy);
            }
        }
        return kept;
    }

    /**
     * The candidate targets a fighter keeps once those standing in cooling fixed fire are skipped, see
     * {@link FixedFire#skippingZone}. A skip is written once per fighter and zone per cooldown, see
     * {@link FixedFire#firstSkip}.
     *
     * @param managedUnit the fighter
     * @param candidates its candidate targets
     * @param coolingZones cooling fixed fire zones that outrange it
     * @param padding pixels added to each zone's reach
     * @param committing whether its squad is committing to the fight
     * @return the candidates kept
     */
    private List<Unit> withoutTargetsInCoolingFire(ManagedUnit managedUnit, List<Unit> candidates,
                                                   List<StaticDefenseZone> coolingZones, int padding,
                                                   boolean committing) {
        if (coolingZones.isEmpty() || committing) {
            return candidates;
        }
        Unit unit = managedUnit.getUnit();
        int ownRange = EnemyReachMemory.baseGroundRange(unit.getType());
        int now = game.getFrameCount();
        List<Unit> kept = new ArrayList<>();
        for (Unit enemy : candidates) {
            StaticDefenseZone zone = FixedFire.skippingZone(enemy.getPosition(), unit.getDistance(enemy), ownRange,
                    coolingZones, padding, false);
            if (zone == null) {
                kept.add(enemy);
            } else if (fixedFire.firstSkip(unit.getID(), zone, now)) {
                FixedFireTelemetry.targetSkipped(now, unit.getID(), unit.getType(), unit.getPosition(),
                        enemy.getID(), enemy.getType(), enemy.getPosition(), zone);
            }
        }
        return kept;
    }

    /**
     * Keeps a fighter whose every target stands in fire it keeps out of, cooling fixed fire or a sieged tank's reach,
     * out of that fire, at its wait point, see {@link FixedFire#waitPoint}. A Lurker within
     * {@link #HOLD_RELEASE_MARGIN} of the fire holds that point in its RETREAT role, see {@link Lurker#holdStep},
     * unless it already holds one; any other fighter rallies to it. A fighter boxed in inside the fire, with no point
     * that gains ground, is left to its targets.
     *
     * @param managedUnit the fighter
     * @param coolingZones the zones it keeps out of, all outranging it
     * @param padding pixels added to each zone's reach
     * @param reason the hold reason written for a Lurker given a new hold point
     * @return true when the fighter was held out of the fire, false when it is left to its targets
     */
    private boolean holdOutOfFire(ManagedUnit managedUnit, List<StaticDefenseZone> coolingZones, int padding,
                                  String reason) {
        Lurker lurker = managedUnit instanceof Lurker ? (Lurker) managedUnit : null;
        if (lurker == null || lurker.getHoldPosition() == null) {
            Position position = managedUnit.getPosition();
            Position point = FixedFire.waitPoint(position, coolingZones, padding, walkablePoints());
            if (point == null) {
                return false;
            }
            if (lurker == null || RunbyTargeting.zoneMargin(position, coolingZones, padding) > HOLD_RELEASE_MARGIN) {
                rallyToDefensePosition(managedUnit, point);
                return true;
            }
            lurker.holdAt(point);
            FixedFireTelemetry.lurkerHold(game.getFrameCount(), lurker.getUnitID(), position, point,
                    FixedFire.coveringZone(position, coolingZones, padding), reason);
        }
        scoutChase.release(managedUnit.getUnit().getID());
        managedUnit.setFightTarget(null);
        managedUnit.setRole(UnitRole.RETREAT);
        return true;
    }

    /**
     * Whether a member's fight target is seeded into the frame's ledger: only while the member is in FIGHT, the
     * target still exists and the member attacks it rather than attack-moving to it in overflow. A member
     * retreating, rallying, containing, on a runby or in overflow holds no fight slot.
     *
     * @param role the member's role
     * @param targetExists true when the member holds a fight target that still exists
     * @param attackMoving true when the member attack-moves to its target in overflow
     */
    static boolean seedsFightTarget(UnitRole role, boolean targetExists, boolean attackMoving) {
        return role == UnitRole.FIGHT && targetExists && !attackMoving;
    }

    /**
     * Rebuilds the scout chase ledger from the scouts that fight squad members and irradiated units already
     * target, so the closest chasers keep their scout and the rest are turned away this frame.
     */
    private void rebuildScoutChase() {
        List<ScoutChase.Claim> claims = new ArrayList<>();
        for (Squad squad : fightSquads) {
            addScoutClaims(squad.getMembers(), claims);
        }
        addScoutClaims(irradiatedUnits, claims);
        scoutChase.beginFrame(claims);
    }

    private void addScoutClaims(Collection<ManagedUnit> units, List<ScoutChase.Claim> claims) {
        for (ManagedUnit managedUnit : units) {
            Unit target = managedUnit.fightTarget;
            if (target == null || !target.exists() || !isEnemyScout(target)) {
                continue;
            }
            Unit attacker = managedUnit.getUnit();
            claims.add(new ScoutChase.Claim(target.getID(), attacker.getID(), attacker.getDistance(target),
                    chaserCap(attacker, target)));
        }
    }

    private boolean isEnemyScout(Unit enemy) {
        return ScoutChase.isScout(enemy.getType(), enemy.isAttacking(), enemy.isConstructing(),
                isNearEnemyBase(enemy.getTilePosition()));
    }

    private boolean isNearEnemyBase(TilePosition tile) {
        for (Base base : gameState.getBaseData().getEnemyBases()) {
            if (manhattanTileDistance(base.getLocation(), tile) <= ScoutChase.ENEMY_BASE_TILE_RADIUS) {
                return true;
            }
        }
        return false;
    }

    private static int chaserCap(Unit attacker, Unit scout) {
        return ScoutChase.chaserCap(attacker.getPlayer().topSpeed(attacker.getType()),
                scout.getPlayer().topSpeed(scout.getType()));
    }

    private boolean isCappedScoutFor(Unit attacker, Unit enemy) {
        return isEnemyScout(enemy) && !scoutChase.admits(enemy.getID(), attacker.getID(), chaserCap(attacker, enemy));
    }

    private void recordScoutClaim(Unit attacker, Unit target) {
        if (isEnemyScout(target)) {
            scoutChase.claim(target.getID(), attacker.getID());
        } else {
            scoutChase.release(attacker.getID());
        }
    }

    private static double nearestDistance(Unit attacker, List<Unit> candidates) {
        double nearest = Double.MAX_VALUE;
        for (Unit candidate : candidates) {
            nearest = Math.min(nearest, attacker.getDistance(candidate));
        }
        return nearest;
    }

    /**
     * Sends a unit to a position in the RALLY role, rather than marching it on the enemy's buildings: a unit the
     * scout cap turned away, or a unit with nothing to attack outside enemy static defence while a contain is active.
     */
    private void rallyToDefensePosition(ManagedUnit managedUnit, Position position) {
        scoutChase.release(managedUnit.getUnit().getID());
        managedUnit.setFightTarget(null);
        managedUnit.setRallyPoint(position);
        managedUnit.setRole(UnitRole.RALLY);
    }

    /**
     * Keeps a fighter that has no attackable target moving toward the enemy.
     *
     * <p>The fighter hunts the remembered enemy structure {@link EndgameHunt#huntPosition} picks from the squad's
     * center: the nearest one its weapons reach, gas structures and lifted buildings included, or the nearest one at
     * all when it reaches none.
     *
     * <p>The current movement target is held until it is reached; ManagedUnit clears it once the tile
     * is visible. pollScoutTarget() mutates scout assignment accounting and returns a different base
     * on every call, so polling it per frame makes fighters thrash between map corners.
     */
    private void assignFallbackMovementTarget(ManagedUnit managedUnit, Squad squad) {
        if (managedUnit.getMovementTargetPosition() != null) {
            return;
        }

        Position closestBuilding = gameState.getEndgameHunt().huntPosition(squad.getCenter(),
                gameState.getObservedUnitTracker().getLivingObservedUnits(), managedUnit.getUnitType());
        if (closestBuilding != null) {
            managedUnit.setMovementTargetPosition(closestBuilding.toTilePosition());
            return;
        }

        Base enemyMain = gameState.getBaseData().getMainEnemyBase();
        if (enemyMain != null) {
            managedUnit.setMovementTargetPosition(enemyMain.getLocation());
            return;
        }

        managedUnit.setMovementTargetPosition(gameState.pollScoutTarget());
    }

    /**
     * @return the candidates within {@link #TARGETING_RADIUS} of the attacker, or every candidate when none are
     */
    static <T> List<T> filterByProximity(List<T> candidates, ToDoubleFunction<T> distance) {
        return filterByProximity(candidates, distance, candidate -> true);
    }

    /**
     * Replaces a saturated pick with one made from the widened candidates, when the widened candidates add any and
     * the pick made from them is not saturated. Otherwise the saturated pick stands.
     *
     * @param selection the pick made from the nearby candidates, or null when there was none
     * @param nearbyCount how many candidates the pick was made from
     * @param widened candidates within the widened radius, built only when the pick is saturated
     * @param select picks a target from a candidate list
     * @return the pick to keep
     */
    static <T> TargetScorer.Selection widenWhenSaturated(TargetScorer.Selection selection, int nearbyCount,
                                                         Supplier<List<T>> widened,
                                                         Function<List<T>, TargetScorer.Selection> select) {
        if (selection == null || !selection.isSaturated()) {
            return selection;
        }
        List<T> candidates = widened.get();
        if (candidates.size() <= nearbyCount) {
            return selection;
        }
        TargetScorer.Selection wider = select.apply(candidates);
        return wider != null && !wider.isSaturated() ? wider.asWidened() : selection;
    }

    /**
     * Candidates a melee fighter falls back to when every target {@link #filterByProximity} gave it is saturated:
     * those within {@link #TARGETING_RADIUS} of it, and those out to {@link #WIDENED_TARGETING_RADIUS} that the
     * fallback admits.
     *
     * @param candidates attackable enemies
     * @param distance distance from the attacker to a candidate
     * @param fallback which candidates beyond the targeting radius may still be chased
     * @return the candidates within the widened radius
     */
    static <T> List<T> widenCandidates(List<T> candidates, ToDoubleFunction<T> distance, Predicate<T> fallback) {
        List<T> widened = new ArrayList<>();
        for (T enemy : candidates) {
            double d = distance.applyAsDouble(enemy);
            if (d <= TARGETING_RADIUS || d <= WIDENED_TARGETING_RADIUS && fallback.test(enemy)) {
                widened.add(enemy);
            }
        }
        return widened;
    }

    /**
     * Candidates a fighter may target: those within {@link #TARGETING_RADIUS} of it, or, when none are, the
     * candidates anywhere on the map that the fallback admits.
     *
     * @param candidates attackable enemies
     * @param distance distance from the attacker to a candidate
     * @param fallback which candidates beyond the targeting radius may still be chased
     * @return the nearby candidates, or the admitted candidates when none are nearby
     */
    static <T> List<T> filterByProximity(List<T> candidates, ToDoubleFunction<T> distance, Predicate<T> fallback) {
        List<T> nearby = new ArrayList<>();
        for (T enemy : candidates) {
            if (distance.applyAsDouble(enemy) <= TARGETING_RADIUS) {
                nearby.add(enemy);
            }
        }
        if (!nearby.isEmpty()) {
            return nearby;
        }
        return candidates.stream().filter(fallback).collect(Collectors.toList());
    }

    /**
     * Whether a target stands where enemy static defence fires on the unit attacking it.
     *
     * @param target target position
     * @param zones enemy static defence zones, at the reach learned over the game
     * @param padding pixels added to every zone's reach, covering the attacker's extent and a margin
     * @return true when any zone covers the target
     */
    static boolean coveredByStaticDefense(Position target, Collection<StaticDefenseZone> zones, int padding) {
        for (StaticDefenseZone zone : zones) {
            if (zone.covers(target, padding)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Retrieves the largest fight squad or returns null if no fight squads exist.
     * @return Squad OR null
     */
    public Squad largestSquad() {
        if (fightSquads.isEmpty()) {
            return null;
        }
        List<Squad> sorted = fightSquads.stream().sorted().collect(Collectors.toList());
        return sorted.get(sorted.size() - 1);
    }

    public Set<ManagedUnit> getDisbandedUnits() {
        return disbanded;
    }

    /**
     * Assigns overlords to Hydralisk and Mutalisk squads when overlord speed is researched.
     * Prioritizes largest squads first and assigns one overlord per squad.
     * Returns overlords to the main squad if they're the only unit left.
     */
    private void assignOverlordsToSquads() {
        if (!gameState.getTechProgression().isOverlordSpeed()) {
            return;
        }

        returnLoneOverlords();

        List<ManagedUnit> availableOverlords = new ArrayList<>(overlords.getMembers());
        if (availableOverlords.isEmpty()) {
            return;
        }

        List<Squad> targetSquads = getHydraliskAndMutaliskSquads();
        if (targetSquads.isEmpty()) {
            return;
        }

        for (Squad squad : targetSquads) {
            if (availableOverlords.isEmpty()) {
                break;
            }

            if (squadHasOverlord(squad)) {
                continue;
            }

            ManagedUnit overlord = availableOverlords.remove(0);
            overlords.removeUnit(overlord);
            squad.addUnit(overlord);
            overlord.setRole(UnitRole.FIGHT);
        }
    }

    /**
     * Returns overlords to the main overlord squad if they are the only unit type in the squad.
     */
    private void returnLoneOverlords() {
        List<Squad> squadsToRemove = new ArrayList<>();
        
        for (Squad squad : fightSquads) {
            boolean allOverlords = true;
            for (ManagedUnit member : squad.getMembers()) {
                if (member.getUnitType() != UnitType.Zerg_Overlord) {
                    allOverlords = false;
                    break;
                }
            }
            
            if (allOverlords && squad.size() > 0) {
                List<ManagedUnit> overlordsToRemove = new ArrayList<>(squad.getMembers());
                for (ManagedUnit overlord : overlordsToRemove) {
                    squad.removeUnit(overlord);
                    overlords.addUnit(overlord);
                    overlord.setRallyPoint(gameState.getSquadRallyPoint());
                    overlord.setRole(UnitRole.RETREAT);
                }
                squadsToRemove.add(squad);
            }
        }
        fightSquads.removeAll(squadsToRemove);
    }

    /**
     * Returns a list of Hydralisk and Mutalisk squads that may take an Overlord, sorted by size (largest first). A
     * squad that takes no reinforcements, such as a harassing one, takes no Overlord either.
     */
    private List<Squad> getHydraliskAndMutaliskSquads() {
        return fightSquads.stream()
            .filter(squad -> {
                return mayJoin(squad.getStatus()) && (squad.getCountOf(UnitType.Zerg_Mutalisk) > 0
                        || squad.getCountOf(UnitType.Zerg_Hydralisk) > 0);
            })
            .sorted((s1, s2) -> Integer.compare(s2.size(), s1.size()))
            .collect(Collectors.toList());
    }

    private Map<Squad, Double> getAdjacentSquads(Squad targetSquad, double maxRadius) {
        Map<Squad, Double> result = new HashMap<>();
        for (Squad squad : fightSquads) {
            if (squad == targetSquad) continue;
            double dist = targetSquad.distance(squad);
            if (dist <= maxRadius) {
                result.put(squad, dist);
            }
        }
        return result;
    }

    /**
     * Checks if a squad already has an overlord assigned to it.
     */
    private boolean squadHasOverlord(Squad squad) {
        for (ManagedUnit member : squad.getMembers()) {
            if (member.getUnitType() == UnitType.Zerg_Overlord) {
                return true;
            }
        }
        return false;
    }
}
