package unit.squad;

import lombok.Getter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

/**
 * Supply a containing squad has lost and killed during its current episode, kept per frame so the recent share
 * can be read over a sliding window.
 *
 * <p>Supply is in BWAPI half-supply units, the scale {@link bwapi.UnitType#supplyRequired()} reports.
 */
public class ContainmentAttrition {

    static final int WINDOW_FRAMES = 480;
    static final double LOSS_SHARE = 0.20;
    static final double KILL_SHARE = 0.50;

    private final Deque<int[]> losses = new ArrayDeque<>();
    private final Deque<int[]> kills = new ArrayDeque<>();

    @Getter
    private int totalLost = 0;

    public void recordLoss(int frame, int supply) {
        losses.addLast(new int[] {frame, supply});
        totalLost += supply;
    }

    public void recordKill(int frame, int supply) {
        kills.addLast(new int[] {frame, supply});
    }

    /**
     * Folds another squad's episode into this one, as when containing squads merge and the episode carries on, so
     * the window and the episode total span both squads' losses and kills.
     *
     * @param other attrition of a squad being merged into this one
     */
    public void absorb(ContainmentAttrition other) {
        mergeByFrame(losses, other.losses);
        mergeByFrame(kills, other.kills);
        totalLost += other.totalLost;
    }

    public void reset() {
        losses.clear();
        kills.clear();
        totalLost = 0;
    }

    /**
     * Whether the episode is being ground down: within the window the squad lost at least {@link #LOSS_SHARE}
     * of the supply it held at the window's start while killing less than {@link #KILL_SHARE} of what it lost.
     *
     * @param now current frame
     * @param supplyNow supply the squad holds now
     * @return true when the contain should end
     */
    public boolean isBleeding(int now, int supplyNow) {
        return bleeding(supplyWithin(losses, now), supplyWithin(kills, now), supplyNow);
    }

    /**
     * The attrition rule on its inputs.
     *
     * @param lost supply lost within the window
     * @param killed enemy supply killed within the window
     * @param supplyNow supply the squad holds now
     * @return true when losses reach the loss share of the window's starting supply and kills stay under the
     *     kill share of the losses
     */
    static boolean bleeding(int lost, int killed, int supplyNow) {
        if (lost <= 0) {
            return false;
        }
        return lost >= LOSS_SHARE * (supplyNow + lost) && killed < KILL_SHARE * lost;
    }

    private static void mergeByFrame(Deque<int[]> into, Deque<int[]> from) {
        List<int[]> merged = new ArrayList<>(into);
        merged.addAll(from);
        merged.sort(Comparator.comparingInt(event -> event[0]));
        into.clear();
        into.addAll(merged);
    }

    private static int supplyWithin(Deque<int[]> events, int now) {
        while (!events.isEmpty() && now - events.peekFirst()[0] >= WINDOW_FRAMES) {
            events.removeFirst();
        }
        int supply = 0;
        for (int[] event : events) {
            supply += event[1];
        }
        return supply;
    }
}
