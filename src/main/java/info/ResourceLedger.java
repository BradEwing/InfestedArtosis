package info;

import bwapi.TilePosition;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The mineral patches left at each base we have claimed, and the state of the geyser under each of our
 * completed Extractors.
 *
 * <p>A mined-out mineral patch is destroyed, so a patch leaves the ledger when its unit is destroyed. A geyser
 * is depleted once our Extractor on it reads zero resources. Workers still draw gas from a depleted geyser,
 * only less per trip, so a depleted Extractor stays tracked until it is destroyed.
 *
 * <p>Units are keyed by unit ID and bases by their tile location, so the ledger is built in a test without a
 * game.
 */
public class ResourceLedger {

    private final Map<TilePosition, Set<Integer>> mineralPatchesByBase = new HashMap<>();
    private final Map<Integer, ExtractorGeyser> extractors = new HashMap<>();
    private final Set<TilePosition> depletedGeyserTiles = new HashSet<>();

    /**
     * Records the living mineral patches of a base we claimed, replacing what an earlier claim of the same
     * base recorded.
     *
     * @param base the base's tile location
     * @param mineralPatchIds unit IDs of the base's mineral patches that still exist
     */
    public void addBase(TilePosition base, Collection<Integer> mineralPatchIds) {
        mineralPatchesByBase.put(base, new HashSet<>(mineralPatchIds));
    }

    /**
     * Forgets a mineral patch that was destroyed, which is how a patch that is mined out leaves the game.
     *
     * @param mineralPatchId the destroyed patch's unit ID
     */
    public void removeMineralPatch(int mineralPatchId) {
        for (Set<Integer> patches : mineralPatchesByBase.values()) {
            patches.remove(mineralPatchId);
        }
    }

    /**
     * @param ownedBases tile locations of the bases we hold now
     * @return mineral patches still alive at those bases; a base we once claimed but no longer hold is not counted
     */
    public int remainingMineralPatches(Collection<TilePosition> ownedBases) {
        int remaining = 0;
        for (TilePosition base : ownedBases) {
            Set<Integer> patches = mineralPatchesByBase.get(base);
            if (patches != null) {
                remaining += patches.size();
            }
        }
        return remaining;
    }

    /**
     * Records a completed Extractor as mining. It is marked depleted on the first {@link #observeResources} that
     * reads zero.
     *
     * @param extractorId the Extractor's unit ID
     * @param geyser the geyser's tile location
     * @param base the tile location of the base the geyser belongs to, or null when it belongs to none
     * @param initialResources the gas the geyser started the game with
     * @param completedFrame the frame the Extractor completed
     */
    public void addExtractor(int extractorId, TilePosition geyser, @Nullable TilePosition base,
                             int initialResources, int completedFrame) {
        extractors.put(extractorId, new ExtractorGeyser(geyser, base, initialResources, completedFrame));
    }

    public void removeExtractor(int extractorId) {
        extractors.remove(extractorId);
    }

    /**
     * Reads the resources left under one of our Extractors.
     *
     * @param extractorId the Extractor's unit ID
     * @param resources what the Extractor's {@code getResources()} reads this frame
     * @return the Extractor's record when this reading is the first to find its geyser empty, or null otherwise.
     *     An Extractor rebuilt on a geyser already seen empty is marked depleted but not returned again.
     */
    @Nullable
    public ExtractorGeyser observeResources(int extractorId, int resources) {
        ExtractorGeyser extractor = extractors.get(extractorId);
        if (extractor == null || extractor.depleted || resources > 0) {
            return null;
        }
        extractor.depleted = true;
        return depletedGeyserTiles.add(extractor.geyser) ? extractor : null;
    }

    /**
     * @return our completed Extractors whose geyser still has gas
     */
    public int miningGeysers() {
        int mining = 0;
        for (ExtractorGeyser extractor : extractors.values()) {
            if (!extractor.depleted) {
                mining += 1;
            }
        }
        return mining;
    }

    /**
     * @return our completed Extractors whose geyser is empty
     */
    public int depletedGeysers() {
        return extractors.size() - miningGeysers();
    }

    /** One of our completed Extractors and the geyser under it. */
    @Getter
    public static final class ExtractorGeyser {
        private final TilePosition geyser;
        @Nullable
        private final TilePosition base;
        private final int initialResources;
        private final int completedFrame;
        private boolean depleted;

        private ExtractorGeyser(TilePosition geyser, @Nullable TilePosition base, int initialResources,
                                int completedFrame) {
            this.geyser = geyser;
            this.base = base;
            this.initialResources = initialResources;
            this.completedFrame = completedFrame;
        }
    }
}
