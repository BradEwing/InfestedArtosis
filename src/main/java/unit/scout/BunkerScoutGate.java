package unit.scout;

import bwapi.TilePosition;

/**
 * Whether a ground scout may route to a base while a Bunker holds the way, read from the BunkerNatural and BunkerMain
 * holds. The way to the enemy main runs through the enemy natural, so either hold closes the main to a ground scout
 * and only a natural hold closes the natural. Any other base is open. A hold lifts once every Bunker behind it is
 * seen dead, see StrategyTracker, so a route is open again as soon as the Bunker is known dead.
 */
public final class BunkerScoutGate {

    /**
     * Skip reason written to the SCOUT_SKIPPED row of a scout kept off a held route.
     */
    public static final String SKIP_REASON = "BUNKER";

    /**
     * Where a ground scout is headed, as far as a Bunker hold matters.
     */
    public enum Destination {
        ENEMY_MAIN,
        ENEMY_NATURAL,
        OTHER
    }

    private BunkerScoutGate() {
    }

    /**
     * Whether a ground scout may route to the destination.
     *
     * @param bunkerNaturalHeld whether BunkerNatural holds
     * @param bunkerMainHeld whether BunkerMain holds
     * @param destination where the scout is headed
     * @return false while a Bunker holds the route to the destination, true otherwise
     */
    public static boolean mayRoute(boolean bunkerNaturalHeld, boolean bunkerMainHeld, Destination destination) {
        switch (destination) {
            case ENEMY_MAIN:
                return !bunkerNaturalHeld && !bunkerMainHeld;
            case ENEMY_NATURAL:
                return !bunkerNaturalHeld;
            default:
                return true;
        }
    }

    /**
     * Whether a ground scout may route to a base, as {@link #mayRouteToBase(boolean, boolean, TilePosition,
     * TilePosition, TilePosition)}, with the Bunker gates switched off letting every route open.
     *
     * @param gateOn whether the Bunker gates are switched on, see Config.bunkerGate
     * @param bunkerNaturalHeld whether BunkerNatural holds
     * @param bunkerMainHeld whether BunkerMain holds
     * @param target the base the scout is headed to
     * @param enemyMain the enemy main's location, null when unknown
     * @param enemyNatural the enemy natural's location, null when unknown
     * @return true with the gates off, otherwise false while a Bunker holds the route to the base
     */
    public static boolean mayRouteToBase(boolean gateOn, boolean bunkerNaturalHeld, boolean bunkerMainHeld,
                                         TilePosition target, TilePosition enemyMain, TilePosition enemyNatural) {
        return !gateOn || mayRouteToBase(bunkerNaturalHeld, bunkerMainHeld, target, enemyMain, enemyNatural);
    }

    /**
     * Whether a ground scout may route to a base, given its tile and the enemy main's and natural's.
     *
     * @param bunkerNaturalHeld whether BunkerNatural holds
     * @param bunkerMainHeld whether BunkerMain holds
     * @param target the base the scout is headed to
     * @param enemyMain the enemy main's location, null when unknown
     * @param enemyNatural the enemy natural's location, null when unknown
     * @return false while a Bunker holds the route to the base, true otherwise
     */
    public static boolean mayRouteToBase(boolean bunkerNaturalHeld, boolean bunkerMainHeld, TilePosition target,
                                         TilePosition enemyMain, TilePosition enemyNatural) {
        return mayRoute(bunkerNaturalHeld, bunkerMainHeld, destination(target, enemyMain, enemyNatural));
    }

    /**
     * Classifies a base by its location.
     *
     * @param target the base's location
     * @param enemyMain the enemy main's location, null when unknown
     * @param enemyNatural the enemy natural's location, null when unknown
     * @return the destination the base is
     */
    public static Destination destination(TilePosition target, TilePosition enemyMain, TilePosition enemyNatural) {
        if (target != null && target.equals(enemyMain)) {
            return Destination.ENEMY_MAIN;
        }
        if (target != null && target.equals(enemyNatural)) {
            return Destination.ENEMY_NATURAL;
        }
        return Destination.OTHER;
    }

    /**
     * The label of a SCOUT_SKIPPED row: the reason and the destination, as BUNKER:ENEMY_MAIN.
     *
     * @param destination where the scout was headed
     * @return the label
     */
    public static String skipLabel(Destination destination) {
        return SKIP_REASON + ":" + destination;
    }
}
