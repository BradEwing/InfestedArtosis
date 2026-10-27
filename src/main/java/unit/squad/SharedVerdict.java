package unit.squad;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Keeps neighbouring ground squads that fight side by side on one fight/retreat decision.
 *
 * <p>The sim counts a ground squad's neighbours inside the reinforcement radius as support, yet each squad applies
 * its own verdict, so two such squads can hold FIGHT and RETREAT on the same frame. After every squad has decided,
 * {@link #reconcile} groups the ground squads holding FIGHT or RETREAT into connected groups of squads within the
 * radius of one another, and gives each group that holds both statuses one status, turning each squad at most once.
 * A frozen squad, one whose status is held by something other than its verdict, keeps its status and pulls the group
 * to it. Otherwise the side with more supply wins, a side holding a lock counting double and a tie going to RETREAT.
 * A squad that turns adopts the remaining lock of the winning side, so its own verdict does not undo the turn at
 * once. Before they decide, {@link #neighbourFightLock} lets one squad's active fight lock hold a FIGHT neighbour.
 */
final class SharedVerdict {

    private static final int LOCK_WEIGHT = 2;

    private SharedVerdict() {
    }

    /**
     * A squad the shared verdict turned, with the status it left and the one it took.
     */
    static final class Turn {
        final Squad squad;
        final SquadStatus from;
        final SquadStatus to;

        Turn(Squad squad, SquadStatus from, SquadStatus to) {
            this.squad = squad;
            this.from = from;
            this.to = to;
        }
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
     * The squads the reconcile considers: those not held at a standoff point this frame. A held squad reports FIGHT
     * while it gathers away from the enemy, so it adds no FIGHT supply and must not be turned toward the enemy.
     *
     * @param squads every fight squad
     * @param held whether a squad is held at its standoff point this frame
     * @return the squads that are not held, in order
     */
    static List<Squad> withoutHeld(List<Squad> squads, Predicate<Squad> held) {
        List<Squad> kept = new ArrayList<>();
        for (Squad squad : squads) {
            if (!held.test(squad)) {
                kept.add(squad);
            }
        }
        return kept;
    }

    /**
     * Whether a FIGHT squad keeps its status in the reconcile because it cannot escape the enemy's speed.
     *
     * @param relativeSpeed {@link config.Config#relativeSpeed}
     * @param status the squad's status
     * @param escapeBlocked the squad's escape reading this frame, 1 when it cannot escape
     * @return true when the squad is pinned, see the pinned parameter of {@link #reconcile}
     */
    static boolean escapeBlockedFight(boolean relativeSpeed, SquadStatus status, int escapeBlocked) {
        return relativeSpeed && status == SquadStatus.FIGHT && escapeBlocked == 1;
    }

    /**
     * Lists the pairs of ground squads that hold opposite statuses within the radius.
     *
     * @param squads every fight squad
     * @param near whether two squads are within the radius
     * @return the pairs, each as a two-element list
     */
    static List<List<Squad>> opposedPairs(List<Squad> squads, BiPredicate<Squad, Squad> near) {
        List<List<Squad>> pairs = new ArrayList<>();
        for (int i = 0; i < squads.size(); i++) {
            Squad first = squads.get(i);
            if (!first.isGroundSquad()) {
                continue;
            }
            for (int j = i + 1; j < squads.size(); j++) {
                Squad second = squads.get(j);
                if (second.isGroundSquad() && opposed(first.getStatus(), second.getStatus())
                        && near.test(first, second)) {
                    List<Squad> pair = new ArrayList<>();
                    pair.add(first);
                    pair.add(second);
                    pairs.add(pair);
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
        return opposedPairs(squads, near).size();
    }

    /**
     * Gives every connected group of neighbouring ground squads that holds both FIGHT and RETREAT one status.
     *
     * @param squads every fight squad
     * @param frozen squads whose status must not change; they pull their group to their own status, and a group
     *     whose frozen squads disagree is left as it is
     * @param near whether two squads are within the support radius
     * @param now current frame
     * @param onFight called for a squad turned to FIGHT, after its status and locks are set
     * @param onRetreat called for a squad turned to RETREAT, after its status and locks are set
     * @return the squads that were turned
     */
    static List<Turn> reconcile(List<Squad> squads, Set<Squad> frozen, BiPredicate<Squad, Squad> near, int now,
                                Consumer<Squad> onFight, Consumer<Squad> onRetreat) {
        return reconcile(squads, frozen, Collections.emptySet(), near, now, onFight, onRetreat);
    }

    /**
     * Same as {@link #reconcile(List, Set, BiPredicate, int, Consumer, Consumer)}, with squads that are pinned.
     *
     * @param squads every fight squad
     * @param frozen squads whose status must not change; they pull their group to their own status
     * @param pinned FIGHT squads that are never turned but do not pull their group: each counts its supply on the
     *     FIGHT side as a held fight
     * @param near whether two squads are within the support radius
     * @param now current frame
     * @param onFight called for a squad turned to FIGHT, after its status and locks are set
     * @param onRetreat called for a squad turned to RETREAT, after its status and locks are set
     * @return the squads that were turned
     */
    static List<Turn> reconcile(List<Squad> squads, Set<Squad> frozen, Set<Squad> pinned,
                                BiPredicate<Squad, Squad> near, int now, Consumer<Squad> onFight,
                                Consumer<Squad> onRetreat) {
        List<Turn> turns = new ArrayList<>();
        Set<Squad> seen = new HashSet<>();
        for (Squad start : squads) {
            if (seen.contains(start) || !decided(start)) {
                continue;
            }
            List<Squad> group = group(start, squads, near, seen);
            SquadStatus target = target(group, frozen, pinned, now);
            if (target == null) {
                continue;
            }
            int fightUntil = 0;
            int retreatUntil = 0;
            for (Squad member : group) {
                if (member.getStatus() == SquadStatus.FIGHT) {
                    fightUntil = Math.max(fightUntil, member.getFightLockedUntilFrame());
                } else {
                    retreatUntil = Math.max(retreatUntil, member.getRetreatLockedUntilFrame());
                }
            }
            for (Squad member : group) {
                if (frozen.contains(member) || pinned.contains(member) || member.getStatus() == target) {
                    continue;
                }
                SquadStatus from = member.getStatus();
                member.setStatus(target);
                if (target == SquadStatus.FIGHT) {
                    member.clearRetreatLock();
                    member.adoptFightLock(fightUntil);
                    onFight.accept(member);
                } else {
                    member.clearFightLock();
                    member.adoptRetreatLock(retreatUntil);
                    onRetreat.accept(member);
                }
                turns.add(new Turn(member, from, target));
            }
        }
        return turns;
    }

    private static boolean decided(Squad squad) {
        return squad.isGroundSquad()
                && (squad.getStatus() == SquadStatus.FIGHT || squad.getStatus() == SquadStatus.RETREAT);
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

    private static SquadStatus target(List<Squad> group, Set<Squad> frozen, Set<Squad> pinned, int now) {
        int fightSupply = 0;
        int retreatSupply = 0;
        boolean fightHeld = false;
        boolean retreatHeld = false;
        boolean frozenFight = false;
        boolean frozenRetreat = false;
        for (Squad member : group) {
            boolean fighting = member.getStatus() == SquadStatus.FIGHT;
            if (fighting) {
                fightSupply += member.getSupply();
                fightHeld |= member.isFightLocked(now) || member.isTurnBackCommitted(now)
                        || pinned.contains(member);
            } else {
                retreatSupply += member.getSupply();
                retreatHeld |= member.isRetreatLocked(now);
            }
            if (frozen.contains(member)) {
                frozenFight |= fighting;
                frozenRetreat |= !fighting;
            }
        }
        boolean hasFight = false;
        boolean hasRetreat = false;
        for (Squad member : group) {
            hasFight |= member.getStatus() == SquadStatus.FIGHT;
            hasRetreat |= member.getStatus() == SquadStatus.RETREAT;
        }
        if (!hasFight || !hasRetreat || frozenFight && frozenRetreat) {
            return null;
        }
        if (frozenFight || frozenRetreat) {
            return frozenFight ? SquadStatus.FIGHT : SquadStatus.RETREAT;
        }
        return fightWins(fightSupply, fightHeld, retreatSupply, retreatHeld) ? SquadStatus.FIGHT
                : SquadStatus.RETREAT;
    }
}
