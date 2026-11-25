package model;

/**
 * Represents the classification label assigned to an email.
 */
public enum Label {
    SPAM,
    HAM;

    /**
     * Parses a user-supplied string into a {@link Label}.
     * Supports "spam", "ham", "not spam", and "not_spam" (case-insensitive).
     *
     * @param raw raw label string
     * @return corresponding {@link Label}
     * @throws IllegalArgumentException if the string cannot be mapped
     */
    public static Label fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Label must not be empty.");
        }
        String normalized = raw.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "SPAM" -> SPAM;
            case "HAM", "NOT_SPAM", "LEGIT", "SAFE" -> HAM;
            default -> throw new IllegalArgumentException("Unsupported label: " + raw);
        };
    }
}
