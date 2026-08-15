# Who may write

A hostname cannot authorize a write. Keys authorize writes.

- principal: `did:key`
- mutable name: key-derived IPNS
- delegation: CACAO; a depth-1 self-mint is the default for an actor's own graph
- admission: a governor rejects an intent before the actor reaches the log

CreateLink-shaped overlay writes pass through this boundary before reaching a
datom log or DHT metadata. Authorization to add an overlay does not authorize a
block rewrite, so the target entry CID remains unchanged.

The model follows Inga's "an actor writes only its own chain" rule. It does not
adopt a Holochain conductor or unordered write surface; an ordered log gives a
Datalog join one current reference.

There is no live HTTP endpoint here. The executable seam is
`kotoba.protocol.govern/write-overlay`. Its default `decide` function does not
verify signatures—cryptography belongs to `cacao`/`kotoba-auth`—but the shape
still fails closed:

- own graph with no CACAO: allow as a depth-1 self-mint
- own graph with a depth other than 1: deny
- another actor's graph: deny with `:foreign-chain`
- hostname-shaped author: deny with `:not-a-key`
- missing governor function: deny rather than defaulting to allow

A denied intent never reaches the action log and cannot advance its parent CID.
