# identity

**何が同じ対象か。** 公開形は `ipfs://{cidv1}`（ADR-2608145100）。

## output-addressed（既定）

CID = hash(bytes)。git blob、IPFS block、Holochain `EntryHash`、unison term、
nix NAR。bytes が先、名前は後。

## input-addressed（recipe）

hash(inputs / call / derivation)。nix `.drv`、RPC の「この関数にこの引数」、
package lock の closure。**どっちか一方を選ばない。** recipe 自体も bytes なので
CID を持つ。違うのは「何の hash か」であって、公開 URI の文法ではない。

| 対象 | アドレスの種類 |
|---|---|
| 配布する HTML / wasm / datom snapshot | output |
| ビルド式、RPC call object | input（その object の CID は output） |
| Holochain `ActionHash` | 署名済み action bytes の output |
| Holochain `DnaHash` | DNA 定義 bytes の output。名前ではない |

CIDv0 (`Qm…`) は歴史的 L0 形。公開ラベルにしない。
HTTPS は identity ではない（transport / location）。

代数は `kotoba.protocol.address`（`output` / `input` / `holochain-kind`）。
