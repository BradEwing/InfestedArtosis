package info.map;

import bwapi.Game;
import bwapi.Point;
import bwapi.TilePosition;

import lombok.Data;
import lombok.NonNull;

@Data
public class TileInfo {
    @NonNull
    private TilePosition tile;
    @NonNull
    private int scoutImportance;
    @NonNull
    private boolean isWalkable;
    @NonNull
    private TileType type;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TilePosition tp = (TilePosition) o;
        return this.tile == tp;
    }

    @Override
    public int hashCode() {
        return tile.hashCode();
    }

    public int getX() {
        return tile.getX();
    }

    public int getY() {
        return tile.getY();
    }
}
