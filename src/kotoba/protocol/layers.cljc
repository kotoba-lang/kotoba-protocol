(ns kotoba.protocol.layers
  "kotoba-protocol の層と責務の正本 (ADR-2607071500)。

  この表が spec の本体であり、README/SPEC はここから読める narrative、
  テストはこの data を検証する。実装は既存 repo 群に住み、この repo は
  宣言と検証だけを持つ (drift はテストで露見させる)。")

(def layers
  [{:layer :l0-address
    :name "address"
    :responsibility "bytes → CID (multihash/multibase/multicodec, IPLD dag-cbor)"
    :impl-repos ["io-multiformats" "io-ipld" "dag-cbor"]}

   {:layer :l1-fact
    :name "fact"
    :responsibility "datom [e a v tx added?] — Datomic モデル。事実は append-only、retraction も事実"
    :impl-repos ["datom"]}

   {:layer :l2-graph
    :name "graph"
    :responsibility "datom log の Merkle DAG 化 → graph CID。db 名前空間 kotobase/db/<did>/<name>"
    :impl-repos ["kotobase-peer" "chain" "prolly-tree" "mst" "arrangement"]}

   {:layer :l3-authority
    :name "authority"
    :responsibility "Ed25519 did:key。graph 名 = 鍵由来 IPNS 名。書込認可 = CACAO capability chain (自 graph へは depth-1 自己 mint)。AUTHORITY は鍵由来 IPNS 名への署名であってサーバではない"
    :impl-repos ["kotobase-client (kotobase.cacao/cid)" "kotoba-auth" "tech-ipfs-specs-ipns"]}

   {:layer :l4-distribution
    :name "distribution"
    :responsibility "CID 実体の配布 (IPFS retrieval/pinning、B2 offload) と IPNS head の publish/resolve"
    :impl-repos ["kotobase (kotobase.net)" "ipfs-pinner"]}

   {:layer :l5-application
    :name "application"
    :responsibility "actor 実行 (kototama actor:host ABI + HostCaps/RuntimeLimits) と app 配布/提供 (manifest datoms / appview / embedUrl)"
    :impl-repos ["kototama" "wasm-webcomponent" "kotoba-protocol (vocab/app)"]}])

(def concerns
  "関心事 → 属する層。owner-of で引く。"
  {:cid :l0-address
   :ipld :l0-address
   :multiformats :l0-address
   :datom :l1-fact
   :retraction :l1-fact
   :graph-cid :l2-graph
   :merkle-dag :l2-graph
   :db-namespace :l2-graph
   :did-key :l3-authority
   :ipns-naming :l3-authority
   :cacao :l3-authority
   :write-authorization :l3-authority
   :ipfs-retrieval :l4-distribution
   :pinning :l4-distribution
   :ipns-publish :l4-distribution
   :blob-offload :l4-distribution
   :actor-execution :l5-application
   :host-caps :l5-application
   :app-manifest :l5-application
   :appview :l5-application
   :embed-url :l5-application})

(defn layer
  "層 keyword → 層エントリ | nil。"
  [k]
  (first (filter #(= k (:layer %)) layers)))

(defn owner-of
  "関心事 keyword → それを所有する層エントリ | nil。"
  [concern]
  (some-> (concerns concern) layer))
