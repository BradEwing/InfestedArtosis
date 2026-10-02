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
import util.Vec2;

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
    private final ExposedTargets.Memory exposedMemory = new ExposedTargets.Memory();
    private final Map<Base, Integer> lastSighted = new HashMap<>();
    private final Map<Base, Integer> probeRefusedUntil = new HashMap<>();

    public AirHarassController(Game game, GameState gameState) {
        this.game = game;
        this.gameState = gameState;
    }

    /**
     * The outcome of an entry check: the verdict, the base and strike point, or the exposed group, a harass would
     * start on, the age of that base's anti-air sighting, and whether known anti-air structures cover its probe
     * point. A PROBE verdict starts the harass with a probe of the base; an exposed group is never probed.
     */
    public static final class Entry {
        private final AirHarassEvaluator.EntryVerdict verdict;
        private final AirHarassEvaluator.BaseOption<Base> option;
        private final ExposedTargets.Group exposed;
        private final int sightingAge;
        private final int knownCover;

        Entry(AirHarassEvaluator.EntryVerdict verdict, AirHarassEvaluator.BaseOption<Base> option,
              ExposedTargets.Group exposed, int sightingAge, int knownCover) {
            this.verdict = verdict;
            this.option = option;
            this.exposed = exposed;
            this.sightingAge = sightingAge;
            this.knownCover = knownCover;
        }

        public boolean enters() {
            return verdict == AirHarassEvaluator.EntryVerdict.ENTER && (option != null || exposed != null)
                    || verdict == AirHarassEvaluator.EntryVerdict.PROBE && option != null;
        }
    }

    /**
     * Steps the harassment heat map every {@link #HEAT_INTERVAL} frames: every known enemy base heats up, has its
     * anti-air sighting refreshed, and the ground within sight of each harassing Mutalisk is zeroed. A probing
     * Mutalisk near the probed base leaves its heat alone, see {@link AirHarassScouting#coolsHeat}.
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
            refreshSighting(base, now);
        }
        heatMap.accumulate(centers, resources::contains);
        int sight = UnitType.Zerg_Mutalisk.sightRange() / 32;
        for (Squad squad : squads) {
            if (squad.getStatus() != SquadStatus.HARASS) {
                continue;
            }
            AirHarassState state = squad.getHarassState();
            boolean probing = state != null && state.getPhase() == AirHarassState.Phase.PROBE;
            Position probed = probing && state.getTargetBase() != null ? state.getTargetBase().getCenter() : null;
            for (ManagedUnit member : squad.getMembers()) {
                if (AirHarassScouting.coolsHeat(probing, member.getPosition(), probed,
                        UnitType.Zerg_Mutalisk.sightRange())) {
                    heatMap.visit(member.getPosition(), sight);
                }
            }
        }
    }

    /**
     * Runs the entry gates for an air squad and records the verdict. The base options and the exposed groups are only
     * read for a squad that passes the cheap gates, and the harass starts on whichever scores higher, see
     * {@link AirHarassEvaluator#exposedOutscoresBase}. A base a probe found defended is left out until its refusal
     * runs out, see {@link AirHarassScouting#unrefused}, and a stale base whose probe point known anti-air structures
     * cover is entered on the entry verdict, not probed. A base or exposed group at the held target is no candidate,
     * see {@link AirHarassEvaluator#isFailedTarget}.
     *
     * @param squad air squad
     * @param now current frame
     * @param basesUnderAttack true when a base of ours is under attack
     * @param containPoints midpoints of the containment arcs held
     * @param heldTarget center or anchor of the target a failed harass was on and the hold keeps closed, or null
     * @return the verdict and the base or exposed group a harass would start on
     */
    public Entry checkEntry(Squad squad, int now, boolean basesUnderAttack, List<Position> containPoints,
                            Position heldTarget) {
        Flock flock = flock(squad);
        double tolerance = AirHarassEvaluator.tolerance(flock.healthy);
        boolean cheapGatesPass = AirHarassEvaluator.harassMatchup(gameState.getOpponentRace())
                && AirHarassEvaluator.mutalisksOnly(squad.getComposition())
                && flock.healthy >= AirHarassEvaluator.MIN_HEALTHY_MUTAS
                && !basesUnderAttack;
        List<AirHarassEvaluator.BaseOption<Base>> options = new ArrayList<>();
        List<AirHarassTargeting.AirThreat> threats = new ArrayList<>();
        ExposedTargets.Group exposed = null;
        double flockDefense = 0;
        if (cheapGatesPass) {
            View view = view(now);
            threats = view.threats;
            Collection<Base> bases = new ArrayList<>();
            for (Base base : AirHarassScouting.unrefused(gameState.getBaseData().getEnemyBases(), probeRefusedUntil,
                    now)) {
                if (!AirHarassEvaluator.isFailedTarget(heldTarget, base.getCenter())) {
                    bases.add(base);
                }
            }
            options = options(threats, tolerance, containPoints, bases, MIN_ENTRY_HEAT);
            exposed = ExposedTargets.choose(withoutFailedTarget(exposedMemory.admitted(ExposedTargets.groups(
                    view.candidates(), flock.mutas, AirHarassTargeting.avoided(threats, tolerance)), now),
                    heldTarget), threats, tolerance, squad.getCenter());
            flockDefense = AirHarassTargeting.defenseAt(threats, squad.getCenter(), 0);
        }
        AirHarassEvaluator.EntryVerdict verdict = AirHarassEvaluator.entryVerdict(
                AirHarassEvaluator.EntryInput.builder()
                        .opponentRace(gameState.getOpponentRace())
                        .composition(squad.getComposition())
                        .healthyMutas(flock.healthy)
                        .basesUnderAttack(basesUnderAttack)
                        .options(new ArrayList<>(options))
                        .exposedTarget(exposed != null)
                        .flockDefense(flockDefense)
                        .tolerance(tolerance)
                        .build());
        boolean enters = verdict == AirHarassEvaluator.EntryVerdict.ENTER;
        AirHarassEvaluator.BaseOption<Base> bestBase = AirHarassEvaluator.chooseBase(options);
        boolean raidsExposed = exposed != null && AirHarassEvaluator.exposedOutscoresBase(
                ExposedTargets.score(exposed, squad.getCenter()), bestBase == null ? -1
                        : AirHarassEvaluator.baseScore(bestBase.getHeat(), bestBase.getContainDistance()));
        AirHarassEvaluator.BaseOption<Base> chosen = enters && !raidsExposed ? bestBase : null;
        ExposedTargets.Group chosenExposed = enters && raidsExposed ? exposed : null;
        Position strike = chosen != null ? chosen.getStrikePoint()
                : chosenExposed != null ? chosenExposed.getAnchor() : null;
        int sightingAge = chosen == null ? -1 : sightingAge(chosen.getBase(), now);
        int knownCover = chosen == null ? -1 : knownCover(chosen.getBase(), threats);
        if (chosenExposed == null) {
            verdict = AirHarassScouting.entryMode(verdict,
                    AirHarassScouting.probeSightingAge(sightingAge, knownCover == 1));
        }
        HarassTelemetry.row(HarassRow.builder()
                .frame(now)
                .squadId(squad.getId())
                .event(HarassRow.Event.ENTRY_CHECK)
                .verdict(verdict)
                .base(chosen == null ? null : chosen.getBase().getCenter())
                .strikePoint(strike)
                .center(squad.getCenter())
                .mutas(flock.mutas)
                .healthyMutas(flock.healthy)
                .flockHitPoints(flock.hitPoints)
                .tolerance(tolerance)
                .airDefense(strike == null ? -1 : AirHarassTargeting.defenseAt(threats, strike,
                        AirHarassEvaluator.STRIKE_RADIUS))
                .containDistance(nearestDistance(squad.getCenter(), containPoints))
                .basesUnderAttack(basesUnderAttack ? 1 : 0)
                .targetKind(targetKind(chosen != null, chosenExposed != null))
                .aaSightingAge(sightingAge)
                .aaKnownCover(knownCover)
                .build());
        return new Entry(verdict, chosen, chosenExposed, sightingAge, knownCover);
    }

    private static List<ExposedTargets.Group> withoutFailedTarget(Collection<ExposedTargets.Group> groups,
                                                                  Position heldTarget) {
        List<ExposedTargets.Group> open = new ArrayList<>();
        for (ExposedTargets.Group group : groups) {
            if (!AirHarassEvaluator.isFailedTarget(heldTarget, group.getAnchor())) {
                open.add(group);
            }
        }
        return open;
    }

    private static HarassRow.TargetKind targetKind(boolean base, boolean exposed) {
        if (base) {
            return HarassRow.TargetKind.BASE;
        }
        return exposed ? HarassRow.TargetKind.EXPOSED : null;
    }

    /**
     * Starts a harass on the entry's base or exposed group and hands every Mutalisk the HARASS role. The anti-air
     * known now is not new to this harass. A PROBE entry starts with a probe of the base, any other with the flight
     * to its target.
     *
     * @param squad squad entering HARASS
     * @param entry the entry that passed
     * @param now current frame
     */
    public void start(Squad squad, Entry entry, int now) {
        Flock flock = flock(squad);
        AirHarassState state = new AirHarassState(now, flock.hitPoints);
        state.learnAntiAir(view(now).threats);
        if (entry.option == null) {
            state.targetExposed(entry.exposed, now);
        } else if (entry.verdict != AirHarassEvaluator.EntryVerdict.PROBE
                || !beginProbe(squad, state, entry.option.getBase(), entry.option.getStrikePoint(), now)) {
            state.target(entry.option.getBase(), entry.option.getStrikePoint(), now);
        }
        state.setLastTickFrame(now - AirHarassEvaluator.HARASS_TICK);
        squad.setHarassState(state);
        for (ManagedUnit member : squad.getMembers()) {
            member.clearRegroup();
            member.setRole(UnitRole.HARASS);
            member.setFightTarget(null);
            member.setContainPosition(null);
            member.setRetreatTarget(null);
            member.setHarassDestination(destinationOf(state, member));
        }
        HarassTelemetry.row(row(squad, state, HarassRow.Event.ENTER, now)
                .mutas(flock.mutas)
                .healthyMutas(flock.healthy)
                .flockHitPoints(flock.hitPoints)
                .tolerance(AirHarassEvaluator.tolerance(flock.healthy))
                .airDefense(AirHarassTargeting.defenseAt(view(now).threats, state.getStrikePoint(),
                        AirHarassEvaluator.STRIKE_RADIUS))
                .aaSightingAge(entry.sightingAge)
                .aaKnownCover(entry.knownCover)
                .build());
    }

    /**
     * Sends every Mutalisk of a squad that just left a harass to one shared retreat point, away from the anti-air
     * near the flock, so the flock leaves together instead of each Mutalisk fleeing on its own. Leaves the retreat
     * targets alone when no anti-air is near the flock. Runs after the caller has handed out the retreat role.
     *
     * @param squad squad that left HARASS this frame
     * @param now current frame
     */
    public void leaveTogether(Squad squad, int now) {
        if (squad.size() == 0) {
            return;
        }
        Position exit = AirHarassScouting.sharedExitPoint(squad.getCenter(), view(now).threats);
        if (exit == null) {
            return;
        }
        Position clamped = Vec2.between(squad.getCenter(), exit).clampToMap(game, squad.getCenter());
        for (ManagedUnit member : squad.getMembers()) {
            member.setRetreatTarget(clamped);
        }
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
        if (state == null || !state.hasTarget()) {
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
     * Ends a harass: records the EXIT row, records an exposed target in the {@link ExposedTargets.Memory}, refuses
     * a base the probe found defended for {@link AirHarassScouting#PROBE_REFUSAL_FRAMES}, and clears every
     * Mutalisk's harass order. When a probe ends the harass and its hold point is clear of known anti-air, see
     * {@link AirHarassScouting#proberRegroups}, the prober flies back to the hold point ahead of the squad's next
     * orders, see {@link ManagedUnit#regroup}. The squad's status is left to the caller.
     *
     * @param squad harassing squad
     * @param reason why the harass ended
     * @param now current frame
     */
    public void stop(Squad squad, AirHarassEvaluator.ExitReason reason, int now) {
        AirHarassState state = squad.getHarassState();
        if (state != null && state.targetsExposed()) {
            exposedMemory.record(state.getExposedAnchor(), now);
        }
        if (state != null && state.getTargetBase() != null && AirHarassScouting.refusesBase(reason)) {
            probeRefusedUntil.put(state.getTargetBase(), now + AirHarassScouting.PROBE_REFUSAL_FRAMES);
        }
        Flock flock = flock(squad);
        List<AirHarassTargeting.AirThreat> threats = view(now).threats;
        HarassTelemetry.row(withProber(exitRow(squad.getId(), state, reason, now,
                squad.size() == 0 ? null : squad.getCenter(), flock.hitPoints, threats), squad, state)
                .mutas(flock.mutas)
                .healthyMutas(flock.healthy)
                .build());
        ManagedUnit prober = state != null && state.getPhase() == AirHarassState.Phase.PROBE
                ? proberOf(squad, state) : null;
        if (prober != null && state.getHoldPoint() != null
                && AirHarassScouting.proberRegroups(reason, AirHarassScouting.holdExposed(threats,
                        state.getHoldPoint()))) {
            prober.regroup(state.getHoldPoint(), now + AirHarassScouting.PROBER_REGROUP_FRAMES);
        }
        for (ManagedUnit member : squad.getMembers()) {
            member.setHarassDestination(null);
            member.setFightTarget(null);
        }
    }

    /**
     * Builds the EXIT row of a harass. air_defense is the anti-air the STRIKE_DEFENDED exit reads: for a base, the
     * anti-air within {@link AirHarassEvaluator#STRIKE_RADIUS} of the strike point; for an exposed target, the
     * group's {@link ExposedTargets#defenseAt} at the anchor it was last followed to. flock_defense is the anti-air
     * covering the flock's center, which the FLOCK_DEFENDED exit reads. Each is -1 when its point is unknown.
     *
     * @param squadId the squad's id
     * @param state the harass state, or null when the squad carried none
     * @param reason why the harass ended
     * @param now current frame
     * @param center the squad's center, or null for an empty squad
     * @param flockHitPoints summed hit points of the Mutalisks
     * @param threats every known anti-air threat
     * @return the row, still open for the flock's counts
     */
    static HarassRow.HarassRowBuilder exitRow(String squadId, AirHarassState state,
                                              AirHarassEvaluator.ExitReason reason, int now, Position center,
                                              int flockHitPoints, List<AirHarassTargeting.AirThreat> threats) {
        HarassRow.HarassRowBuilder row;
        if (state == null) {
            row = HarassRow.builder().frame(now).squadId(squadId).event(HarassRow.Event.EXIT);
        } else {
            row = row(squadId, state, HarassRow.Event.EXIT, now)
                    .hpLossFraction(AirHarassEvaluator.hpLossFraction(state.getStartHitPoints(), flockHitPoints));
            if (state.targetsExposed() && state.getExposedGroup() != null) {
                row.airDefense(ExposedTargets.defenseAt(state.getExposedGroup(), threats));
            } else if (state.getStrikePoint() != null) {
                row.airDefense(AirHarassTargeting.defenseAt(threats, state.getStrikePoint(),
                        AirHarassEvaluator.STRIKE_RADIUS));
            }
        }
        if (center != null) {
            row.flockDefense(AirHarassTargeting.defenseAt(threats, center, 0));
        }
        return row
                .exitReason(reason)
                .center(center)
                .flockHitPoints(flockHitPoints);
    }

    /**
     * Books a death against a harassing squad: a member's death as a Mutalisk lost, and an enemy's death as a kill
     * as {@link AirHarassEvaluator#creditsKill} rules, the credit radius being a Mutalisk's range plus
     * {@link #KILL_CREDIT_MARGIN}. A kill goes to the harassing squad whose Mutalisk was attacking it, else to the
     * first one in range. Must run before a dead member is removed from its squad.
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
        if (ours) {
            creditLoss(unit, squads, now);
            return;
        }
        int creditRadius = UnitType.Zerg_Mutalisk.groundWeapon().maxRange() + KILL_CREDIT_MARGIN;
        Position position = unit.getPosition();
        Squad targeting = null;
        Squad near = null;
        boolean otherNear = false;
        for (Squad squad : squads) {
            boolean harassing = squad.getStatus() == SquadStatus.HARASS && squad.getHarassState() != null;
            for (ManagedUnit member : squad.getMembers()) {
                boolean inRange = member.getPosition().getDistance(position) <= creditRadius;
                if (!harassing) {
                    otherNear |= inRange;
                    continue;
                }
                if (targeting == null && member.fightTarget == unit) {
                    targeting = squad;
                }
                if (near == null && inRange) {
                    near = squad;
                }
            }
        }
        if (!AirHarassEvaluator.creditsKill(targeting != null, near != null, otherNear)) {
            return;
        }
        Squad credited = targeting != null ? targeting : near;
        AirHarassState state = credited.getHarassState();
        state.creditKill(killKind(unit.getType()));
        HarassTelemetry.row(row(credited, state, HarassRow.Event.KILL, now)
                .center(position)
                .killedType(unit.getType())
                .build());
    }

    private void creditLoss(Unit unit, Collection<Squad> squads, int now) {
        for (Squad squad : squads) {
            AirHarassState state = squad.getHarassState();
            if (squad.getStatus() != SquadStatus.HARASS || state == null) {
                continue;
            }
            for (ManagedUnit member : squad.getMembers()) {
                if (member.getUnit() == unit) {
                    state.creditLoss();
                    HarassTelemetry.row(row(squad, state, HarassRow.Event.MUTA_LOST, now)
                            .center(unit.getPosition())
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
        boolean targetGone;
        boolean heated;
        Position strike;
        List<AirHarassTargeting.AirThreat> avoided = AirHarassTargeting.avoided(view.threats, tolerance);
        if (state.targetsExposed()) {
            ExposedTargets.Group group = ExposedTargets.follow(
                    ExposedTargets.groups(view.candidates(), flock.mutas, avoided), state.getExposedAnchor());
            targetGone = group == null;
            heated = !targetGone;
            if (heated) {
                state.follow(group);
            }
            strike = heated && ExposedTargets.exposed(group, view.threats, tolerance) ? group.getAnchor() : null;
        } else {
            HarassHeatMap heatMap = gameState.getGameMap().getHarassHeatMap();
            Base base = state.getTargetBase();
            targetGone = !gameState.getBaseData().getEnemyBases().contains(base);
            strike = targetGone ? null : heatMap.hottestNear(base.getCenter(), tolerated(view.threats, tolerance));
            heated = !targetGone && heatMap.hottestNear(base.getCenter(), point -> true) != null;
        }
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
            return AirHarassScouting.probeExitReason(reason, state.getPhase() == AirHarassState.Phase.PROBE,
                    flockDefense > tolerance);
        }
        List<AirHarassTargeting.AirThreat> newThreats = state.learnAntiAir(view.threats);
        if (state.getPhase() == AirHarassState.Phase.PROBE && !targetGone) {
            reason = heated ? probeTick(squad, state, strike, view.threats, now)
                    : AirHarassEvaluator.ExitReason.NO_TARGET;
            if (reason != null) {
                return reason;
            }
        } else {
            if (AirHarassScouting.newAntiAirExit(newThreats, view.threats, targetGone ? null : state.targetCenter(),
                    squad.getCenter(), tolerance)) {
                return AirHarassEvaluator.ExitReason.NEW_AA;
            }
            if (strike != null) {
                state.setStrikePoint(strike);
            }
            if (!state.hasArrived() && AirHarassEvaluator.arrived(squad.getCenter(), state.getStrikePoint())) {
                state.arrive(now);
            }
            if (AirHarassEvaluator.shouldRetarget(targetGone, heated, state.hasArrived(), now,
                    state.getLastProgressFrame())
                    && !retarget(squad, state, view, flock.mutas, tolerance, containPoints, now)) {
                return AirHarassEvaluator.ExitReason.NO_TARGET;
            }
        }
        HarassTelemetry.row(row(squad, state, HarassRow.Event.TICK, now)
                .flockDefense(flockDefense)
                .center(squad.getCenter())
                .mutas(flock.mutas)
                .healthyMutas(flock.healthy)
                .flockHitPoints(flock.hitPoints)
                .hpLossFraction(hpLoss)
                .tolerance(tolerance)
                .airDefense(AirHarassTargeting.defenseAt(view.threats, state.getStrikePoint(),
                        AirHarassEvaluator.STRIKE_RADIUS))
                .avoidedZones(avoided.size())
                .containDistance(nearestDistance(squad.getCenter(), containPoints))
                .basesUnderAttack(basesUnderAttack ? 1 : 0)
                .aaSightingAge(sightingAge(state.getTargetBase(), now))
                .build());
        return null;
    }

    /**
     * Runs one decision tick of a probe: follows the strike point, moves the hold point out of newly known anti-air,
     * notes when the probed base's resources come into sight, sends the prober on to the strike point once they have,
     * and acts on what the probe found. A cleared probe sends the whole flock to the strike point.
     *
     * @param strike the base's hottest tolerated point, or null when it has none
     * @param threats every known anti-air threat
     * @return PROBE_DEFENDED or NO_TARGET to end the harass, or null to keep going
     */
    private AirHarassEvaluator.ExitReason probeTick(Squad squad, AirHarassState state, Position strike,
                                                    List<AirHarassTargeting.AirThreat> threats, int now) {
        Base base = state.getTargetBase();
        int sightingAge = sightingAge(base, now);
        ManagedUnit prober = proberOf(squad, state);
        int proberHitPoints = prober == null ? 0 : prober.getUnit().getHitPoints();
        if (strike != null) {
            state.setStrikePoint(strike);
        }
        if (AirHarassScouting.holdExposed(threats, state.getHoldPoint())) {
            state.setHoldPoint(holdPoint(base, squad.getCenter(), threats));
        }
        state.setProbeResourcesSighted(AirHarassScouting.probeResourcesSighted(state.isProbeResourcesSighted(),
                base.getCenter(), resourceCenterOf(base), point -> game.isVisible(point.toTilePosition())));
        boolean sighted = AirHarassScouting.probeSighted(state.isProbeResourcesSighted(), strike != null,
                strike != null && game.isVisible(strike.toTilePosition()));
        AirHarassScouting.ProbeOutcome outcome = AirHarassScouting.probeOutcome(prober != null, proberHitPoints,
                state.getProberPeakHitPoints(), sighted, strike != null, now, state.getProbeStartFrame());
        state.observeProberHitPoints(proberHitPoints);
        switch (outcome) {
            case DEFENDED:
                return AirHarassEvaluator.ExitReason.PROBE_DEFENDED;
            case TIMED_OUT:
                return AirHarassEvaluator.ExitReason.NO_TARGET;
            case CLEAR:
                int proberPeakHitPoints = state.getProberPeakHitPoints();
                state.clearProbe(strike, now);
                HarassTelemetry.row(row(squad, state, HarassRow.Event.PROBE_CLEAR, now)
                        .center(squad.getCenter())
                        .aaSightingAge(sightingAge)
                        .proberHitPoints(proberHitPoints)
                        .proberPeakHitPoints(proberPeakHitPoints)
                        .proberId(state.getProberId())
                        .build());
                return null;
            default:
                return null;
        }
    }

    /**
     * Starts a probe of a base: the healthiest Mutalisk flies to the base's resources while the rest hold short of
     * the base, outside the anti-air known now.
     *
     * @return false when the squad has no Mutalisk to probe with
     */
    private boolean beginProbe(Squad squad, AirHarassState state, Base base, Position strike, int now) {
        Map<Integer, Integer> hitPoints = new HashMap<>();
        for (ManagedUnit member : squad.getMembers()) {
            if (member.getUnitType() == UnitType.Zerg_Mutalisk) {
                hitPoints.put(member.getUnitID(), member.getUnit().getHitPoints());
            }
        }
        int prober = AirHarassScouting.chooseProber(hitPoints);
        if (prober < 0) {
            return false;
        }
        state.probe(base, strike, prober, hitPoints.get(prober), probePointOf(base),
                holdPoint(base, squad.getCenter(), view(now).threats), now);
        return true;
    }

    private Position holdPoint(Base base, Position flockCenter, List<AirHarassTargeting.AirThreat> threats) {
        Position hold = AirHarassScouting.holdPoint(base.getCenter(), flockCenter, threats);
        return Vec2.between(flockCenter, hold).clampToMap(game, flockCenter);
    }

    private static ManagedUnit proberOf(Squad squad, AirHarassState state) {
        for (ManagedUnit member : squad.getMembers()) {
            if (member.getUnitID() == state.getProberId()) {
                return member;
            }
        }
        return null;
    }

    /**
     * Where a harassing Mutalisk flies when it has no target: during a probe the hold point, or for the prober its
     * {@link AirHarassScouting#proberDestination}; the strike point otherwise.
     *
     * @param state the harass state
     * @param member a Mutalisk of the harassing squad
     * @return the destination
     */
    static Position destinationOf(AirHarassState state, ManagedUnit member) {
        if (state.getPhase() != AirHarassState.Phase.PROBE) {
            return state.getStrikePoint();
        }
        if (member.getUnitID() != state.getProberId()) {
            return state.getHoldPoint();
        }
        return AirHarassScouting.proberDestination(state.isProbeResourcesSighted(), state.getProbePoint(),
                state.getStrikePoint());
    }

    /**
     * Marks a base's anti-air as sighted this frame when its core is in sight.
     */
    private void refreshSighting(Base base, int now) {
        List<Position> samples = new ArrayList<>();
        samples.add(base.getCenter());
        Position resources = resourceCenterOf(base);
        if (resources != null) {
            samples.add(resources);
        }
        if (AirHarassScouting.coreSighted(samples, point -> game.isVisible(point.toTilePosition()))) {
            lastSighted.put(base, now);
        }
    }

    /**
     * Whether known anti-air structures cover a base's probe point, see {@link AirHarassScouting#knownAntiAirCovers}.
     *
     * @return 1 when they do, 0 otherwise
     */
    private static int knownCover(Base base, List<AirHarassTargeting.AirThreat> threats) {
        return AirHarassScouting.knownAntiAirCovers(threats, probePointOf(base)) ? 1 : 0;
    }

    private static Position probePointOf(Base base) {
        return AirHarassScouting.probePoint(base.getCenter(), resourceCenterOf(base),
                UnitType.Zerg_Mutalisk.sightRange());
    }

    /**
     * Frames since a base's anti-air was sighted, counting this frame when its core is in sight now.
     *
     * @return the age, or -1 with no base
     */
    private int sightingAge(Base base, int now) {
        if (base == null) {
            return -1;
        }
        refreshSighting(base, now);
        return AirHarassScouting.sightingAge(lastSighted.getOrDefault(base, -1), now);
    }

    private static Position resourceCenterOf(Base base) {
        int sumX = 0;
        int sumY = 0;
        int count = 0;
        for (bwem.Mineral mineral : base.getMinerals()) {
            sumX += mineral.getCenter().getX();
            sumY += mineral.getCenter().getY();
            count++;
        }
        for (bwem.Geyser geyser : base.getGeysers()) {
            sumX += geyser.getCenter().getX();
            sumY += geyser.getCenter().getY();
            count++;
        }
        return count == 0 ? null : new Position(sumX / count, sumY / count);
    }

    /**
     * Moves a harass on to the best known enemy base it has not raided yet this episode and no probe has refused,
     * probing it first when its anti-air sighting is stale and no known anti-air structure covers its probe point, or
     * to the best exposed group of enemies the {@link ExposedTargets.Memory} admits when that group outscores the
     * base, see {@link AirHarassEvaluator#exposedOutscoresBase}. An exposed target being left is recorded there
     * first.
     *
     * @return true when a target was found
     */
    private boolean retarget(Squad squad, AirHarassState state, View view, int mutas, double tolerance,
                             List<Position> containPoints, int now) {
        if (state.targetsExposed()) {
            exposedMemory.record(state.getExposedAnchor(), now);
        }
        Set<Base> candidates = new HashSet<>(AirHarassScouting.unrefused(gameState.getBaseData().getEnemyBases(),
                probeRefusedUntil, now));
        candidates.removeAll(state.getVisitedBases());
        AirHarassEvaluator.BaseOption<Base> next = AirHarassEvaluator.chooseBase(
                options(view.threats, tolerance, containPoints, candidates, 0));
        ExposedTargets.Group exposed = ExposedTargets.choose(exposedMemory.admitted(ExposedTargets.groups(
                view.candidates(), mutas, AirHarassTargeting.avoided(view.threats, tolerance)), now),
                view.threats, tolerance, squad.getCenter());
        if (exposed != null && next != null && !AirHarassEvaluator.exposedOutscoresBase(
                ExposedTargets.score(exposed, squad.getCenter()),
                AirHarassEvaluator.baseScore(next.getHeat(), next.getContainDistance()))) {
            exposed = null;
        }
        int sightingAge = -1;
        int knownCover = -1;
        if (exposed != null) {
            state.targetExposed(exposed, now);
        } else if (next != null) {
            sightingAge = sightingAge(next.getBase(), now);
            knownCover = knownCover(next.getBase(), view.threats);
            if (!AirHarassScouting.stale(AirHarassScouting.probeSightingAge(sightingAge, knownCover == 1))) {
                state.target(next.getBase(), next.getStrikePoint(), now);
            } else if (!beginProbe(squad, state, next.getBase(), next.getStrikePoint(), now)) {
                return false;
            }
        } else {
            return false;
        }
        HarassTelemetry.row(row(squad, state, HarassRow.Event.RETARGET, now)
                .center(squad.getCenter())
                .aaSightingAge(sightingAge)
                .aaKnownCover(knownCover)
                .build());
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
     * last order keeps it. Only enemies around the target base, or within {@link ExposedTargets#SEEK_RADIUS} of an
     * exposed target, are sought out; an enemy anywhere else is taken only when it is close to the Mutalisk. On an
     * exposed target a Missile Turret the flock tolerates is taken too, see {@link AirHarassTargeting#turretTaken}.
     * During a probe no Mutalisk attacks: the prober flies to the probe point and the rest to the hold point.
     */
    private void assignOrders(Squad squad, AirHarassState state, View view,
                              List<AirHarassTargeting.AirThreat> avoided, int mutas, int now) {
        if (state.getPhase() == AirHarassState.Phase.PROBE) {
            assignProbeOrders(squad, state, now);
            return;
        }
        Position baseCenter = state.targetCenter();
        int baseRadius = state.targetsExposed() ? ExposedTargets.SEEK_RADIUS : HarassHeatMap.RADIUS_TILES * 32;
        int mapWidth = game.mapWidth() * 32;
        int mapHeight = game.mapHeight() * 32;
        AirHarassTargeting.Situation situation = AirHarassTargeting.Situation.builder()
                .contacts(view.contacts)
                .avoided(avoided)
                .flockSize(mutas)
                .seekPoint(state.getStrikePoint())
                .targetAllowed(point -> point.getDistance(baseCenter) <= baseRadius)
                .turretsTaken(state.targetsExposed())
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

    private static void assignProbeOrders(Squad squad, AirHarassState state, int now) {
        for (ManagedUnit member : squad.getMembers()) {
            if (member.getRole() != UnitRole.HARASS) {
                member.setRole(UnitRole.HARASS);
            }
            if (!member.isReady() && now < member.getUnreadyUntilFrame()) {
                continue;
            }
            member.setFightTarget(null);
            member.setHarassDestination(destinationOf(state, member));
        }
    }

    /**
     * Reads the enemy once for a harass frame: every visible enemy a Mutalisk could attack, every remembered enemy
     * structure out of sight whose last known tile is not in sight either, and every known anti-air threat.
     * Anti-air structures count wherever they were last seen; a mobile unit only while its observation is fresh.
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
            if (position == null) {
                continue;
            }
            Unit unit = observed.getUnit();
            boolean visible = unit.isVisible();
            if (type.isBuilding() && !visible && !Filter.isLowPriorityCombatTarget(type)
                    && !game.isVisible(position.toTilePosition())) {
                int pool = type.maxHitPoints() + type.maxShields();
                int hitPoints = observed.getLastKnownHitPoints() + observed.getLastKnownShields();
                view.remembered.add(new AirHarassTargeting.Contact(unit.getID(), type, position, hitPoints,
                        pool <= 0 ? 1.0 : (double) hitPoints / pool));
            }
            if (!AirHarassTargeting.isAntiAir(type)) {
                continue;
            }
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

    /**
     * Starts a row for a harassing squad. While the harass probes, the row carries the prober's hit points and the
     * most it has had during the probe, 0 hit points once the prober is gone.
     */
    private static HarassRow.HarassRowBuilder row(Squad squad, AirHarassState state, HarassRow.Event event, int now) {
        return withProber(row(squad.getId(), state, event, now), squad, state);
    }

    private static HarassRow.HarassRowBuilder withProber(HarassRow.HarassRowBuilder row, Squad squad,
                                                         AirHarassState state) {
        if (state != null && state.getPhase() == AirHarassState.Phase.PROBE) {
            ManagedUnit prober = proberOf(squad, state);
            int proberHitPoints = prober == null ? 0 : prober.getUnit().getHitPoints();
            row.proberHitPoints(proberHitPoints)
                    .proberPeakHitPoints(Math.max(state.getProberPeakHitPoints(), proberHitPoints))
                    .proberId(state.getProberId());
        }
        return row;
    }

    private static HarassRow.HarassRowBuilder row(String squadId, AirHarassState state, HarassRow.Event event,
                                                  int now) {
        return HarassRow.builder()
                .frame(now)
                .squadId(squadId)
                .event(event)
                .phase(state.getPhase())
                .base(state.getTargetBase() == null ? null : state.getTargetBase().getCenter())
                .strikePoint(state.getStrikePoint())
                .targetKind(targetKind(state.getTargetBase() != null, state.targetsExposed()))
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
        private final List<AirHarassTargeting.Contact> remembered = new ArrayList<>();
        private final Map<Integer, Unit> units = new HashMap<>();
        private final List<AirHarassTargeting.AirThreat> threats = new ArrayList<>();

        /**
         * @return the visible contacts and the remembered structures, the enemies an exposed target is grouped from
         */
        private List<AirHarassTargeting.Contact> candidates() {
            List<AirHarassTargeting.Contact> candidates = new ArrayList<>(contacts);
            candidates.addAll(remembered);
            return candidates;
        }
    }
}
