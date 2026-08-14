# kotoba-protocol

主権データ基盤 **kotoba** の層と責務の正本（spec-first / pure `.cljc` /
zero runtime deps）。ADR-2607071500 / ADR-2608145100 / ADR-2608145200。

実装は既存 repo 群に住む。この repo が持つのは**宣言（data）と検証（テスト）**
だけで、drift はテストで露見させる。

## 5 段（hash-addressed object network）

```
identity        content hash / CID          docs/identity.md
    ↓
structure       DAG / 2 種の link           docs/content-protocol.md
    ↓
mutable name    ref / IPNS / branch         docs/naming.md
    ↓
discovery       DHT / IPNI / gossip         docs/discovery.md
    ↓
transport       TCP / QUIC / WebRTC / Tor   docs/transport.md
```

path はどの段でも identity にしない。

## 8 面（URL / DNS / HTTP / Git / pkg / RPC の落とし先）

L0–L5 と直交する。1 つの Merkle DAG に全部畳まない。

| 面 | README | 既存系の例 |
|---|---|---|
| identity | [docs/identity.md](docs/identity.md) | git blob, CID, EntryHash, drv/NAR |
| naming | [docs/naming.md](docs/naming.md) | git ref, IPNS, DNS, package tag |
| routing | [docs/routing.md](docs/routing.md) | kad lookup |
| discovery | [docs/discovery.md](docs/discovery.md) | IPNI, providers |
| transport | [docs/transport.md](docs/transport.md) | TCP/QUIC, HTTPS gateway |
| session | [docs/mux-and-head.md](docs/mux-and-head.md) | Noise/Yamux, source-chain head |
| authorization | [docs/who-may-write.md](docs/who-may-write.md) | CACAO, governor |
| content protocol | [docs/content-protocol.md](docs/content-protocol.md) | IPLD merkle **and** datom/CreateLink |

正本は `kotoba.protocol.layers` の `planes` / `link-kinds`。
動く代数は `kotoba.protocol.graph`（edge）・`address`（hash の *of-what*）・
`surfaces`（既存系の投影）・`discover`（IPNI）・`route`（peer lookup）。

## Holochain との切り方

相性が良いのは **overlay**（CreateLink = 後から足せる signed edge、親 CID 不変）。
置き換えないのは **merkle**（配布物の closure を 1 CID で検証する）。
ランタイムとしての conductor と無順序 DHT は採らない（ADR-2608038000）。
datom が CreateLink 相当。IPLD link が git tree 相当。

## L0–L5（データ梯子）

| 層 | 責務 | 実装 |
|---|---|---|
| L0 address | bytes → CID | io-multiformats, io-ipld, dag-cbor |
| L1 fact | datom（overlay edge もここ） | datom |
| L2 graph | datom log の Merkle DAG → graph CID | kotobase-peer, chain, prolly-tree, mst |
| L3 authority | did:key、鍵由来 IPNS、CACAO | cacao, kotoba-auth, tech-ipfs-specs-ipns |
| L4 distribution | 配布 + discovery | kotobase.net, ipfs-pinner, kad-dht |
| L5 application | actor 実行と app manifest | kototama, this repo |

## Namespaces

- `kotoba.protocol.layers` — `layers` / `planes` / `link-kinds` / `owner-of` / `owner-plane`
- `kotoba.protocol.ref` — `ipfs://{cidv1}` \| `ipns://{k51}`。path は identity ではない
- `kotoba.protocol.graph` — 2 種の link の参照代数。`commit-log` が L2 graph CID（hasher は `chain.core/commit!`）
- `kotoba.protocol.address` — output vs input。Holochain の Entry/Action/Dna/Agent は *of-what*
- `kotoba.protocol.surfaces` — URL / DNS / HTTP / Git / pkg / RPC を 8 面へ投影。1 DAG に畳まない
- `kotoba.protocol.discover` — IPNI / provider record。CID を書き換えない。live は `lookup-live` / `advertise-live`
- `kotoba.protocol.route` — peer-id → addrs。peer id を書き換えない。live は `lookup-live`
- `kotoba.protocol.vocab` — `:kotoba.actor/*` `:kotoba.graph/*` `:kotoba.app/*` `:kotoba.link/*`
- `kotoba.protocol.app` — L5 manifest / embed-url / caps
- `kotoba.protocol.cid` — CIDv1 digest。UnixFS walk はしない

## atproto

[`kotoba-lang/atprotocol`](https://github.com/kotoba-lang/atprotocol) は投影層。

## Dev

```bash
clojure -M:test
```
