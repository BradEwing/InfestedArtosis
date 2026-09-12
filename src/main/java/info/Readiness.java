package info;

/**
 * Which question a gate is asking about a structure it counts.
 *
 * <p>Every structure passes through three stages: a plan in flight, a shell standing on the map
 * part-built, and a finished building. A gate that reads a raw count has to pick a point in that
 * sequence, and the two useful points answer different questions. Naming the point at the call
 * site is what this enum is for: {@link GameState#structureCount} takes one and has no default,
 * so a new gate cannot inherit the wrong answer by saying nothing.
 *
 * <p>The distinction does not arise for mobile units. Their count already folds in the plans and
 * eggs in flight, so {@link GameState#ourUnitCount} answers the committed question for them and
 * {@link GameState#ourLivingUnitCount} the usable one.
 */
public enum Readiness {
    /**
     * Finished structures only: ones that can produce, research, detect or earn income now.
     *
     * <p>What a gate wants when the structure has to do something before the gate's decision
     * pays off. An Extractor standing half-built mines nothing, and a Lair still morphing cannot
     * start a Spire.
     */
    USABLE,

    /**
     * Finished structures, structures under construction, and building plans still in flight.
     *
     * <p>What a gate wants when the decision follows from having chosen the structure rather than
     * from the structure working yet. Reading {@link #USABLE} in that position makes the gate
     * wait out a build time it has already paid for: a second Extractor gated on a usable Spire
     * is withheld for the whole 1,800-frame Spire build, long after the Spire is a settled fact.
     */
    COMMITTED
}
