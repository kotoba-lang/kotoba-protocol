# discovery

**index。identity でも naming でもない。**

「この CID を誰が serve するか」を別データとして持つ。bytes の hash を
変えない。

## IPNI

[IPNI](https://github.com/ipni/go-libipni)（InterPlanetary Network Indexer）は
この面。CID → provider の広告索引。公開 URI に IPNI を入れない。
`ipfs://{cid}` のまま、finder が provider を返す。

IPNI 専用 repo は作らない。いま live な adapter は
`io-libp2p-specs-kad-dht` の delegated routing
（`kad.routing/find-providers` → `GET /routing/v1/providers/{cid}`）。
それは Kad の `GET_PROVIDERS` に近い HTTP 窓口であって、この process が
DHT node になることではない。IPNI を足すなら同じ discovery adapter を
差し替えるだけ。identity の再設計ではない。

レコード代数は `kotoba.protocol.discover`（`advertise` / `lookup`）。
live の継ぎ目は `lookup-live`（finder は注入）と `advertise-live`（putter は注入）。
protocol は kad に依存しない。provider を足しても CID は同じ。

**読みは spec、書きは historic。** `GET /routing/v1/providers/{cid}` は
Delegated Routing V1 にある。`PUT /routing/v1/providers` は spec に無い
（IPNI / index-provider の bitswap envelope。CID は URL ではなく
`Payload.Keys`。IPIP-378 の POST は着地せず閉じた）。署名も時計も kad は
持たない。router が署名を要求して 400 を返すのは失敗であって、黙った成功ではない。

## 他の index

| 系 | 何を索引するか |
|---|---|
| Bitswap wantlist / provider record | CID → peer |
| gossip of Holochain warrants | 違反証拠。ADR-2608038000 の既知 gap |
| DHT entry for CreateLink | overlay edge。親 EntryHash は変わらない |

discovery の沈黙を「無い」と読まない（測れなかった検査は緑ではない）。
`lookup-live` が `[]` なのは「訊いて、誰も持っていない」。`:ok? false` は
「訊けなかった」。
