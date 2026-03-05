package unit.squad.cluster;

import lombok.Getter;

@Getter
public class SupplyBreakdown {

    private double groundSupply;
    private double rangedGroundSupply;
    private double airToGroundSupply;
    private double airToAirSupply;
    private double antiAirSupply;

    public SupplyBreakdown(double groundSupply, double rangedGroundSupply, double airToGroundSupply, double airToAirSupply) {
        this(groundSupply, rangedGroundSupply, airToGroundSupply, airToAirSupply, 0);
    }

    public SupplyBreakdown(double groundSupply, double rangedGroundSupply, double airToGroundSupply, double airToAirSupply, double antiAirSupply) {
        this.groundSupply = groundSupply;
        this.rangedGroundSupply = rangedGroundSupply;
        this.airToGroundSupply = airToGroundSupply;
        this.airToAirSupply = airToAirSupply;
        this.antiAirSupply = antiAirSupply;
    }

    public double total() {
        return groundSupply + airToGroundSupply + airToAirSupply;
    }

    public SupplyBreakdown scale(double factor) {
        return new SupplyBreakdown(
                groundSupply * factor,
                rangedGroundSupply * factor,
                airToGroundSupply * factor,
                airToAirSupply * factor,
                antiAirSupply * factor
        );
    }
}
