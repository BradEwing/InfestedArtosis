package info.map;

import bwem.Base;
import info.exception.NoWalkablePathException;
import lombok.Getter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class StartingLocationPaths {

    private final HashMap<Base, GroundPath> paths = new HashMap<>();
    @Getter
    private final HashSet<Base> islands = new HashSet<>();
    @Getter
    private final Base naturalExpansion;

    public StartingLocationPaths(Base origin, Iterable<Base> allBases, GameMap map) {
        for (Base b : allBases) {
            if (b == origin) continue;
            try {
                paths.put(b, map.aStarSearch(origin.getLocation(), b.getLocation()));
            } catch (NoWalkablePathException e) {
                islands.add(b);
            }
        }

        this.naturalExpansion = paths.entrySet().stream()
                .filter(e -> !e.getKey().getGeysers().isEmpty())
                .min(Map.Entry.comparingByValue(new GroundPathComparator()))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    public GroundPath getPath(Base base) {
        return paths.get(base);
    }
}
