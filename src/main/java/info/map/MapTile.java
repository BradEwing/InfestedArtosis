package info.map;

import bwapi.TilePosition;

import lombok.Data;
import lombok.NonNull;

@Data
public class MapTile {
    @NonNull
    private TilePosition tile;
    private int scoutImportance;
    private boolean isBuildable;
    private boolean isWalkable;
    @NonNull
    private MapTileType type;

    public MapTile(TilePosition tile, int scoutImportance, boolean isBuildable, boolean isWalkable, MapTileType type) {
        this.tile = tile;
        this.scoutImportance = scoutImportance;
        this.isBuildable = isBuildable;
        this.isWalkable = isWalkable;
        this.type = type;
    }

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
