package strategy.buildorder.terran;

import bwapi.UnitType;
import info.TechProgression;
import macro.plan.Plan;
import macro.plan.PlanBlocker;
import macro.plan.PlanState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import telemetry.PlanEventSink;
import telemetry.PlanEvents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuardianBranchTest {

    private static final boolean ENABLED = true;

    private static final boolean DISABLED = false;

    private static final boolean HIVE = true;

    private static final GuardianBranch.Entrenchment TANKS = GuardianBranch.Entrenchment.SIEGED_TANKS;

    private final List<String> labels = new ArrayList<>();

    @AfterEach
    void clearSink() {
        PlanEvents.clear();
    }

    private void record() {
        PlanEvents.register(new PlanEventSink() {
            @Override
            public void onEnqueue(Plan plan) {
            }

            @Override
            public void onStateChange(Plan plan, PlanState from, PlanState to) {
            }

            @Override
            public void onBlocked(Plan plan, PlanBlocker blocker) {
            }

            @Override
            public void onGuardianBranch(String branchLabel) {
                labels.add(branchLabel);
            }
        });
    }

    private static TechProgression spireAndHive() {
        TechProgression techProgression = new TechProgression();
        techProgression.setLair(true);
        techProgression.setSpire(true);
        techProgression.setHive(true);
        return techProgression;
    }

    private static TechProgression greaterSpire() {
        TechProgression techProgression = spireAndHive();
        techProgression.setGreaterSpire(true);
        return techProgression;
    }

    @Test
    void severalSiegedTanksOrABunkerSeenRecentlyOpenTheEntrenchmentRead() {
        assertEquals(GuardianBranch.Entrenchment.NONE, GuardianBranch.entrenchment(1, 0));
        assertEquals(GuardianBranch.Entrenchment.SIEGED_TANKS, GuardianBranch.entrenchment(2, 0));
        assertEquals(GuardianBranch.Entrenchment.BUNKER, GuardianBranch.entrenchment(0, 1));
        assertEquals(GuardianBranch.Entrenchment.SIEGED_TANKS_AND_BUNKER, GuardianBranch.entrenchment(4, 2));
        assertEquals("SIEGED_TANKS+BUNKER", GuardianBranch.Entrenchment.SIEGED_TANKS_AND_BUNKER.label());
    }

    @Test
    void aSightingIsRecentUntilThreeMinutesAfterItWasLastSeen() {
        assertTrue(GuardianBranch.isRecent(1000, 1000 + GuardianBranch.RECENT_FRAMES));
        assertFalse(GuardianBranch.isRecent(1000, 1001 + GuardianBranch.RECENT_FRAMES));
    }

    @Test
    void theGateOpensOnlyWhenEveryTermHolds() {
        assertEquals(GuardianBranch.Gate.OPEN,
                GuardianBranch.gate(ENABLED, false, HIVE, 3, 3, TANKS, false));
        assertEquals(GuardianBranch.Gate.DISABLED,
                GuardianBranch.gate(DISABLED, false, HIVE, 3, 3, TANKS, false));
        assertEquals(GuardianBranch.Gate.LATCHED,
                GuardianBranch.gate(ENABLED, true, HIVE, 3, 3, TANKS, false));
        assertEquals(GuardianBranch.Gate.NO_HIVE,
                GuardianBranch.gate(ENABLED, false, false, 3, 3, TANKS, false));
        assertEquals(GuardianBranch.Gate.FEW_BASES,
                GuardianBranch.gate(ENABLED, false, HIVE, 2, 3, TANKS, false));
        assertEquals(GuardianBranch.Gate.FEW_GEYSERS,
                GuardianBranch.gate(ENABLED, false, HIVE, 3, 2, TANKS, false));
        assertEquals(GuardianBranch.Gate.NOT_ENTRENCHED,
                GuardianBranch.gate(ENABLED, false, HIVE, 3, 3, GuardianBranch.Entrenchment.NONE, false));
    }

    @Test
    void anOpenBranchStaysOpenWhenTheEntrenchmentGoesStale() {
        assertEquals(GuardianBranch.Gate.OPEN,
                GuardianBranch.gate(ENABLED, false, HIVE, 3, 3, GuardianBranch.Entrenchment.NONE, true));
    }

    @Test
    void theLatchWinsOverEveryOtherTerm() {
        assertEquals(GuardianBranch.Gate.LATCHED,
                GuardianBranch.gate(ENABLED, true, false, 1, 0, GuardianBranch.Entrenchment.NONE, true));
    }

    @Test
    void theAntiAirSetIsTheFourTypesTheTicketNames() {
        assertEquals(4, GuardianBranch.ANTI_AIR_TYPES.size());
        assertTrue(GuardianBranch.ANTI_AIR_TYPES.containsAll(Arrays.asList(UnitType.Terran_Goliath,
                UnitType.Terran_Missile_Turret, UnitType.Terran_Wraith, UnitType.Terran_Valkyrie)));
    }

    @Test
    void theWaveIsCappedAtThreeGuardiansLostOnesIncluded() {
        assertEquals(3, GuardianBranch.guardiansRemaining(0, 0));
        assertEquals(1, GuardianBranch.guardiansRemaining(1, 1));
        assertEquals(0, GuardianBranch.guardiansRemaining(3, 0));
        assertEquals(0, GuardianBranch.guardiansRemaining(1, 5));
    }

    @Test
    void entersTheBranchOnceAndWritesTheEntrenchmentThatOpenedIt() {
        record();
        GuardianBranch branch = new GuardianBranch();

        assertEquals(GuardianBranch.Gate.OPEN, branch.evaluate(ENABLED, null, HIVE, 3, 3, TANKS));
        assertEquals(GuardianBranch.Gate.OPEN, branch.evaluate(ENABLED, null, HIVE, 3, 3, TANKS));

        assertTrue(branch.isEntered());
        assertEquals(Arrays.asList("ENTER:SIEGED_TANKS"), labels);
    }

    @Test
    void exitsWithTheGateThatClosedTheBranchAndEntersAgainWhenItReopens() {
        record();
        GuardianBranch branch = new GuardianBranch();
        branch.evaluate(ENABLED, null, HIVE, 3, 3, TANKS);

        assertEquals(GuardianBranch.Gate.FEW_BASES, branch.evaluate(ENABLED, null, HIVE, 2, 3, TANKS));
        assertFalse(branch.isEntered());
        branch.evaluate(ENABLED, null, HIVE, 3, 3, GuardianBranch.Entrenchment.BUNKER);

        assertEquals(Arrays.asList("ENTER:SIEGED_TANKS", "EXIT:FEW_BASES", "ENTER:BUNKER"), labels);
    }

    @Test
    void theFirstAntiAirSightingLatchesTheBranchOffForTheGame() {
        record();
        GuardianBranch branch = new GuardianBranch();
        branch.evaluate(ENABLED, null, HIVE, 3, 3, TANKS);

        assertEquals(GuardianBranch.Gate.LATCHED,
                branch.evaluate(ENABLED, UnitType.Terran_Goliath, HIVE, 3, 3, TANKS));
        assertEquals(GuardianBranch.Gate.LATCHED, branch.evaluate(ENABLED, null, HIVE, 3, 3, TANKS));
        assertEquals(GuardianBranch.Gate.LATCHED,
                branch.evaluate(ENABLED, UnitType.Terran_Wraith, HIVE, 4, 4, TANKS));

        assertTrue(branch.isLatched());
        assertFalse(branch.isEntered());
        assertEquals(Arrays.asList("ENTER:SIEGED_TANKS", "LATCH:Terran_Goliath", "EXIT:LATCHED"), labels);
    }

    @Test
    void aSightingBeforeTheBranchOpensStopsItEverOpening() {
        record();
        GuardianBranch branch = new GuardianBranch();

        branch.evaluate(ENABLED, UnitType.Terran_Missile_Turret, false, 2, 1, GuardianBranch.Entrenchment.NONE);

        assertEquals(GuardianBranch.Gate.LATCHED, branch.evaluate(ENABLED, null, HIVE, 3, 3, TANKS));
        assertEquals(Arrays.asList("LATCH:Terran_Missile_Turret"), labels);
    }

    @Test
    void aSwitchedOffBranchNeverLatchesEntersOrWritesARow() {
        record();
        GuardianBranch branch = new GuardianBranch();

        assertEquals(GuardianBranch.Gate.DISABLED,
                branch.evaluate(DISABLED, UnitType.Terran_Goliath, HIVE, 3, 3, TANKS));

        assertFalse(branch.isLatched());
        assertFalse(branch.isEntered());
        assertTrue(labels.isEmpty());
    }

    @Test
    void theSpireComesFirstOnceTheBranchIsOpen() {
        TechProgression techProgression = new TechProgression();
        techProgression.setLair(true);
        techProgression.setHive(true);

        assertEquals(GuardianBranch.Step.SPIRE, GuardianBranch.nextStep(techProgression, 3, 0, 0, 0));

        techProgression.setPlannedSpire(true);
        assertEquals(GuardianBranch.Step.NONE, GuardianBranch.nextStep(techProgression, 3, 0, 0, 0));
    }

    @Test
    void mutalisksComeOnePerGuardianThenTheGreaterSpire() {
        TechProgression techProgression = spireAndHive();

        assertEquals(GuardianBranch.Step.MUTALISK, GuardianBranch.nextStep(techProgression, 3, 0, 0, 0));
        assertEquals(GuardianBranch.Step.MUTALISK, GuardianBranch.nextStep(techProgression, 3, 2, 2, 0));
        assertEquals(GuardianBranch.Step.NONE, GuardianBranch.nextStep(techProgression, 3, 2, 3, 0));
        assertEquals(GuardianBranch.Step.GREATER_SPIRE, GuardianBranch.nextStep(techProgression, 3, 3, 3, 0));
    }

    @Test
    void theGreaterSpireNeedsAFinishedHive() {
        TechProgression techProgression = spireAndHive();
        techProgression.setHive(false);

        assertEquals(GuardianBranch.Step.NONE, GuardianBranch.nextStep(techProgression, 3, 3, 3, 0));
    }

    @Test
    void noMutaliskIsPlannedWhileTheGreaterSpireMorphs() {
        TechProgression techProgression = spireAndHive();
        techProgression.setPlannedGreaterSpire(true);

        assertEquals(GuardianBranch.Step.NONE, GuardianBranch.nextStep(techProgression, 3, 0, 0, 0));
    }

    @Test
    void aGuardianIsPlannedForEachLivingMutaliskNoGuardianPlanHasClaimed() {
        TechProgression techProgression = greaterSpire();

        assertEquals(GuardianBranch.Step.GUARDIAN, GuardianBranch.nextStep(techProgression, 3, 3, 3, 0));
        assertEquals(GuardianBranch.Step.GUARDIAN, GuardianBranch.nextStep(techProgression, 3, 3, 3, 2));
        assertEquals(GuardianBranch.Step.NONE, GuardianBranch.nextStep(techProgression, 3, 3, 3, 3));
    }

    @Test
    void aMutaliskIsReplacedWhenTheGuardiansStillToComeOutnumberThem() {
        TechProgression techProgression = greaterSpire();

        assertEquals(GuardianBranch.Step.MUTALISK, GuardianBranch.nextStep(techProgression, 3, 1, 1, 1));
        assertEquals(GuardianBranch.Step.NONE, GuardianBranch.nextStep(techProgression, 3, 1, 3, 1));
    }

    @Test
    void nothingIsPlannedOnceTheWaveIsFielded() {
        assertEquals(GuardianBranch.Step.NONE, GuardianBranch.nextStep(greaterSpire(), 0, 3, 3, 0));
        assertEquals(GuardianBranch.Step.NONE, GuardianBranch.nextStep(new TechProgression(), 0, 0, 0, 0));
    }
}
