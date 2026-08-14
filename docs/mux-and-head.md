# mux and head

ソケットが生きていることと、誰の chain の先端に居るかは別の座標。

- mux: Noise の握手のあと Yamux が切る stream。`io-libp2p-specs-transport`。
- head: その agent が最後に署名した action / datom tx。Holochain source chain、
  git HEAD、IPNS が指す graph CID、inga の quorum cert。

call を graph に載せる手順は identity が先。stream を張るのはその後。
誰が書いてよいかは `who-may-write.md`。
