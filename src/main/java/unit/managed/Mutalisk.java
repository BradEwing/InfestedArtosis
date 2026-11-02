package unit.managed;

import bwapi.Game;
import bwapi.Order;
import bwapi.Position;
import bwapi.Unit;
import bwapi.UnitType;
import config.Config;
import info.map.GameMap;
import util.Time;

import java.util.List;

public class Mutalisk extends ManagedUnit {
    private Time retreatUntilFrame = null;
    static final int RETREAT_DURATION_FRAMES = 30;
    /** Tuning value: frames between two orders while the weapon cools down. */
    static final int KITE_ORDER_FRAMES = 1;
    private final CooldownKite kite = new CooldownKite();

    public Mutalisk(Game game, Unit unit, UnitRole role, GameMap gameMap) {
        super(game, unit, role, gameMap);
    }

    /**
     * Fights its target. With {@link Config#mutaCooldownKite} on it kites each weapon cooldown, see
     * {@link #fightWithCooldownKite}; with it off, every attack frame starts a flee of
     * {@link #RETREAT_DURATION_FRAMES} away from every enemy near it, see {@link #mainStep}.
     */
    @Override
    protected void fight() {
        if (Config.mutaCooldownKite) {
            fightWithCooldownKite();
            return;
        }
        fightWithFixedRetreat();
    }

    /**
     * What a fighting Mutalisk does with the cooldown kite off.
     */
    enum MainStep {
        /** Its attack frame: a flee window opens and no order is issued. */
        START_RETREAT,
        /** Inside the flee window: fly to the retreat point. */
        FLEE,
        /** Attack the target, or move on when there is none. */
        ENGAGE
    }

    /**
     * Picks the step of a fighting Mutalisk with the cooldown kite off.
     *
     * @param attackFrame true while the attack animation plays
     * @param frame current frame
     * @param retreatUntil frame the flee window ends, or null when none was opened
     * @return the step
     */
    static MainStep mainStep(boolean attackFrame, int frame, Time retreatUntil) {
        if (attackFrame) {
            return MainStep.START_RETREAT;
        }
        if (retreatUntil != null && frame < retreatUntil.getFrames()) {
            return MainStep.FLEE;
        }
        return MainStep.ENGAGE;
    }

    private void fightWithFixedRetreat() {
        MainStep step = mainStep(unit.isAttackFrame(), game.getFrameCount(), retreatUntilFrame);
        if (step == MainStep.START_RETREAT) {
            retreatUntilFrame = new Time(game.getFrameCount() + RETREAT_DURATION_FRAMES);
            return;
        }
        setUnready(4);

        if (step == MainStep.FLEE) {
            if (retreatTarget == null) {
                retreatTarget = getRetreatPosition();
            }
            if (retreatTarget != null) {
                unit.move(retreatTarget);
            } else if (rallyPoint != null) {
                unit.move(rallyPoint);
            }
            return;
        }

        engageOrMoveOn();
    }

    /**
     * Kites one weapon cooldown at a time. Nothing is ordered while the attack animation plays. Each time the weapon
     * fires, the previous back-off point and retreat target are dropped and a new back-off is planned once, from the
     * threats that can attack air and whose reach covers the Mutalisk, see {@link CooldownKite#backOffPoint}, with the
     * cooldown the unit reports. With no such threat it keeps its attack on the target. It turns back once the
     * cooldown left is only enough to fly back within its reach of the target, see {@link CooldownKite#mustTurnBack},
     * and attacks again when the cooldown ends.
     */
    private void fightWithCooldownKite() {
        if (unit.isAttackFrame()) {
            return;
        }
        if (fightTarget == null || !fightTarget.exists()) {
            setUnready(4);
            engageOrMoveOn();
            return;
        }

        int cooldown = cooldownFramesLeft();
        if (kite.begin(getLastWeaponFireFrame())) {
            retreatTarget = null;
        }
        if (cooldown <= 0) {
            setUnready(4);
            attackFightTarget();
            return;
        }

        setUnready(KITE_ORDER_FRAMES);
        Position self = unit.getPosition();
        Position target = fightTarget.getPosition();
        int targetReach = targetReach(fightTarget.getType());
        double topSpeed = unit.getType().topSpeed();
        int framesLeft = cooldown - overheadFrames();
        if (!kite.isPlanned()) {
            List<CooldownKite.Threat> near = CooldownKite.near(airThreats(), self);
            kite.plan(CooldownKite.backOffPoint(self, target, targetReach, near,
                    CooldownKite.flight(topSpeed, framesLeft), game.mapWidth() * 32, game.mapHeight() * 32),
                    CooldownKite.widest(near));
        }

        Position point = kite.getPoint();
        boolean turnBack = point != null
                && CooldownKite.mustTurnBack(self, target, targetReach, topSpeed, framesLeft);
        boolean arrived = point != null && self.getDistance(point) <= CooldownKite.ARRIVAL_DISTANCE;
        switch (CooldownKite.step(false, cooldown, point, arrived, turnBack)) {
            case BACK_OFF:
                unit.move(point);
                break;
            case HOLD:
                break;
            default:
                kite.turnBack();
                attackFightTarget();
                break;
        }
    }

    /**
     * @return frames left on the cooldown of the Glaive Wurm, the Mutalisk's only weapon, which is its air weapon
     *     against ground and air targets alike
     */
    int cooldownFramesLeft() {
        return unit.getAirWeaponCooldown();
    }

    /**
     * @return frames of a cooldown set aside before a planned flight: the command latency, the frames between two
     *     orders, and {@link CooldownKite#TURN_FRAMES}
     */
    private int overheadFrames() {
        return game.getLatencyFrames() + KITE_ORDER_FRAMES + CooldownKite.TURN_FRAMES;
    }

    /**
     * @param targetType the target's type
     * @return the Mutalisk's reach to the target centre to centre: its air weapon's range for its owner, which the
     *     Glaive Wurm uses against ground and air alike, plus both units' smallest half extents
     */
    private int targetReach(UnitType targetType) {
        int range = unit.getPlayer().weaponMaxRange(unit.getType().airWeapon());
        return range + CooldownKite.smallestHalfExtent(unit.getType()) + CooldownKite.smallestHalfExtent(targetType);
    }

    /**
     * Orders the attack on the fight target unless that attack is already the unit's order, so an attack under way is
     * not issued again.
     */
    private void attackFightTarget() {
        if (unit.getOrder() == Order.AttackUnit && fightTarget.equals(unit.getOrderTarget())) {
            return;
        }
        unit.attack(fightTarget);
    }

    private void engageOrMoveOn() {
        if (fightTarget != null) {
            unit.attack(fightTarget);
            return;
        }

        if (movementTargetPosition != null) {
            unit.move(movementTargetPosition.toPosition());
            return;
        }

        role = UnitRole.IDLE;
    }

    /**
     * @return this cooldown's back-off point, or null when the Mutalisk stays on its target
     */
    public Position getKitePoint() {
        return kite.getPoint();
    }

    /**
     * @return the threat with the largest reach that covered the Mutalisk when this cooldown was planned, or null
     */
    public CooldownKite.Threat getKiteThreat() {
        return kite.getThreat();
    }

    /**
     * Moves to the harass destination when one is set, otherwise attacks the fight target. The role is never
     * changed here, so a Mutalisk without an order waits for the next one instead of going idle and being re-homed.
     */
    @Override
    protected void harass() {
        if (unit.isAttackFrame()) {
            return;
        }

        if (harassDestination != null) {
            setUnready(4);
            unit.move(harassDestination);
            return;
        }

        if (fightTarget != null) {
            setUnready(4);
            unit.attack(fightTarget);
        }
    }

    @Override
    protected int retreatScanRadius() {
        return 256;
    }

    @Override
    protected int retreatFleeDistance() {
        return 256;
    }

    @Override
    protected void rally() {
        if (rallyPoint == null) return;

        if (role == UnitRole.RALLY) {
            if (unit.getDistance(rallyPoint) < 16) {
                return;
            }
        }

        if (unit.getDistance(rallyPoint) < 4) {
            return;
        }

        setUnready();
        unit.move(rallyPoint);
    }
}
