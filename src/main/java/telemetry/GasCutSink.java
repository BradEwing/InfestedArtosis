package telemetry;

/**
 * Receives the changes of the gas cut a build makes after Metabolic Boost. Implementations are registered with
 * {@link GasCutTelemetry} and must never throw: they run inside the per frame worker update, where an escaped
 * exception kills the JVM.
 */
public interface GasCutSink {

    /**
     * The gas cut engaged or was lifted.
     *
     * @param frame current frame
     * @param event CUT when the cut engaged, DEMAND when a queued plan costing gas lifted it
     * @param applied true when the cut decides the worker split, false when its switch is off and the row only records
     *                what the cut would have done
     * @param planType type of the queued plan that lifted the cut, or null on a CUT row
     * @param planGas gas price of that plan, or 0 on a CUT row
     * @param availableGas gas mined and unreserved
     * @param gasWorkers drones gathering gas
     */
    void onGasCut(int frame, String event, boolean applied, String planType, int planGas, int availableGas,
                  int gasWorkers);
}
