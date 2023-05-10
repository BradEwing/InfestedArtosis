package info.exception;

/**
 * NoWalkablePathException is thrown by path-finding search if no walkable path exists between two TilePositions.
 *
 * Useful to identify islands or paths blocked by neutral structures.
 */
public class NoWalkablePathException extends Exception {
    public NoWalkablePathException(String s) {
        super(s);
    }
}
