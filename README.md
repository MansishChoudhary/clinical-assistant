# Clinical Coding Assistant

An agentic RAG system that reads a free-text clinical consult note and proposes ICD-11 code(s), grounded in a retrieved ICD-11 catalog and a set of coding guideline snippets. A proposal is only returned once an independent verification pass confirms it's actually supported by the retrieved evidence; otherwise the system fails safely with `"no confident match"` rather than guessing.

## Output contract

Every response from `POST /api/coding/analyze` includes:
1. **Proposed code(s)** — or `NO_CONFIDENT_MATCH` if nothing passes verification.
2. **Retrieved evidence** — the catalog entries and guideline snippets considered, with source ids/citations.
3. **Confidence level** — `HIGH` / `MEDIUM` / `LOW`.

A `NO_CONFIDENT_MATCH` response is a valid, fully-specified result (not an error) — see `CLAUDE.md`'s "Output Contract" section.

## How it works

`retrieve → propose → verify → (retry | fail-safe)`, orchestrated by `AgenticCodingWorkflow`:

1. Embed the note and retrieve the top-matching ICD-11 catalog entries and guideline snippets from pgvector (separately, by metadata type).
2. A **proposal agent** (`ChatClient` #1) proposes code(s) with a rationale, citing specific evidence ids.
3. An **independent verification agent** (`ChatClient` #2, no shared context with the proposer) checks whether the cited evidence actually supports the proposal.
4. On `SUPPORTED` → return the code(s). On `NOT_SUPPORTED` → exclude those codes and retry (up to `clinical-assistant.workflow.max-iterations`, default 3), feeding the verifier's feedback back into the next proposal. On `INSUFFICIENT_EVIDENCE` or exhausted retries → fail safe.
5. Every run (all iterations, final outcome) is persisted to a `coding_audit_log` Postgres table for traceability.

Full design rationale and the original implementation plan are in `CLAUDE.md`.

## Stack

Spring Boot 4.1.1, Java 25, Spring AI 2.0.0, PostgreSQL + `pgvector`. Chat and embedding models are both pluggable:

| | Provider | Model | Notes |
|---|---|---|---|
| Chat (propose + verify) | Anthropic **or** Ollama | `claude-sonnet-5` / `llama3.1` | switch via `CHAT_PROVIDER` |
| Embeddings | Ollama | `nomic-embed-text` | always local — Anthropic has no embeddings API |
| Vector store | pgvector | — | 768-dim (matches `nomic-embed-text`) |

## Prerequisites

- Java 25, Maven (or use the bundled `./mvnw`)
- Docker (for Postgres + pgvector)
- [Ollama](https://ollama.com) installed locally, with `nomic-embed-text` pulled (embeddings always run locally):
  ```
  ollama pull nomic-embed-text
  ```
- If using `CHAT_PROVIDER=ollama` (the current default — see `.env.local`), also pull a chat model:
  ```
  ollama pull llama3.1
  ```
- If using `CHAT_PROVIDER=anthropic`, an `ANTHROPIC_API_KEY` instead.

## Setup & run

```bash
docker compose up -d          # Postgres + pgvector on localhost:5433
source .env.local             # CHAT_PROVIDER, OLLAMA_CHAT_MODEL, ANTHROPIC_API_KEY (fill in real values first)
./mvnw spring-boot:run         # app on localhost:8060 (server.port in application.yml)
```

On first run, the app ingests `icd_catalog.json` and `guideline_snippets.json` (via `src/main/resources/data/`) into pgvector; subsequent runs skip ingestion if the table is already populated.

## Try it

```bash
curl -X POST http://localhost:8060/api/coding/analyze \
  -H "Content-Type: application/json" \
  -d '{"text":"65-year-old man presents with crushing substernal chest pain radiating to left arm and jaw, sweating profusely, and breathless for the last hour."}'
```

See `EVAL_CASES.md` for 15 worked examples (note, evidence, response, correctness assessment) and a discussion of local-model vs. hosted-model tradeoffs observed during evaluation.

## Configuration

Key properties (`src/main/resources/application.yml`), all overridable via environment variables:

| Property | Env var | Default | Purpose |
|---|---|---|---|
| `server.port` | — | `8060` | HTTP port |
| `spring.ai.model.chat` | `CHAT_PROVIDER` | `ollama` | `anthropic` or `ollama` |
| `spring.ai.anthropic.api-key` | `ANTHROPIC_API_KEY` | — | required if chat provider is `anthropic` |
| `spring.ai.ollama.chat.options.model` | `OLLAMA_CHAT_MODEL` | `llama3.1` | local chat model |
| `spring.datasource.url` | `DB_URL` | `jdbc:postgresql://localhost:5433/clinical_assistant` | matches `docker-compose.yml`'s port mapping |
| `clinical-assistant.retrieval.top-k` | — | `5` | evidence items retrieved per source type |
| `clinical-assistant.retrieval.similarity-threshold` | — | `0.5` | pgvector similarity cutoff |
| `clinical-assistant.workflow.max-iterations` | — | `3` | propose/verify retry bound |

Postgres is mapped to host port `5433` (not `5432`) and the app defaults to port `8060` (not `8080`) specifically to avoid clashing with other services that may already be running locally — see `CLAUDE.md`'s dev commands note.

## Testing

```bash
./mvnw test
```

Unit tests mock `ChatClient`/`VectorStore` — no external services required. There is no Testcontainers-based integration suite yet (see `CLAUDE.md`'s Implementation Plan, step 11, for the intended scope).

## Project layout

```
src/main/java/com/clinicalassistant/
  domain/      records/enums for the core model (ClinicalNote, CodingResult, ConfidenceLevel, ...)
  ingestion/   JSON catalog/guideline loader + idempotent vector store ingestion runner
  retrieval/   EvidenceRetrievalService (pgvector similarity search)
  agent/       CodingProposalAgent, VerificationAgent (independent ChatClients)
  workflow/    AgenticCodingWorkflow (the propose/verify/retry orchestrator)
  audit/       coding_audit_log JPA entity + repository
  api/         REST controller
  config/      ChatClient and Jackson bean configuration
```

## Known limitations

- Evaluated at small catalog scale (288 codes); see `REFLECTION.md` for what changes at real ICD-11 scale (~50,000 codes).
- `EVAL_CASES.md` documents a live run where the local `llama3.1:8b` verifier was unreliable (produced several false-negative fail-safes despite strong evidence) — `CHAT_PROVIDER=anthropic` is recommended for accuracy evaluation.
- No auth/rate-limiting on the API; not production-hardened.
