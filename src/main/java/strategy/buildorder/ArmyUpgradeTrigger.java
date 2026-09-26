package strategy.buildorder;

import bwapi.UnitType;
import info.UnitTypeCount;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The living units that move an army upgrade into {@link BuildOrder#ARMY_UPGRADE_PRIORITY}.
 *
 * <p>The trigger is met once the living units of the named types, summed, reach the count.
 * Planned units and units still in an egg are not counted, so the upgrade waits for the army it
 * upgrades to be fielded before it holds that army's production.
 */
public final class ArmyUpgradeTrigger {

    private final int livingUnits;

    private final List<UnitType> unitTypes;

    /**
     * @param livingUnits the living units of the named types the trigger needs
     * @param unitTypes the unit types counted toward it
     */
    public ArmyUpgradeTrigger(int livingUnits, UnitType... unitTypes) {
        this.livingUnits = livingUnits;
        this.unitTypes = Collections.unmodifiableList(Arrays.asList(unitTypes));
    }

    /**
     * @param count our unit counts
     * @return true when the living units of the named types reach the trigger count
     */
    public boolean isMet(UnitTypeCount count) {
        int living = 0;
        for (UnitType unitType : unitTypes) {
            living += count.livingCount(unitType);
        }
        return living >= livingUnits;
    }
}
