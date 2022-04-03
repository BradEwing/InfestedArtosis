package map;

import bwapi.TilePosition;

import lombok.Data;
import lombok.NonNull;

@Data
public class TileInfo {
    @NonNull
    private TilePosition tile;
    @NonNull
    private int importance;
    @NonNull
    private boolean isWalkable;
    @NonNull
    private TileType type;
}
