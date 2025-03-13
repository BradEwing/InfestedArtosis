package info.map;

import bwapi.TilePosition;

import lombok.Data;
import lombok.NonNull;

@Data
public class MapTile {
    @NonNull
    private TilePosition tile;
    @NonNull
    private int scoutImportance;
    @NonNull
    private boolean isBuildable;
    @NonNull
    private boolean isWalkable;
    @NonNull
    private MapTileType type;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MapTile tp = (MapTile) o;
        return this.tile == tp.getTile();
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
