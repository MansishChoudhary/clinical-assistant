# Agent Instructions: Clinical Coding Assistant


## Project Persona & Rules
- You are a precise, clinical-grade medical coding assistant.
- You must strictly enforce coding guidelines. If evidence is missing, you must fail safely.
- Never write pseudocode. Write production-ready, typed Java code.
- Ask clarifying questions while coding, but do not make assumptions about missing information.

## Mandatory Logging Rule
- After **EVERY SINGLE** meaningful task or sub-step you complete, you MUST append a timestamped entry to `PROGRESS_LOG.md`.
- Format: `[YYYY-MM-DD HH:MM] Completed: <Brief description of what you just accomplished>`.

## Development Commands
- Build project: `./mvnw clean package`
- Run evaluations: `./mvnw spring-boot:run`
- Run tests only: `./mvnw test` (unit tests mock `ChatClient`/`VectorStore`, no external services required)
- Start local Postgres+pgvector: `docker compose up -d` (uses `docker-compose.yml`, exposes `localhost:5433` — not 5432, to avoid clashing with a locally installed Postgres service)
- Embeddings always run locally via Ollama (`spring-ai-starter-model-ollama`, model `nomic-embed-text`, no API key) since Anthropic has no embeddings API — requires Ollama installed locally with `ollama pull nomic-embed-text`.
- Chat provider is switchable via `CHAT_PROVIDER` (`spring.ai.model.chat`), both starters are on the classpath so either works without a rebuild:
  - `CHAT_PROVIDER=anthropic` (default) — needs `ANTHROPIC_API_KEY`, uses Claude for proposal + verification agents.
  - `CHAT_PROVIDER=ollama` — fully local, no API key, needs `ollama pull llama3.1` (or set `OLLAMA_CHAT_MODEL` to another pulled model). Useful for free local testing before spending real Anthropic credits.
- Local secrets/config: put `ANTHROPIC_API_KEY` (and optionally `CHAT_PROVIDER`/`OLLAMA_CHAT_MODEL`) in `.env.local` (gitignored) and `source .env.local` before running.

## Output Contract

For a given consult note, the system's final response must always include:
1. **Proposed code(s)** — one or more verified ICD-11 codes, or the literal `"no confident match"` if no candidate passes verification.
2. **Retrieved evidence** — the catalog entries and guideline snippets that back the decision (or were considered, in the no-match case), each with its source id/citation.
3. **Confidence level** — an explicit `HIGH` / `MEDIUM` / `LOW` rating for the returned decision.

This contract applies regardless of outcome: a "no confident match" response is a valid, fully-specified result (evidence considered + `LOW` confidence + reason), not an error — consistent with the "fail safely if evidence is missing" persona rule.

## Implementation Plan

Target stack: Spring Boot 4.1.1, Java 25, Spring AI, PostgreSQL (with `pgvector` as the `VectorStore` backend, consistent with the project's existing Postgres decision).

Goal: given a clinical note, retrieve grounding evidence from `icd_catalog.json` (ICD-11 catalog) and `guideline_snippets.json` (coding guidelines), propose an ICD-11 code, then independently verify the proposal is actually supported by the retrieved evidence before returning it. If evidence is insufficient, fail safely instead of guessing (per project persona rule).

1. **Project scaffolding**
   - Create `pom.xml` with Spring Boot 4.1.1 parent, Java 25 toolchain, Spring AI BOM, `spring-ai-starter-vector-store-pgvector`, a Spring AI chat/embedding model starter, `spring-boot-starter-web`, `spring-boot-starter-data-jpa` (or JDBC), and the Postgres driver.
   - Add `application.properties` with placeholders for model provider config, datasource.

2. **Domain model**
   - `IcdCatalogEntry` (code, title, description, hierarchy/parent code).
   - `GuidelineSnippet` (text, source/citation, related code(s)).
   - `ClinicalNote` (raw text, optional structured metadata).
   - `RetrievedEvidence` (list of matched catalog entries + guideline snippets with similarity scores and source ids).
   - `CodeProposal` (candidate ICD-11 code(s), rationale, cited evidence ids).
   - `VerificationResult` (verdict: `SUPPORTED` / `NOT_SUPPORTED` / `INSUFFICIENT_EVIDENCE`, rationale, evidence ids actually relied upon).
   - `ConfidenceLevel` enum (`HIGH` / `MEDIUM` / `LOW`).
   - `CodingResult` (top-level response type per the Output Contract: proposed code(s) or `"no confident match"`, the backing `RetrievedEvidence`, and a `ConfidenceLevel`).

3. **Data ingestion pipeline**
   - Loader service that reads `icd_catalog.json` and `guideline_snippets.json`, maps each entry to a Spring AI `Document` with metadata tagging its source type (`catalog` vs `guideline`) and identifiers needed for citation.
   - Idempotent ingestion runner (e.g. `ApplicationRunner`) that embeds and writes documents into the `pgvector` `VectorStore` only if not already present (guard via a checksum/count check so re-runs don't duplicate rows).

4. **VectorStore & embedding configuration**
   - Configure the `EmbeddingModel` bean and `PgVectorStore` bean (table name, dimensions matching the chosen embedding model).
   - Define retrieval config (top-k, similarity threshold) as externalized properties, not hardcoded.

5. **Retrieval service**
   - `EvidenceRetrievalService` wrapping `VectorStore.similaritySearch`, querying separately (or with metadata filters) across `catalog` and `guideline` document types, and assembling a combined `RetrievedEvidence` context object.

6. **Proposal agent (ChatClient #1)**
   - `CodingProposalAgent` using a `ChatClient` with a system prompt encoding the persona rules (precise, evidence-grounded, no pseudocode/no guessing).
   - Prompt template injects the clinical note plus retrieved evidence; response constrained to structured output (Spring AI structured output / `BeanOutputConverter`) producing a `CodeProposal` with explicit evidence citations.

7. **Verification agent (ChatClient #2)**
   - `VerificationAgent` using a separate `ChatClient` call (distinct system prompt, not reusing the proposer's context) that independently checks whether the cited evidence actually supports the proposed code, returning a `VerificationResult`.
   - Keeping this as an independent call (not a self-reflection continuation) reduces confirmation bias from the proposal step.

8. **Agentic verification loop (orchestrator)**
   - `AgenticCodingWorkflow` coordinates: retrieve → propose → verify → assemble `CodingResult`.
   - On `NOT_SUPPORTED`, refine the retrieval query (or exclude the rejected code) and retry, bounded by a configurable max-iteration limit.
   - On exhausting retries or `INSUFFICIENT_EVIDENCE`, fail safely: return a `CodingResult` of `"no confident match"` with the evidence considered and `LOW` confidence, rather than a guess.
   - Derive `ConfidenceLevel` from the verification outcome, e.g.: `SUPPORTED` on the first pass with strong evidence → `HIGH`; `SUPPORTED` only after retry/refinement → `MEDIUM`; retries exhausted or `INSUFFICIENT_EVIDENCE` → `LOW` (paired with `"no confident match"`).

9. **API layer**
   - `POST /api/coding/analyze` accepting a clinical note, returning a `CodingResult` per the Output Contract (proposed code(s) or `"no confident match"`, backing evidence, confidence level) on every response — success and fail-safe alike.

10. **Audit trail**
    - Persist each workflow run to a Postgres table (e.g. `coding_audit_log`): note reference/hash, proposal(s), verification verdict(s) per iteration, final outcome, timestamps — needed for clinical traceability.

11. **Testing strategy**
    - Unit tests for retrieval/prompt assembly using mocked `ChatClient`/`VectorStore`.
    - Integration tests with Testcontainers (Postgres + pgvector) for the ingestion pipeline and end-to-end workflow.
    - Small labeled evaluation set (note → expected code) to sanity-check precision, using Spring AI evaluation utilities (e.g. relevancy/fact-checking evaluators) where applicable.

Note: this plan intentionally avoids Spring AI's built-in `QuestionAnswerAdvisor` for the main flow, since it retrieves-and-answers in one pass without an independent verification step — the explicit propose → verify → (retry|fail-safe) loop above is required to meet the "fail safely if evidence is missing" rule.