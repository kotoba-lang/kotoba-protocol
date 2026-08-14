# routing

**この CID / 名へ近づく。** packet routing ではない。

content routing: key から holder へ反復接近する。実装の正は
`io-libp2p-specs-kad-dht`（`/ipfs/kad/1.0.0` + delegated HTTP routing）。
このプロセスが DHT node であることと、signed record を quorum で読むことは
別（kad-dht README が切っている）。

| | routing | discovery |
|---|---|---|
| 問 | 次に誰を訊くか | 誰が持っていると索引されているか |
| 例 | kad lookup | IPNI, provider record |

`GET /routing/v1/ipns/{k51}` は naming の解決を routing 面が代行しているだけ。
返る値の identity は CID のまま。

`GET /routing/v1/peers/{peer-id}` は peer の到達先（addrs / protocols）。
discovery の `GET /providers/{cid}` とは問が違う。live の継ぎ目は
`kotoba.protocol.route/lookup-live`（finder は
`kad.routing/find-peers`）。finder が別 peer id を返すと `:peer-mismatch`。

provide の署名を kad が持つことはしない。IPIP-0526 は historic で、
署名対象 bytes は未決。捏造しない。
