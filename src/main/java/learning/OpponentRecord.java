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

    public int totalGames() {
        return this.wins + this.losses;
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
