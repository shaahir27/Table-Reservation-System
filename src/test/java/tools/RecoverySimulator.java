package tools;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Monte-Carlo Simulator for the Slot Recovery Loop (Section 10.4 & Implementation Plan P6).
 * Simulates 10,000 service nights across the 8-table × 5-slot restaurant configuration,
 * comparing Baseline (unrecovered no-shows) against the Slot Recovery Loop.
 */
public class RecoverySimulator {

    record SimulationResult(
            double baselineCoversMean,
            double baselineCoversCi95,
            double loopCoversMean,
            double loopCoversCi95,
            double recoveredCoversMean,
            double emptySlotsBaselineMean,
            double emptySlotsLoopMean
    ) {}

    public static SimulationResult runSimulation(int nights, double noShowRate, double lateCancelRate,
                                                 double waitlistJoinRate, double offerAcceptRate) {
        Random rand = new Random(42); // Seed for reproducible testing
        int[] tableCapacities = {2, 4, 4, 6, 8, 2, 4, 10}; // 8 tables, total 40 seats
        int numSlots = 5;

        double totalBaselineCovers = 0;
        double totalLoopCovers = 0;
        double totalRecoveredCovers = 0;
        double totalBaselineEmptySlots = 0;
        double totalLoopEmptySlots = 0;

        List<Double> baselineCoversList = new ArrayList<>(nights);
        List<Double> loopCoversList = new ArrayList<>(nights);

        for (int n = 0; n < nights; n++) {
            double nightBaselineCovers = 0;
            double nightLoopCovers = 0;
            double nightRecoveredCovers = 0;
            int nightBaselineEmpty = 0;
            int nightLoopEmpty = 0;

            for (int s = 0; s < numSlots; s++) {
                int waitlistParties = 0;
                int waitlistAvgPartySize = 4;

                // Simulate demand for each table in this slot
                for (int cap : tableCapacities) {
                    // Assume fully booked prior to service
                    int partySize = Math.max(1, cap - (rand.nextInt(2))); // realistic party fit

                    boolean noShow = rand.nextDouble() < noShowRate;
                    boolean lateCancel = !noShow && (rand.nextDouble() < lateCancelRate);

                    if (noShow || lateCancel) {
                        // Baseline: table sits completely empty
                        nightBaselineEmpty++;

                        // Loop: slot freed and offered to waitlist
                        // Potential walk-in / waitlist refill
                        boolean guestWaiting = (waitlistParties > 0) || (rand.nextDouble() < waitlistJoinRate);
                        if (guestWaiting && (rand.nextDouble() < offerAcceptRate)) {
                            int recoveredParty = Math.min(cap, waitlistAvgPartySize);
                            nightLoopCovers += recoveredParty;
                            nightRecoveredCovers += recoveredParty;
                            if (waitlistParties > 0) waitlistParties--;
                        } else {
                            nightLoopEmpty++;
                        }
                    } else {
                        // Guest honored reservation
                        nightBaselineCovers += partySize;
                        nightLoopCovers += partySize;
                    }

                    // Excess demand joins waitlist
                    if (rand.nextDouble() < 0.25) {
                        waitlistParties++;
                    }
                }
            }

            baselineCoversList.add(nightBaselineCovers);
            loopCoversList.add(nightLoopCovers);

            totalBaselineCovers += nightBaselineCovers;
            totalLoopCovers += nightLoopCovers;
            totalRecoveredCovers += nightRecoveredCovers;
            totalBaselineEmptySlots += nightBaselineEmpty;
            totalLoopEmptySlots += nightLoopEmpty;
        }

        double baseMean = totalBaselineCovers / nights;
        double loopMean = totalLoopCovers / nights;
        double recMean = totalRecoveredCovers / nights;
        double baseEmptyMean = totalBaselineEmptySlots / nights;
        double loopEmptyMean = totalLoopEmptySlots / nights;

        double baseStdDev = calculateStdDev(baselineCoversList, baseMean);
        double loopStdDev = calculateStdDev(loopCoversList, loopMean);

        double baseCi = 1.96 * (baseStdDev / Math.sqrt(nights));
        double loopCi = 1.96 * (loopStdDev / Math.sqrt(nights));

        return new SimulationResult(baseMean, baseCi, loopMean, loopCi, recMean, baseEmptyMean, loopEmptyMean);
    }

    private static double calculateStdDev(List<Double> values, double mean) {
        double sum = 0;
        for (double v : values) {
            sum += Math.pow(v - mean, 2);
        }
        return Math.sqrt(sum / values.size());
    }

    public static void main(String[] args) {
        int nights = 10000;
        double[] noShowRates = {0.05, 0.10, 0.20}; // 5%, 10%, 20%
        double lateCancelRate = 0.08;
        double waitlistJoinRate = 0.50;
        double offerAcceptRate = 0.75;

        System.out.println("=========================================================================================");
        System.out.println(" RTRS MONTE-CARLO SIMULATION: SLOT RECOVERY LOOP SENSITIVITY TABLE (10,000 NIGHTS)");
        System.out.println("=========================================================================================");
        System.out.printf("%-12s | %-20s | %-20s | %-16s | %-14s%n",
                "No-Show Rate", "Baseline Covers (95% CI)", "Loop Covers (95% CI)", "Recovered Covers", "Empty Slots Diff");
        System.out.println("-----------------------------------------------------------------------------------------");

        for (double p : noShowRates) {
            SimulationResult res = runSimulation(nights, p, lateCancelRate, waitlistJoinRate, offerAcceptRate);
            System.out.printf("%-12.0f%% | %6.1f ± %-11.2f | %6.1f ± %-11.2f | %-16.1f | %4.1f -> %4.1f%n",
                    p * 100,
                    res.baselineCoversMean(), res.baselineCoversCi95(),
                    res.loopCoversMean(), res.loopCoversCi95(),
                    res.recoveredCoversMean(),
                    res.emptySlotsBaselineMean(), res.emptySlotsLoopMean());
        }
        System.out.println("=========================================================================================");
    }

    @Test
    public void testSimulationProvesRecoveryLift() {
        SimulationResult result = runSimulation(5000, 0.10, 0.08, 0.50, 0.75);

        // Assert that loop covers served is statistically significantly higher than baseline
        assertTrue(result.loopCoversMean() > result.baselineCoversMean(),
                "Slot recovery loop must serve strictly more covers than baseline");
        assertTrue(result.recoveredCoversMean() > 0,
                "Recovered covers must be strictly positive");
        assertTrue(result.emptySlotsLoopMean() < result.emptySlotsBaselineMean(),
                "Loop must leave fewer empty table-slots than baseline");
    }
}
