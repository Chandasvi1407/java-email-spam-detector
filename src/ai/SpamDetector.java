package ai;

import model.ClassificationResult;
import model.Email;

/**
 * Core contract for all email spam detection strategies.
 */
public interface SpamDetector {

    /**
     * Evaluates an email and produces a classification result with probability
     * and explainability signals.
     *
     * @param email email to inspect
     * @return {@link ClassificationResult} containing label, probability, and signals
     */
    ClassificationResult classify(Email email);

    /**
     * Returns the display name of this detector implementation.
     *
     * @return human-readable detector name
     */
    String getName();
}
