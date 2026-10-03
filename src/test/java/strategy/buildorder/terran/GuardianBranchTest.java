package strategy.buildorder.terran;

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

    private static final boolean HIVE = true;

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
    void theGateOpensOnlyOnAFundedEconomy() {
        assertEquals(GuardianBranch.Gate.OPEN, GuardianBranch.gate(HIVE, 3, 3));
        assertEquals(GuardianBranch.Gate.NO_HIVE, GuardianBranch.gate(false, 3, 3));
        assertEquals(GuardianBranch.Gate.FEW_BASES, GuardianBranch.gate(HIVE, 2, 3));
        assertEquals(GuardianBranch.Gate.FEW_GEYSERS, GuardianBranch.gate(HIVE, 3, 2));
    }

    @Test
    void theWaveIsCappedAtThreeGuardiansLostOnesIncluded() {
        assertEquals(3, GuardianBranch.guardiansRemaining(0, 0));
        assertEquals(1, GuardianBranch.guardiansRemaining(1, 1));
        assertEquals(0, GuardianBranch.guardiansRemaining(3, 0));
        assertEquals(0, GuardianBranch.guardiansRemaining(1, 5));
    }

    @Test
    void entersTheBranchOnce() {
        record();
        GuardianBranch branch = new GuardianBranch();

        assertEquals(GuardianBranch.Gate.OPEN, branch.evaluate(HIVE, 3, 3));
        assertEquals(GuardianBranch.Gate.OPEN, branch.evaluate(HIVE, 3, 3));

        assertTrue(branch.isEntered());
        assertEquals(Arrays.asList("ENTER"), labels);
    }

    @Test
    void exitsWithTheGateThatClosedTheBranchAndEntersAgainWhenItReopens() {
        record();
        GuardianBranch branch = new GuardianBranch();
        branch.evaluate(HIVE, 3, 3);

        assertEquals(GuardianBranch.Gate.FEW_BASES, branch.evaluate(HIVE, 2, 3));
        assertFalse(branch.isEntered());
        branch.evaluate(HIVE, 3, 3);

        assertEquals(Arrays.asList("ENTER", "EXIT:FEW_BASES", "ENTER"), labels);
    }

    @Test
    void aBranchThatNeverOpensWritesNoRow() {
        record();
        GuardianBranch branch = new GuardianBranch();

        assertEquals(GuardianBranch.Gate.NO_HIVE, branch.evaluate(false, 3, 3));

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

    @Test
    void theBranchWalksFromTheHiveToTheLastGuardianInOrder() {
        TechProgression techProgression = new TechProgression();
        techProgression.setLair(true);
        techProgression.setHive(true);
        List<GuardianBranch.Step> steps = new ArrayList<>();

        steps.add(GuardianBranch.nextStep(techProgression, 3, 0, 0, 0));
        techProgression.setSpire(true);
        for (int mutalisks = 0; mutalisks < 3; mutalisks++) {
            steps.add(GuardianBranch.nextStep(techProgression, 3, mutalisks, mutalisks, 0));
        }
        steps.add(GuardianBranch.nextStep(techProgression, 3, 3, 3, 0));
        techProgression.setGreaterSpire(true);
        for (int guardianPlans = 0; guardianPlans < 3; guardianPlans++) {
            steps.add(GuardianBranch.nextStep(techProgression, 3, 3, 3, guardianPlans));
        }
        steps.add(GuardianBranch.nextStep(techProgression, 3, 3, 3, 3));

        assertEquals(Arrays.asList(GuardianBranch.Step.SPIRE, GuardianBranch.Step.MUTALISK,
                GuardianBranch.Step.MUTALISK, GuardianBranch.Step.MUTALISK, GuardianBranch.Step.GREATER_SPIRE,
                GuardianBranch.Step.GUARDIAN, GuardianBranch.Step.GUARDIAN, GuardianBranch.Step.GUARDIAN,
                GuardianBranch.Step.NONE), steps);
    }
}
