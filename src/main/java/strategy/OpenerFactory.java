package strategy;

import bwapi.Race;
import strategy.openers.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class OpenerFactory {
    private static int TWO = 2;

    private Race opponentRace;

    private List<Opener> allOpeners = new ArrayList<>();
    private Map<String, Opener> playableOpeners = new HashMap<>();

    public OpenerFactory(int numStartingLocations, Race opponentRace) {
        this.opponentRace = opponentRace;

        initOpeners(numStartingLocations);
    }

    public Opener getByName(String name) {
        return playableOpeners.get(name);
    }

    public List<String> listAllOpenerNames() {
        return allOpeners.stream().map(s -> s.getNameString()).collect(Collectors.toList());
    }

    public Set<String> getPlayableOpeners() {
        return playableOpeners.values().stream().map(s -> s.getNameString()).collect(Collectors.toSet());
    }

    private void initOpeners(int numStartingLocations) {
        allOpeners.add(new NinePoolSpeed());
        allOpeners.add(new TwelveHatch());
        allOpeners.add(new OverPool());
        allOpeners.add(new FivePool());
        allOpeners.add(new FourPool());
        allOpeners.add(new TwelvePool());
        allOpeners.add(new NineHatch());

        for (Opener opener: allOpeners) {
            if (numStartingLocations != TWO && opener.getName() == OpenerName.FOUR_POOL) {
                continue;
            }

            if (!playsRace(opener)) {
                continue;
            }
            playableOpeners.put(opener.getNameString(), opener);
        }
    }

    private boolean playsRace(Opener opener) {
        switch(this.opponentRace) {
            case Zerg:
                return playsZerg(opener);
            case Terran:
                return playsTerran(opener);
            case Protoss:
                return playsProtoss(opener);
            default:
                return playsRandom(opener);
        }
    }

    private boolean playsZerg(Opener opener) {
        switch (opener.getName()) {
            case OVER_POOL:
            case NINE_POOL_SPEED:
            case NINE_HATCH:
            case TWELVE_HATCH:
            case TWELVE_POOL:
                return true;
            default:
                return false;
        }
    }

    private boolean playsTerran(Opener opener) {
        switch (opener.getName()) {
            case FOUR_POOL:
            case TWELVE_HATCH:
            case TWELVE_POOL:
            case NINE_POOL_SPEED:
                return true;
            default:
                return false;
        }
    }

    private boolean playsProtoss(Opener opener) {
        switch (opener.getName()) {
            case OVER_POOL:
            case FOUR_POOL:
            case NINE_POOL_SPEED:
            case TWELVE_POOL:
            case TWELVE_HATCH:
                return true;
            default:
                return false;
        }
    }

    private boolean playsRandom(Opener opener) {
        switch (opener.getName()) {
            case OVER_POOL:
            case NINE_POOL_SPEED:
            case FOUR_POOL:
            case FIVE_POOL:
                return true;
            default:
                return false;
        }
    }
}
