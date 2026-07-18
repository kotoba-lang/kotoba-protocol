(ns kotoba.protocol.app
  "L5 application — app manifest / appview / embedUrl のモデル
  (ADR-2607071500)。

  manifest は actor 自身の graph に置く :kotoba.app/* datoms。graph 書込は
  L3 の CACAO 認可を通るので、manifest は本質的に **署名済み・履歴付き**
  (Farcaster の signed manifest / Matrix の state event / Nostr NIP-89 が
  外付けで実現しているものが、graph の性質として付いてくる)。

  - appview  = graph を描画する全画面 app (:kotoba.app/appview-of が対象)
  - embed    = host アプリが文脈内に mount する UI (:kotoba.app/embed-url)
  - actor    = wasm actor 本体 (:kotoba.app/wasm, kototama actor:host ABI)

  mount の手段 (iframe / web component / native) は host の実装詳細であり
  protocol は関知しない。"
  (:require [clojure.string :as str]
            [kotoba.protocol.vocab :as vocab]))

;; ── capability registry ──────────────────────────────────────────────────────

(def actor-host-imports
  "kototama `actor:host` ABI の import 名 (protocol 側の正本。
  kototama.contract / wasm-webcomponent actor-host.js が実装。
  guest の import module 名は \"kotoba\" — ADR-2607062400)。"
  ["gen-keypair" "sign" "verify" "sha256-hex"
   "http-post" "log-read" "log-write" "clock-monotonic"])

(defn cap->wasm-import
  "cap 名 (kebab, protocol 正本) → wasm import 関数名 (snake, guest ABI)。
  guest の module kotoba は snake_case で import する。"
  [cap]
  (str/replace cap "-" "_"))

(defn wasm-import->cap
  "wasm import 関数名 (snake) → cap 名 (kebab)。"
  [name]
  (str/replace name "_" "-"))

(def bridge-caps
  "host 代行 capability (postMessage 等の host bridge 経由)。wasm の同期
  ABI では `http-post` がブラウザで実装不能 (ADR-2607062400) なため、
  ネットワーク/graph/LLM 系は guest から直接ではなく host が代行する —
  鍵も token も app には渡らない。

  `oauth/graph.write` は他の3つと性質が違う (ADR-2607182600 d4 axis 2)。
  graph/query・graph/transact・llm/complete は host が『代行実行』する
  (app には結果だけ返る、鍵/token は一切見せない)のに対し、
  oauth/graph.write は host が同意確認の上でユーザー自身の DID 鍵から
  scope 限定・短命の CACAO を client-side mint して app に渡す —
  渡された CACAO で app 自身が (host を経由せず、host を跨いでも) 直接
  PDS に書き込める。fail-closed な同意 UI を挟む点、および app に
  実際に委任クレデンシャルが渡る点が他の3 cap と異なる。"
  ["net/http-post" "graph/query" "graph/transact" "llm/complete" "oauth/graph.write"])

(def known-caps (set (concat actor-host-imports bridge-caps)))

(defn unknown-caps
  "要求 caps のうち registry に無いもの。[] = ok。"
  [caps]
  (vec (remove known-caps caps)))

;; ── embed-url ────────────────────────────────────────────────────────────────

(defn parse-embed-url
  "embed-url → {:scheme :https|:ipfs|:ipns …} | {:error …}。
  ipfs は {:cid :path}、ipns は {:name :path} を持つ。"
  [s]
  (cond
    (not (string? s)) {:error :not-a-string}

    (str/starts-with? s "https://")
    {:scheme :https :url s}

    (str/starts-with? s "ipfs://")
    (let [rest (subs s 7)
          [cid & path] (str/split rest #"/")]
      (if (vocab/cid? cid)
        {:scheme :ipfs :cid cid :path (when (seq path) (str "/" (str/join "/" path)))}
        {:error :invalid-cid :value cid}))

    (str/starts-with? s "ipns://")
    (let [rest (subs s 7)
          [name & path] (str/split rest #"/")]
      (if (vocab/ipns-name? name)
        {:scheme :ipns :name name :path (when (seq path) (str "/" (str/join "/" path)))}
        {:error :invalid-ipns-name :value name}))

    :else {:error :unknown-scheme :value s}))

(defn resolve-embed-url
  "parse 済み embed-url → host が実際に開く URL と検証性。
  opts: {:gateway \"https://<host>\"} (L4 の retrieval endpoint)。
  :verifiable? — 取得内容を CID で検証できるか (https は不可、ipfs は可、
  ipns は署名済みポインタ経由で可)。"
  [{:keys [scheme url cid name path error]} {:keys [gateway]}]
  (cond
    error {:error error}
    (= scheme :https) {:url url :verifiable? false}
    (= scheme :ipfs) (if gateway
                       {:url (str gateway "/ipfs/" cid (or path ""))
                        :cid cid :verifiable? true}
                       {:error :gateway-required})
    (= scheme :ipns) (if gateway
                       {:url (str gateway "/ipns/" name (or path ""))
                        :ipns name :verifiable? true}
                       {:error :gateway-required})
    :else {:error :unknown-scheme}))

;; ── manifest ─────────────────────────────────────────────────────────────────

(def ^:private kind-requirements
  {"appview" {:required [:kotoba.app/appview-of]
              :any-of [[:kotoba.app/bundle-cid :kotoba.app/embed-url]]}
   "embed"   {:any-of [[:kotoba.app/bundle-cid :kotoba.app/embed-url]]}
   "actor"   {:required [:kotoba.app/wasm]}})

(defn bundle-cid-consistent?
  "manifest が :kotoba.app/bundle-cid と ipfs:// scheme の :kotoba.app/embed-url
  を両方持つ場合、同じ CID を指していなければならない (どちらか一方だけ、
  または embed-url が https/ipns なら該当なし = true)。手で 2 箇所に同じ
  CID 文字列を書く現行運用は、片方だけ更新されると気付かず desync する —
  それを検出する。"
  [m]
  (let [bundle-cid (:kotoba.app/bundle-cid m)
        embed-url (:kotoba.app/embed-url m)
        parsed (when (string? embed-url) (parse-embed-url embed-url))]
    (if (and bundle-cid parsed (= :ipfs (:scheme parsed)))
      (= bundle-cid (:cid parsed))
      true)))

(defn validate-manifest
  "app manifest entity map → problems ([] = ok)。
  共通必須: id / version / kind。kind 別要件は kind-requirements。
  caps は registry の部分集合。値形は vocab/validate-entity に委譲。
  bundle-cid と ipfs:// embed-url を両方持つ場合は同一 CID であることも検証
  (bundle-cid-consistent?)。"
  [m]
  (let [problems (vocab/validate-entity m)
        problems (reduce (fn [ps attr]
                           (if (contains? m attr)
                             ps
                             (conj ps {:attr attr :error :missing})))
                         problems
                         [:kotoba.app/id :kotoba.app/version :kotoba.app/kind])
        kind (:kotoba.app/kind m)
        {:keys [required any-of]} (get kind-requirements kind)
        problems (reduce (fn [ps attr]
                           (if (contains? m attr)
                             ps
                             (conj ps {:attr attr :error :missing-for-kind :kind kind})))
                         problems
                         (or required []))
        problems (reduce (fn [ps alts]
                           (if (some #(contains? m %) alts)
                             ps
                             (conj ps {:attrs alts :error :one-of-required :kind kind})))
                         problems
                         (or any-of []))
        problems (into problems
                       (map (fn [c] {:cap c :error :unknown-cap})
                            (unknown-caps (:kotoba.app/caps m []))))
        problems (if (bundle-cid-consistent? m)
                   problems
                   (conj problems {:attrs [:kotoba.app/bundle-cid :kotoba.app/embed-url]
                                   :error :bundle-cid-embed-url-mismatch}))]
    (vec problems)))
