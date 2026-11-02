package strategy.buildorder.opener;

import bwapi.Race;
import config.Config;
import org.junit.jupiter.api.Test;
import strategy.BuildOrderFactory;
import strategy.buildorder.BuildOrder;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyOverrideTest {

    private static final String PER_RACE = "1HatchSpire,2HatchMuta,3HatchMuta";

    private final BuildOrderFactory factory = new BuildOrderFactory(2, Race.Zerg);

    private BuildOrder transition(String override, Race race) {
        return StrategyOverride.pick(override, OpenerTransitions.forRace(race), factory::getByName);
    }

    private BuildOrder atGameStart(String override, Race race) {
        return StrategyOverride.atGameStart(override, race, factory::getByName);
    }

    @Test
    void aListPicksOneBuildPerRace() {
        assertEquals("1HatchSpire", transition(PER_RACE, Race.Zerg).getName());
        assertEquals("2HatchMuta", transition(PER_RACE, Race.Terran).getName());
        assertEquals("3HatchMuta", transition(PER_RACE, Race.Protoss).getName());
    }

    @Test
    void aListForcesTheRacesBuildAtGameStart() {
        assertEquals("1HatchSpire", atGameStart(PER_RACE, Race.Zerg).getName());
        assertEquals("2HatchMuta", atGameStart(PER_RACE, Race.Terran).getName());
        assertEquals("3HatchMuta", atGameStart(PER_RACE, Race.Protoss).getName());
    }

    @Test
    void aRandomOpponentGetsNoForcedBuildAtGameStart() {
        assertNull(atGameStart(PER_RACE, Race.Random));
        assertNull(atGameStart(PER_RACE, Race.Unknown));
    }

    @Test
    void aSingleNameBehavesAsBefore() {
        assertEquals("2HatchMuta", transition("2HatchMuta", Race.Terran).getName());
        assertNull(transition("2HatchMuta", Race.Protoss));
        assertEquals("2HatchMuta", atGameStart("2HatchMuta", Race.Protoss).getName());
        assertEquals("2HatchMuta", atGameStart("2HatchMuta", Race.Random).getName());
        assertNull(atGameStart("12Pool", Race.Terran));
        assertNull(atGameStart("NoSuchBuild", Race.Terran));
    }

    @Test
    void anUnsetOverrideForcesNothing() {
        assertNull(transition(null, Race.Terran));
        assertNull(atGameStart(null, Race.Terran));
        assertTrue(StrategyOverride.names(null).isEmpty());
    }

    @Test
    void aNameNotOfferedForTheRaceIsIgnored() {
        assertEquals("3HatchMuta", transition("2HatchMuta,3HatchMuta", Race.Protoss).getName());
        assertEquals("3HatchMuta", atGameStart("2HatchMuta,3HatchMuta", Race.Protoss).getName());
        assertNull(transition("2HatchMuta,1HatchSpire", Race.Protoss));
        assertNull(atGameStart("2HatchMuta,1HatchSpire", Race.Protoss));
        assertNull(atGameStart("12Pool,NoSuchBuild", Race.Terran));
    }

    @Test
    void namesAreTrimmedAndEmptyEntriesDropped() {
        assertEquals(Arrays.asList("1HatchSpire", "2HatchMuta"), StrategyOverride.names(" 1HatchSpire , ,2HatchMuta,"));
    }

    @Test
    void theHydraBuildIsForcedWhenAnyListedNameIsIt() {
        Config listed = new Config();
        listed.strategyOverride = "1HatchSpire,2HatchHydraZvT,3HatchMuta";
        Config notListed = new Config();
        notListed.strategyOverride = PER_RACE;

        assertTrue(OpenerTransitions.isHydraBuildForced(listed));
        assertFalse(OpenerTransitions.isHydraBuildForced(notListed));
        assertEquals("2HatchHydraZvT", StrategyOverride.pick(listed.strategyOverride,
                OpenerTransitions.forRace(Race.Terran, false, OpenerTransitions.isHydraBuildForced(listed)),
                factory::getByName).getName());
    }
}
