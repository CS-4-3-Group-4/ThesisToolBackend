package cs43.group4.core;

import cs43.group4.utils.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages multiple scenario CSV files for barangay data.
 * Scenarios are numbered 1-30 and stored as data/scenarios/barangays_1.csv, etc.
 * Falls back to data/barangays.csv if specific scenario doesn't exist.
 */
public class ScenarioManager {

    private static final int TOTAL_SCENARIOS = 30;
    private static final Path SCENARIOS_DIR = Paths.get("data", "scenarios");
    private static final Path DEFAULT_CSV = Paths.get("data", "barangays.csv");

    /**
     * Get the path for a specific scenario number.
     * Falls back to default CSV if scenario file doesn't exist.
     *
     * @param scenarioNumber Scenario number (1-30)
     * @return Path to the CSV file
     * @throws IllegalArgumentException if scenarioNumber is out of range
     */
    public static Path getScenarioPath(int scenarioNumber) {
        if (scenarioNumber < 1 || scenarioNumber > TOTAL_SCENARIOS) {
            throw new IllegalArgumentException(
                "Scenario number must be between 1 and " + TOTAL_SCENARIOS +
                ", got: " + scenarioNumber);
        }

        // Try scenario-specific file first
        Path scenarioPath = SCENARIOS_DIR.resolve("barangays_" + scenarioNumber + ".csv");

        if (Files.exists(scenarioPath)) {
            Log.info("Using scenario file: " + scenarioPath);
            return scenarioPath;
        }

        // Fallback to default
        Log.warn("Scenario " + scenarioNumber + " not found, using default: " + DEFAULT_CSV);
        return DEFAULT_CSV;
    }

    /**
     * Load data for a specific scenario.
     *
     * @param scenarioNumber Scenario number (1-30)
     * @return DataLoader.Data object
     * @throws IOException if file cannot be read
     */
    public static DataLoader.Data loadScenario(int scenarioNumber) throws IOException {
        Path path = getScenarioPath(scenarioNumber);
        return DataLoader.load(path);
    }

    /**
     * Get all available scenario numbers (1-30).
     *
     * @return List of scenario numbers
     */
    public static List<Integer> getAllScenarioNumbers() {
        List<Integer> scenarios = new ArrayList<>();
        for (int i = 1; i <= TOTAL_SCENARIOS; i++) {
            scenarios.add(i);
        }
        return scenarios;
    }

    /**
     * Get list of existing scenario files.
     *
     * @return List of scenario numbers that have actual files
     */
    public static List<Integer> getExistingScenarios() {
        List<Integer> existing = new ArrayList<>();

        for (int i = 1; i <= TOTAL_SCENARIOS; i++) {
            Path scenarioPath = SCENARIOS_DIR.resolve("barangays_" + i + ".csv");
            if (Files.exists(scenarioPath)) {
                existing.add(i);
            }
        }

        return existing;
    }

    /**
     * Check if a scenario file exists.
     *
     * @param scenarioNumber Scenario number
     * @return true if file exists, false otherwise
     */
    public static boolean scenarioExists(int scenarioNumber) {
        if (scenarioNumber < 1 || scenarioNumber > TOTAL_SCENARIOS) {
            return false;
        }

        Path scenarioPath = SCENARIOS_DIR.resolve("barangays_" + scenarioNumber + ".csv");
        return Files.exists(scenarioPath);
    }

    /**
     * Get total number of scenarios supported.
     *
     * @return Total scenarios (always 30)
     */
    public static int getTotalScenarios() {
        return TOTAL_SCENARIOS;
    }

    /**
     * Create scenarios directory if it doesn't exist.
     */
    public static void ensureScenariosDirectory() {
        try {
            if (!Files.exists(SCENARIOS_DIR)) {
                Files.createDirectories(SCENARIOS_DIR);
                Log.info("Created scenarios directory: " + SCENARIOS_DIR);
            }
        } catch (IOException e) {
            Log.error("Failed to create scenarios directory: " + e.getMessage());
        }
    }
}
