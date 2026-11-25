package ai;

import model.ClassificationResult;
import model.Email;
import model.Label;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Multinomial Naive Bayes spam classifier with Laplace smoothing.
 * Supports incremental live training via {@link #train(Email, Label)}.
 */
public class NaiveBayesSpamDetector extends BaseSpamDetector {

    private static final double LAPLACE_ALPHA = 1.0;
    private static final int MAX_SIGNALS = 6;

    private final Map<String, Integer> spamWordCounts = new HashMap<>();
    private final Map<String, Integer> hamWordCounts = new HashMap<>();
    private final Set<String> vocabulary = new HashSet<>();

    private int spamDocCount = 0;
    private int hamDocCount = 0;
    private int totalSpamWords = 0;
    private int totalHamWords = 0;

    @Override
    public String getName() {
        return "Multinomial Naive Bayes";
    }

    /**
     * Incrementally trains the Naive Bayes model on a single labeled email.
     *
     * @param email training email
     * @param label ground-truth {@link Label}
     */
    public synchronized void train(Email email, Label label) {
        if (email == null || label == null) {
            throw new IllegalArgumentException("Email and Label must not be null.");
        }
        List<String> tokens = extractEmailTokens(email);

        if (label == Label.SPAM) {
            spamDocCount++;
            for (String token : tokens) {
                spamWordCounts.merge(token, 1, Integer::sum);
                vocabulary.add(token);
                totalSpamWords++;
            }
        } else {
            hamDocCount++;
            for (String token : tokens) {
                hamWordCounts.merge(token, 1, Integer::sum);
                vocabulary.add(token);
                totalHamWords++;
            }
        }
    }

    @Override
    public synchronized ClassificationResult classify(Email email) {
        int totalDocs = spamDocCount + hamDocCount;
        if (totalDocs == 0) {
            return new ClassificationResult(Label.HAM, 0.5, List.of("Model not yet trained"), getName());
        }

        List<String> tokens = extractEmailTokens(email);
        double vocabSize = Math.max(1, vocabulary.size());

        // Smoothed class priors
        double logPriorSpam = Math.log((spamDocCount + 0.5) / (totalDocs + 1.0));
        double logPriorHam = Math.log((hamDocCount + 0.5) / (totalDocs + 1.0));

        double logSpam = logPriorSpam;
        double logHam = logPriorHam;

        Map<String, Integer> termFreq = new HashMap<>();
        for (String token : tokens) {
            termFreq.merge(token, 1, Integer::sum);
        }

        List<TokenScore> tokenScores = new ArrayList<>();
        double spamDenom = totalSpamWords + LAPLACE_ALPHA * vocabSize;
        double hamDenom = totalHamWords + LAPLACE_ALPHA * vocabSize;

        for (Map.Entry<String, Integer> entry : termFreq.entrySet()) {
            String token = entry.getKey();
            int freq = entry.getValue();

            double logPWordSpam = Math.log((spamWordCounts.getOrDefault(token, 0) + LAPLACE_ALPHA) / spamDenom);
            double logPWordHam = Math.log((hamWordCounts.getOrDefault(token, 0) + LAPLACE_ALPHA) / hamDenom);

            logSpam += freq * logPWordSpam;
            logHam += freq * logPWordHam;

            double logOdds = (logPWordSpam - logPWordHam) * freq;
            tokenScores.add(new TokenScore(token, logOdds));
        }

        // Numerically stable log-sum-exp conversion to posterior probability
        double maxLog = Math.max(logSpam, logHam);
        double expSpam = Math.exp(logSpam - maxLog);
        double expHam = Math.exp(logHam - maxLog);
        double spamProbability = clampProbability(expSpam / (expSpam + expHam));

        Label predicted = spamProbability >= 0.5 ? Label.SPAM : Label.HAM;
        List<String> signals = buildSignals(tokenScores, predicted);

        return new ClassificationResult(predicted, spamProbability, signals, getName());
    }

    private List<String> buildSignals(List<TokenScore> tokenScores, Label predicted) {
        List<String> signals = new ArrayList<>();
        if (predicted == Label.SPAM) {
            tokenScores.stream()
                    .filter(ts -> ts.logOdds > 0.15)
                    .sorted(Comparator.comparingDouble((TokenScore ts) -> ts.logOdds).reversed())
                    .limit(MAX_SIGNALS)
                    .forEach(ts -> signals.add(formatTokenSignal(ts.token, ts.logOdds, true)));
        } else {
            tokenScores.stream()
                    .filter(ts -> ts.logOdds < -0.15)
                    .sorted(Comparator.comparingDouble(ts -> ts.logOdds))
                    .limit(MAX_SIGNALS)
                    .forEach(ts -> signals.add(formatTokenSignal(ts.token, ts.logOdds, false)));
        }

        if (signals.isEmpty() && !tokenScores.isEmpty()) {
            tokenScores.stream()
                    .sorted(Comparator.comparingDouble((TokenScore ts) -> Math.abs(ts.logOdds)).reversed())
                    .limit(3)
                    .forEach(ts -> signals.add(formatTokenSignal(ts.token, ts.logOdds, ts.logOdds >= 0)));
        }
        return signals;
    }

    private String formatTokenSignal(String token, double logOdds, boolean spamLeaning) {
        String displayToken = "url_link".equals(token) ? "embedded URL" : "'" + token + "'";
        String sign = logOdds >= 0 ? "+" : "";
        String direction = spamLeaning ? "spam token" : "ham token";
        return String.format(Locale.ROOT, "%s (%s %s%.2f)", displayToken, direction, sign, logOdds);
    }

    public synchronized int getSpamDocCount() {
        return spamDocCount;
    }

    public synchronized int getHamDocCount() {
        return hamDocCount;
    }

    public synchronized int getTotalTrainedDocs() {
        return spamDocCount + hamDocCount;
    }

    public synchronized int getVocabularySize() {
        return vocabulary.size();
    }

    private record TokenScore(String token, double logOdds) {
    }
}
