package macro.plan;

import bwapi.TilePosition;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColonyClaimsTest {

    private static final TilePosition FIRST_TILE = new TilePosition(114, 33);
    private static final TilePosition SECOND_TILE = new TilePosition(11, 7);

    private Plan creepColony(TilePosition tile) {
        return new BuildingPlan(UnitType.Zerg_Creep_Colony, 5, tile);
    }

    private Plan sunkenPairedWith(Plan creepColonyPlan, int priority) {
        Plan sunken = new BuildingPlan(UnitType.Zerg_Sunken_Colony, priority, creepColonyPlan.getBuildPosition());
        sunken.setPairedColonyPlan(creepColonyPlan);
        return sunken;
    }

    @Test
    void claimedColonyTileFollowsThePairedPlan() {
        Plan creepColonyPlan = creepColony(FIRST_TILE);
        Plan sunken = sunkenPairedWith(creepColonyPlan, 5);

        creepColonyPlan.setBuildPosition(SECOND_TILE);

        assertEquals(SECOND_TILE, sunken.claimedColonyTile());
    }

    @Test
    void claimedColonyTileFallsBackToOwnBuildPosition() {
        Plan sunken = new BuildingPlan(UnitType.Zerg_Sunken_Colony, 5, FIRST_TILE);

        assertEquals(FIRST_TILE, sunken.claimedColonyTile());
    }

    @Test
    void claimedColonyTileIsNullWhenNothingIsPlaced() {
        Plan sunken = new BuildingPlan(UnitType.Zerg_Sunken_Colony, 5);

        assertNull(sunken.claimedColonyTile());
    }

    @Test
    void collectMapsMorphPlansToTheirColonyTile() {
        Plan creepColonyPlan = creepColony(FIRST_TILE);
        Plan sunken = sunkenPairedWith(creepColonyPlan, 5);
        List<Plan> plans = Arrays.asList(creepColonyPlan, sunken);

        Map<TilePosition, Plan> claims = ColonyClaims.collect(plans);

        assertEquals(1, claims.size());
        assertSame(sunken, claims.get(FIRST_TILE));
    }

    @Test
    void collectMergesEveryPlanGroup() {
        Plan firstPair = creepColony(FIRST_TILE);
        Plan firstSunken = sunkenPairedWith(firstPair, 5);
        Plan secondPair = creepColony(SECOND_TILE);
        Plan secondSunken = sunkenPairedWith(secondPair, 1);

        Map<TilePosition, Plan> claims = ColonyClaims.collect(
                Collections.singletonList(firstSunken),
                Collections.singletonList(secondSunken));

        assertSame(firstSunken, claims.get(FIRST_TILE));
        assertSame(secondSunken, claims.get(SECOND_TILE));
    }

    @Test
    void aLaterPairCannotTakeTheColonyAnEarlierPairIsWaitingOn() {
        Plan earlyPair = creepColony(FIRST_TILE);
        Plan earlySunken = sunkenPairedWith(earlyPair, 5);
        Plan latePair = creepColony(SECOND_TILE);
        Plan lateSunken = sunkenPairedWith(latePair, 1);

        Map<TilePosition, Plan> claims = ColonyClaims.collect(Arrays.asList(lateSunken, earlySunken));

        assertTrue(ColonyClaims.isClaimedByOther(claims, FIRST_TILE, lateSunken));
        assertFalse(ColonyClaims.isClaimedByOther(claims, FIRST_TILE, earlySunken));
    }

    @Test
    void anUnclaimedColonyIsFreeToAdopt() {
        Plan pair = creepColony(FIRST_TILE);
        Plan sunken = sunkenPairedWith(pair, 5);

        Map<TilePosition, Plan> claims = ColonyClaims.collect(Collections.singletonList(sunken));

        assertFalse(ColonyClaims.isClaimedByOther(claims, SECOND_TILE, sunken));
    }

    @Test
    void adoptingAnotherColonyReleasesTheOldClaim() {
        Plan pair = creepColony(FIRST_TILE);
        Plan sunken = sunkenPairedWith(pair, 5);

        sunken.setPairedColonyPlan(null);
        sunken.setBuildPosition(SECOND_TILE);
        Map<TilePosition, Plan> claims = ColonyClaims.collect(Collections.singletonList(sunken));

        assertEquals(SECOND_TILE, sunken.claimedColonyTile());
        assertNull(claims.get(FIRST_TILE));
        assertSame(sunken, claims.get(SECOND_TILE));
    }

    @Test
    void nonMorphPlansHoldNoClaim() {
        Plan creepColonyPlan = creepColony(FIRST_TILE);
        Plan drone = new UnitPlan(UnitType.Zerg_Drone, 5);

        Map<TilePosition, Plan> claims = ColonyClaims.collect(Arrays.asList(creepColonyPlan, drone));

        assertTrue(claims.isEmpty());
    }

    @Test
    void isColonyMorphCoversSunkenAndSpore() {
        assertTrue(ColonyClaims.isColonyMorph(UnitType.Zerg_Sunken_Colony));
        assertTrue(ColonyClaims.isColonyMorph(UnitType.Zerg_Spore_Colony));
        assertFalse(ColonyClaims.isColonyMorph(UnitType.Zerg_Creep_Colony));
        assertFalse(ColonyClaims.isColonyMorph(null));
    }
}
