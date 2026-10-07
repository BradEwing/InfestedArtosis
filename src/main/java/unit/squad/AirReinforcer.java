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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
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
    private final Set<String> heldTargets = new HashSet<>();
    private final Function<Collection<AirHarassTargeting.AirThreat>, List<AirHarassTargeting.AirThreat>> remembered;

    public AirReinforcer(Game game, GameState gameState) {
        this(game, gameState, known -> Collections.emptyList());
    }

    /**
     * @param remembered the anti-air remembered beyond the known threats, given the known threats; priced into every
     *     path as the anti-air the harass defense zones remember
     */
    public AirReinforcer(Game game, GameState gameState,
                         Function<Collection<AirHarassTargeting.AirThreat>, List<AirHarassTargeting.AirThreat>> remembered) {
        this.game = game;
        this.gameState = gameState;
        this.remembered = remembered;
    }

    private static final class Plan {
        private final Squad target;
        private final List<Position> path;
        private final int frame;
        private final int routedFrame;
        private final int zoneThreats;

        private Plan(Squad target, List<Position> path, int frame, int routedFrame, int zoneThreats) {
            this.target = target;
            this.path = path;
            this.frame = frame;
            this.routedFrame = routedFrame;
            this.zoneThreats = zoneThreats;
        }

        private Plan keepingRoute(int routed) {
            return new Plan(target, path, frame, routed, zoneThreats);
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
     * How many air squads are flying to a squad to reinforce it.
     *
     * @param target the squad they fly to
     * @return the count
     */
    public int inFlightTo(Squad target) {
        int count = 0;
        for (Plan plan : plans.values()) {
            if (plan.target == target) {
                count++;
            }
        }
        return count;
    }

    /**
     * Whether reinforcements flying to a squad hold its entry into a harass: any of them routed to it less than
     * {@link AirReinforcement#LINK_HOLD_FRAMES} ago, see {@link AirReinforcement#holdsEntry}. The first frame a squad
     * is held writes a HOLD row.
     *
     * @param target the squad about to enter
     * @param now current frame
     * @return true while the hold stands
     */
    public boolean holdsEntry(Squad target, int now) {
        boolean held = false;
        for (Plan plan : plans.values()) {
            if (plan.target == target && AirReinforcement.holdsEntry(plan.routedFrame, now)) {
                held = true;
                break;
            }
        }
        if (!held) {
            heldTargets.remove(target.getId());
        } else if (heldTargets.add(target.getId())) {
            AirReinforcementTelemetry.row(row(target, AirReinforcementRow.Event.HOLD, target, now)
                    .inFlight(inFlightTo(target))
                    .build());
        }
        return held;
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
        heldTargets.retainAll(ids);
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
            boolean newRoute = plan == null || plan.target != replanned.target;
            plan = newRoute ? replanned : replanned.keepingRoute(plan.routedFrame);
            plans.put(squad.getId(), plan);
            if (newRoute) {
                AirReinforcementTelemetry.row(row(squad, AirReinforcementRow.Event.ROUTE, plan.target, now)
                        .waypoints(plan.path.size())
                        .pathLength(pathLength(squad.getCenter(), plan.path))
                        .zoneThreats(plan.zoneThreats)
                        .detour(AirReinforcement.detour(squad.getCenter(), plan.path))
                        .inFlight(inFlightTo(plan.target))
                        .build());
            }
        }
        if (AirReinforcement.arrived(squad.distance(plan.target))) {
            AirReinforcementTelemetry.row(row(squad, AirReinforcementRow.Event.JOIN, plan.target, now)
                    .zoneThreats(plan.zoneThreats)
                    .inFlight(inFlightTo(plan.target))
                    .linkFrames(now - plan.routedFrame)
                    .build());
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
        List<AirHarassTargeting.AirThreat> threats = threats(now);
        List<AirHarassTargeting.AirThreat> zoned = remembered.apply(threats);
        List<AirHarassTargeting.AirThreat> priced = new ArrayList<>(threats);
        priced.addAll(zoned);
        AirReinforcement.Route<Squad> route = AirReinforcement.choose(squad.getCenter(), candidates, priced,
                inMap());
        return route == null ? null : new Plan(route.getTarget(), route.getPath(), now, now, zoned.size());
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
