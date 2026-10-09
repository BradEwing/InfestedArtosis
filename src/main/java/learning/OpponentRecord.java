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

    @Default
    private int priorGames = 0;

    public int totalGames() {
        return this.wins + this.losses;
    }

    /**
     * Returns the games the bandit has evidence from: the real games plus the seeded prior pseudo-games.
     */
    public int selectionGames() {
        return totalGames() + priorGames;
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
