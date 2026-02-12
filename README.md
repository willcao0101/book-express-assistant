# book-express-assistant

AI-assisted book classification demo that maps **title + author** to existing **Trade Me categories** and **Shopify tags**.

## Overview

`book-express-assistant` is a full-stack web project:

- **Backend**: Spring Boot (Java 21)
- **Frontend**: React
- **Database**: SQLite
- **LLM Gateway**: ModelGate (optional demonstrated architecture)

Given a book `title` and `author`, the system predicts:

1. a category (**from existing taxonomy only**), and
2. one or more tags (**from existing mapping only**).

If no direct DB hit is found, the backend runs rule-based retrieval first, then optional LLM reranking/selection constrained to allowed candidates.

---

## Tech Stack

- Java 21
- Spring Boot 3.x
- Maven
- SQLite (Xerial JDBC)
- React + Node.js
- ModelGate (`/v1/chat/completions`) with local model (e.g., `ollama/deepseek-r1:8b`)

---

## Project Structure

```text
book-express-assistant/
├─ backend/
│  ├─ src/main/java/com/bookexpress/backend/...
│  ├─ src/main/resources/
│  │  └─ application.yml
│  ├─ data/                 # local sqlite db (ignored by git)
│  └─ scripts/              # import/seed scripts
├─ frontend/
│  ├─ src/...
│  └─ package.json
└─ README.md
```

---

## Prerequisites

- JDK 21
- Maven 3.9+
- Node.js 18+ (20+ recommended)
- npm 10+
- (Optional) ModelGate running at `http://127.0.0.1:8010`

---

## Configuration

`backend/src/main/resources/application.yml` example:

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:sqlite:./data/books.db
    driver-class-name: org.sqlite.JDBC

llm:
  enabled: true
  endpoint: http://127.0.0.1:8010/v1/chat/completions
  model: ollama/deepseek-r1:8b
  token: ${LLM_TOKEN:}
```

If your ModelGate requires Bearer auth, export `LLM_TOKEN` before starting the backend.

---

## Run Locally

### Backend

```bash
cd backend
mvn clean spring-boot:run
```

### Frontend

```bash
cd frontend
npm install
npm run dev
```

---

## API Quick Test

### Seed sample data (if enabled in your current backend)

```bash
curl -X POST http://localhost:8080/api/v1/seed/categories
curl -X POST http://localhost:8080/api/v1/seed/sample-books
```

### Predict (rule-only)

```bash
curl -X POST http://localhost:8080/api/v1/predict \
  -H "Content-Type: application/json" \
  -d '{
    "title":"Tracks of Aotearoa: A Railway Journey",
    "author":"Mila Hart",
    "topK":5,
    "useAI":false
  }'
```

### Predict (LLM enabled)

```bash
curl -X POST http://localhost:8080/api/v1/predict \
  -H "Content-Type: application/json" \
  -d '{
    "title":"Tracks of Aotearoa: A Railway Journey",
    "author":"Mila Hart",
    "topK":5,
    "useAI":true
  }'
```

---

## Prediction Logic

1. Normalize `title` and `author`
2. Attempt exact/near DB retrieval
3. Build candidate category-tag mappings from existing data
4. If `useAI=true` and LLM is enabled:
   - ask LLM to rerank/select **within allowed candidates only**
5. Fall back to deterministic rule strategy if LLM fails, times out, or returns invalid output

---

## Notes

- Returned categories/tags are constrained to existing mapping data.
- No new taxonomy is invented by the model.
- SQLite is used for fast local demo iteration.

---

## License

MIT (or your preferred license)
