package unit.managed;

import bwapi.Game;
import bwapi.Order;
import bwapi.Position;
import bwapi.Unit;
import bwapi.UnitType;
import config.Config;
import info.map.GameMap;
import util.Time;

public class Mutalisk extends ManagedUnit {
    private Time retreatUntilFrame = null;
    static final int RETREAT_DURATION_FRAMES = 30;
    /** Tuning value: frames between two decisions while the weapon cools down. */
    static final int KITE_ORDER_FRAMES = 1;
    private final CooldownKite kite = new CooldownKite();
    private final CooldownKite shadowKite = new CooldownKite();

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
     * Sets the role. A change of role drops the cooldown kite's plan, so a Mutalisk that left FIGHT and came back
     * within one cooldown plans it again.
     *
     * @param newRole the role
     */
    @Override
    public void setRole(UnitRole newRole) {
        if (newRole != role) {
            kite.reset();
            shadowKite.reset();
        }
        super.setRole(newRole);
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
        MainStep step = mainStep(unit.isAttackFrame(), currentFrame(), retreatUntilFrame);
        if (step == MainStep.START_RETREAT) {
            retreatUntilFrame = new Time(currentFrame() + RETREAT_DURATION_FRAMES);
            return;
        }
        setUnready(4);

        if (step == MainStep.FLEE) {
            if (retreatTarget == null) {
                retreatTarget = getRetreatPosition();
            }
            if (retreatTarget != null) {
                issueMove(retreatTarget);
            } else if (rallyPoint != null) {
                issueMove(rallyPoint);
            }
            return;
        }

        engageOrMoveOn();
    }

    /**
     * Kites one weapon cooldown at a time. Nothing is ordered while the attack animation plays. Each time the weapon
     * fires, or the role or fight target changes, the previous plan and retreat target are dropped and the cooldown is
     * planned once, see {@link CooldownKite#plan}, with the cooldown the unit reports, see {@link #cooldownFramesLeft}.
     * With no back-off it keeps its attack on the target. It flies to the back-off point, ordering the move again only
     * when the unit is not already moving there, and turns back once the cooldown left is only enough to fly back
     * within its reach of the target, see {@link CooldownKite#mustTurnBack}; a committed back-off turns back on
     * arrival instead. It attacks again when the cooldown ends.
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
        if (kite.begin(getLastWeaponFireFrame(), role, fightTarget.getID())) {
            retreatTarget = null;
        }
        if (cooldown <= 0) {
            setUnready(4);
            attackFightTarget();
            return;
        }

        setUnready(KITE_ORDER_FRAMES);
        if (!kite.isPlanned()) {
            CooldownKite.Plan plan = planCooldown(cooldown);
            kite.accept(plan);
            getAttackCycles().notePlan(plan.getOutcome());
        }

        Position self = unit.getPosition();
        Position point = kite.getPoint();
        boolean arrived = point != null && self.getDistance(point) <= CooldownKite.ARRIVAL_DISTANCE;
        boolean turnBack = point != null && (kite.isCommitted() ? arrived
                : CooldownKite.mustTurnBack(self, fightTarget.getPosition(), targetReach(fightTarget.getType()),
                ownTopSpeed(), cooldown - overheadFrames()));
        switch (CooldownKite.step(false, cooldown, point, arrived, turnBack)) {
            case BACK_OFF:
                moveTo(point);
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
     * Plans this cooldown from where the Mutalisk and its fight target stand now.
     *
     * @param cooldown frames left on the cooldown
     * @return the plan
     */
    private CooldownKite.Plan planCooldown(int cooldown) {
        double topSpeed = ownTopSpeed();
        return CooldownKite.plan(unit.getPosition(), fightTarget.getPosition(), targetReach(fightTarget.getType()),
                airThreats(), topSpeed, CooldownKite.flight(topSpeed, cooldown - overheadFrames()),
                mapWidthPixels(), mapHeightPixels());
    }

    /**
     * With the kite off, plans each cooldown of a fighting Mutalisk read-only once its attack animation is over and
     * counts the outcome, so the control arm records what the kite would have decided. Nothing is ordered. With the
     * kite on, the kite counts its own plans.
     *
     * @param cooldown frames left on the weapon cooldown
     */
    @Override
    protected void observeKite(int cooldown) {
        if (Config.mutaCooldownKite || role != UnitRole.FIGHT || cooldown <= 0 || unit.isAttackFrame()
                || fightTarget == null || !fightTarget.exists()) {
            return;
        }
        shadowKite.begin(getLastWeaponFireFrame(), role, fightTarget.getID());
        if (shadowKite.isPlanned()) {
            return;
        }
        CooldownKite.Plan plan = planCooldown(cooldown);
        shadowKite.accept(plan);
        getAttackCycles().notePlan(plan.getOutcome());
    }

    /**
     * @return frames left on the Glaive Wurm's cooldown. JBWAPI gives the Mutalisk the Glaive Wurm as both its ground
     *     and its air weapon, and which of the two cooldown fields the game counts down depends on the target, so the
     *     larger is read
     */
    int cooldownFramesLeft() {
        return Math.max(unit.getGroundWeaponCooldown(), unit.getAirWeaponCooldown());
    }

    /**
     * @return frames of a cooldown set aside before a planned flight: the command latency, the frames between two
     *     orders, and {@link CooldownKite#TURN_FRAMES}
     */
    protected int overheadFrames() {
        return game.getLatencyFrames() + KITE_ORDER_FRAMES + CooldownKite.TURN_FRAMES;
    }

    /**
     * @return the Mutalisk's top speed for its owner, in pixels per frame
     */
    protected double ownTopSpeed() {
        return unit.getPlayer().topSpeed(unit.getType());
    }

    protected int currentFrame() {
        return game.getFrameCount();
    }

    protected int mapWidthPixels() {
        return game.mapWidth() * 32;
    }

    protected int mapHeightPixels() {
        return game.mapHeight() * 32;
    }

    /**
     * @param targetType the target's type
     * @return the Mutalisk's reach to the target centre to centre: its weapon's range for its owner plus both units'
     *     smallest half extents
     */
    protected int targetReach(UnitType targetType) {
        int range = unit.getPlayer().weaponMaxRange(unit.getType().airWeapon());
        return range + CooldownKite.smallestHalfExtent(unit.getType()) + CooldownKite.smallestHalfExtent(targetType);
    }

    protected void issueMove(Position point) {
        unit.move(point);
    }

    protected void issueAttack(Unit target) {
        unit.attack(target);
    }

    /**
     * Orders the move to the point unless the unit is already moving there.
     */
    private void moveTo(Position point) {
        if (!unit.isIdle() && unit.getOrder() == Order.Move && point.equals(unit.getOrderTargetPosition())) {
            return;
        }
        issueMove(point);
    }

    /**
     * Orders the attack on the fight target unless that attack is already the unit's order, so an attack under way is
     * not issued again.
     */
    private void attackFightTarget() {
        if (unit.getOrder() == Order.AttackUnit && fightTarget.equals(unit.getOrderTarget())) {
            return;
        }
        issueAttack(fightTarget);
    }

    private void engageOrMoveOn() {
        if (fightTarget != null) {
            issueAttack(fightTarget);
            return;
        }

        if (movementTargetPosition != null) {
            issueMove(movementTargetPosition.toPosition());
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
     * @return the widest counted threat that covered the Mutalisk when this cooldown was planned, or null
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
