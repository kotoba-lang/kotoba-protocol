# discovery

**index。identity でも naming でもない。**

「この CID を誰が serve するか」を別データとして持つ。bytes の hash を
変えない。

## IPNI

[IPNI](https://github.com/ipni/go-libipni)（InterPlanetary Network Indexer）は
この面。CID → provider の広告索引。公開 URI に IPNI を入れない。
`ipfs://{cid}` のまま、finder が provider を返す。

この workspace に IPNI 専用 repo はまだ無い。いま実在するのは
`io-libp2p-specs-kad-dht` の delegated routing（Kad の `GET_PROVIDERS` に近い）。
IPNI を足すなら discovery の adapter であって、identity の再設計ではない。

レコード代数は `kotoba.protocol.discover`（`advertise` / `lookup`）。
provider を足しても CID は同じ。

## 他の index

| 系 | 何を索引するか |
|---|---|
| Bitswap wantlist / provider record | CID → peer |
| gossip of Holochain warrants | 違反証拠。ADR-2608038000 の既知 gap |
| DHT entry for CreateLink | overlay edge。親 EntryHash は変わらない |

discovery の沈黙を「無い」と読まない（測れなかった検査は緑ではない）。
