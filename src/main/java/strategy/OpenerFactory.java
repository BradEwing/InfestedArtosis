package strategy;

import strategy.openers.FivePool;
import strategy.openers.FourPool;
import strategy.openers.NinePoolSpeed;
import strategy.openers.Opener;
import strategy.openers.OverPool;
import strategy.openers.TwelveHatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class OpenerFactory {
    private static int FOUR = 4;

    private List<Opener> allOpeners = new ArrayList<>();
    private Map<String, Opener> playableOpeners = new HashMap<>();

    public OpenerFactory(int numStartingLocations) {
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

        for (Opener opener: allOpeners) {
            if (numStartingLocations == FOUR && !opener.playsFourPlayerMap()) {
                continue;
            }
            playableOpeners.put(opener.getNameString(), opener);
        }
    }
}
