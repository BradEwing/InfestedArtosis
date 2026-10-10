package learning;

import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Builder
@Data
public class OpponentRecord {
    private String name;
    private String race;

    private int wins;
    private int losses;
    private int version;

    @Default
    private List<Long> gameTimestamps = new ArrayList<>();

    private Map<String, Record> openerRecord;
    private Map<String, Record> buildOrderRecord;
    
    private Map<String, MapAwareRecord> mapSpecificOpenerRecord;
    private Map<String, MapAwareRecord> mapSpecificBuildOrderRecord;

    @Default
    private Map<String, Integer> openerBuildPairs = new HashMap<>();

    private boolean priorSeeded;

    public int totalGames() {
        return this.wins + this.losses;
    }

    /**
     * Returns the real games, counting a seeded prior as one game so the index is not random before the first real game.
     */
    public int selectionGames() {
        return totalGames() + (priorSeeded ? 1 : 0);
    }
    
    public void ensureMapSpecificRecords() {
        if (mapSpecificOpenerRecord == null) {
            mapSpecificOpenerRecord = new HashMap<>();
        }
        if (mapSpecificBuildOrderRecord == null) {
            mapSpecificBuildOrderRecord = new HashMap<>();
        }
    }
}
