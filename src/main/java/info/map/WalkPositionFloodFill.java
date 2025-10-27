package info.map;

import bwapi.Game;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwapi.WalkPosition;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

/**
 * Flood fill algorithm to calculate all accessible WalkPositions from a starting position.
 * Uses breadth-first search to explore all connected walkable areas.
 * Considers neutral structures and resources as barriers to movement.
 */
public class WalkPositionFloodFill {
    
    private final Game game;
    
    public WalkPositionFloodFill(Game game) {
        this.game = game;
    }
    
    /**
     * Performs flood fill from the main base to find all accessible WalkPositions.
     * 
     * @param mainBaseTilePosition The starting TilePosition (main base location)
     * @return Set of all accessible WalkPositions
     */
    public Set<WalkPosition> calculateAccessibleWalkPositions(TilePosition mainBaseTilePosition) {
        Set<WalkPosition> accessiblePositions = new HashSet<>();
        Queue<WalkPosition> queue = new LinkedList<>();
        Set<WalkPosition> visited = new HashSet<>();
        
        WalkPosition startPosition = mainBaseTilePosition.toWalkPosition();
        
        queue.add(startPosition);
        visited.add(startPosition);
        
        int[] dx = {-1, -1, -1, 0, 0, 1, 1, 1};
        int[] dy = {-1, 0, 1, -1, 1, -1, 0, 1};
        
        while (!queue.isEmpty()) {
            WalkPosition current = queue.poll();
            accessiblePositions.add(current);
            
            for (int i = 0; i < 8; i++) {
                int newX = current.getX() + dx[i];
                int newY = current.getY() + dy[i];
                
                if (newX < 0 || newY < 0 || newX >= game.mapWidth() * 4 || newY >= game.mapHeight() * 4) {
                    continue;
                }
                
                WalkPosition neighbor = new WalkPosition(newX, newY);
                
                if (visited.contains(neighbor)) {
                    continue;
                }
                
                if (isAccessibleWalkPosition(neighbor)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }
        
        return accessiblePositions;
    }
    
    /**
     * Checks if a WalkPosition is accessible, considering neutral structures and resources as barriers.
     * 
     * @param walkPosition The WalkPosition to check
     * @return true if the position is accessible, false if blocked by neutral structures/resources
     */
    private boolean isAccessibleWalkPosition(WalkPosition walkPosition) {
        if (!game.isWalkable(walkPosition)) {
            return false;
        }
        
        for (Unit unit : game.getUnitsInRadius(walkPosition.toPosition(), 8)) {
            if (isNeutralBarrier(unit)) {
                if (unitBlocksWalkPosition(unit, walkPosition)) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * Determines if a unit is a neutral structure or resource that should act as a barrier.
     * 
     * @param unit The unit to check
     * @return true if the unit is a neutral barrier, false otherwise
     */
    private boolean isNeutralBarrier(Unit unit) {
        if (unit.getPlayer() != game.neutral()) {
            return false;
        }
        
        UnitType unitType = unit.getType();
        
        if (unitType.isBuilding()) {
            return true;
        }
        
        if (unitType.isResourceContainer() || unitType.isMineralField()) {
            return true;
        }
        
        if (unitType.isSpecialBuilding()) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Checks if a unit actually blocks a specific WalkPosition.
     * 
     * @param unit The unit to check
     * @param walkPosition The WalkPosition to check
     * @return true if the unit blocks this WalkPosition, false otherwise
     */
    private boolean unitBlocksWalkPosition(Unit unit, WalkPosition walkPosition) {
        WalkPosition unitWalkPos = unit.getPosition().toWalkPosition();
        
        int unitWidth = (unit.getType().tileWidth() * 4);
        int unitHeight = (unit.getType().tileHeight() * 4);
        
        int unitLeft = unitWalkPos.getX();
        int unitRight = unitLeft + unitWidth;
        int unitTop = unitWalkPos.getY();
        int unitBottom = unitTop + unitHeight;
        
        int wpX = walkPosition.getX();
        int wpY = walkPosition.getY();
        
        return wpX >= unitLeft && wpX < unitRight && wpY >= unitTop && wpY < unitBottom;
    }
}
