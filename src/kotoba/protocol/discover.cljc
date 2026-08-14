(ns kotoba.protocol.discover
  "Discovery plane: CID → who serves it (ADR-2608145200).

  IPNI / Bitswap provider records / kad GET_PROVIDERS live here.
  A record does not change the CID. Putting a provider is not a merkle
  rewrite and not an overlay edge between entries.

  This ns is the record algebra. The live index is
  `io-libp2p-specs-kad-dht` delegated routing until an IPNI adapter exists.
  Do not put IPNI into the public URI."
  (:require [kotoba.protocol.vocab :as vocab]))

(defn index
  []
  {})

(defn record
  [{:keys [cid peer addrs]}]
  (cond
    (not (vocab/cid? cid)) {:error :invalid-cid :value cid}
    (not (string? peer)) {:error :invalid-peer :value peer}
    :else
    {:plane :discovery
     :cid cid
     :peer peer
     :addrs (vec addrs)
     :mutates-cid? false}))

(defn advertise
  "Add a provider for `cid`. The CID key is unchanged."
  [idx rec]
  (if (:error rec)
    rec
    (update idx (:cid rec) (fnil conj []) rec)))

(defn lookup
  [idx cid]
  (if (vocab/cid? cid)
    (or (get idx cid) [])
    {:error :invalid-cid :value cid}))

(defn cids
  "CIDs that have at least one provider. Not an identity set — an index."
  [idx]
  (into (sorted-set) (keys idx)))
