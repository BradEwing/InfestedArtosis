package strategy;

import bwapi.Race;
import strategy.buildorder.BuildOrder;
import strategy.buildorder.opener.FourPool;
import strategy.buildorder.opener.NinePoolSpeed;
import strategy.buildorder.opener.Overpool;
import strategy.buildorder.protoss.ThreeHatchMuta;
import strategy.buildorder.opener.TwelveHatch;
import strategy.buildorder.opener.TwelvePool;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class BuildOrderFactory {

    private List<BuildOrder> allBuildOrders = new ArrayList<>();
    private List<BuildOrder> allOpeners = new ArrayList<>();
    private Set<BuildOrder> playableOpeners = new HashSet<>();

    private Race opponentRace;

    public BuildOrderFactory(int numStartingLocations, Race opponentRace) {
        this.opponentRace = opponentRace;

        initBuildOrders();
        initOpeners(numStartingLocations);
    }

    public Set<String> getOpenerNames() {
        return playableOpeners.stream()
                .map(bo -> bo.getName())
                .collect(Collectors.toSet());
    }

    public BuildOrder getByName(String name) {
        return allBuildOrders.stream()
                .filter(bo -> Objects.equals(bo.getName(), name))
                .findFirst()
                .orElse(null);
    }

    public boolean isPlayableOpener(BuildOrder buildOrder) {
        return playableOpeners.contains(buildOrder);
    }

    private void initBuildOrders() {
        allBuildOrders.add(new FourPool());
        allBuildOrders.add(new NinePoolSpeed());
        allBuildOrders.add(new Overpool());
        allBuildOrders.add(new ThreeHatchMuta());
        allBuildOrders.add(new TwelveHatch());
        allBuildOrders.add(new TwelvePool());
    }

    private void initOpeners(int numStartingLocations) {
        this.allOpeners = allBuildOrders.stream()
                .filter(bo -> bo.isOpener())
                .collect(Collectors.toList());
        this.playableOpeners = allOpeners.stream()
                .filter(bo -> bo.playsRace(opponentRace))
                .collect(Collectors.toSet());
    }
}
