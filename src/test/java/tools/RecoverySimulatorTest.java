package tools;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for Monte-Carlo Simulator for the Slot Recovery Loop (Section 10.4 & P6).
 */
public class RecoverySimulatorTest {

    @Test
    public void testSimulationProvesRecoveryLift() {
        RecoverySimulator.SimulationResult result = RecoverySimulator.runSimulation(5000, 0.10, 0.08, 0.50, 0.75);

        assertTrue(result.loopCoversMean() > result.baselineCoversMean(),
                "Slot recovery loop must serve strictly more covers than baseline");
        assertTrue(result.recoveredCoversMean() > 0,
                "Recovered covers must be strictly positive");
        assertTrue(result.emptySlotsLoopMean() < result.emptySlotsBaselineMean(),
                "Loop must leave fewer empty table-slots than baseline");
    }
}
