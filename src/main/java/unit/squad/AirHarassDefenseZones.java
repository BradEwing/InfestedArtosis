package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The defense groups a flock turned away from, remembered until their ground has been seen clear.
 *
 * <p>A zone is recorded when a harass ends on newly seen anti-air that stood only at the flock: its center is the
 * trigger's position, its radius the trigger's reach plus {@link #PADDING}, and its members are the trigger and every
 * known anti-air unit within {@link #MEMBER_RADIUS} of it or covering it, see {@link AirHarassScouting#contributors}.
 * Each member is priced as a threat at its last known position and reach, on top of the anti-air currently known, so
 * the next entry and the avoided zones keep the whole defense in view instead of meeting its next unit as new.
 *
 * <p>A member leaves its zone when its unit is reported dead, when it is known again at a position farther than the
 * zone's radius plus {@link #MEMBER_RADIUS} from the zone's center, or when the zone's center is in sight and the
 * unit is not among the anti-air known now. An empty zone is dropped, and so is one older than
 * {@link #MAX_AGE_FRAMES}, since a zone no one looks at again must not shut its ground for the rest of the game.
 */
public final class AirHarassDefenseZones {

    /** Tuning value: pixels around the trigger within which every known anti-air unit joins its zone. */
    static final int MEMBER_RADIUS = 384;
    /** Tuning value: pixels added to the trigger's reach for the zone's radius. */
    static final int PADDING = 64;
    /** Tuning value: frames after which a zone that was never seen clear is dropped, ten minutes of game time. */
    static final int MAX_AGE_FRAMES = 14400;

    private final List<Zone> zones = new ArrayList<>();

    /**
     * One remembered defense group.
     */
    @Getter
    public static final class Zone {
        private final Position center;
        private int radius;
        private int recordedFrame;
        private final Map<Integer, AirHarassTargeting.AirThreat> members = new LinkedHashMap<>();

        Zone(Position center, int radius, int recordedFrame) {
            this.center = center;
            this.radius = radius;
            this.recordedFrame = recordedFrame;
        }

        /**
         * @return how many anti-air units the zone remembers
         */
        public int size() {
            return members.size();
        }
    }

    /**
     * Records the defense group a flock turned away from. A zone already centered within {@link #MEMBER_RADIUS} of the
     * trigger takes the new members and the new radius and starts its age afresh, instead of a second zone being
     * opened over the same ground.
     *
     * @param trigger the anti-air unit whose sighting ended the harass
     * @param threats every known anti-air threat
     * @param now current frame
     * @return the zone, recorded or extended
     */
    public Zone record(AirHarassTargeting.AirThreat trigger, Collection<AirHarassTargeting.AirThreat> threats,
                       int now) {
        int radius = trigger.getReach() + PADDING;
        Zone zone = null;
        for (Zone existing : zones) {
            if (existing.center.getDistance(trigger.getPosition()) <= MEMBER_RADIUS) {
                zone = existing;
                break;
            }
        }
        if (zone == null) {
            zone = new Zone(trigger.getPosition(), radius, now);
            zones.add(zone);
        }
        zone.radius = Math.max(zone.radius, radius);
        zone.recordedFrame = now;
        Set<Integer> covering = new HashSet<>(AirHarassScouting.contributors(trigger, threats));
        zone.members.put(trigger.getId(), trigger);
        for (AirHarassTargeting.AirThreat threat : threats) {
            boolean near = threat.getPosition().getDistance(trigger.getPosition()) <= MEMBER_RADIUS;
            if ((near || covering.contains(threat.getId())) && threat.getType() != UnitType.Protoss_Interceptor) {
                zone.members.put(threat.getId(), threat);
            }
        }
        return zone;
    }

    /**
     * Steps every zone against the anti-air known now: a member known again is moved to where it stands, or leaves
     * the zone when that is out of the zone's reach; a member not known is dropped when the zone's center is in sight.
     * Zones left empty, or older than {@link #MAX_AGE_FRAMES}, are dropped.
     *
     * @param threats every known anti-air threat
     * @param visible whether a position is in sight now
     * @param now current frame
     * @return how many zones were dropped
     */
    public int refresh(Collection<AirHarassTargeting.AirThreat> threats, Predicate<Position> visible, int now) {
        Map<Integer, AirHarassTargeting.AirThreat> known = new HashMap<>();
        for (AirHarassTargeting.AirThreat threat : threats) {
            known.put(threat.getId(), threat);
        }
        int dropped = 0;
        Iterator<Zone> zoneIterator = zones.iterator();
        while (zoneIterator.hasNext()) {
            Zone zone = zoneIterator.next();
            boolean seen = visible.test(zone.center);
            Iterator<Map.Entry<Integer, AirHarassTargeting.AirThreat>> members = zone.members.entrySet().iterator();
            while (members.hasNext()) {
                Map.Entry<Integer, AirHarassTargeting.AirThreat> member = members.next();
                AirHarassTargeting.AirThreat current = known.get(member.getKey());
                if (current == null) {
                    if (seen) {
                        members.remove();
                    }
                } else if (current.getPosition().getDistance(zone.center) > zone.radius + MEMBER_RADIUS) {
                    members.remove();
                } else {
                    member.setValue(current);
                }
            }
            if (zone.members.isEmpty() || now - zone.recordedFrame > MAX_AGE_FRAMES) {
                zoneIterator.remove();
                dropped++;
            }
        }
        return dropped;
    }

    /**
     * Forgets a unit that died, in every zone, and drops the zones it leaves empty.
     *
     * @param unitId the dead unit's id
     */
    public void forget(int unitId) {
        Iterator<Zone> zoneIterator = zones.iterator();
        while (zoneIterator.hasNext()) {
            Zone zone = zoneIterator.next();
            zone.members.remove(unitId);
            if (zone.members.isEmpty()) {
                zoneIterator.remove();
            }
        }
    }

    /**
     * The anti-air the zones remember that is not known now: every member whose id is not among the known threats,
     * once.
     *
     * @param threats every known anti-air threat
     * @return the remembered threats
     */
    public List<AirHarassTargeting.AirThreat> remembered(Collection<AirHarassTargeting.AirThreat> threats) {
        Set<Integer> known = new HashSet<>();
        for (AirHarassTargeting.AirThreat threat : threats) {
            known.add(threat.getId());
        }
        List<AirHarassTargeting.AirThreat> remembered = new ArrayList<>();
        for (Zone zone : zones) {
            for (AirHarassTargeting.AirThreat member : zone.members.values()) {
                if (known.add(member.getId())) {
                    remembered.add(member);
                }
            }
        }
        return remembered;
    }

    /**
     * @return how many zones are remembered
     */
    public int size() {
        return zones.size();
    }
}
