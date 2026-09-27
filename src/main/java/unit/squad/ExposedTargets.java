package unit.squad;

import bwapi.Position;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Harass targets away from a base's heat: groups of enemy units and buildings a Mutalisk flock can raid without
 * standing in more anti-air than it tolerates.
 *
 * <p>Every contact the harass would attack ({@link AirHarassTargeting#tier} is not null), and every Missile Turret
 * whose zone the flock does not avoid ({@link AirHarassTargeting#turretTaken}), joins the group of the first contact,
 * in id order, within {@link #GROUP_RADIUS} of it. A group is exposed when the anti-air that can fire within
 * {@link AirHarassEvaluator#STRIKE_RADIUS} of its anchor, less a lone anti-air unit in the group the flock kills
 * quickly, is within the tolerance. A Turret the flock does not kill quickly still counts there, so a Turret alone is an exposed
 * target exactly when the flock tolerates the anti-air around it, its own included. The constants are tuning values,
 * not Brood War facts.
 */
public final class ExposedTargets {

    /** Tuning value: pixels from a group's first contact within which another contact joins the group. */
    static final int GROUP_RADIUS = 192;
    /**
     * Tuning value: pixels around an exposed target's anchor within which a harassing Mutalisk seeks out enemies,
     * and within which the target is still followed from one decision tick to the next.
     */
    static final int SEEK_RADIUS = 320;
    /** Tuning value: pixels of flight that halve a group's value when choosing among exposed groups. */
    static final double DISTANCE_SCALE = 1024;
    /** Tuning value: frames an exposed target a harass gave up on is not nominated again. */
    static final int RETRY_FRAMES = 1440;

    private ExposedTargets() {
    }

    /**
     * A group of contacts the harass would attack.
     */
    @Getter
    public static final class Group {
        private final Position anchor;
        private final int members;
        private final double value;
        private final Set<Integer> isolatedAntiAirIds;

        Group(Position anchor, int members, double value, Set<Integer> isolatedAntiAirIds) {
            this.anchor = anchor;
            this.members = members;
            this.value = value;
            this.isolatedAntiAirIds = isolatedAntiAirIds;
        }
    }

    /**
     * Groups the contacts the harass would attack. A group's anchor is the centroid of its members and its value
     * the sum of their tiers, counted from 1 for {@link AirHarassTargeting.Tier#OTHER} up to
     * {@link AirHarassTargeting.Tier#WORKER}; a Turret taken on joins in the isolated anti-air tier.
     *
     * @param contacts enemies a Mutalisk could attack, visible or remembered
     * @param flockSize Mutalisks in the squad
     * @param avoided the zones the flock avoids, see {@link AirHarassTargeting#avoided}
     * @return the groups
     */
    public static List<Group> groups(Collection<AirHarassTargeting.Contact> contacts, int flockSize,
                                     Collection<AirHarassTargeting.AirThreat> avoided) {
        AirHarassTargeting.Situation situation = AirHarassTargeting.Situation.builder()
                .flockSize(flockSize)
                .avoided(new ArrayList<>(avoided))
                .turretsTaken(true)
                .build();
        List<AirHarassTargeting.Contact> sorted = new ArrayList<>();
        for (AirHarassTargeting.Contact contact : contacts) {
            if (AirHarassTargeting.tier(contact, situation) != null) {
                sorted.add(contact);
            }
        }
        sorted.sort(Comparator.comparingInt(AirHarassTargeting.Contact::getId));
        Set<Integer> grouped = new HashSet<>();
        List<Group> groups = new ArrayList<>();
        for (AirHarassTargeting.Contact seed : sorted) {
            if (grouped.contains(seed.getId())) {
                continue;
            }
            int members = 0;
            long x = 0;
            long y = 0;
            double value = 0;
            Set<Integer> isolatedAntiAir = new HashSet<>();
            for (AirHarassTargeting.Contact contact : sorted) {
                if (grouped.contains(contact.getId())
                        || contact.getPosition().getDistance(seed.getPosition()) > GROUP_RADIUS) {
                    continue;
                }
                grouped.add(contact.getId());
                AirHarassTargeting.Tier tier = AirHarassTargeting.tier(contact, situation);
                members++;
                x += contact.getPosition().getX();
                y += contact.getPosition().getY();
                value += tier.ordinal() + 1;
                if (AirHarassTargeting.tier(contact, flockSize) == AirHarassTargeting.Tier.ISOLATED_AA) {
                    isolatedAntiAir.add(contact.getId());
                }
            }
            groups.add(new Group(new Position((int) (x / members), (int) (y / members)), members, value,
                    isolatedAntiAir));
        }
        return groups;
    }

    /**
     * Anti-air that can fire within {@link AirHarassEvaluator#STRIKE_RADIUS} of a group's anchor. A group holding a
     * single anti-air unit the flock kills quickly does not count that unit, since the flock kills it rather than
     * avoids it; a group holding two or more counts them all, since each covers the others.
     *
     * @param group the group
     * @param threats every known anti-air threat
     * @return summed strength
     */
    public static double defenseAt(Group group, Collection<AirHarassTargeting.AirThreat> threats) {
        Set<Integer> discounted = group.getIsolatedAntiAirIds().size() == 1
                ? group.getIsolatedAntiAirIds()
                : Collections.emptySet();
        List<AirHarassTargeting.AirThreat> others = new ArrayList<>();
        for (AirHarassTargeting.AirThreat threat : threats) {
            if (!discounted.contains(threat.getId())) {
                others.add(threat);
            }
        }
        return AirHarassTargeting.defenseAt(others, group.getAnchor(), AirHarassEvaluator.STRIKE_RADIUS);
    }

    /**
     * Whether a group is exposed to the flock.
     *
     * @param group the group
     * @param threats every known anti-air threat
     * @param tolerance anti-air strength the flock accepts
     * @return true when {@link #defenseAt} is within the tolerance
     */
    public static boolean exposed(Group group, Collection<AirHarassTargeting.AirThreat> threats, double tolerance) {
        return defenseAt(group, threats) <= tolerance;
    }

    /**
     * How much a group is worth flying to: its value, halved at {@link #DISTANCE_SCALE} pixels from the flock.
     *
     * @param group the group
     * @param from the flock's center, or null when unknown
     * @return the score
     */
    public static double score(Group group, Position from) {
        double distance = from == null ? 0 : from.getDistance(group.getAnchor());
        return group.getValue() / (1 + distance / DISTANCE_SCALE);
    }

    /**
     * The exposed group with the highest {@link #score}.
     *
     * @param groups candidate groups
     * @param threats every known anti-air threat
     * @param tolerance anti-air strength the flock accepts
     * @param from the flock's center
     * @return the group, or null when none is exposed
     */
    public static Group choose(Collection<Group> groups, Collection<AirHarassTargeting.AirThreat> threats,
                               double tolerance, Position from) {
        Group best = null;
        double bestScore = -1;
        for (Group group : groups) {
            if (!exposed(group, threats, tolerance)) {
                continue;
            }
            double score = score(group, from);
            if (score > bestScore) {
                best = group;
                bestScore = score;
            }
        }
        return best;
    }

    /**
     * The exposed targets harasses have given up on lately, game-wide: a group whose anchor lies within
     * {@link #SEEK_RADIUS} of a target given up within the last {@link #RETRY_FRAMES} frames is not nominated again,
     * so a flock does not cycle on a group it could not hit or on a remembered structure that is no longer there.
     */
    public static final class Memory {
        private final List<Position> anchors = new ArrayList<>();
        private final List<Integer> frames = new ArrayList<>();

        /**
         * Records an exposed target a harass gave up on.
         *
         * @param anchor the target's last anchor
         * @param frame current frame
         */
        public void record(Position anchor, int frame) {
            if (anchor == null) {
                return;
            }
            anchors.add(anchor);
            frames.add(frame);
        }

        /**
         * Whether a group may be nominated.
         *
         * @param anchor the group's anchor
         * @param now current frame
         * @return false within {@link #SEEK_RADIUS} of a target given up within {@link #RETRY_FRAMES}
         */
        public boolean admits(Position anchor, int now) {
            for (int i = anchors.size() - 1; i >= 0; i--) {
                if (now - frames.get(i) >= RETRY_FRAMES) {
                    anchors.remove(i);
                    frames.remove(i);
                } else if (anchors.get(i).getDistance(anchor) <= SEEK_RADIUS) {
                    return false;
                }
            }
            return true;
        }

        /**
         * @param groups candidate groups
         * @param now current frame
         * @return the groups {@link #admits} lets through
         */
        public List<Group> admitted(Collection<Group> groups, int now) {
            List<Group> admitted = new ArrayList<>();
            for (Group group : groups) {
                if (admits(group.getAnchor(), now)) {
                    admitted.add(group);
                }
            }
            return admitted;
        }
    }

    /**
     * The group a harass on an exposed target keeps following: the group whose anchor is nearest the target's last
     * anchor, within {@link #SEEK_RADIUS} of it.
     *
     * @param groups the groups this tick
     * @param anchor the target's last anchor
     * @return the group, or null when the target is gone
     */
    public static Group follow(Collection<Group> groups, Position anchor) {
        Group nearest = null;
        double nearestDistance = SEEK_RADIUS;
        for (Group group : groups) {
            double distance = group.getAnchor().getDistance(anchor);
            if (distance <= nearestDistance) {
                nearest = group;
                nearestDistance = distance;
            }
        }
        return nearest;
    }
}
