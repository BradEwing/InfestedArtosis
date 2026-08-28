package telemetry;

import bwapi.Position;
import lombok.Getter;

/**
 * An enemy unit that can open or hold an engagement, reduced to what the clustering needs.
 */
@Getter
final class EnemyThreat {

    private final Position position;
    private final int supply;

    EnemyThreat(Position position, int supply) {
        this.position = position;
        this.supply = supply;
    }
}
