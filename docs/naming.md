# naming

**可変名。hash ではない。** 公開形は `ipns://{k51}`。

名前は「いまこの hash を指す」ポインタ。git ref、nix attrpath、unison の
local name、DNS、Holochain の DNA/role 名がここに落ちる。

| 既存系 | この面 |
|---|---|
| git `refs/heads/main` | naming |
| IPNS k51 | naming（鍵由来。サーバではない） |
| DNS / DNSLink | naming → IPNS への発見 alias |
| HTTP `/{org}/{repo}` | naming の投影。content identity ではない |
| package tag (`latest`, `1.2.3`) | naming。lock の hash は identity |
| `:kotoba.app/id` | reverse-dns の local name |
| `:kotoba.app/latest` | 提供 actor の IPNS |

IPNS は overlay link（`:action`）の退化形: 1 name → 1 CID。
graph が要るなら datom / Holochain CreateLink 相当を使い、名前を増やさない。

live の継ぎ目は `kotoba.protocol.naming/lookup-live` と `publish-live`。
finder / putter は `kad.routing/resolve` / `publish`（`GET`/`PUT`
`/routing/v1/ipns/{k51}`）。名前を書き換えたら `:name-mismatch`。
値の CID が変わるのは正常 — それが名前の仕事。404 は
`:not-found`（名前が無い）。全 router 沈黙は `:all-routers-failed`。
混ぜない。この process は DHT node ではない。
