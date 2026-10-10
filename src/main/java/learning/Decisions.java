package learning;

import lombok.Data;
import strategy.buildorder.BuildOrder;

@Data
public class Decisions {
    private BuildOrder opener;

    /**
     * The strategy the strategy override forces for the game, or null when the learning picks it at the transition.
     */
    private BuildOrder strategy;

    /**
     * Whether the learning file shows a Terran wall persisting across recent games against this opponent.
     */
    private boolean terranWallPersists;

    /**
     * Whether the learning file shows Terran mech persisting across recent games against this opponent.
     */
    private boolean terranMechPersists;
}
