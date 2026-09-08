package macro.plan;

import bwapi.TilePosition;
import bwapi.UnitType;

import java.util.HashMap;
import java.util.Map;

/**
 * Ownership of creep colonies by the Sunken and Spore plans waiting to morph them.
 *
 * <p>A creep colony belongs to the morph plan its pair was queued with. Every other morph plan
 * leaves that tile alone and waits for the colony its own pair is building, so a later pair cannot
 * take the colony an earlier one paid for.
 */
public final class ColonyClaims {

    private ColonyClaims() {}

    public static boolean isColonyMorph(UnitType type) {
        return type == UnitType.Zerg_Sunken_Colony || type == UnitType.Zerg_Spore_Colony;
    }

    /**
     * Maps every tile a live colony morph plan is waiting on to the plan holding that claim. The
     * first plan seen for a tile keeps it, so callers pass the more authoritative group first.
     */
    @SafeVarargs
    public static Map<TilePosition, Plan> collect(Iterable<Plan>... planGroups) {
        Map<TilePosition, Plan> claims = new HashMap<>();
        for (Iterable<Plan> group : planGroups) {
            for (Plan plan : group) {
                if (!isColonyMorph(plan.getPlannedUnit())) {
                    continue;
                }
                TilePosition claim = plan.claimedColonyTile();
                if (claim != null) {
                    claims.putIfAbsent(claim, plan);
                }
            }
        }
        return claims;
    }

    /** Whether a colony tile is spoken for by a morph plan other than the one asking for it. */
    public static boolean isClaimedByOther(Map<TilePosition, Plan> claims, TilePosition colonyTile, Plan plan) {
        Plan claimant = claims.get(colonyTile);
        return claimant != null && !claimant.equals(plan);
    }
}
