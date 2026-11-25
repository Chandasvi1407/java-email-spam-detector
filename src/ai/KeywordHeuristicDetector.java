package ai;

import model.ClassificationResult;
import model.Email;
import model.Label;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule- and weight-based heuristic spam detector.
 * Evaluates suspicious keywords/phrases, URLs, excessive uppercase ratio,
 * urgency punctuation, and suspicious sender patterns.
 */
public class KeywordHeuristicDetector extends BaseSpamDetector {

    private static final Pattern URL_REGEX = Pattern.compile(
            "(https?://[^\\s]+|www\\.[^\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHORT_URL_REGEX = Pattern.compile(
            "(bit\\.ly|tinyurl\\.com|t\\.co|goo\\.gl|rb\\.gy|ow\\.ly|is\\.gd)/\\S+",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SUSPICIOUS_TLD_REGEX = Pattern.compile(
            "https?://[^\\s/]+\\.(xyz|top|click|loan|win|gq|tk|ml)(/|\\b)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern URGENCY_PUNCT_REGEX = Pattern.compile("(!{2,}|\\?{2,}|\\${2,})");
    private static final Pattern ALL_CAPS_WORD_REGEX = Pattern.compile("\\b[A-Z]{3,}\\b");

    private static final Map<String, Double> PHRASE_WEIGHTS = new LinkedHashMap<>();
    private static final Map<String, Double> TOKEN_WEIGHTS = new LinkedHashMap<>();

    static {
        // Multi-word phishing / scam phrases
        PHRASE_WEIGHTS.put("verify your account", 2.4);
        PHRASE_WEIGHTS.put("account suspended", 2.4);
        PHRASE_WEIGHTS.put("wire transfer", 2.5);
        PHRASE_WEIGHTS.put("act now", 1.8);
        PHRASE_WEIGHTS.put("click here", 1.6);
        PHRASE_WEIGHTS.put("claim your prize", 2.6);
        PHRASE_WEIGHTS.put("limited time", 1.5);
        PHRASE_WEIGHTS.put("risk free", 1.7);
        PHRASE_WEIGHTS.put("100% free", 1.9);
        PHRASE_WEIGHTS.put("bank details", 2.3);
        PHRASE_WEIGHTS.put("social security", 2.5);
        PHRASE_WEIGHTS.put("reset your password", 1.8);
        PHRASE_WEIGHTS.put("unusual activity", 2.0);
        PHRASE_WEIGHTS.put("congratulations you won", 2.6);

        // Single-token suspicious indicators (matched after NLP preprocessing)
        TOKEN_WEIGHTS.put("winner", 1.8);
        TOKEN_WEIGHTS.put("lottery", 2.2);
        TOKEN_WEIGHTS.put("jackpot", 2.2);
        TOKEN_WEIGHTS.put("prize", 1.7);
        TOKEN_WEIGHTS.put("urgent", 1.5);
        TOKEN_WEIGHTS.put("immediately", 1.4);
        TOKEN_WEIGHTS.put("crypto", 1.4);
        TOKEN_WEIGHTS.put("bitcoin", 1.5);
        TOKEN_WEIGHTS.put("free", 1.1);
        TOKEN_WEIGHTS.put("bonus", 1.2);
        TOKEN_WEIGHTS.put("cash", 1.3);
        TOKEN_WEIGHTS.put("million", 1.5);
        TOKEN_WEIGHTS.put("inheritance", 2.3);
        TOKEN_WEIGHTS.put("beneficiary", 2.2);
        TOKEN_WEIGHTS.put("guaranteed", 1.3);
        TOKEN_WEIGHTS.put("discount", 0.9);
        TOKEN_WEIGHTS.put("pharmacy", 1.9);
        TOKEN_WEIGHTS.put("viagra", 2.5);
        TOKEN_WEIGHTS.put("suspended", 1.7);
        TOKEN_WEIGHTS.put("verify", 1.4);
        TOKEN_WEIGHTS.put("unauthorized", 1.6);
        TOKEN_WEIGHTS.put("claim", 1.3);
        TOKEN_WEIGHTS.put("refund", 1.1);
        TOKEN_WEIGHTS.put("unsubscribe", 0.7);
    }

    @Override
    public String getName() {
        return "Heuristic Rule Engine";
    }

    @Override
    public ClassificationResult classify(Email email) {
        if (email == null) {
            return new ClassificationResult(Label.HAM, 0.05, List.of("Empty email"), getName());
        }

        double score = 0.0;
        List<String> signals = new ArrayList<>();

        String subject = email.getSubject();
        String body = email.getBody();
        String combinedRaw = subject + "\n" + body;
        String combinedLower = combinedRaw.toLowerCase(Locale.ROOT);

        // 1. Multi-word suspicious phrase detection
        for (Map.Entry<String, Double> entry : PHRASE_WEIGHTS.entrySet()) {
            if (combinedLower.contains(entry.getKey())) {
                score += entry.getValue();
                signals.add(String.format(Locale.ROOT, "Suspicious phrase: '%s' (+%.1f)", entry.getKey(), entry.getValue()));
            }
        }

        // 2. Preprocessed single-token keyword detection (reuses BaseSpamDetector NLP pipeline)
        List<String> tokens = preprocess(combinedRaw);
        List<String> matchedKeywords = new ArrayList<>();
        for (String token : tokens) {
            Double weight = TOKEN_WEIGHTS.get(token);
            if (weight != null && !matchedKeywords.contains(token)) {
                matchedKeywords.add(token);
                score += weight;
                signals.add(String.format(Locale.ROOT, "Flagged keyword: '%s' (+%.1f)", token, weight));
            }
        }

        // 3. URL detection
        int urlCount = countMatches(URL_REGEX, combinedRaw);
        if (urlCount > 0) {
            double urlScore = Math.min(2.2, 0.8 + (urlCount - 1) * 0.7);
            score += urlScore;
            signals.add(String.format(Locale.ROOT, "Detected %d URL(s) (+%.1f)", urlCount, urlScore));
        }
        if (SHORT_URL_REGEX.matcher(combinedRaw).find()) {
            score += 1.6;
            signals.add("Shortened redirect URL detected (+1.6)");
        }
        if (SUSPICIOUS_TLD_REGEX.matcher(combinedRaw).find()) {
            score += 1.8;
            signals.add("High-risk URL TLD detected (+1.8)");
        }

        // 4. Excessive uppercase ratio
        UppercaseStats upperStats = analyzeUppercase(combinedRaw);
        if (upperStats.letterCount >= 16 && upperStats.ratio > 0.30) {
            double upperScore = upperStats.ratio > 0.55 ? 2.0 : 1.3;
            score += upperScore;
            signals.add(String.format(Locale.ROOT, "Excessive uppercase %.0f%% (+%.1f)",
                    upperStats.ratio * 100.0, upperScore));
        } else if (upperStats.allCapsWords >= 3) {
            score += 1.1;
            signals.add(String.format(Locale.ROOT, "Multiple ALL-CAPS words (%d) (+1.1)", upperStats.allCapsWords));
        }

        // 5. Urgency punctuation
        int urgencyPunctCount = countMatches(URGENCY_PUNCT_REGEX, combinedRaw);
        if (urgencyPunctCount > 0) {
            double punctScore = Math.min(1.8, urgencyPunctCount * 0.8);
            score += punctScore;
            signals.add(String.format(Locale.ROOT, "Urgency punctuation (!!/$$) (+%.1f)", punctScore));
        }

        // 6. Suspicious sender inspection
        String senderLower = email.getSender().toLowerCase(Locale.ROOT);
        if (senderLower.contains("prize") || senderLower.contains("lottery")
                || senderLower.contains("security-alert") || senderLower.contains("verify")
                || senderLower.endsWith(".xyz") || senderLower.endsWith(".top") || senderLower.endsWith(".click")) {
            score += 1.5;
            signals.add("Suspicious sender address pattern (+1.5)");
        }

        // Smooth logistic mapping calibrated so score=3.2 corresponds to 0.50 threshold
        double probability = clampProbability(1.0 / (1.0 + Math.exp(-(score - 3.2) * 0.90)));
        Label predicted = probability >= 0.5 ? Label.SPAM : Label.HAM;

        if (signals.isEmpty()) {
            signals.add("No heuristic spam rules triggered");
        }

        return new ClassificationResult(predicted, probability, signals, getName());
    }

    private int countMatches(Pattern pattern, String input) {
        Matcher matcher = pattern.matcher(input);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private UppercaseStats analyzeUppercase(String text) {
        int upper = 0;
        int letters = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetter(c)) {
                letters++;
                if (Character.isUpperCase(c)) {
                    upper++;
                }
            }
        }
        int capsWords = countMatches(ALL_CAPS_WORD_REGEX, text);
        double ratio = letters == 0 ? 0.0 : (double) upper / letters;
        return new UppercaseStats(letters, ratio, capsWords);
    }

    private record UppercaseStats(int letterCount, double ratio, int allCapsWords) {
    }
}
