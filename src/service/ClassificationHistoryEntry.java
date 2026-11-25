package service;

import model.ClassificationResult;
import model.Email;
import model.Label;

import java.time.Instant;
import java.util.List;

/**
 * Immutable audit record representing a single email classification event
 * displayed in the recent classification history log.
 */
public final class ClassificationHistoryEntry {
    private final String sender;
    private final String subject;
    private final String bodyPreview;
    private final Label predictedLabel;
    private final double spamProbability;
    private final String modelName;
    private final List<String> matchedSignals;
    private final Instant timestamp;

    public ClassificationHistoryEntry(Email email, ClassificationResult result) {
        this.sender = email.getSender();
        this.subject = email.getSubject();
        String body = email.getBody();
        this.bodyPreview = body.length() > 140 ? body.substring(0, 140) + "..." : body;
        this.predictedLabel = result.getPredictedLabel();
        this.spamProbability = result.getSpamProbability();
        this.modelName = result.getModelName();
        this.matchedSignals = result.getMatchedSignals();
        this.timestamp = Instant.now();
    }

    public String getSender() {
        return sender;
    }

    public String getSubject() {
        return subject;
    }

    public String getBodyPreview() {
        return bodyPreview;
    }

    public Label getPredictedLabel() {
        return predictedLabel;
    }

    public double getSpamProbability() {
        return spamProbability;
    }

    public String getModelName() {
        return modelName;
    }

    public List<String> getMatchedSignals() {
        return matchedSignals;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
