package unit.squad;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * Keeps neighbouring ground squads that fight side by side on one fight/retreat decision.
 *
 * <p>The sim counts a ground squad's neighbours inside the reinforcement radius as support, yet each squad applies
 * its own verdict, so two such squads can hold FIGHT and RETREAT on the same frame. After every squad has decided,
 * {@link #reconcile} pairs the ground squads that hold FIGHT against ones that hold RETREAT and turns one of the pair
 * to the other's status. A fight lock or a retreat lock held by exactly one of them decides the pair, and otherwise
 * the squad with more supply does, a tie going to RETREAT. The squad that turns takes the matching lock, so its own
 * verdict does not turn it back on the next frame. Before they decide, {@link #neighbourFightLock} lets one squad's
 * active fight lock hold a FIGHT neighbour.
 */
final class SharedVerdict {

    private static final int MAX_PASSES = 3;

    private SharedVerdict() {
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
     * Whether the FIGHT side of an opposed pair wins it.
     *
     * @param fightLocked whether the FIGHT squad's fight lock is active
     * @param retreatLocked whether the RETREAT squad's retreat lock is active
     * @param fightSupply supply of the FIGHT squad
     * @param retreatSupply supply of the RETREAT squad
     * @return true when the pair fights, false when it retreats
     */
    static boolean fightWins(boolean fightLocked, boolean retreatLocked, int fightSupply, int retreatSupply) {
        if (fightLocked != retreatLocked) {
            return fightLocked;
        }
        return fightSupply > retreatSupply;
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
    static boolean neighbourFightLock(Squad squad, List<Squad> squads, BiPredicate<Squad, Squad> near, int now) {
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
     * Turns one squad of every opposed pair to the status of the other until no pair is opposed, or the pass limit is
     * reached. A pair with a squad in {@code frozen} is left as it is.
     *
     * @param squads every fight squad
     * @param frozen squads whose status must not change
     * @param near whether two squads are within the support radius
     * @param now current frame
     * @param onFight called for a squad turned to FIGHT, after its status and lock are set
     * @param onRetreat called for a squad turned to RETREAT, after its status and lock are set
     * @return the squads that were turned
     */
    static List<Squad> reconcile(List<Squad> squads, List<Squad> frozen, BiPredicate<Squad, Squad> near, int now,
                                 Consumer<Squad> onFight, Consumer<Squad> onRetreat) {
        List<Squad> turned = new ArrayList<>();
        for (int pass = 0; pass < MAX_PASSES; pass++) {
            boolean changed = false;
            for (List<Squad> pair : opposedPairs(squads, near)) {
                if (frozen.contains(pair.get(0)) || frozen.contains(pair.get(1))) {
                    continue;
                }
                Squad fighter = pair.get(0).getStatus() == SquadStatus.FIGHT ? pair.get(0) : pair.get(1);
                Squad retreater = fighter == pair.get(0) ? pair.get(1) : pair.get(0);
                if (fighter.getStatus() != SquadStatus.FIGHT || retreater.getStatus() != SquadStatus.RETREAT) {
                    continue;
                }
                if (fightWins(fighter.isFightLocked(now), retreater.isRetreatLocked(now), fighter.getSupply(),
                        retreater.getSupply())) {
                    retreater.setStatus(SquadStatus.FIGHT);
                    retreater.clearRetreatLock();
                    retreater.startFightLock(now);
                    onFight.accept(retreater);
                    turned.add(retreater);
                } else {
                    fighter.setStatus(SquadStatus.RETREAT);
                    fighter.startRetreatLock(now);
                    onRetreat.accept(fighter);
                    turned.add(fighter);
                }
                changed = true;
            }
            if (!changed) {
                break;
            }
        }
        return turned;
    }
}
