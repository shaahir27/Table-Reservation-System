package model.analytics;

/**
 * Metric for hospitality and dietary tag frequency.
 */
public class TagCountMetric {
    private final String tag;
    private final int count;

    public TagCountMetric(String tag, int count) {
        this.tag = tag;
        this.count = count;
    }

    public String getTag() {
        return tag;
    }

    public int getCount() {
        return count;
    }
}
