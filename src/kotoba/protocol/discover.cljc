(ns kotoba.protocol.discover
  "Discovery plane: CID → who serves it (ADR-2608145200 / ADR-2608145500).

  IPNI / Bitswap provider records / kad GET_PROVIDERS live here.
  A record does not change the CID. Putting a provider is not a merkle
  rewrite and not an overlay edge between entries.

  `advertise` / `lookup` are the in-memory algebra. `lookup-live` is the
  injected seam: production binds
  `(fn [cid] (kad.routing/find-providers http-fn cid opts))`.
  This ns does not require kad. Do not put IPNI into the public URI.
  Do not create an IPNI repo to hold this adapter."
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

(defn from-provider
  "Normalize a kad/IPNI provider map into a discovery record.
  Forces `:cid` to the asked CID so a finder cannot rewrite identity."
  [cid p]
  (cond
    (:error p) p
    (not (vocab/cid? cid)) {:error :invalid-cid :value cid}
    :else
    (let [peer (or (:peer p) (:id p) (:ID p) (get p "ID") (get p "id"))
          addrs (or (:addrs p) (:Addrs p) (get p "Addrs") (get p "addrs") [])]
      (record {:cid cid :peer peer :addrs addrs}))))

(defn- as-records
  [cid xs]
  (mapv (fn [p]
          (if (and (map? p) (= :discovery (:plane p)))
            (if (= cid (:cid p))
              p
              {:error :cid-mismatch :asked cid :got (:cid p)})
            (from-provider cid p)))
        xs))

(defn lookup-live
  "Ask an injected finder who serves `cid`. Does not rewrite the CID.

  `finder-fn` is `(fn [cid] -> {:ok? :providers} | [records] | {:ok? false …})`.
  Production:

      (fn [cid]
        (kad.routing/find-providers http-fn cid opts))

  Empty `:providers` with `:ok? true` is \"we asked, nobody provides\".
  `:ok? false` is \"we could not ask\". Silence is not a green pass.
  This ns does not depend on kad."
  [cid finder-fn]
  (cond
    (not (vocab/cid? cid)) {:error :invalid-cid :value cid}
    (not (ifn? finder-fn)) {:error :finder-fn-required}
    :else
    (try
      (let [r (finder-fn cid)]
        (cond
          (vector? r)
          (let [recs (as-records cid r)]
            (or (first (filter :error recs)) recs))
          (and (map? r) (:error r)) r
          (and (map? r) (false? (:ok? r))) r
          (and (map? r) (contains? r :providers))
          (let [recs (as-records cid (:providers r))]
            (or (first (filter :error recs)) recs))
          :else {:error :invalid-finder-result :value r}))
      (catch #?(:clj Exception :cljs :default) e
        {:error :finder-error
         :detail #?(:clj (.getMessage e) :cljs (str e))}))))

(defn cids
  "CIDs that have at least one provider. Not an identity set — an index."
  [idx]
  (into (sorted-set) (keys idx)))
