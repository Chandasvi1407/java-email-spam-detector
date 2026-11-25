package model;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable, encapsulated domain object representing an email message.
 */
public final class Email {
    private final String sender;
    private final String subject;
    private final String body;
    private final Instant timestamp;

    /**
     * Creates an {@code Email} stamped with the current UTC instant.
     *
     * @param sender  sender email address or display string
     * @param subject email subject line
     * @param body    email message body
     */
    public Email(String sender, String subject, String body) {
        this(sender, subject, body, Instant.now());
    }

    /**
     * Creates an {@code Email} with an explicit timestamp.
     *
     * @param sender    sender email address or display string
     * @param subject   email subject line
     * @param body      email message body
     * @param timestamp creation or receipt timestamp
     */
    public Email(String sender, String subject, String body, Instant timestamp) {
        this.sender = sender == null ? "" : sender.trim();
        this.subject = subject == null ? "" : subject.trim();
        this.body = body == null ? "" : body;
        this.timestamp = timestamp == null ? Instant.now() : timestamp;
    }

    public String getSender() {
        return sender;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * Extracts the lowercase domain portion of the sender address, if present.
     *
     * @return sender domain or empty string
     */
    public String getSenderDomain() {
        int atIndex = sender.lastIndexOf('@');
        if (atIndex >= 0 && atIndex < sender.length() - 1) {
            String domain = sender.substring(atIndex + 1).toLowerCase();
            int end = domain.indexOf('>');
            return end >= 0 ? domain.substring(0, end).trim() : domain.trim();
        }
        return "";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Email other)) return false;
        return Objects.equals(sender, other.sender)
                && Objects.equals(subject, other.subject)
                && Objects.equals(body, other.body)
                && Objects.equals(timestamp, other.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sender, subject, body, timestamp);
    }
}
