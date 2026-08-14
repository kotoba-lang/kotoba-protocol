# transport

**bytes を隣へ運ぶ。identity を持たない。**

TCP / QUIC / WebRTC / Tor / relay / WebSocket。multiaddr。
実装: `io-libp2p-specs-transport`、`noise`、`libp2p`。

HTTPS gateway（`https://ipfs.kotobase.net/ipfs/{cid}`、
`{cid}.ipfs.itonami.cloud`）は transport + location の投影。
canonical は `ipfs://{cid}` のまま（ADR-2608145100 / 2608140500）。

URL を object graph に落とすとき、HTTP URL はここに落ちる。
「同じ CID」の証拠にはならない。
