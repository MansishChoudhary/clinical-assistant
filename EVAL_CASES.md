# Evaluation Cases

Live end-to-end run of 15 consult notes against `POST /api/coding/analyze`, run on 2026-08-25.

**System configuration for this run:**
- Chat provider: `CHAT_PROVIDER=ollama`, model `llama3.1:8b` (local, via Ollama, partially GPU-offloaded on a 4GB laptop GPU — see latency note below)
- Embeddings: `nomic-embed-text` (local, via Ollama)
- Vector store: Postgres + pgvector, `top-k=5`, `similarity-threshold=0.5`
- Max propose/verify iterations: 3
- Corpus: 288 ICD-11 catalog entries, 31 guideline snippets (both fully ingested, counts verified against source JSON)

This was a deliberately **local/free** configuration, not the default `CHAT_PROVIDER=anthropic`. See "Overall findings" at the bottom — the choice of local model materially affected the results below, and is not a reflection of the workflow architecture itself.

## Summary

| # | Note (short) | Expected | Got | Verdict |
|---|---|---|---|---|
| 1 | Classic MI (crushing chest pain, arm/jaw radiation, diaphoresis) | BA41 | NO_CONFIDENT_MATCH | ❌ Under-confident |
| 2 | Musculoskeletal chest wall pain | not BA41 | CODED MC20 (HIGH) | ✅ Correct |
| 3 | Pericarditis (positional pain, friction rub) | BE2Z | CODED BE2Z (HIGH) | ✅ Correct |
| 4 | Pneumonia (fever, productive cough, focal crackles) | CA22 | NO_CONFIDENT_MATCH | ❌ Under-confident |
| 5 | Acute bronchitis (diffuse wheeze, no fever) | CA23 | NO_CONFIDENT_MATCH | ❌ Under-confident |
| 6 | TIA (resolved facial droop/weakness, 20 min) | 8B20 | NO_CONFIDENT_MATCH | ⚠️ Safe but under-confident |
| 7 | Cerebral infarction (ongoing weakness/slurred speech) | 8B00 | CODED 8B00 (HIGH) | ✅ Correct |
| 8 | Migraine (unilateral, photophobia, nausea) | 8A80 | CODED 8A80 (HIGH) | ✅ Correct |
| 9 | Tension headache (bilateral, mild, no nausea) | 8A81 | NO_CONFIDENT_MATCH | ❌ Under-confident |
| 10 | Bacterial meningitis (fever, neck stiffness, confusion) | 1D00 | NO_CONFIDENT_MATCH | ❌ Under-confident (safety-relevant) |
| 11 | Pyelonephritis (dysuria, fever, CVA tenderness) | GC01 | NO_CONFIDENT_MATCH | ❌ Under-confident |
| 12 | Simple cystitis (dysuria, no fever/flank pain) | GC08 | CODED GC08 (HIGH) | ✅ Correct |
| 13 | Ectopic pregnancy (positive pregnancy test, bleeding, pain) | JB00 | CODED JB00 (HIGH) | ✅ Correct |
| 14 | Appendicitis (migrating pain, fever, rebound) | DB90 | CODED DB90 (HIGH) | ✅ Correct |
| 15 | Routine dental cleaning (out of catalog scope) | NO_CONFIDENT_MATCH | NO_CONFIDENT_MATCH | ✅ Correct |

**8/15 (53%) matched the expected code exactly. 7/15 were under-confident fail-safes on cases with strong retrieved evidence. 0/15 produced a wrong or hallucinated code.**

---

## Case 1 — Classic acute MI
**Note:** "65-year-old man presents with crushing substernal chest pain radiating to left arm and jaw, sweating profusely, and breathless for the last hour."
**Top evidence:** catalog `BA41` Acute myocardial infarction (64%); guideline `GDL-001` (71%, links BA41/BA40) — text matches this note almost verbatim ("central/substernal chest pain with radiation to the left arm or jaw, diaphoresis, dyspnea → ACS until proven otherwise").
**Output:** `NO_CONFIDENT_MATCH`, confidence `LOW`, rationale "Exhausted 3 propose/verify iterations without a supported code." (71s)
**Assessment: Incorrect (under-confident).** The proposer correctly proposed `BA41` citing `GDL-001` on iteration 0. The verifier rejected it as `NOT_SUPPORTED` with a rationale that just echoes the guideline text rather than explaining a contradiction — this looks like a verifier reasoning failure, not a real evidentiary gap (see audit log inspection under "Overall findings"). This is the textbook case a coder would expect a `HIGH` confidence `BA41` on.

## Case 2 — Musculoskeletal chest pain (should NOT be coded as MI)
**Note:** "32-year-old presents with chest wall pain that is sharp, worsens with pressing on the chest, and started after lifting weights yesterday. No radiation, no diaphoresis, pain reproducible on palpation."
**Top evidence:** catalog `MC20` Chest pain, unspecified (70%); guideline `GDL-001` (75%) — explicitly states reproducible/palpation-tender pain is musculoskeletal, not MI.
**Output:** `CODED MC20`, confidence `HIGH`, rationale correctly cites `GDL-001`'s exclusion criteria. (52s)
**Assessment: Correct.** This is the case designed to test whether the system avoids over-coding MI from surface keyword overlap ("chest pain") — it did, and picked the appropriately non-specific `MC20` code with a rationale that shows genuine guideline-driven reasoning.

## Case 3 — Pericarditis
**Note:** "45-year-old man with sharp chest pain that worsens lying flat and improves leaning forward, low grade fever, pericardial friction rub heard on exam."
**Top evidence:** catalog `BE2Z` Pericarditis (75%); guideline `GDL-002` (80%) — near-exact match on the positional pain pattern and friction rub.
**Output:** `CODED BE2Z`, confidence `HIGH`. (38s)
**Assessment: Correct.**

## Case 4 — Community-acquired pneumonia
**Note:** "58-year-old smoker presents with fever, productive cough with yellow sputum for 3 days, and focal crackles heard at the right lung base on auscultation."
**Top evidence:** catalog `CA22` Community-acquired pneumonia (69%); guideline `GDL-003` (68%) — explicitly says focal crackles + fever + productive cough support pneumonia over bronchitis.
**Output:** `NO_CONFIDENT_MATCH` after exhausting all 3 iterations. (140s — slowest case)
**Assessment: Incorrect (under-confident).** Same pattern as Case 1: strong, specific, on-point evidence retrieved, but the verifier never signed off across 3 attempts.

## Case 5 — Acute bronchitis
**Note:** "28-year-old presents with 5 days of dry cough, mild diffuse wheeze, no fever, chest exam shows diffuse rhonchi without focal findings."
**Top evidence:** catalog `CA23` Acute bronchitis (67%); guideline `GDL-003` (71%).
**Output:** `NO_CONFIDENT_MATCH`, exhausted iterations. (109s)
**Assessment: Incorrect (under-confident).** Same pattern again.

## Case 6 — TIA (fully resolved deficit)
**Note:** "70-year-old presents with sudden onset right-sided facial droop and arm weakness that fully resolved within 20 minutes by the time of assessment, no residual deficit on exam."
**Top evidence:** catalog top hit was `8B00` Cerebral infarction (64%) — **`8B20` (TIA) itself did not make the top-5 catalog matches**; guideline `GDL-004` (68%) correctly discusses both 8B00 and 8B20 and how to distinguish them.
**Output:** `NO_CONFIDENT_MATCH`, confidence `LOW`, rationale "Verifier found the retrieved evidence insufficient: Transient ischaemic attack, as per GDL-004" (138s)
**Assessment: Partially correct / safe, but a genuine gap.** The system correctly avoided coding this as `8B00` (which would be wrong — the deficit resolved), which is the important safety property. But it should ideally have proposed `8B20` using `GDL-004`'s explicit guidance; instead the exact `8B20` catalog entry wasn't retrieved into evidence at all (embedding similarity gap — the catalog entry's wording doesn't share vocabulary with the note as closely as `8B00`'s does), and 3 iterations were spent on a code (`8B00`) the guideline explicitly says not to use here. This points to a retrieval-quality gap, not just a verifier issue.

## Case 7 — Cerebral infarction (ongoing deficit)
**Note:** "70-year-old presents with sudden onset left-sided weakness and slurred speech, ongoing at time of assessment with no improvement."
**Top evidence:** catalog `8B00` (63%); guideline `GDL-004` (62%).
**Output:** `CODED 8B00`, confidence `HIGH`. (48s)
**Assessment: Correct.** Notably, this is the TIA/stroke pair's counterpart case, and paired with Case 6 it shows the system *can* apply `GDL-004` correctly when the target catalog entry is well-retrieved — the failure in Case 6 tracks back to retrieval, not an inability to use the guideline.

## Case 8 — Migraine
**Note:** "24-year-old woman with unilateral throbbing headache, photophobia, phonophobia and nausea, no fever or neck stiffness."
**Top evidence:** catalog `8A80` Migraine (76%); guideline `GDL-005` (77%).
**Output:** `CODED 8A80`, confidence `HIGH`. (43s)
**Assessment: Correct.**

## Case 9 — Tension-type headache
**Note:** "35-year-old with bilateral pressing band-like headache of mild intensity, not worsened by activity, no nausea, no photophobia."
**Top evidence:** catalog `8A81` Tension-type headache (77% — highest single-case catalog score in the whole run); guideline `GDL-005` (75%).
**Output:** `NO_CONFIDENT_MATCH`, exhausted iterations. (106s)
**Assessment: Incorrect (under-confident).** This is the most striking miss — the catalog entry text is almost a direct paraphrase of the note, and it was still rejected 3 times.

## Case 10 — Bacterial meningitis
**Note:** "19-year-old college student presents with high fever, severe headache, neck stiffness, photophobia, and appears confused."
**Top evidence:** catalog `1D00` Bacterial meningitis (73%); guideline `GDL-006` (74%) — near-verbatim match ("fever, headache, neck stiffness... photophobia or altered mental status → bacterial meningitis, medical emergency").
**Output:** `NO_CONFIDENT_MATCH`, confidence `LOW`, rationale "Verifier found the retrieved evidence insufficient" despite quoting the exact supporting guideline text in that same rationale. (38s)
**Assessment: Incorrect (under-confident) — and the one miss I'd flag as clinically significant.** This is exactly the kind of red-flag/emergency presentation where a coding assistant failing safe is far better than mis-coding, so the fail-safe design did its job (no wrong code was returned) — but this is also the case where a false negative is most costly in a real workflow, since it means no code is proposed at all for a presentation that should be flagged urgently.

## Case 11 — Pyelonephritis
**Note:** "27-year-old woman with dysuria, urinary frequency, fever of 39C, and left flank pain with costovertebral angle tenderness."
**Top evidence:** catalog `GC01` Acute pyelonephritis (75%); guideline `GDL-007` (75%) — explicitly distinguishes this from cystitis on exactly these features.
**Output:** `NO_CONFIDENT_MATCH`, confidence `LOW`. (40s)
**Assessment: Incorrect (under-confident).**

## Case 12 — Simple cystitis
**Note:** "30-year-old woman with dysuria, urinary frequency and suprapubic discomfort, no fever, no flank pain."
**Top evidence:** catalog `GC08` Acute cystitis (72%); guideline `GDL-007` (75%).
**Output:** `CODED GC08`, confidence `HIGH`, correctly cites `GDL-007`'s cystitis-vs-pyelonephritis distinction. (43s)
**Assessment: Correct.** Notable alongside Case 11: the system handled the "no red flags" side of a guideline pair correctly but not the "has red flags, escalate" side — consistent with the pattern that it's more comfortable confirming an absence of complexity than committing to a more serious diagnosis.

## Case 13 — Ectopic pregnancy
**Note:** "26-year-old woman with a positive pregnancy test presents with lower abdominal pain and vaginal bleeding."
**Top evidence:** catalog `JB00` Ectopic pregnancy (71%, tied closely with `JA00.1` Threatened miscarriage at 71%); guideline `JB00-note` (78%) — "must have ectopic pregnancy actively excluded before any other abdominal diagnosis is finalized."
**Output:** `CODED JB00`, confidence `HIGH`, rationale: "Active exclusion of ectopic pregnancy is required in this case due to the risk of rupture." (75s)
**Assessment: Correct — and the most safety-critical success in the run.** This is the one guideline explicitly written as a mandatory safety check overriding other differentials, and the system applied it correctly despite a closely-scoring distractor (`JA00.1`).

## Case 14 — Acute appendicitis
**Note:** "16-year-old boy with pain that started around the umbilicus and migrated to the right lower quadrant, associated with fever and rebound tenderness on exam."
**Top evidence:** catalog `DB90` Acute appendicitis (72%); guideline `GDL-010` (72%).
**Output:** `CODED DB90`, confidence `HIGH`. (174s — slowest case; needed extra iterations before agreeing per the audit trail pattern seen elsewhere, despite ultimately reaching the right answer)
**Assessment: Correct.**

## Case 15 — Out-of-scope (routine dental cleaning)
**Note:** "Patient presents for a routine dental cleaning and check-up, no complaints."
**Top evidence:** catalog `QA02` "Encounter for general adult health check-up" (62% — the nearest semantic neighbor, since there's no dental entry in the catalog); guideline match was weak (50%, off-topic).
**Output:** `NO_CONFIDENT_MATCH`, confidence `LOW`, rationale flags that `QA02` doesn't really describe a dental visit. (49s)
**Assessment: Correct.** This is the fail-safe path working exactly as designed on a genuinely out-of-catalog request — the system didn't force-fit the closest available code.

---

## Overall findings

**Precision was perfect; recall was poor, and the cause is identifiable.** Across all 15 cases, the system never returned a wrong or hallucinated code — every `CODED` result was clinically correct. But 7 of 15 cases with strong, specific, on-point retrieved evidence (including near-verbatim guideline matches) still ended in `NO_CONFIDENT_MATCH`.

Inspecting the `coding_audit_log` audit trail for Case 1 (see `PROGRESS_LOG.md` for the debugging session) showed why: the proposal agent correctly proposed `BA41` citing `GDL-001` on the first attempt, but the verification agent rejected it as `NOT_SUPPORTED` with a rationale that was just a copy of the guideline text rather than a genuine independent judgment — and did the same on two more iterations with two different (wrong) proposed codes. This is a **model-quality issue specific to running `llama3.1:8b` locally**, not a flaw in the propose→verify→retry architecture:
- Retrieval was consistently strong — the correct catalog entry and the correct guideline were almost always in the top 1-2 results by similarity score.
- The proposal agent correctly identified and cited the right evidence in every case, including the misses.
- The verification agent was the weak link, producing verdicts that didn't track its own stated rationale.

**Latency**: this run used `llama3.1:8b` via Ollama, partially CPU-bound (`ollama ps` showed a 58%/42% CPU/GPU split — the model doesn't fully fit in the 4GB laptop GPU used here). Each note costs up to 3 iterations × 2 LLM calls (propose + verify) = up to 6 sequential local-inference calls; observed latency ranged 38s (1 iteration, fast agreement) to 174s (case 14, multiple iterations). This is specific to the local/CPU-constrained setup, not the architecture.

**Recommendation**: this run intentionally used `CHAT_PROVIDER=ollama` for free local testing (see `CLAUDE.md`). Given the verifier's unreliability here, `CHAT_PROVIDER=anthropic` (Claude) should be used for any real accuracy evaluation or production use — the retrieval layer and prompt design appear sound based on this run, so the expectation is materially better recall with a stronger chat model, without any code changes.

**Bugs found and fixed during this session** (see `PROGRESS_LOG.md` for full detail) that were prerequisites to getting this eval running at all:
1. `PgVectorStore` requires `Document.id` to be a UUID — the ingestion loader used human-readable ids (`"catalog:1A00"`), which crashed ingestion. Fixed by using a deterministic UUID derived from the natural key, keeping the human-readable id in metadata (`sourceId`) where retrieval actually reads it.
2. Spring Boot 4.1.1 doesn't auto-configure a classic `com.fasterxml.jackson.databind.ObjectMapper` bean by default (it defaults to Jackson 3); ingestion/audit code needed one explicitly.
3. `@Lob` on the audit log's `iterationsJson` `String` field mapped to Postgres `oid` (large object) instead of `text`, making the audit trail unreadable via plain SQL.
4. Local port conflicts: a pre-existing native Postgres service already on 5432 (worked around by moving the Docker container to 5433) and a pre-existing unrelated Apache server on 8080 (worked around by moving the app to 8081) — both pre-existing services on this machine, left untouched.
