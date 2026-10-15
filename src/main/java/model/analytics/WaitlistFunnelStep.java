package model.analytics;

/**
 * Metric representing a stage in the waitlist recovery conversion funnel.
 */
public class WaitlistFunnelStep {
    private final String stage;
    private final int count;
    private final double pctOfTotal;

    public WaitlistFunnelStep(String stage, int count, double pctOfTotal) {
        this.stage = stage;
        this.count = count;
        this.pctOfTotal = pctOfTotal;
    }

    public String getStage() {
        return stage;
    }

    public int getCount() {
        return count;
    }

    public double getPctOfTotal() {
        return pctOfTotal;
    }
}
