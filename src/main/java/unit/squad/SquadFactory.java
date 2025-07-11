package unit.squad;

import bwapi.UnitType;

/**
 * Factory class for creating squad instances based on unit type.
 * Allows for specialized squad behavior per unit type while maintaining
 * a common interface.
 */
public class SquadFactory {

    /**
     * Creates a squad instance appropriate for the given unit type.
     *
     * @param unitType The type of units this squad will contain
     * @return Squad instance specialized for the unit type
     */
    public Squad createSquad(UnitType unitType) {
        switch (unitType) {
            case Zerg_Mutalisk:
                return new MutaliskSquad();
            default:
                return new Squad();
        }
    }
}