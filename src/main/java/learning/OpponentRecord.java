package learning;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.util.HashMap;
import java.util.Map;

@Jacksonized
@Builder
@Data
public class OpponentRecord {
    private String name;
    private String race;
    private int wins;
    private int losses;
    private Map<String, StrategyRecord> opponentStrategies;
}
