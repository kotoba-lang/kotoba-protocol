# Accountability tiers

Accountability attaches to **the power an act exercises**, not to the person.
The same principal may act anonymously for one act and as an accountable party
for another (ADR-2610081200). The executable algebra is
`kotoba.protocol.accountability`; R0 is pure data and validation, with no
cryptography.

| Tier | Visible to others | Linkable | Disclosure path |
|---|---|---|---|
| `:t0` | a credential predicate only | never | none |
| `:t1` | pairwise DID + context reputation | within one context | none |
| `:t2` | same as `:t1` | within one context | k-of-n threshold escrow |
| `:t3` | verified identity | always | public log |

## Three layers

- **L0 — invariants.** `tiers`, `externalities` (with `:floor`), `invariants`,
  `disclosure-floor`. Fixed. Neither consensus nor a profile changes them.
- **L1 — policy.** A content-addressed object a graph adopts: act → externality
  → minimum tier, plus open params. `policy-errors` rejects a power act below
  its `:t3` floor, a disclosure threshold below k≥3 / n≥5, and a notice period
  with no maximum. `default-policy` copies every externality default.
- **L2 — profile.** Fills the policy's open params for one jurisdiction,
  industry, or community, names the authorizers that may request a disclosure,
  and may raise a tier. Unknown keys are errors: that is how a profile is kept
  from naming L0. Statutes connect here, never in L0.

## Requirement and meet

`requirement` resolves one binding `{:policy :profile}` for an act at a time
`:at`. A threshold escalation with no threshold set escalates. An act before the
binding took effect is `:retroactive`: a later policy never re-judges an earlier
act. `meet` takes the stricter of several bindings; no binding is
`:accountability-unbound`, not `:t0`.

`kotoba.protocol.govern/decide` runs this check after the chain check, and only
when the intent names an `:action`. The held `:tier` is a claim; verifying the
credential behind it belongs to `kotoba-auth`, not here. An absent tier is `:t0`.

## Disclosure

`disclosure-errors` admits a request only for a `:t2` act, with an act CID, a
warrant CID, an authorizer the profile names, a transparency-log entry, and a
trustee set where no jurisdiction or organisation holds more than one third.
`:t0` and `:t1` return `:no-disclosure-path`. Admissible is not executed: the
k-of-n opening lives elsewhere.

## R1: pairwise DIDs, policy composition, receipt visibility

**Pairwise DIDs** (`kotoba.protocol.pairwise`) give tier `:t1` its meaning: one
root seed, a different `did:key` per context, derived as
`HKDF-SHA256(root-seed, salt "kotoba/pairwise/v1", info context, 32)` → Ed25519
seed. A context is a canonical ref or a reverse-DNS id, never free text.
`binding-errors` rejects one DID in two contexts (`:linked-across-contexts`),
a context bound twice, and the root DID shown as a pairwise DID. Derivation
itself lives in kotoba-auth.

**`compose-policies`** is the meet of whole policies: the union of acts, each
at the stricter tier, and each param at the intersection of ranges. An empty
intersection is `:param-range-empty`; two escalations on different params are
`:escalate-conflict`. The result is derived, not adopted, so it has no id.

**Receipt visibility.** Receipts are always written: an unreceipted key release
is still a custodian offence. `receipt-visibility` makes self-access
(`accessor = owner`, both `did:key`) `:owner-only`; everything else, anonymous
included, stays `:operator`. An owner-only receipt carries only
`owner-only-receipt-fields` (graph, operation, timestamp, visibility) in
plaintext — enough for the custody cross-audit, nothing about the reader.
