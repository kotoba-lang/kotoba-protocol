# kotoba-protocol

主権データ基盤 **kotoba** の層と責務の正本（spec-first / pure `.cljc` /
zero runtime deps）。Datomic モデルの datom・IPLD/CID・鍵由来 IPNS・IPFS
配布・kototama actor 実行を **1 つの層表**に統合する。ADR-2607071500。

実装は既存 repo 群に住む。この repo が持つのは**宣言（data）と検証（テスト）**
だけで、drift はテストで露見させる。

## Layers

| 層 | 責務 | 実装 |
|---|---|---|
| L0 address | bytes → CID（multiformats / IPLD dag-cbor） | io-multiformats, io-ipld, dag-cbor |
| L1 fact | datom `[e a v tx added?]`（append-only、retraction も事実） | datom |
| L2 graph | datom log の Merkle DAG → graph CID | kotobase-peer, chain, prolly-tree, mst |
| L3 authority | Ed25519 did:key、**graph 名 = 鍵由来 IPNS 名**、CACAO capability chain。サーバは authority ではない | kotobase.cacao/cid, kotoba-auth, tech-ipfs-specs-ipns |
| L4 distribution | CID 実体の配布（IPFS/pinning/B2）と IPNS head の publish | kotobase(.net), ipfs-pinner |
| L5 application | actor 実行（kototama `actor:host` ABI）と app 提供（manifest / **appview** / **embedUrl**） | kototama, wasm-webcomponent, this repo |

## Namespaces

- `kotoba.protocol.layers` — 層表と関心事→層の対応（`owner-of`）。**data が spec**。
- `kotoba.protocol.vocab` — datom 語彙 registry（`:kotoba.actor/*`
  `:kotoba.graph/*` `:kotoba.app/*`）+ 値述語 + `validate-entity`。
- `kotoba.protocol.app` — L5 app モデル:
  - **manifest** = actor 自身の graph に置く `:kotoba.app/*` datoms。graph
    書込は L3 の CACAO 認可を通るので**署名済み・履歴付き**が構造的に付く
    （Farcaster signed manifest / Matrix state event / Nostr NIP-89 の外付け
    機構が、graph の性質として得られる）。
  - **appview** = graph を描画する全画面 app（`:kotoba.app/appview-of`）。
  - **embedUrl** = host が文脈内に mount する URL（`https|ipfs|ipns`、
    `parse-embed-url` / `resolve-embed-url` が検証可能性込みで解決）。
    mount 手段（iframe / web component）は host 実装詳細で protocol 外。
  - capability registry: kototama `actor:host` 8 imports（実装は
    kototama.contract / actor-host.js、guest module 名は `"kotoba"`）+
    host bridge caps（同期 ABI でブラウザ実装不能な `http-post` 系の代行 —
    鍵も token も app には渡らない）。

## atproto との関係

atproto 互換は [`kotoba-lang/atprotocol`](https://github.com/kotoba-lang/atprotocol)
— **kotoba-protocol の上に立つ投影層**。lexicon/record/XRPC/handle は
あちらが所有し、鍵・datom の真実・CID/IPNS・配布はこちらに委譲する。
旧 W-Protocol の profile 拡張（performerType/uiType 等）は atprotocol 側の
deprecated compat alias に降格した。

## Dev

```bash
clojure -M:test
```
