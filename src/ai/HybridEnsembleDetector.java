package ai;

import model.ClassificationResult;
import model.Email;
import model.Label;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Hybrid ensemble detector demonstrating composition and polymorphism.
 * Combines weighted probability predictions from a statistical detector
 * ({@link NaiveBayesSpamDetector}) and a rule-based detector
 * ({@link KeywordHeuristicDetector}) through the {@link SpamDetector} interface.
 */
public class HybridEnsembleDetector implements SpamDetector {

    private final SpamDetector primaryDetector;
    private final SpamDetector secondaryDetector;
    private final double primaryWeight;
    private final double secondaryWeight;

    /**
     * Creates a hybrid ensemble using default weights (60% Naive Bayes, 40% Heuristic).
     */
    public HybridEnsembleDetector(SpamDetector primaryDetector, SpamDetector secondaryDetector) {
        this(primaryDetector, secondaryDetector, 0.60, 0.40);
    }

    /**
     * Creates a hybrid ensemble with custom weights (normalized automatically).
     */
    public HybridEnsembleDetector(
            SpamDetector primaryDetector,
            SpamDetector secondaryDetector,
            double primaryWeight,
            double secondaryWeight) {
        this.primaryDetector = Objects.requireNonNull(primaryDetector, "primaryDetector must not be null");
        this.secondaryDetector = Objects.requireNonNull(secondaryDetector, "secondaryDetector must not be null");
        double totalWeight = primaryWeight + secondaryWeight;
        if (totalWeight <= 0.0) {
            throw new IllegalArgumentException("Combined weights must be positive.");
        }
        this.primaryWeight = primaryWeight / totalWeight;
        this.secondaryWeight = secondaryWeight / totalWeight;
    }

    @Override
    public String getName() {
        return "Hybrid Ensemble AI (NB + Heuristic)";
    }

    @Override
    public ClassificationResult classify(Email email) {
        ClassificationResult primaryResult = primaryDetector.classify(email);
        ClassificationResult secondaryResult = secondaryDetector.classify(email);

        double combinedProbability = primaryWeight * primaryResult.getSpamProbability()
                + secondaryWeight * secondaryResult.getSpamProbability();
        combinedProbability = Math.max(0.0, Math.min(1.0, combinedProbability));

        Label predicted = combinedProbability >= 0.5 ? Label.SPAM : Label.HAM;

        Set<String> mergedSignals = new LinkedHashSet<>();
        for (String s : primaryResult.getMatchedSignals()) {
            mergedSignals.add("[NB] " + s);
        }
        for (String s : secondaryResult.getMatchedSignals()) {
            if (!"No heuristic spam rules triggered".equals(s) || mergedSignals.isEmpty()) {
                mergedSignals.add("[Rule] " + s);
            }
        }

        List<String> finalSignals = new ArrayList<>(mergedSignals);
        if (finalSignals.size() > 10) {
            finalSignals = finalSignals.subList(0, 10);
        }

        return new ClassificationResult(predicted, combinedProbability, finalSignals, getName());
    }
}
