# mux and head

ソケットが生きていることと、誰の chain の先端に居るかは別の座標。

- mux: Noise の握手のあと Yamux が切る stream。`io-libp2p-specs-transport`。
- head: その agent が最後に署名した action / datom tx。Holochain source chain、
  git HEAD、IPNS が指す graph CID、inga の quorum cert。

代数は `kotoba.protocol.mux`。`at-head?` は mux の生存を見ない。
dialer の stream-id は奇数、listener は偶数、0 は予約。
`open-live` はこの process では常に `:not-a-dht-node`。sockets は開かない。
live は `:blocked-until :transport`（ADR-2608145800）。

call を graph に載せる手順は identity が先。stream を張るのはその後。
誰が書いてよいかは `who-may-write.md`。
この面の docs はこのファイル。`session.md` は作らない。
