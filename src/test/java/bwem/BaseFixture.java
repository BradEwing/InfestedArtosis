package bwem;

import bwapi.TilePosition;

import java.util.Collections;

/**
 * Builds real bwem Bases for tests. Base is final with a package-private constructor, so the fixture lives in
 * its package. The bases have no Area, minerals or resource units; the constructor's check for resources is
 * silenced, and a null entry stands in for a geyser where a base must count as having one.
 */
public final class BaseFixture {

    private BaseFixture() {
    }

    /**
     * A starting location at the tile, with no geysers.
     */
    public static Base startingLocation(TilePosition location) {
        Base base = new Base(null, location, Collections.emptyList(), Collections.emptyList(), silentAsserter());
        base.assignStartingLocation(location);
        return base;
    }

    /**
     * An expansion at the tile with one geyser, so it can be a starting location's natural.
     */
    public static Base expansionWithGas(TilePosition location) {
        Base base = new Base(null, location, Collections.emptyList(), Collections.emptyList(), silentAsserter());
        base.getGeysers().add(null);
        return base;
    }

    private static Asserter silentAsserter() {
        Asserter asserter = new Asserter();
        asserter.setFailOnError(false);
        asserter.setFailOutputStream(null);
        return asserter;
    }
}
