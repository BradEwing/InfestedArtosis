package learning;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GameRecordTest {

    @Test
    void toCsvRowIncludesFrameCount() {
        GameRecord record = GameRecord.builder()
                .timestamp(1761107433421L)
                .isWinner(true)
                .numStartingLocations(4)
                .mapName("(4)Polypoid_1.65.scx")
                .opponentName("Akilae Tribe")
                .opponentRace("Protoss")
                .opener("Overpool")
                .buildOrder("3HatchMuta")
                .detectedStrategies("2Gate;1Base")
                .frameCount(18432)
                .build();

        assertEquals("1761107433421,true,4,(4)Polypoid_1.65.scx,Akilae Tribe,Protoss,Overpool,3HatchMuta,2Gate;1Base,18432",
                record.toCsvRow());
    }

    @Test
    void fromCsvRowRoundTrips() {
        GameRecord original = GameRecord.builder()
                .timestamp(1L)
                .isWinner(false)
                .numStartingLocations(3)
                .mapName("map, with comma")
                .opponentName("Bot \"quoted\"")
                .opponentRace("Zerg")
                .opener("9PoolSpeed")
                .buildOrder("9PoolSpeed")
                .detectedStrategies("")
                .frameCount(4321)
                .build();

        GameRecord parsed = GameRecord.fromCsvRow(original.toCsvRow());

        assertEquals(original, parsed);
    }

    @Test
    void fromCsvRowToleratesLegacyRowWithoutFrameCount() {
        GameRecord parsed = GameRecord.fromCsvRow(
                "1761282554674,false,3,(3)PowerBond_1.00.scx,Akilae Tribe,Protoss,Overpool,3HatchMuta,2Gate;1Base");

        assertEquals(1761282554674L, parsed.getTimestamp());
        assertEquals("Protoss", parsed.getOpponentRace());
        assertEquals("2Gate;1Base", parsed.getDetectedStrategies());
        assertEquals(0, parsed.getFrameCount());
    }

    @Test
    void fromCsvRowToleratesEmptyFrameCount() {
        GameRecord parsed = GameRecord.fromCsvRow("1,true,2,Map,Opp,Terran,12Hatch,12Hatch,,");

        assertEquals(0, parsed.getFrameCount());
    }

    @Test
    void toCsvRowWritesChainedBuildOrderVerbatim() {
        GameRecord record = GameRecord.builder()
                .timestamp(1761399150203L)
                .isWinner(true)
                .numStartingLocations(4)
                .mapName("(4)Polypoid_1.65.scx")
                .opponentName("Akilae Tribe")
                .opponentRace("Protoss")
                .opener("Overpool")
                .buildOrder("2HatchMuta;3HatchLurker")
                .detectedStrategies("2Gate")
                .frameCount(20480)
                .build();

        assertEquals("1761399150203,true,4,(4)Polypoid_1.65.scx,Akilae Tribe,Protoss,Overpool,2HatchMuta;3HatchLurker,2Gate,20480",
                record.toCsvRow());
    }

    @Test
    void fromCsvRowRoundTripsChainedBuildOrder() {
        GameRecord original = GameRecord.builder()
                .timestamp(2L)
                .isWinner(true)
                .numStartingLocations(4)
                .mapName("Map")
                .opponentName("Opp")
                .opponentRace("Terran")
                .opener("Overpool")
                .buildOrder("2HatchMuta;3HatchLurker")
                .detectedStrategies("")
                .frameCount(100)
                .build();

        assertEquals(original, GameRecord.fromCsvRow(original.toCsvRow()));
    }
}
