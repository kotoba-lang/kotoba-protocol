(ns kotoba.protocol.vocab
  "kotoba-protocol の datom 語彙 registry (ADR-2607071500)。

  :kotoba.actor/* — actor の自己記述 (自分の graph に自分で書く)
  :kotoba.graph/* — graph の同一性/位置
  :kotoba.app/*   — app manifest (actor が提供する appview / embed / actor-wasm)

  値の検証は述語ベース。retraction/所有の意味論は L1/L3 (layers) の責務で、
  ここは属性と値形だけを定義する。"
  (:require [clojure.string :as str]))

;; ── value predicates ─────────────────────────────────────────────────────────

(defn did-key? [s]
  (boolean (and (string? s) (re-matches #"did:key:z[1-9A-HJ-NP-Za-km-z]+" s))))

(defn cid?
  "CIDv0 (Qm…) / CIDv1 (b… multibase, 典型は bafy…)。厳密 decode は L0 実装
  (io-multiformats) の仕事 — ここは形の弁別のみ。"
  [s]
  (boolean (and (string? s)
                (or (re-matches #"Qm[1-9A-HJ-NP-Za-km-z]{44}" s)
                    (re-matches #"b[a-z2-7]{20,}" s)))))

(defn ipns-name?
  "鍵由来 IPNS 名 (libp2p-key, base36 k51…)。"
  [s]
  (boolean (and (string? s) (re-matches #"k51[a-z0-9]{50,}" s))))

(defn reverse-dns-id? [s]
  (boolean (and (string? s)
                (re-matches #"[a-z][a-z0-9-]*(\.[a-z][a-z0-9-]*){2,}" s))))

(defn version? [s]
  (boolean (and (string? s) (re-matches #"\d+\.\d+\.\d+(-[A-Za-z0-9.-]+)?" s))))

(defn app-uri? [s]
  (boolean (and (string? s) (re-find #"^(https|ipfs|ipns)://\S+" s))))

(defn- non-blank-string? [s]
  (and (string? s) (not (str/blank? s))))

(defn sig-meta?
  "manifest 署名 metadata (ADR-2607182600 d1b)。署名 byte 列そのものではなく
  「どの鍵のどの epoch で署名したか」の参照のみ — 検証時に kagi key registry
  (ADR-2607181200) を解決して key state (retired epoch は verify-only) を
  強制するための座標。"
  [v]
  (boolean (and (map? v)
                (non-blank-string? (:suite-id v))
                (non-blank-string? (:key-id v))
                (nat-int? (:epoch v)))))

;; ── attribute registry ───────────────────────────────────────────────────────

(def attributes
  {;; actor
   :kotoba.actor/did {:doc "actor の Ed25519 did:key (L3 authority の主体)"
                      :pred did-key?}
   :kotoba.actor/ipns {:doc "鍵由来 IPNS 名 = この actor の graph 名 (L3)"
                       :pred ipns-name?}
   :kotoba.actor/handle {:doc "人間可読ハンドル (atprotocol 層が DID へ解決する — ここでは表示名に過ぎない)"
                         :pred non-blank-string?}
   :kotoba.actor/app {:doc "この actor が提供する app の :kotoba.app/id (profile が app を advertise する)"
                      :pred reverse-dns-id?}

   ;; graph
   :kotoba.graph/name {:doc "db 名前空間 kotobase/db/<did>/<name> の <name>"
                       :pred non-blank-string?}
   :kotoba.graph/cid {:doc "graph snapshot の root CID (L2)"
                      :pred cid?}
   :kotoba.graph/head {:doc "可変 head の IPNS 名 (publish/resolve は L4)"
                       :pred ipns-name?}

   ;; app manifest
   :kotoba.app/id {:doc "app id (reverse-dns、例 net.kotoba.mangaka)"
                   :pred reverse-dns-id?}
   :kotoba.app/version {:doc "semver"
                        :pred version?}
   :kotoba.app/kind {:doc "appview = graph を描画する全画面 app / embed = host が文脈内 mount する UI / actor = wasm actor 本体"
                     :pred #{"appview" "embed" "actor"}}
   :kotoba.app/bundle-cid {:doc "静的バンドル (dir) の root CID — 配信元が何であれ内容はこれで検証する"
                           :pred cid?}
   :kotoba.app/entry {:doc "bundle 内 entry path (例 index.html)"
                      :pred non-blank-string?}
   :kotoba.app/embed-url {:doc "mount 可能 URL (https:// | ipfs://<cid>[/p] | ipns://<name>[/p])。mount 手段 (iframe/web-component) は host 実装詳細で protocol 外"
                          :pred app-uri?}
   :kotoba.app/appview-of {:doc "appview が描画する対象の selector (graph 名 / 属性 namespace 群) — EDN"
                           :pred (fn [v] (or (map? v) (vector? v) (string? v)))}
   :kotoba.app/wasm {:doc "wasm モジュール群 [{:cid <cid> :imports [<actor:host import 名>…]}]"
                     :pred (fn [v]
                             (and (sequential? v)
                                  (every? #(and (map? %) (cid? (:cid %))
                                                (sequential? (:imports % [])))
                                          v)))}
   :kotoba.app/caps {:doc "要求 capability (kotoba.protocol.app/known-caps の部分集合)"
                     :pred sequential?}
   :kotoba.app/limits {:doc "RuntimeLimits 形 (kototama.contract が実装) — EDN map"
                       :pred map?}
   :kotoba.app/latest {:doc "更新チャネル = 提供 actor の鍵由来 IPNS 名 (署名済み latest ポインタ)"
                       :pred ipns-name?}
   :kotoba.app/icon {:doc "アイコン (CID or URI)"
                     :pred (fn [v] (or (cid? v) (app-uri? v)))}
   :kotoba.app/sig {:doc "manifest 署名 metadata {:suite-id <suite> :key-id <id> :epoch <nat-int>} — 検証者は kagi key registry (ADR-2607181200) を verify 時に解決して key state を強制する。retired epoch は過去 manifest の検証のみ可 (ADR-2607182600 d1b)"
                    :pred sig-meta?}})

(defn known-attribute? [attr]
  (contains? attributes attr))

(defn valid-value? [attr v]
  (if-let [{:keys [pred]} (attributes attr)]
    (boolean (pred v))
    false))

(defn validate-entity
  "entity map の :kotoba.* 属性を検証。→ problems ([] = ok)。
  未知の :kotoba.* 属性と、既知属性の不正値を報告する。"
  [m]
  (reduce-kv (fn [problems attr v]
               (let [kotoba? (and (keyword? attr)
                                  (some-> (namespace attr) (str/starts-with? "kotoba.")))]
                 (cond
                   (not kotoba?) problems
                   (not (known-attribute? attr)) (conj problems {:attr attr :error :unknown-attribute})
                   (not (valid-value? attr v)) (conj problems {:attr attr :error :invalid-value :value v})
                   :else problems)))
             []
             m))
