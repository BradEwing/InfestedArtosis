package unit.squad;

import bwapi.Game;
import bwapi.Position;
import bwapi.Unit;
import bwapi.UnitType;
import info.GameState;
import info.tracking.ObservedUnit;
import telemetry.AirReinforcementRow;
import telemetry.AirReinforcementTelemetry;
import unit.managed.ManagedUnit;
import unit.managed.UnitRole;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Sends a rallying air squad to the nearest active air squad it may merge with, along a path that stays outside
 * known anti-air, and says when it has arrived. The decisions are {@link AirReinforcement}'s; this class reads the
 * game for them, keeps each reinforcing squad's route between searches and steers its members.
 *
 * <p>Known anti-air is every enemy that can fire on a flyer, as {@link AirHarassTargeting#isAntiAir} reads it:
 * structures wherever they were last seen, mobile units only while their observation is fresh.
 */
public class AirReinforcer {

    /**
     * What a rallying squad does this frame.
     */
    public enum Outcome {
        ROUTED,
        ARRIVED,
        REFUSED
    }

    private final Game game;
    private final GameState gameState;
    private final Map<String, Plan> plans = new HashMap<>();

    public AirReinforcer(Game game, GameState gameState) {
        this.game = game;
        this.gameState = gameState;
    }

    private static final class Plan {
        private final Squad target;
        private final List<Position> path;
        private final int frame;

        private Plan(Squad target, List<Position> path, int frame) {
            this.target = target;
            this.path = path;
            this.frame = frame;
        }
    }

    /**
     * Whether an air squad is flying to reinforce another.
     *
     * @param squad the squad
     * @return true while it holds a route
     */
    public boolean isReinforcing(Squad squad) {
        return plans.containsKey(squad.getId());
    }

    /**
     * The squad a reinforcing squad is flying to.
     *
     * @param squad the reinforcing squad
     * @return its target, or null when it holds no route
     */
    public Squad targetOf(Squad squad) {
        Plan plan = plans.get(squad.getId());
        return plan == null ? null : plan.target;
    }

    /**
     * Drops the route of a squad that stopped reinforcing.
     *
     * @param squad the squad
     */
    public void forget(Squad squad) {
        plans.remove(squad.getId());
    }

    /**
     * Drops the routes of squads no longer in the fight squads.
     *
     * @param squads fight squads
     */
    public void prune(Collection<Squad> squads) {
        List<String> ids = new ArrayList<>();
        for (Squad squad : squads) {
            ids.add(squad.getId());
        }
        plans.keySet().retainAll(ids);
    }

    /**
     * Whether any air squad a squad may merge with is active.
     *
     * @param squad the rallying air squad
     * @param squads fight squads
     * @return true when one is
     */
    public static boolean hasActiveAirSquad(Squad squad, Collection<Squad> squads) {
        for (Squad other : squads) {
            if (reinforces(squad, other) && AirReinforcement.isActive(other.getStatus())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Runs one frame of a rallying air squad while an air squad it may merge with is active. The route is searched
     * again every {@link AirReinforcement#REPLAN_FRAMES} frames, or at once when its target stops being active. A
     * routed squad's members take the RALLY role toward the next waypoint.
     *
     * @param squad the rallying air squad
     * @param squads fight squads
     * @param now current frame
     * @return ARRIVED when the squad has reached its target, {@link #targetOf}; the caller hands its members
     *     over and calls {@link #forget}, ROUTED while it flies there, REFUSED when no active squad has a safe path
     */
    public Outcome reinforce(Squad squad, Collection<Squad> squads, int now) {
        Plan plan = plans.get(squad.getId());
        boolean valid = plan != null && validTarget(squad, plan.target, squads);
        if (!valid || now - plan.frame >= AirReinforcement.REPLAN_FRAMES) {
            Plan replanned = replan(squad, squads, now);
            if (replanned == null) {
                plans.remove(squad.getId());
                AirReinforcementTelemetry.row(row(squad, AirReinforcementRow.Event.REFUSED, null, now).build());
                return Outcome.REFUSED;
            }
            if (plan == null || plan.target != replanned.target) {
                AirReinforcementTelemetry.row(row(squad, AirReinforcementRow.Event.ROUTE, replanned.target, now)
                        .waypoints(replanned.path.size())
                        .pathLength(pathLength(squad.getCenter(), replanned.path))
                        .build());
            }
            plan = replanned;
            plans.put(squad.getId(), plan);
        }
        if (AirReinforcement.arrived(squad.distance(plan.target))) {
            AirReinforcementTelemetry.row(row(squad, AirReinforcementRow.Event.JOIN, plan.target, now).build());
            return Outcome.ARRIVED;
        }
        Position point = AirReinforcement.steer(squad.getCenter(), plan.path);
        for (ManagedUnit member : squad.getMembers()) {
            member.setRallyPoint(point);
            member.setRole(UnitRole.RALLY);
        }
        return Outcome.ROUTED;
    }

    private Plan replan(Squad squad, Collection<Squad> squads, int now) {
        List<AirReinforcement.Candidate<Squad>> candidates = new ArrayList<>();
        for (Squad other : squads) {
            if (validTarget(squad, other, squads)) {
                candidates.add(new AirReinforcement.Candidate<>(other, other.getStatus(), other.getCenter()));
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        AirReinforcement.Route<Squad> route = AirReinforcement.choose(squad.getCenter(), candidates, threats(now),
                inMap());
        return route == null ? null : new Plan(route.getTarget(), route.getPath(), now);
    }

    private static boolean validTarget(Squad squad, Squad target, Collection<Squad> squads) {
        return squads.contains(target) && reinforces(squad, target) && AirReinforcement.isActive(target.getStatus());
    }

    private static boolean reinforces(Squad squad, Squad other) {
        return other != squad && other.isAirSquad() && other.size() > 0
                && AirReinforcement.mayReinforce(other.getStatus(), squad.getComposition())
                && SquadManager.mayMergeAirSquads(SquadManager.holdsOnlyScourge(squad.getComposition()),
                        SquadManager.holdsOnlyScourge(other.getComposition()));
    }

    private Predicate<Position> inMap() {
        int width = game.mapWidth() * 32;
        int height = game.mapHeight() * 32;
        return point -> point.getX() >= 0 && point.getY() >= 0 && point.getX() < width && point.getY() < height;
    }

    private List<AirHarassTargeting.AirThreat> threats(int now) {
        List<AirHarassTargeting.AirThreat> threats = new ArrayList<>();
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
            threats.add(AirHarassTargeting.AirThreat.of(unit.getID(), type, position, range));
        }
        return threats;
    }

    private static double pathLength(Position from, List<Position> path) {
        double length = 0;
        Position at = from;
        for (Position point : path) {
            length += at.getDistance(point);
            at = point;
        }
        return length;
    }

    private static AirReinforcementRow.AirReinforcementRowBuilder row(Squad squad, AirReinforcementRow.Event event,
                                                                     Squad target, int now) {
        return AirReinforcementRow.builder()
                .frame(now)
                .event(event)
                .squadId(squad.getId())
                .squadStatus(squad.getStatus())
                .targetSquadId(target == null ? null : target.getId())
                .targetStatus(target == null ? null : target.getStatus())
                .center(squad.getCenter())
                .targetCenter(target == null ? null : target.getCenter())
                .mutas(squad.getComposition().getOrDefault(UnitType.Zerg_Mutalisk, 0));
    }
}
