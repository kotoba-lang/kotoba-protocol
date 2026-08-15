# Discovery

Discovery is an **index**, not identity and not naming. It records who claims to
serve a CID without changing the hash of the bytes.

## IPNI and delegated providers

[IPNI](https://github.com/ipni/go-libipni) belongs to this plane: it indexes CID
to provider advertisements. IPNI never appears in the public resource URI; the
identity remains `ipfs://{cid}` while a finder returns candidate providers.

The current live adapter is delegated routing in
`io-libp2p-specs-kad-dht`:

```text
kad.routing/find-providers → GET /routing/v1/providers/{cid}
```

That HTTP surface is analogous to Kad `GET_PROVIDERS`; it does not make this
process a DHT node. A future IPNI adapter can replace the finder without
redesigning identity.

`kotoba.protocol.discover` owns the pure `advertise`/`lookup` algebra and the
injected `lookup-live`/`advertise-live` seams. It deliberately has no Kad
dependency. Provider changes never change the CID.

Reading is standardized; HTTP writing is historical. Delegated Routing V1
defines `GET /routing/v1/providers/{cid}`, but not
`PUT /routing/v1/providers`. Historic IPNI/index-provider writes use a Bitswap
envelope whose `Payload.Keys` contain CIDs. IPIP-378's POST proposal did not
land. A router returning 400 because signing is required is a failure, not a
silent success.

## Other indexes

| System | Indexed relationship |
|---|---|
| Bitswap wantlist/provider record | CID to peer |
| Holochain warrant gossip | violation evidence; a known ADR-2608038000 gap |
| DHT CreateLink entry | overlay edge; parent EntryHash remains unchanged |

Silence must not be interpreted as absence. `lookup-live` returning `[]` means
the query succeeded and no provider was found. `:ok? false` means the query
could not be completed.
