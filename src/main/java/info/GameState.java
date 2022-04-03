package info;

import lombok.Data;

@Data
public class GameState {

    private boolean enemyHasCloakedUnits = false;
    private boolean enemyHasHostileFlyers = false;
}
