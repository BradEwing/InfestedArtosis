package learning;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Builder
@Data
public class OpponentRecord {
    private String name;
    private String race;

    private int wins;
    private int losses;
    private int version;

    private Map<String, Record> openerRecord;
    private Map<String, Record> buildOrderRecord;

    public int totalGames() {
        return this.wins + this.losses;
    }
}
