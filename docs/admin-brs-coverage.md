# Admin backend: BRS and persona assessment

Reference: LEAP-BRS-2026-014 v0.9, sections 6, 8, 9 and 11. This is an implementation assessment, not regulatory certification. No frontend is delivered in this branch. Full deployment smoke testing and production controls are not implied by unit/integration tests.

## Personas

| Persona | Delivered backend behavior | Remaining limits |
| --- | --- | --- |
| David, trading operations | Role-protected order search, lifecycle timelines, actual pricing basis, fill/balance snapshots, failure evidence, individual trade lookup, CSV export, dispute creation/assignment/notes/resolution/reopening, original evidence snapshots, action/access attribution, pandas workload insights | Older history cannot be invented; current-quote execution evidence is absent because pricing is order-based. API exposes gaps/truncation/ingestion timestamps. One operations fixture account; SSO and full retention controls pending. |
| Priya, commercial analyst | Isolated OLAP reporting by UTC period, instrument, account-type segment and currency; volumes/value/client trends, previous period, rankings, pandas daily series | Account type is the currently implemented segment definition; commercial segmentation needs agreement. Historical backfill and frontend chart integration pending. |
| Joanna, retail client | Existing Java client backend remains | Client isolation, current-quote execution and other trading gaps below still affect overall BRS acceptance. |

## Requirement traceability

| Requirement | Assessment | Evidence / gap |
| --- | --- | --- |
| BR-01 registration/sign-in | Existing client scope | Not newly certified by admin work; admin identities are separate |
| BR-02 own-client data | Known client-backend gap | Arbitrary account/order routes still need ownership enforcement. Admin analyst/operations boundaries are tested. |
| BR-03 bounded/revocable client session | Partial outside admin | Internal sessions expire/revoke; client JWT revocation remains separate |
| BR-04 buy/sell | Existing trading capability | Admin cases never place or reverse trades |
| BR-05 full trading-rule validation before acceptance | Not complete | Submission evidence explicitly identifies limited checks; cash/holding reservations and tradability validation remain |
| BR-06 durable intent then separate execution | Partial foundation | Orders and submission audit commit before matching; original submission-dispatch after-commit publication still lacks its own recovery outbox |
| BR-07 automatic client status updates | Not delivered here | Requires client SSE/WebSocket or equivalent |
| BR-08 current quote at execution | Not satisfied by current engine | Engine uses resting order price. Investigation reports this accurately; no quote is fabricated. |
| BR-09 atomic cash/holdings/trade | Partial foundation | Canonical matching is transactional, and successful evidence/outbox commits with it. DB-level concurrency and the separate direct-execution route need further work. |
| BR-10 current holdings/cash | Existing client scope | Must be proven under concurrent execution/restarts; admin work is not that proof |
| BR-11 own chronological history | Existing client scope | Client ownership/security and frontend integration pending |
| BR-12 market/instrument coverage | Not fully demonstrated | Provider ingestion exists, but current-quote settlement and all launch markets/classes need end-to-end verification |
| BR-13 indicative prices | Data API exists | Client frontend integration remains |
| BR-14 permanent attributable order/pricing/financial history | Improved, partial | New source lifecycle evidence, durable relay and protected projection cover observed actions. Current quote proofs, full validation, legacy history, comprehensive retention/backups and older mutable record paths remain gaps. |
| BR-15 complete reconstruction for any trade | Improved, partial | David's APIs and case evidence can reconstruct captured events without engineering logs. Missing historical/quote evidence is explicitly exposed, so the "any trade/full lifecycle" requirement cannot yet be signed off. |
| BR-16 isolated trading analysis | Implemented for projected completed fills | Reporting API has no OLTP connection. Complete source coverage, deployment capacity testing and agreed segment definition still need acceptance. |
| BR-17 business insights | Backend implemented | Trends, rankings, active clients and dispute insights available; teammate must deliver frontend surfaces |
| BR-18 justified extra capability | Candidate, not signed off | Dispute case workflow/operations insights extend investigation, but the team must justify differentiation. David's required lifecycle lookup alone is not the extra capability. |

## Service expectations and success criteria

- 9.1 trust/continuity: idempotent projection, replay, failure rollback and workflow concurrency are tested. End-to-end Java-to-OLAP restart/recovery, HA, balance concurrency and backup restore checks remain required.
- 9.2 responsiveness: bounded queries, indexes and pool limits exist. No production latency/capacity target has been measured.
- 9.3 confidentiality: roles, explicit CORS, CSRF, session rotation/expiry and operations-only detail APIs are implemented. Real staff identity management, secret management, TLS, role grants and client ownership enforcement remain.
- 9.4 retention/immutability: UPDATE/DELETE protection exists for lifecycle and dispute/access evidence and case origin. Superuser/DDL/TRUNCATE access, archiving, retention durations and backups need operational controls. Existing trading entities are not all converted into an immutable legal ledger.
- 9.5 usability: backend contracts exist; usability must be verified with the frontend and personas.
- Section 11: isolated reporting and dispute-support backend are available, but full client end-to-end behavior, complete traceability for every trade and deployment acceptance are not yet all demonstrated.

Conclusion: David's investigation and case-management backend and Priya's reporting backend are substantially implemented. It would be inaccurate to confirm that the entire admin dashboard/platform meets every BRS requirement today. The highest-priority blockers remain current-quote execution evidence, complete source lifecycle coverage, client isolation and production retention/recovery controls.
