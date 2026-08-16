(ns kotoba.protocol.discover
  "Discovery plane: CID → who serves it (ADR-2608145200 / ADR-2608145500 /
  ADR-2608145600 / ADR-2608160300).

  IPNI / Bitswap provider records / kad GET_PROVIDERS live here.
  A record does not change the CID. Putting a provider is not a merkle
  rewrite and not an overlay edge between entries.

  `advertise` / `lookup` are the in-memory algebra.
  `lookup-live` binds `(fn [cid] (kad.routing/find-providers http-fn cid opts))`.
  `advertise-live` production putter is
  `(fn [rec] (ipni.advertise http-fn rec opts))` (io-ipni-specs).
  `kad.routing/provide` remains the historic Bitswap PUT; it is not
  kotobase's production write. This ns requires neither kad nor ipni.
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

(defn advertise-live
  "Publish a provider record through an injected putter. Does not rewrite
  the CID.

  `putter-fn` is `(fn [rec] -> {:ok? :cid :accepted …})`. Production:

      (fn [rec]
        (ipni.advertise http-fn rec opts))

  Historic Bitswap PUT remains:

      (fn [rec]
        (kad.routing/provide http-fn rec opts))

  A putter that returns a different `:cid` is `:cid-mismatch`.
  `:ok? false` is \"no indexer/router accepted\" — not a silent pass.
  This ns depends on neither kad nor ipni."
  [rec putter-fn]
  (cond
    (:error rec) rec
    (not (map? rec)) {:error :invalid-record :value rec}
    (not (vocab/cid? (:cid rec))) {:error :invalid-cid :value (:cid rec)}
    (not (ifn? putter-fn)) {:error :putter-fn-required}
    :else
    (try
      (let [r (putter-fn rec)]
        (cond
          (and (map? r) (:error r)) r
          (and (map? r) (false? (:ok? r))) r
          (and (map? r) (:ok? r)
               (:cid r) (not= (:cid rec) (:cid r)))
          {:error :cid-mismatch :asked (:cid rec) :got (:cid r)}
          (and (map? r) (:ok? r))
          (merge rec {:live? true
                      :accepted (:accepted r)
                      :mutates-cid? false})
          :else {:error :invalid-putter-result :value r}))
      (catch #?(:clj Exception :cljs :default) e
        {:error :putter-error
         :detail #?(:clj (.getMessage e) :cljs (str e))}))))

(defn cids
  "CIDs that have at least one provider. Not an identity set — an index."
  [idx]
  (into (sorted-set) (keys idx)))
