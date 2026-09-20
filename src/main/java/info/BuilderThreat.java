package info;

import lombok.Getter;

/**
 * What a lone builder would walk into on its way to a build site, what waits at the site, and
 * whether the site is ground we already hold.
 *
 * <p>Three independent readings of the same enemy intelligence: mobile ground combat units last
 * known on the corridor from our main, the same units last known at the site's base, and enemy
 * static defence whose reach covers the corridor. A builder walking is only ever killed by one of
 * the three, so they are kept apart rather than summed: the column a batch reads tells which one
 * held the plan back.
 */
public final class BuilderThreat {

    public static final BuilderThreat NONE = new BuilderThreat(0, 0, 0, false, false);

    @Getter
    private final int routeEnemies;

    @Getter
    private final int siteEnemies;

    @Getter
    private final int routeDefenseZones;

    /** Whether the builder already stands on the site's base tiles. Recorded for diagnosis. */
    @Getter
    private final boolean builderAtSite;

    /**
     * Whether the site sits at a base we already hold. There is no cross-map walk to ground we
     * own, so enemies on it are a reason to build rather than a reason to stay home.
     */
    @Getter
    private final boolean siteAtOurBase;

    public BuilderThreat(int routeEnemies, int siteEnemies, int routeDefenseZones, boolean builderAtSite,
                         boolean siteAtOurBase) {
        this.routeEnemies = routeEnemies;
        this.siteEnemies = siteEnemies;
        this.routeDefenseZones = routeDefenseZones;
        this.builderAtSite = builderAtSite;
        this.siteAtOurBase = siteAtOurBase;
    }
}
