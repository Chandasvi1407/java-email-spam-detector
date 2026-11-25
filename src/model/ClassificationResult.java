package model;

import java.util.List;
import java.util.Objects;

/**
 * Immutable classification output produced by a {@code SpamDetector}.
 */
public final class ClassificationResult {
    private final Label predictedLabel;
    private final double spamProbability;
    private final List<String> matchedSignals;
    private final String modelName;

    /**
     * Constructs a new {@code ClassificationResult}.
     *
     * @param predictedLabel  predicted {@link Label} (SPAM or HAM)
     * @param spamProbability estimated probability of SPAM in [0.0, 1.0]
     * @param matchedSignals  list of top flagged tokens or heuristic rules
     * @param modelName       human-readable detector identifier
     */
    public ClassificationResult(
            Label predictedLabel,
            double spamProbability,
            List<String> matchedSignals,
            String modelName) {
        this.predictedLabel = Objects.requireNonNull(predictedLabel, "predictedLabel must not be null");
        if (Double.isNaN(spamProbability) || Double.isInfinite(spamProbability)) {
            this.spamProbability = 0.5;
        } else {
            this.spamProbability = Math.max(0.0, Math.min(1.0, spamProbability));
        }
        this.matchedSignals = matchedSignals == null ? List.of() : List.copyOf(matchedSignals);
        this.modelName = modelName == null || modelName.isBlank() ? "Unknown Model" : modelName;
    }

    public Label getPredictedLabel() {
        return predictedLabel;
    }

    public double getSpamProbability() {
        return spamProbability;
    }

    /**
     * Returns confidence in the predicted class (spamProbability for SPAM,
     * 1 - spamProbability for HAM).
     *
     * @return confidence score in [0.0, 1.0]
     */
    public double getConfidence() {
        return predictedLabel == Label.SPAM ? spamProbability : (1.0 - spamProbability);
    }

    public List<String> getMatchedSignals() {
        return matchedSignals;
    }

    public String getModelName() {
        return modelName;
    }
}
