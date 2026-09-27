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
 * <p>A claimed base registers every mineral patch the map gives it, whether or not the patch is visible at
 * the claim. A mined-out mineral patch is destroyed, so a patch leaves the ledger when its unit is destroyed or
 * when its tiles are seen without it, and a destroyed patch is never registered again. A geyser is depleted
 * once our Extractor on it reads zero resources. Workers still draw gas from a depleted geyser,
 * only less per trip, so a depleted Extractor stays tracked until it is destroyed.
 *
 * <p>Units are keyed by unit ID and bases by their tile location, so the ledger is built in a test without a
 * game.
 */
public class ResourceLedger {

    /**
     * Consecutive observations, one per frame, a patch's tiles must be visible without the patch before it is
     * taken as gone. A patch that is really gone stays gone, so waiting costs only latency, and a single frame
     * where tile visibility and the unit's state disagree cannot drop a live patch.
     */
    static final int SEEN_GONE_OBSERVATIONS = 24;

    private final Map<TilePosition, Set<Integer>> mineralPatchesByBase = new HashMap<>();
    private final Map<Integer, ExtractorGeyser> extractors = new HashMap<>();
    private final Set<TilePosition> depletedGeyserTiles = new HashSet<>();
    private final Map<TilePosition, Integer> firstExtractorCompletedFrames = new HashMap<>();
    private final Set<Integer> destroyedMineralPatches = new HashSet<>();
    private final Map<Integer, Integer> seenGoneObservations = new HashMap<>();

    /**
     * Records the mineral patches of a base we claimed, replacing what an earlier claim of the same base
     * recorded. A patch already destroyed is left out.
     *
     * @param base the base's tile location
     * @param mineralPatchIds unit IDs of every mineral patch the map assigns the base, visible or not
     */
    public void addBase(TilePosition base, Collection<Integer> mineralPatchIds) {
        Set<Integer> patches = new HashSet<>(mineralPatchIds);
        patches.removeAll(destroyedMineralPatches);
        mineralPatchesByBase.put(base, patches);
    }

    /**
     * Forgets a mineral patch that was destroyed, which is how a patch that is mined out leaves the game.
     *
     * @param mineralPatchId the destroyed patch's unit ID
     */
    public void removeMineralPatch(int mineralPatchId) {
        destroyedMineralPatches.add(mineralPatchId);
        for (Set<Integer> patches : mineralPatchesByBase.values()) {
            patches.remove(mineralPatchId);
        }
    }

    /**
     * Forgets a mineral patch once its tiles have been visible without it for {@link #SEEN_GONE_OBSERVATIONS}
     * observations in a row, which is how a patch mined out while no one watched is found gone. A patch that
     * is not fully in sight, or that exists, restarts the run and is kept.
     *
     * @param mineralPatchId the patch's unit ID
     * @param tilesVisible whether every tile the patch covers is visible this frame
     * @param exists whether the patch's unit exists this frame
     * @return true only on the observation that forgets the patch
     */
    public boolean observeMineralPatch(int mineralPatchId, boolean tilesVisible, boolean exists) {
        if (destroyedMineralPatches.contains(mineralPatchId)) {
            return false;
        }
        if (!tilesVisible || exists) {
            seenGoneObservations.remove(mineralPatchId);
            return false;
        }
        int observations = seenGoneObservations.merge(mineralPatchId, 1, Integer::sum);
        if (observations < SEEN_GONE_OBSERVATIONS) {
            return false;
        }
        seenGoneObservations.remove(mineralPatchId);
        removeMineralPatch(mineralPatchId);
        return true;
    }

    /**
     * @param base the base's tile location
     * @return mineral patches still alive at that base, whether or not we hold it
     */
    public int mineralPatchesAt(TilePosition base) {
        Set<Integer> patches = mineralPatchesByBase.get(base);
        return patches == null ? 0 : patches.size();
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
     * @param completedFrame the frame the Extractor completed; the first Extractor completed on the geyser's
     *     tile also sets the geyser's first completion frame, which a rebuilt Extractor keeps
     */
    public void addExtractor(int extractorId, TilePosition geyser, @Nullable TilePosition base,
                             int initialResources, int completedFrame) {
        firstExtractorCompletedFrames.putIfAbsent(geyser, completedFrame);
        extractors.put(extractorId, new ExtractorGeyser(geyser, base, initialResources, completedFrame,
                firstExtractorCompletedFrames.get(geyser)));
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

    /**
     * One of our completed Extractors and the geyser under it. completedFrame is this Extractor's completion,
     * and firstCompletedFrame the completion of our first Extractor on the same geyser, which is earlier for a
     * rebuilt Extractor.
     */
    @Getter
    public static final class ExtractorGeyser {
        private final TilePosition geyser;
        @Nullable
        private final TilePosition base;
        private final int initialResources;
        private final int completedFrame;
        private final int firstCompletedFrame;
        private boolean depleted;

        private ExtractorGeyser(TilePosition geyser, @Nullable TilePosition base, int initialResources,
                                int completedFrame, int firstCompletedFrame) {
            this.geyser = geyser;
            this.base = base;
            this.initialResources = initialResources;
            this.completedFrame = completedFrame;
            this.firstCompletedFrame = firstCompletedFrame;
        }
    }
}
