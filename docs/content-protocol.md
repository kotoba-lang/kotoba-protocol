# content protocol

**object の形と、2 種の link。** structure の正本。

URL・DNS・HTTP・Git・package manager・RPC を「全部同じ Merkle DAG」に
落とさない。落とす先は plane が違う。ここは **object と edge の種類**だけ。

## 2 種の link（`kotoba.protocol.layers/link-kinds`）

```
:merkle  edge は block の中身     親 CID が変わる    IPLD / git tree / nix closure
:action  edge は signed metadata  親 CID は不変      Holochain CreateLink / datom
```

公開した wasm の import 表、datom snapshot の中の CID、git tree の blob は
`:merkle`。ピンと検証が「この closure」を要求する。

「この entry は今あの entry を指す」を後から足すのは `:action`。
 Holochain が DHT 上の graph DB だと言っているのはこれ。
datom `[e :kotoba.link/to b tx true]` が既に同じ形: 値 CID は変わらず、
log の snapshot CID（L2）だけが進む。

IPLD Merkle link だけにすると、後から edge を足すたびに親を publish し直す。
CreateLink だけにすると、配布物の closure を 1 個の CID で検証できない。
**両方要る。混ぜて 1 種にしない。**

## 既存系の落とし先

| 系 | identity | structure | naming |
|---|---|---|---|
| Git | blob SHA | tree = merkle | ref |
| Nix | NAR hash *and* drv hash | closure = merkle | attrpath / lock |
| Unison | term hash | なし（名前は namespace） | local name |
| Holochain | EntryHash / ActionHash | CreateLink = action | DNA/role |
| HTTP URL | なし（location） | なし | パスは naming 投影 |
| DNS | なし | なし | naming |
| RPC | call object の CID | 結果 CID は別 object | capability 名 |
| IPFS UnixFS path | 使わない | 使わない | 使わない |

L1 datom が content protocol の事実面。L5 app manifest はその上の応用。
