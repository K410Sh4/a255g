# GENESIS v0.1

GENESIS is the first learning layer inside A25 Lab. Its goal is not to pretend that a chatbot has "learned" a file; it stores evidence with provenance, creates explicitly labeled hypotheses, receives human feedback, and evolves a bounded policy while preserving rollback.

## v0.1 capabilities

- Import text-based PDF files through Android's native PDF APIs on Android 15+.
- Keep source name and page number for every knowledge chunk.
- Encrypt persistent brain state with an AES-256-GCM key held by Android Keystore.
- Store data under `noBackupFilesDir`.
- Retrieve evidence for a natural-language query using local lexical relevance.
- Run autonomous study cycles that discover recurring concept relationships.
- Keep derived statements as hypotheses, never silently promote them to source facts.
- Accept positive/negative human feedback.
- Adapt bounded evidence/novelty weights after feedback batches.
- Maintain encrypted snapshots and rollback.
- Schedule a bounded study cycle every six hours through WorkManager, only while charging and when the battery is not low.

## Security boundary

The immutable application/kernel layer controls storage, permissions, scheduling, import limits, encryption and rollback.

GENESIS v0.1 may mutate:

- learned documents and chunks;
- hypotheses;
- hypothesis feedback;
- bounded policy weights;
- brain-state version.

GENESIS v0.1 may **not** mutate:

- APK/Kotlin/Java code;
- Android permissions;
- dependencies;
- manifest;
- security boundary;
- WorkManager constraints.

Self-generated source patches, shadow evaluation, CI promotion and explicit human approval belong to a later phase.

## PDF limits

To avoid memory and storage abuse, one import is bounded to:

- 400 pages;
- 2,000,000 extracted characters.

The global v0.1 memory is bounded to 12,000 chunks. A PDF with no embedded text is registered but not OCRed yet.

## Meaning of "autonomous study"

A study cycle does not claim consciousness. It analyzes the persisted corpus, finds recurring concept pairs, measures support across chunks/sources, creates hypotheses with supporting chunk IDs, and ranks them using the current brain policy.

Human feedback changes the learning state without rewriting source evidence.

## Next engineering milestones

1. OCR for scanned PDFs with an on-device path.
2. Embeddings/semantic retrieval instead of lexical retrieval only.
3. Local reasoning model backend behind a replaceable interface.
4. Knowledge graph with typed relations and contradiction tracking.
5. Candidate behavior DSL, shadow evaluation, human promotion, rollback.
6. Exportable audit trail showing exactly which sources support each conclusion.
