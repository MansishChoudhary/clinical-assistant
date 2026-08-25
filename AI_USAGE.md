# AI Usage

This entire project — planning, implementation, debugging, evaluation, and documentation — was built with Claude Code (Claude Sonnet 5) as an agentic pair-programming assistant, working from a `CLAUDE.md` persona/plan that was iteratively reviewed and revised with the human before and during implementation.

## Where AI was used

- **Planning**: drafted the multi-step Implementation Plan and Output Contract in `CLAUDE.md` before any code was written.
- **Implementation**: generated the full Spring Boot application — domain model, JSON ingestion pipeline, pgvector retrieval service, two independent `ChatClient` agents (proposal + verification), the agentic propose/verify/retry orchestrator, JPA audit trail, REST API, and unit tests.
- **Dependency/version research**: used web search to confirm real, current artifact coordinates and compatibility (Spring Boot 4.1.1, Spring AI 2.0.0, Testcontainers 2.0.5's renamed modules) rather than relying on possibly-stale training data for fast-moving, version-sensitive dependencies.
- **Live debugging**: ran the actual app against real infrastructure (Postgres+pgvector in Docker, Ollama for local embeddings/chat) and diagnosed/fixed issues that only surfaced at runtime, not at compile time or in mocked unit tests.
- **Evaluation**: designed 15 clinical test cases, ran them against the live API, and assessed correctness in `EVAL_CASES.md` — including root-causing *why* certain cases failed by inspecting the audit trail, not just reporting pass/fail.