package web;

import ai.HybridEnsembleDetector;
import ai.KeywordHeuristicDetector;
import ai.NaiveBayesSpamDetector;
import ai.SpamDetector;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import model.ClassificationResult;
import model.Email;
import model.Label;
import service.ClassificationHistoryEntry;
import service.DatasetLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Embedded HTTP server exposing the AI Email Spam Detection REST API
 * and serving the static web dashboard from {@code public/}.
 */
public class SpamApiServer {

    private static final int DEFAULT_PORT = 8080;
    private static final int MAX_HISTORY_SIZE = 50;

    private final NaiveBayesSpamDetector naiveBayesDetector;
    private final KeywordHeuristicDetector heuristicDetector;
    private final HybridEnsembleDetector ensembleDetector;
    private final DatasetLoader datasetLoader;
    private final Deque<ClassificationHistoryEntry> recentHistory = new ArrayDeque<>();
    private final Path publicDir;
    private int totalClassifications = 0;

    public SpamApiServer(Path publicDir) {
        this.publicDir = publicDir.toAbsolutePath().normalize();
        this.naiveBayesDetector = new NaiveBayesSpamDetector();
        this.heuristicDetector = new KeywordHeuristicDetector();
        this.ensembleDetector = new HybridEnsembleDetector(naiveBayesDetector, heuristicDetector);
        this.datasetLoader = new DatasetLoader();
        this.datasetLoader.loadInto(naiveBayesDetector);
    }

    public static void main(String[] args) throws IOException {
        int port = resolvePort(args);
        Path publicRoot = resolvePublicDirectory();
        SpamApiServer app = new SpamApiServer(publicRoot);
        app.start(port);
    }

    public void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/api/classify", this::handleClassify);
        server.createContext("/api/train", this::handleTrain);
        server.createContext("/api/stats", this::handleStats);
        server.createContext("/", this::handleStatic);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        System.out.printf(Locale.ROOT,
                "AI Spam Detection Server running at http://localhost:%d (trained=%d, vocab=%d)%n",
                port, naiveBayesDetector.getTotalTrainedDocs(), naiveBayesDetector.getVocabularySize());
    }

    private void handleClassify(HttpExchange exchange) throws IOException {
        if (handleCorsPreflight(exchange)) return;
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed. Use POST.");
            return;
        }

        try {
            Map<String, Object> payload = JsonUtil.parseObject(readRequestBody(exchange));
            String sender = getString(payload, "sender");
            String subject = getString(payload, "subject");
            String body = getString(payload, "body");
            String modelKey = getString(payload, "model");

            if (subject.isBlank() && body.isBlank()) {
                sendError(exchange, 400, "Please provide an email subject or body to classify.");
                return;
            }

            String normalizedModelKey = normalizeModelKey(modelKey);
            SpamDetector detector = selectDetector(normalizedModelKey);

            Email email = new Email(sender, subject, body);
            ClassificationResult result = detector.classify(email);

            recordHistory(email, result);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("predictedLabel", result.getPredictedLabel().name());
            response.put("spamProbability", round4(result.getSpamProbability()));
            response.put("confidence", round4(result.getConfidence()));
            response.put("matchedSignals", result.getMatchedSignals());
            response.put("modelName", result.getModelName());
            response.put("modelKey", normalizedModelKey);
            response.put("timestamp", Instant.now().toString());

            sendJson(exchange, 200, response);
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, e.getMessage());
        } catch (Exception e) {
            sendError(exchange, 500, "Classification error: " + e.getMessage());
        }
    }

    private void handleTrain(HttpExchange exchange) throws IOException {
        if (handleCorsPreflight(exchange)) return;
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed. Use POST.");
            return;
        }

        try {
            Map<String, Object> payload = JsonUtil.parseObject(readRequestBody(exchange));
            String sender = getString(payload, "sender");
            String subject = getString(payload, "subject");
            String body = getString(payload, "body");
            String rawLabel = getString(payload, "label");

            if (subject.isBlank() && body.isBlank()) {
                sendError(exchange, 400, "Email subject or body must not be empty for training.");
                return;
            }

            Label label = Label.fromString(rawLabel);
            Email email = new Email(sender, subject, body);
            datasetLoader.appendTrainingSample(email, label, naiveBayesDetector);

            Map<String, Object> response = buildStatsMap();
            response.put("status", "trained");
            response.put("trainedLabel", label.name());
            sendJson(exchange, 200, response);
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, e.getMessage());
        } catch (Exception e) {
            sendError(exchange, 500, "Training error: " + e.getMessage());
        }
    }

    private void handleStats(HttpExchange exchange) throws IOException {
        if (handleCorsPreflight(exchange)) return;
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed. Use GET.");
            return;
        }
        sendJson(exchange, 200, buildStatsMap());
    }

    private void handleStatic(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())
                && !"HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed.");
            return;
        }

        String rawPath = exchange.getRequestURI().getPath();
        String relative = (rawPath == null || "/".equals(rawPath)) ? "index.html" : rawPath.substring(1);
        Path target = publicDir.resolve(relative).normalize();

        if (!target.startsWith(publicDir) || !Files.exists(target) || Files.isDirectory(target)) {
            byte[] notFound = "404 Not Found".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(404, notFound.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(notFound);
            }
            return;
        }

        byte[] bytes = Files.readAllBytes(target);
        exchange.getResponseHeaders().set("Content-Type", detectContentType(target));
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(200, -1);
        } else {
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private synchronized void recordHistory(Email email, ClassificationResult result) {
        totalClassifications++;
        recentHistory.addFirst(new ClassificationHistoryEntry(email, result));
        while (recentHistory.size() > MAX_HISTORY_SIZE) {
            recentHistory.removeLast();
        }
    }

    private synchronized Map<String, Object> buildStatsMap() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("spamCount", naiveBayesDetector.getSpamDocCount());
        stats.put("hamCount", naiveBayesDetector.getHamDocCount());
        stats.put("totalTrained", naiveBayesDetector.getTotalTrainedDocs());
        stats.put("vocabularySize", naiveBayesDetector.getVocabularySize());
        stats.put("totalClassifications", totalClassifications);

        List<Map<String, Object>> historyList = new ArrayList<>(recentHistory.size());
        for (ClassificationHistoryEntry entry : recentHistory) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sender", entry.getSender());
            item.put("subject", entry.getSubject());
            item.put("bodyPreview", entry.getBodyPreview());
            item.put("predictedLabel", entry.getPredictedLabel().name());
            item.put("spamProbability", round4(entry.getSpamProbability()));
            item.put("modelName", entry.getModelName());
            item.put("matchedSignals", entry.getMatchedSignals());
            item.put("timestamp", entry.getTimestamp().toString());
            historyList.add(item);
        }
        stats.put("recentHistory", historyList);
        return stats;
    }

    private SpamDetector selectDetector(String modelKey) {
        return switch (modelKey) {
            case "naive_bayes" -> naiveBayesDetector;
            case "heuristic" -> heuristicDetector;
            case "ensemble" -> ensembleDetector;
            default -> throw new IllegalArgumentException(
                    "Unknown model '" + modelKey + "'. Expected naive_bayes, heuristic, or ensemble.");
        };
    }

    private String normalizeModelKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return "ensemble";
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private boolean handleCorsPreflight(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange.getResponseHeaders());
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return true;
        }
        return false;
    }

    private void addCorsHeaders(Headers headers) {
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type");
    }

    private void sendJson(HttpExchange exchange, int status, Map<String, Object> body) throws IOException {
        addCorsHeaders(exchange.getResponseHeaders());
        byte[] bytes = JsonUtil.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendError(HttpExchange exchange, int status, String message) throws IOException {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("error", message);
        err.put("status", status);
        sendJson(exchange, status, err);
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private double round4(double val) {
        return Math.round(val * 10000.0) / 10000.0;
    }

    private static int resolvePort(String[] args) {
        if (args.length > 0) {
            try {
                return Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
            }
        }
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.isBlank()) {
            try {
                return Integer.parseInt(envPort.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return DEFAULT_PORT;
    }

    private static Path resolvePublicDirectory() {
        Path direct = Path.of("public");
        if (Files.isDirectory(direct)) {
            return direct;
        }
        Path nested = Path.of("email-spam-detector", "public");
        if (Files.isDirectory(nested)) {
            return nested;
        }
        return direct;
    }

    private static String detectContentType(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (name.endsWith(".json")) return "application/json; charset=utf-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }
}
