package dev.selestor1462.cannonencoder;

import dev.selestor1462.cannonencoder.state.CannonMode;
import dev.selestor1462.cannonencoder.state.ClientStateStore;
import dev.selestor1462.cannonencoder.state.SessionState;
import dev.selestor1462.cannonencoder.state.TargetRecord;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

public class StatePersistenceTest {

    @Test
    public void testEasyPlaceAndSequentialRailsPersistAcrossRejoin(@TempDir Path tempDir) {
        Path configFile = tempDir.resolve("cannoncalculator.json");

        // 1. Initial store: default EasyPlace and SequentialRails are false
        ClientStateStore store1 = new ClientStateStore(configFile);
        assertFalse(store1.globalEasyPlace());
        assertFalse(store1.globalSequentialRails());

        String envKey = "singleplayer:my_test_world";
        SessionState state1 = store1.get(envKey);
        assertFalse(state1.easyPlaceEnabled());
        assertFalse(state1.sequentialRails());

        SessionState modified = state1.withEasyPlace(true).withSequentialRails(true);
        store1.set(envKey, modified);

        // 2. Simulate rejoin / reload from disk
        ClientStateStore store2 = new ClientStateStore(configFile);
        assertTrue(store2.globalEasyPlace(), "Global easy place preference should be remembered");
        assertTrue(store2.globalSequentialRails(), "Global sequential rails preference should be remembered");

        SessionState loaded = store2.get(envKey);
        assertNotNull(loaded);
        assertTrue(loaded.easyPlaceEnabled(), "EasyPlace state must be persisted across rejoin");
        assertTrue(loaded.sequentialRails(), "Sequential rails state must be persisted across rejoin");
    }

    @Test
    public void testSequentialRailsDefaultsToFalse(@TempDir Path tempDir) {
        Path configFile = tempDir.resolve("cannoncalculator.json");
        ClientStateStore store = new ClientStateStore(configFile);
        assertFalse(store.globalSequentialRails());

        SessionState emptyState = store.get("singleplayer:default");
        assertFalse(emptyState.sequentialRails());
    }

    @Test
    public void testDifferentEnvironmentsKeepIndependentSettings(@TempDir Path tempDir) {
        Path configFile = tempDir.resolve("cannoncalculator.json");
        ClientStateStore store = new ClientStateStore(configFile);

        store.set("server:play.example.com", SessionState.empty().withEasyPlace(true).withSequentialRails(true));
        store.set("singleplayer:test_creative", SessionState.empty().withEasyPlace(false).withSequentialRails(false));

        ClientStateStore reloaded = new ClientStateStore(configFile);
        SessionState serverSession = reloaded.get("server:play.example.com");
        SessionState spSession = reloaded.get("singleplayer:test_creative");

        assertTrue(serverSession.easyPlaceEnabled());
        assertTrue(serverSession.sequentialRails());

        assertFalse(spSession.easyPlaceEnabled());
        assertFalse(spSession.sequentialRails());
    }

    @Test
    public void testTargetRecordCustomLabelAndDeletion(@TempDir Path tempDir) {
        Path configFile = tempDir.resolve("cannoncalculator.json");
        ClientStateStore store1 = new ClientStateStore(configFile);
        String envKey = "singleplayer:test_world";

        TargetRecord rec1 = new TargetRecord(1000, 2000, CannonMode.FTL_PEARL_CANNON, 0, 1, 2236.0, System.currentTimeMillis(), "Main Base");
        TargetRecord rec2 = new TargetRecord(500, 600, CannonMode.PIG_CANNON, 1, 2, 781.0, System.currentTimeMillis(), "Farm");

        SessionState state = store1.get(envKey).addTarget(rec1).addTarget(rec2);
        store1.set(envKey, state);

        // Reload and verify labels persisted
        ClientStateStore store2 = new ClientStateStore(configFile);
        SessionState loaded = store2.get(envKey);
        assertEquals(2, loaded.targetHistory().size());
        assertEquals("Farm", loaded.targetHistory().get(0).label());
        assertEquals("Main Base", loaded.targetHistory().get(1).label());

        // Update label
        SessionState updatedLabel = loaded.updateTargetLabel(0, 1, CannonMode.FTL_PEARL_CANNON, "Renamed Outpost");
        store2.set(envKey, updatedLabel);

        ClientStateStore store3 = new ClientStateStore(configFile);
        SessionState loaded3 = store3.get(envKey);
        assertEquals("Renamed Outpost", loaded3.targetHistory().get(1).label());

        // Delete rec2 (Farm)
        SessionState deleted = loaded3.removeTarget(loaded3.targetHistory().get(0));
        assertEquals(1, deleted.targetHistory().size());
        assertEquals("Renamed Outpost", deleted.targetHistory().get(0).label());
        store3.set(envKey, deleted);

        ClientStateStore store4 = new ClientStateStore(configFile);
        SessionState loaded4 = store4.get(envKey);
        assertEquals(1, loaded4.targetHistory().size());
        assertEquals(1000, loaded4.targetHistory().get(0).destX());
    }

    @Test
    public void testHighlightsVisibleAndCalculationsPersist(@TempDir Path tempDir) {
        Path configFile = tempDir.resolve("cannoncalculator.json");
        ClientStateStore store1 = new ClientStateStore(configFile);

        // Default should be true
        assertTrue(store1.globalHighlightsVisible(), "Global highlights default should be true (ON)");

        String envKey = "singleplayer:test_world";
        SessionState state1 = store1.get(envKey);
        assertTrue(state1.highlightsVisible(), "Initial session highlights should default to true (ON)");

        // 1. Set a calculation and keep highlightsVisible true
        dev.selestor1462.cannonencoder.calc.CalculationResult calc = new dev.selestor1462.cannonencoder.calc.CalculationResult(
            0, 0, 100, 200, 100, 200, 100, 200, 1.5, 2.5, 10, 20, 5, 5, 1
        );
        SessionState withCalc = state1.withLastCalculation(calc);
        store1.set(envKey, withCalc);

        // Reload and verify calculation & highlightsVisible persisted
        ClientStateStore store2 = new ClientStateStore(configFile);
        assertTrue(store2.globalHighlightsVisible());
        SessionState loaded2 = store2.get(envKey);
        assertTrue(loaded2.highlightsVisible());
        assertNotNull(loaded2.lastCalculation());
        assertEquals(100, loaded2.lastCalculation().destX());
        assertEquals(200, loaded2.lastCalculation().destZ());

        // 2. User explicitly toggles glowing OFF
        SessionState disabled = loaded2.withHighlightsVisible(false);
        store2.set(envKey, disabled);

        // Reload and verify highlightsVisible stays disabled (false)
        ClientStateStore store3 = new ClientStateStore(configFile);
        assertFalse(store3.globalHighlightsVisible(), "Global highlights should stay false when explicitly disabled");
        SessionState loaded3 = store3.get(envKey);
        assertFalse(loaded3.highlightsVisible(), "Session highlights should stay false when explicitly disabled");
        assertNotNull(loaded3.lastCalculation(), "Calculation should still persist when glowing is disabled");
    }
}
