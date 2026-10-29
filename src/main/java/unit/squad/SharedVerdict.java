package unit.squad;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * Keeps neighbouring ground squads that fight side by side on one fight/retreat decision.
 *
 * <p>The sim counts a ground squad's neighbours inside the reinforcement radius as support, yet each squad applies
 * its own verdict, so two such squads can hold FIGHT and RETREAT on the same frame. After every squad has decided,
 * {@link #reconcile} groups the ground squads holding FIGHT or RETREAT into connected groups of squads within the
 * radius of one another, and gives each group that holds both statuses one status, turning each squad at most once.
 * A frozen squad, one whose status is held by something other than its verdict, keeps its status and pulls the group
 * to it; a group whose frozen squads disagree is left as it is. Otherwise the side with more supply wins, a side
 * holding a lock (a fight lock or a turn-back commit on the FIGHT side, a retreat lock on the RETREAT side) counting
 * double and a tie going to RETREAT.
 *
 * <p>A squad turned to FIGHT adopts the remaining fight lock of the FIGHT side, or a fresh fight lock of its own
 * when that side holds none, and drops its retreat lock. A squad turned to RETREAT adopts the remaining retreat lock
 * and blind turn-back gate of the RETREAT side, or a fresh retreat lock when that side holds none, and drops its
 * fight lock. Either way the turned squad's own verdict on the next frames is held on the new status by a lock, so it
 * is not turned again on every frame.
 *
 * <p>A RETREAT squad is never turned to FIGHT when its own read this frame was a measured RETREAT below its retreat
 * threshold, when it still holds a fight lock (it left a fight its lock no longer held), or when its blind turn-back
 * gate stands and nothing on the FIGHT side holds a lock, a commit or a frozen fight. The first keeps a squad that is
 * losing free to leave on the frame it reads the loss; the second stops a squad that broke an adopted lock from being
 * turned straight back before that lock has run out; the third keeps a turn back out of a measured retreat waiting
 * for a measured reason to fight, as the gate does for the squad's own verdict.
 *
 * <p>Before they decide, {@link #neighbourFightLock} lets one squad's active fight lock hold a FIGHT neighbour.
 */
final class SharedVerdict {

    private static final int LOCK_WEIGHT = 2;

    /**
     * Why a squad of an opposed group kept its status after the reconcile, or why the reconcile did not run.
     */
    enum Exclusion {
        NONE,
        SWITCH_OFF,
        STORM,
        FROZEN,
        FROZEN_SPLIT,
        BELOW_RETREAT,
        LOCK_BROKEN,
        BLIND_GATE
    }

    /**
     * What decided the status an opposed group took: a frozen squad, a lock that turned a supply comparison the
     * other way, or supply alone.
     */
    enum Basis {
        FROZEN,
        LOCK,
        SUPPLY
    }

    private SharedVerdict() {
    }

    /**
     * A squad the shared verdict turned, with the status it left, the one it took, what decided its group's status,
     * and the squad that stood for the winning side: a frozen squad on a FROZEN basis, otherwise the side's member
     * with the most supply.
     */
    static final class Turn {
        final Squad squad;
        final SquadStatus from;
        final SquadStatus to;
        final Basis basis;
        final Squad decider;

        Turn(Squad squad, SquadStatus from, SquadStatus to, Basis basis, Squad decider) {
            this.squad = squad;
            this.from = from;
            this.to = to;
            this.basis = basis;
            this.decider = decider;
        }
    }

    /**
     * Two ground squads within the radius that hold FIGHT and RETREAT, with why the reconcile left them so.
     */
    static final class Pair {
        final Squad first;
        final Squad second;
        final Exclusion exclusion;

        Pair(Squad first, Squad second, Exclusion exclusion) {
            this.first = first;
            this.second = second;
            this.exclusion = exclusion;
        }
    }

    /**
     * The turns one reconcile made, and the squads of opposed groups it left on their status with the reason.
     */
    static final class Outcome {
        final List<Turn> turns;
        final Map<Squad, Exclusion> declined;

        Outcome(List<Turn> turns, Map<Squad, Exclusion> declined) {
            this.turns = turns;
            this.declined = declined;
        }
    }

    private static final class Verdict {
        SquadStatus target;
        Basis basis;
        Squad decider;
        boolean fightSideHeld;
        int fightUntil;
        int retreatUntil;
        int gateUntil;
    }

    /**
     * Whether a pair holds opposite decided statuses.
     *
     * @param first status of one squad
     * @param second status of the other
     * @return true when one is FIGHT and the other RETREAT
     */
    static boolean opposed(SquadStatus first, SquadStatus second) {
        return first == SquadStatus.FIGHT && second == SquadStatus.RETREAT
                || first == SquadStatus.RETREAT && second == SquadStatus.FIGHT;
    }

    /**
     * Whether the FIGHT side of an opposed group wins it. A side's supply counts double while it holds a lock.
     *
     * @param fightSupply supply of the squads holding FIGHT
     * @param fightHeld whether one of them holds a fight lock or a turn-back commit
     * @param retreatSupply supply of the squads holding RETREAT
     * @param retreatHeld whether one of them holds a retreat lock
     * @return true when the group fights, false when it retreats
     */
    static boolean fightWins(int fightSupply, boolean fightHeld, int retreatSupply, boolean retreatHeld) {
        return fightSupply * (fightHeld ? LOCK_WEIGHT : 1) > retreatSupply * (retreatHeld ? LOCK_WEIGHT : 1);
    }

    /**
     * Whether a read is a RETREAT measured against a real enemy below the retreat threshold: the read on which a
     * squad leaves whatever holds it, and on which the shared verdict does not turn it to FIGHT.
     *
     * @param result this frame's combat sim verdict
     * @param enemyMeasured whether the sim measured a real enemy this frame
     * @param ratio the sim's overall strength ratio this frame
     * @param retreatThreshold the retreat threshold the sim judged the ratio against
     * @return true when the read is below the retreat threshold
     */
    static boolean belowRetreat(CombatSimulator.CombatResult result, boolean enemyMeasured, double ratio,
                                double retreatThreshold) {
        return result == CombatSimulator.CombatResult.RETREAT && enemyMeasured && ratio < retreatThreshold;
    }

    /**
     * Whether a FIGHT squad is held by a neighbour's fight lock: some other ground squad in FIGHT, near it, holds an
     * active fight lock.
     *
     * @param squad the squad about to decide
     * @param squads every fight squad
     * @param near whether two squads are within the support radius
     * @param now current frame
     * @return true when a neighbour's fight lock holds the squad
     */
    static boolean neighbourFightLock(Squad squad, Collection<Squad> squads, BiPredicate<Squad, Squad> near,
                                      int now) {
        if (!squad.isGroundSquad() || squad.getStatus() != SquadStatus.FIGHT) {
            return false;
        }
        for (Squad other : squads) {
            if (other != squad && other.isGroundSquad() && other.getStatus() == SquadStatus.FIGHT
                    && other.isFightLocked(now) && near.test(squad, other)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Lists the pairs of ground squads that hold opposite statuses within the radius, each with the reason the
     * reconcile left it: the reason of its RETREAT squad when that squad was declined, else that of its FIGHT squad,
     * else the fallback.
     *
     * @param squads every fight squad
     * @param near whether two squads are within the radius
     * @param declined squads the reconcile left on their status, with the reason
     * @param fallback reason of a pair neither of whose squads was declined
     * @return the pairs
     */
    static List<Pair> opposedPairs(List<Squad> squads, BiPredicate<Squad, Squad> near,
                                   Map<Squad, Exclusion> declined, Exclusion fallback) {
        List<Pair> pairs = new ArrayList<>();
        for (int i = 0; i < squads.size(); i++) {
            Squad first = squads.get(i);
            if (!first.isGroundSquad()) {
                continue;
            }
            for (int j = i + 1; j < squads.size(); j++) {
                Squad second = squads.get(j);
                if (second.isGroundSquad() && opposed(first.getStatus(), second.getStatus())
                        && near.test(first, second)) {
                    Squad retreater = first.getStatus() == SquadStatus.RETREAT ? first : second;
                    Squad fighter = retreater == first ? second : first;
                    Exclusion exclusion = declined.getOrDefault(retreater,
                            declined.getOrDefault(fighter, fallback));
                    pairs.add(new Pair(first, second, exclusion));
                }
            }
        }
        return pairs;
    }

    /**
     * Counts the pairs of ground squads that hold opposite statuses within the radius.
     *
     * @param squads every fight squad
     * @param near whether two squads are within the radius
     * @return the number of opposed pairs
     */
    static int countOpposed(List<Squad> squads, BiPredicate<Squad, Squad> near) {
        return opposedPairs(squads, near, Collections.emptyMap(), Exclusion.NONE).size();
    }

    /**
     * Gives every connected group of neighbouring ground squads that holds both FIGHT and RETREAT one status, see the
     * class description.
     *
     * @param squads every fight squad
     * @param frozen squads whose status must not change; they pull their group to their own status, and a group
     *     whose frozen squads disagree is left as it is
     * @param leaving squads whose own read this frame was a measured RETREAT below their retreat threshold, see
     *     {@link #belowRetreat}
     * @param near whether two squads are within the support radius
     * @param now current frame
     * @param onFight called for a squad turned to FIGHT, after its status and locks are set
     * @param onRetreat called for a squad turned to RETREAT, after its status and locks are set
     * @return the turns made and the squads left on their status
     */
    static Outcome reconcile(List<Squad> squads, Set<Squad> frozen, Set<Squad> leaving,
                             BiPredicate<Squad, Squad> near, int now, Consumer<Squad> onFight,
                             Consumer<Squad> onRetreat) {
        List<Turn> turns = new ArrayList<>();
        Map<Squad, Exclusion> declined = new HashMap<>();
        Set<Squad> seen = new HashSet<>();
        for (Squad start : squads) {
            if (seen.contains(start) || !decided(start)) {
                continue;
            }
            List<Squad> group = group(start, squads, near, seen);
            if (!mixed(group)) {
                continue;
            }
            Verdict verdict = verdict(group, frozen, now);
            if (verdict.target == null) {
                for (Squad member : group) {
                    declined.put(member, Exclusion.FROZEN_SPLIT);
                }
                continue;
            }
            for (Squad member : group) {
                if (member.getStatus() == verdict.target) {
                    continue;
                }
                Exclusion exclusion = verdict.target == SquadStatus.FIGHT
                        ? declinesFight(member, frozen, leaving, verdict.fightSideHeld, now)
                        : declinesRetreat(member, frozen);
                if (exclusion != Exclusion.NONE) {
                    declined.put(member, exclusion);
                    continue;
                }
                SquadStatus from = member.getStatus();
                if (verdict.target == SquadStatus.FIGHT) {
                    turnToFight(member, verdict.fightUntil, now);
                    onFight.accept(member);
                } else {
                    turnToRetreat(member, verdict.retreatUntil, verdict.gateUntil, now);
                    onRetreat.accept(member);
                }
                turns.add(new Turn(member, from, verdict.target, verdict.basis, verdict.decider));
            }
        }
        return new Outcome(turns, declined);
    }

    /**
     * Why a RETREAT squad of a group that fights is not turned to FIGHT, NONE when it is.
     *
     * @param member a RETREAT squad of the group
     * @param frozen squads whose status must not change
     * @param leaving squads whose own read this frame was below their retreat threshold
     * @param fightSideHeld whether the group's FIGHT side holds a fight lock, a turn-back commit or a frozen squad
     * @param now current frame
     * @return the reason the squad keeps RETREAT, or NONE
     */
    static Exclusion declinesFight(Squad member, Set<Squad> frozen, Set<Squad> leaving, boolean fightSideHeld,
                                   int now) {
        if (frozen.contains(member)) {
            return Exclusion.FROZEN;
        }
        if (leaving.contains(member)) {
            return Exclusion.BELOW_RETREAT;
        }
        if (member.isFightLocked(now)) {
            return Exclusion.LOCK_BROKEN;
        }
        if (member.isBlindTurnBackGated(now) && !fightSideHeld) {
            return Exclusion.BLIND_GATE;
        }
        return Exclusion.NONE;
    }

    private static Exclusion declinesRetreat(Squad member, Set<Squad> frozen) {
        return frozen.contains(member) ? Exclusion.FROZEN : Exclusion.NONE;
    }

    private static void turnToFight(Squad member, int fightUntil, int now) {
        member.setStatus(SquadStatus.FIGHT);
        member.clearRetreatLock();
        if (fightUntil > now) {
            member.adoptFightLock(now, fightUntil);
        } else {
            member.startFightLock(now);
        }
    }

    private static void turnToRetreat(Squad member, int retreatUntil, int gateUntil, int now) {
        member.setStatus(SquadStatus.RETREAT);
        member.clearFightLock();
        if (retreatUntil > now) {
            member.adoptRetreatLock(retreatUntil);
        } else {
            member.startRetreatLock(now);
        }
        if (gateUntil > now) {
            member.adoptBlindTurnBackGate(gateUntil);
        }
    }

    private static boolean decided(Squad squad) {
        return squad.isGroundSquad()
                && (squad.getStatus() == SquadStatus.FIGHT || squad.getStatus() == SquadStatus.RETREAT);
    }

    private static boolean mixed(List<Squad> group) {
        boolean hasFight = false;
        boolean hasRetreat = false;
        for (Squad member : group) {
            hasFight |= member.getStatus() == SquadStatus.FIGHT;
            hasRetreat |= member.getStatus() == SquadStatus.RETREAT;
        }
        return hasFight && hasRetreat;
    }

    private static List<Squad> group(Squad start, List<Squad> squads, BiPredicate<Squad, Squad> near,
                                     Set<Squad> seen) {
        List<Squad> group = new ArrayList<>();
        group.add(start);
        seen.add(start);
        for (int i = 0; i < group.size(); i++) {
            Squad current = group.get(i);
            for (Squad other : squads) {
                if (!seen.contains(other) && decided(other) && near.test(current, other)) {
                    seen.add(other);
                    group.add(other);
                }
            }
        }
        return group;
    }

    private static Verdict verdict(List<Squad> group, Set<Squad> frozen, int now) {
        Verdict verdict = new Verdict();
        int fightSupply = 0;
        int retreatSupply = 0;
        boolean fightHeld = false;
        boolean retreatHeld = false;
        Squad frozenFight = null;
        Squad frozenRetreat = null;
        Squad heaviestFight = null;
        Squad heaviestRetreat = null;
        for (Squad member : group) {
            boolean fighting = member.getStatus() == SquadStatus.FIGHT;
            if (fighting) {
                fightSupply += member.getSupply();
                fightHeld |= member.isFightLocked(now) || member.isTurnBackCommitted(now);
                verdict.fightUntil = Math.max(verdict.fightUntil, member.getFightLockedUntilFrame());
                heaviestFight = heavier(heaviestFight, member);
            } else {
                retreatSupply += member.getSupply();
                retreatHeld |= member.isRetreatLocked(now);
                verdict.retreatUntil = Math.max(verdict.retreatUntil, member.getRetreatLockedUntilFrame());
                verdict.gateUntil = Math.max(verdict.gateUntil, member.getBlindTurnBackGateUntilFrame());
                heaviestRetreat = heavier(heaviestRetreat, member);
            }
            if (frozen.contains(member)) {
                if (fighting && frozenFight == null) {
                    frozenFight = member;
                } else if (!fighting && frozenRetreat == null) {
                    frozenRetreat = member;
                }
            }
        }
        verdict.fightSideHeld = fightHeld || frozenFight != null;
        if (frozenFight != null && frozenRetreat != null) {
            return verdict;
        }
        if (frozenFight != null || frozenRetreat != null) {
            verdict.target = frozenFight != null ? SquadStatus.FIGHT : SquadStatus.RETREAT;
            verdict.basis = Basis.FROZEN;
            verdict.decider = frozenFight != null ? frozenFight : frozenRetreat;
            return verdict;
        }
        boolean fights = fightWins(fightSupply, fightHeld, retreatSupply, retreatHeld);
        verdict.target = fights ? SquadStatus.FIGHT : SquadStatus.RETREAT;
        verdict.basis = fights == fightWins(fightSupply, false, retreatSupply, false) ? Basis.SUPPLY : Basis.LOCK;
        verdict.decider = fights ? heaviestFight : heaviestRetreat;
        return verdict;
    }

    private static Squad heavier(Squad best, Squad candidate) {
        return best == null || candidate.getSupply() > best.getSupply() ? candidate : best;
    }
}
