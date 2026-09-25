package info;

import bwapi.Position;
import bwapi.TilePosition;
import bwem.Base;
import bwem.BaseFixture;
import info.map.GameMap;
import info.map.MapTile;
import info.map.MapTileType;

import java.util.Arrays;
import java.util.List;

/**
 * The three starting locations game LV28400N touched on (4)Icarus, from its unit_events.csv, on an open map with
 * one gas expansion twelve tiles from each start standing in for its natural. A 4x3 depot at location (x, y)
 * reports its centre at pixel (32x + 64, 32y + 48), so our main's centre (1440,304) is location (43,8), the
 * empty start's (320,2512) is (8,77), and the real Nexus at (3776,1552) stands on (116,47). The map's fourth
 * start is left out: the game never touched it.
 */
public final class LV28400NFixture {

    public static final Position PROXY_GATEWAY = new Position(416, 1616);

    public static final Position PROXY_PYLON = new Position(384, 1696);

    public static final Position SECOND_PROXY_GATEWAY = new Position(288, 1712);

    public static final Position REAL_NEXUS = new Position(3776, 1552);

    private static final int MAP_SIZE = 128;

    public final Base ourMain = BaseFixture.startingLocation(new TilePosition(43, 8));

    public final Base emptyStart = BaseFixture.startingLocation(new TilePosition(8, 77));

    public final Base realMain = BaseFixture.startingLocation(new TilePosition(116, 47));

    public final Base ourNatural = BaseFixture.expansionWithGas(new TilePosition(43, 20));

    public final Base emptyStartNatural = BaseFixture.expansionWithGas(new TilePosition(8, 65));

    public final Base realNatural = BaseFixture.expansionWithGas(new TilePosition(104, 47));

    public final List<Base> bases = Arrays.asList(ourMain, emptyStart, realMain, ourNatural, emptyStartNatural,
            realNatural);

    public final BaseData baseData = new BaseData(bases);

    public LV28400NFixture() {
        baseData.initializeMainBase(ourMain, openMap());
    }

    /**
     * The same bases without the empty start and its natural, as on a two-player map, where the real main is
     * the only other start from the first frame.
     */
    public BaseData twoStartBaseData() {
        BaseData twoStarts = new BaseData(Arrays.asList(ourMain, realMain, ourNatural, realNatural));
        twoStarts.initializeMainBase(ourMain, openMap());
        return twoStarts;
    }

    private static GameMap openMap() {
        GameMap gameMap = new GameMap(MAP_SIZE, MAP_SIZE);
        for (int x = 0; x < MAP_SIZE; x++) {
            for (int y = 0; y < MAP_SIZE; y++) {
                MapTile tile = new MapTile(new TilePosition(x, y), 0, true, true, MapTileType.NORMAL);
                tile.setGroundOccupiable(true);
                gameMap.addTile(tile, x, y);
            }
        }
        return gameMap;
    }
}
