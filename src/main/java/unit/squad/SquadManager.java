package unit.squad;

import bwapi.Color;
import bwapi.Game;
import bwapi.Unit;
import bwapi.UnitType;
import info.InformationManager;
import org.bk.ass.sim.BWMirrorAgentFactory;
import org.bk.ass.sim.Simulator;
import unit.managed.ManagedUnit;
import unit.managed.UnitRole;
import unit.squad.Squad;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static util.Filter.isHostileBuilding;

public class SquadManager {

    private Game game;

    private BWMirrorAgentFactory agentFactory;

    private InformationManager informationManager;

    private HashSet<Squad> fightSquads;

    public SquadManager(Game game, InformationManager informationManager) {
        this.game = game;
        this.informationManager = informationManager;
        this.agentFactory = new BWMirrorAgentFactory();
        this.fightSquads = new HashSet<>();
    }

    public void updateSquads() {
        debugPainters();
        removeEmptySquads();

        // Iterate through squads
        // Check if squads are can merge
        mergeSquads();

        // TODO: split behavior (if unit exceeds squad radius)
        // TODO: simulate squads ONLY against nearest neighbors

        for (Squad fightSquad: fightSquads) {
            fightSquad.onFrame();
            simulateSquad(fightSquad);
        }
    }

    private void removeEmptySquads() {
        List<Squad> emptySquads = new ArrayList<>();
        for (Squad squad: fightSquads) {
            if (squad.size() == 0) {
                emptySquads.add(squad);
            }
        }

        for (Squad squad: emptySquads) {
            fightSquads.remove(squad);
        }
    }

    private void mergeSquads() {
        // Merge every 50 frames
        if (game.getFrameCount() % 50 != 0) {
            return;
        }

        List<Set<Squad>> toMerge = new ArrayList<>();
        Set<Squad> considered = new HashSet<>();
        for (Squad squad1: fightSquads) {
            for (Squad squad2: fightSquads) {
                if (squad1 == squad2) {
                    continue;
                }
                if (considered.contains(squad1) || considered.contains(squad2)) {
                    continue;
                }
                if (squad1.distance(squad2) < 256) {
                    Set<Squad> mergeSet = new HashSet<>();
                    mergeSet.add(squad1);
                    mergeSet.add(squad2);
                    considered.add(squad1);
                    considered.add(squad2);
                    toMerge.add(mergeSet);
                }
            }
        }

        for (Set<Squad> mergeSet: toMerge) {
            Squad newSquad = new Squad();
            for (Squad mergingSquad: mergeSet) {
                newSquad.merge(mergingSquad);
                fightSquads.remove(mergingSquad);
            }
            fightSquads.add(newSquad);
        }
    }

    public void simulateSquad(Squad squad) {
        // Run ASS every 50 frames
        HashSet<ManagedUnit> managedFighters = squad.getMembers();
        if (game.getFrameCount() % 50 == 0 && managedFighters.size() > 0 && informationManager.isEnemyUnitVisible()) {
            HashSet<Unit> enemyUnits = informationManager.getEnemyUnits();
            HashSet<Unit> enemyBuildings = informationManager.getEnemyBuildings();
            Simulator simulator = new Simulator.Builder().build();

            for (ManagedUnit managedUnit: managedFighters) {
                simulator.addAgentA(agentFactory.of(managedUnit.getUnit()));
            }

            for (Unit enemyUnit: enemyUnits) {
                if (enemyUnit.getType() == UnitType.Unknown) {
                    continue;
                }
                if ( (int) enemyUnit.getPosition().getDistance(squad.getCenter()) > 256) {
                    continue;
                }
                try {
                    simulator.addAgentB(agentFactory.of(enemyUnit));
                } catch (ArithmeticException e) {
                    return;
                }
            }

            for (Unit enemyBuilding: enemyBuildings) {
                if (!isHostileBuilding(enemyBuilding.getType())) {
                    continue;
                }
                try {
                    simulator.addAgentB(agentFactory.of(enemyBuilding));
                } catch (ArithmeticException e) {
                    return;
                }
            }


            simulator.simulate(150); // Simulate 15 seconds

            if (simulator.getAgentsA().isEmpty()) {
                for (ManagedUnit managedUnit: managedFighters) {
                    managedUnit.setRole(UnitRole.RETREAT);
                    managedUnit.setRetreatTarget(informationManager.getMyBase().getLocation());
                }
                return;
            }

            if (!simulator.getAgentsB().isEmpty()) {
                // If less than half of units are left, retreat
                float percentRemaining = (float) simulator.getAgentsA().size() / managedFighters.size();
                if (percentRemaining < 0.20) {
                    for (ManagedUnit managedUnit: managedFighters) {
                        managedUnit.setRole(UnitRole.RETREAT);
                        managedUnit.setRetreatTarget(informationManager.getMyBase().getLocation());
                    }
                    return;
                }
            }

            for (ManagedUnit managedUnit: managedFighters) {
                managedUnit.setRole(UnitRole.FIGHT);
            }
            return;
        }
    }

    public void addManagedUnit(ManagedUnit managedUnit) {
        for (Squad squad: fightSquads) {
            if (squad.distance(managedUnit) < 256) {
                squad.addUnit(managedUnit);
                return;
            }
        }

        // No assignment, create new squad
        Squad newSquad = new Squad();
        newSquad.setCenter(managedUnit.getUnit().getPosition());
        newSquad.addUnit(managedUnit);
        fightSquads.add(newSquad);
    }

    public void removeManagedUnit(ManagedUnit managedUnit) {
        for (Squad squad: fightSquads) {
            if (squad.containsManagedUnit(managedUnit)) {
                squad.removeUnit(managedUnit);
                return;
            }
        }
    }

    private void debugPainters() {
        for (Squad squad: fightSquads) {
            game.drawCircleMap(squad.getCenter(), 256, Color.White);
        }
    }
}
