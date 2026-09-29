package learning;

import lombok.Data;
import strategy.buildorder.BuildOrder;

@Data
public class Decisions {
    private BuildOrder opener;

    /**
     * Whether the learning file shows a Terran wall persisting across recent games against this opponent.
     */
    private boolean terranWallPersists;
}
