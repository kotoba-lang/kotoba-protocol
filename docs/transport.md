# transport

**bytes を隣へ運ぶ。identity を持たない。**

TCP / QUIC / WebRTC / Tor / relay / WebSocket。multiaddr。
実装: `io-libp2p-specs-transport`、`noise`、`libp2p`。

代数は `kotoba.protocol.transport`。hop は multiaddr + capability。
`ipfs://` / `ipns://` / `https://` を hop の addr にすると `:not-a-multiaddr`。
hop を CID / peer に `attach` しても identity は書き換わらない。
`dial-live` はこの process では常に `:not-a-dht-node`。sockets は開かない。
live は `:blocked-until :dht-node-transport`（ADR-2608145800）。

HTTPS gateway（`https://ipfs.kotobase.net/ipfs/{cid}`、
`{cid}.ipfs.itonami.cloud`）は transport + location の投影。
canonical は `ipfs://{cid}` のまま（ADR-2608145100 / 2608140500）。

URL を object graph に落とすとき、HTTP URL はここに落ちる。
「同じ CID」の証拠にはならない。
