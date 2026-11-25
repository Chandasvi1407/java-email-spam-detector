package ai;

import model.Email;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Abstract base class providing shared NLP preprocessing utilities
 * (lowercasing, regex cleaning, tokenization, and English stop-word removal)
 * for concrete {@link SpamDetector} implementations.
 */
public abstract class BaseSpamDetector implements SpamDetector {

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9\\s]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+|www\\.\\S+", Pattern.CASE_INSENSITIVE);

    protected static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "been", "being", "but", "by",
            "can", "could", "did", "do", "does", "doing", "down", "during", "each",
            "few", "for", "from", "further", "had", "has", "have", "having", "he",
            "her", "here", "hers", "herself", "him", "himself", "his", "how", "i",
            "if", "in", "into", "is", "it", "its", "itself", "just", "me", "more",
            "most", "my", "myself", "no", "nor", "not", "now", "of", "off", "on",
            "once", "only", "or", "other", "our", "ours", "ourselves", "out", "over",
            "own", "same", "she", "should", "so", "some", "such", "than", "that",
            "the", "their", "theirs", "them", "themselves", "then", "there", "these",
            "they", "this", "those", "through", "to", "too", "under", "until", "up",
            "very", "was", "we", "were", "what", "when", "where", "which", "while",
            "who", "whom", "why", "will", "with", "would", "you", "your", "yours",
            "yourself", "yourselves"
    );

    /**
     * Runs the standard NLP pipeline on arbitrary text:
     * lowercasing -> URL normalization -> regex cleaning -> tokenization -> stop-word removal.
     *
     * @param text raw input text
     * @return ordered list of normalized tokens
     */
    protected List<String> preprocess(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String lower = text.toLowerCase(Locale.ROOT);
        String urlsNormalized = URL_PATTERN.matcher(lower).replaceAll(" url_link ");
        String cleaned = NON_ALPHANUMERIC.matcher(urlsNormalized).replaceAll(" ");
        String[] rawTokens = WHITESPACE.split(cleaned.trim());

        List<String> tokens = new ArrayList<>(rawTokens.length);
        for (String token : rawTokens) {
            if (token.length() < 2) {
                continue;
            }
            if (STOP_WORDS.contains(token)) {
                continue;
            }
            tokens.add(token);
        }
        return tokens;
    }

    /**
     * Extracts normalized tokens from an {@link Email}, weighting the subject line
     * more heavily (2x) and including sender domain tokens.
     *
     * @param email input email
     * @return combined token stream for statistical modeling
     */
    protected List<String> extractEmailTokens(Email email) {
        if (email == null) {
            return List.of();
        }
        List<String> subjectTokens = preprocess(email.getSubject());
        List<String> bodyTokens = preprocess(email.getBody());
        List<String> senderTokens = preprocess(email.getSender());

        List<String> combined = new ArrayList<>(subjectTokens.size() * 2 + bodyTokens.size() + senderTokens.size());
        combined.addAll(subjectTokens);
        combined.addAll(subjectTokens);
        combined.addAll(bodyTokens);
        combined.addAll(senderTokens);
        return combined;
    }

    /**
     * Clamps a probability value safely to [0.0, 1.0].
     */
    protected double clampProbability(double probability) {
        if (Double.isNaN(probability) || Double.isInfinite(probability)) {
            return 0.5;
        }
        return Math.max(0.0, Math.min(1.0, probability));
    }
}
