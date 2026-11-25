# SentinelMail AI — Full-Stack Java Email Spam Detection

A zero-dependency, full-stack AI Email Spam Detection web application built with **Object-Oriented Java 17+** (`com.sun.net.httpserver.HttpServer`) on the backend and a responsive **HTML / CSS / Vanilla JS** dashboard on the frontend.

---

## Project Structure

```text
email-spam-detector/
├── src/
│   ├── model/
│   │   ├── Email.java                        # Encapsulated domain entity (sender, subject, body, timestamp)
│   │   ├── Label.java                        # Classification enum (SPAM, HAM)
│   │   └── ClassificationResult.java         # Immutable prediction result & explainability signals
│   ├── ai/
│   │   ├── SpamDetector.java                 # Detector strategy interface
│   │   ├── BaseSpamDetector.java             # Abstract NLP preprocessor (lowercase, regex, tokens, stop-words)
│   │   ├── NaiveBayesSpamDetector.java       # Multinomial Naive Bayes with Laplace smoothing & live train()
│   │   ├── KeywordHeuristicDetector.java     # Weighted rule/URL/uppercase/punctuation engine
│   │   └── HybridEnsembleDetector.java       # Weighted ensemble combining Naive Bayes + Heuristic
│   ├── service/
│   │   ├── DatasetLoader.java                # Seeds 40 built-in SPAM/HAM emails & appends user samples
│   │   └── ClassificationHistoryEntry.java   # Audit record for recent classifications
│   └── web/
│       ├── JsonUtil.java                     # Zero-dependency JSON parser and serializer
│       └── SpamApiServer.java                # Embedded HTTP server & REST API controller
├── public/
│   ├── index.html                            # Dashboard UI
│   ├── styles.css                            # Responsive dark-slate theme
│   └── app.js                                # Frontend API client, presets, explainability & active learning
├── run.bat                                   # Windows one-command compile & run script
├── run.sh                                    # Linux/macOS one-command compile & run script
└── README.md
```

---

## Object-Oriented Design Principles

1. **Encapsulation**
   - `model.Email`, `model.ClassificationResult`, and `service.ClassificationHistoryEntry` keep all state `private final`, normalize inputs on construction, return defensive unmodifiable copies (`List.copyOf`), and expose behavior strictly through getters and domain helpers (`getSenderDomain()`, `getConfidence()`).
   - `NaiveBayesSpamDetector` encapsulates its frequency tables, vocabulary set, and document counts behind `synchronized` methods (`train`, `classify`, and metric getters).

2. **Abstraction**
   - `ai.SpamDetector` defines the minimal contract (`classify(Email)` and `getName()`) without exposing implementation details.
   - `ai.BaseSpamDetector` abstracts the shared NLP pipeline (`preprocess(String)`, `extractEmailTokens(Email)`, `clampProbability(double)`) so subclasses focus solely on classification logic.

3. **Inheritance**
   - `NaiveBayesSpamDetector` and `KeywordHeuristicDetector` both extend `BaseSpamDetector`, reusing its lowercasing, URL normalization, regex tokenization, and English stop-word filtering without code duplication.

4. **Polymorphism**
   - `SpamApiServer.selectDetector(String)` and `HybridEnsembleDetector` interact with detectors exclusively through the `SpamDetector` interface (`naive_bayes`, `heuristic`, and `ensemble` are invoked uniformly via `detector.classify(email)`).

5. **Composition**
   - `HybridEnsembleDetector` composes two `SpamDetector` instances (`primaryDetector` and `secondaryDetector`) rather than inheriting from either, combining their weighted probability scores (default 60% Naive Bayes / 40% Heuristic) and merging their explainability signals.

---

## REST API Endpoints

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/` | Serves the static web dashboard from `public/` |
| `POST` | `/api/classify` | Classifies `{ "sender", "subject", "body", "model" }` (`naive_bayes`, `heuristic`, or `ensemble`) |
| `POST` | `/api/train` | Trains the live Naive Bayes model with `{ "sender", "subject", "body", "label" }` (`SPAM` or `HAM`) |
| `GET` | `/api/stats` | Returns `spamCount`, `hamCount`, `totalTrained`, `vocabularySize`, and `recentHistory` |

---

## How to Run

### Prerequisites
- **Java JDK 17 or higher** (`javac` and `java` on `PATH`)

### Windows
```bat
run.bat
```

### Linux / macOS
```bash
chmod +x run.sh
./run.sh
```

Then open **http://localhost:8080** in your browser. You can pass an optional custom port (e.g., `run.bat 9090` or `PORT=9090 ./run.sh`).
