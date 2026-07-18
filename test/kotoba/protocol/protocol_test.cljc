(ns kotoba.protocol.protocol-test
  (:require [clojure.test :refer [deftest is testing]]
            [kotoba.protocol.app :as app]
            [kotoba.protocol.bridge]
            [kotoba.protocol.cid :as cid-ns]
            [kotoba.protocol.layers :as layers]
            [kotoba.protocol.vocab :as vocab]))

;; ── layers ───────────────────────────────────────────────────────────────────

(deftest layer-table-is-total
  (is (= [:l0-address :l1-fact :l2-graph :l3-authority :l4-distribution :l5-application]
         (mapv :layer layers/layers)))
  (testing "every concern maps to a declared layer"
    (doseq [[concern _] layers/concerns]
      (is (some? (layers/owner-of concern)) (str concern)))))

(deftest ownership-samples
  (is (= :l3-authority (:layer (layers/owner-of :ipns-naming)))
      "IPNS 名は authority (鍵由来) の関心事")
  (is (= :l4-distribution (:layer (layers/owner-of :ipns-publish)))
      "IPNS の publish/resolve は distribution")
  (is (= :l5-application (:layer (layers/owner-of :embed-url)))))

;; ── vocab ────────────────────────────────────────────────────────────────────

(deftest value-predicates
  (is (vocab/did-key? "did:key:z6MkoPd1PVGGf5gTMGy4nZNrBMszzfaeaNmZfSzgAZZNhDWq"))
  (is (not (vocab/did-key? "did:web:example.com")))
  (is (vocab/cid? "bafybeidl5t4ztktqmfcqrfqpio6qf64n6t65a7inkz2pa6jq4tyqwfjfhy"))
  (is (vocab/cid? "QmYwAPJzv5CZsnA625s3Xf2nemtYgPpHdWEz79ojWnPbdG"))
  (is (not (vocab/cid? "not-a-cid")))
  (is (vocab/ipns-name? (str "k51qzi5uqu5d" (apply str (repeat 50 "a")))))
  (is (vocab/reverse-dns-id? "net.kotoba.mangaka"))
  (is (not (vocab/reverse-dns-id? "mangaka"))))

(deftest entity-validation
  (is (= [] (vocab/validate-entity
             {:kotoba.actor/did "did:key:z6MkoPd1PVGGf5gTMGy4nZNrBMszzfaeaNmZfSzgAZZNhDWq"
              :kotoba.actor/app "net.kotoba.mangaka"
              :unrelated/attr "ignored"})))
  (is (= [{:attr :kotoba.app/nonsense :error :unknown-attribute}]
         (vocab/validate-entity {:kotoba.app/nonsense 1})))
  (is (= :invalid-value
         (:error (first (vocab/validate-entity {:kotoba.app/kind "widget"}))))
      "ActorFrame/widget は語彙に無い — appview | embed | actor のみ"))

;; ── app: embed-url ───────────────────────────────────────────────────────────

(def cid "bafybeidl5t4ztktqmfcqrfqpio6qf64n6t65a7inkz2pa6jq4tyqwfjfhy")
(def ipns (str "k51qzi5uqu5d" (apply str (repeat 50 "a"))))

(deftest embed-url-parse-and-resolve
  (is (= {:scheme :https :url "https://aozora.app/studio"}
         (app/parse-embed-url "https://aozora.app/studio")))
  (let [p (app/parse-embed-url (str "ipfs://" cid "/index.html"))]
    (is (= :ipfs (:scheme p)))
    (is (= cid (:cid p)))
    (is (= "/index.html" (:path p)))
    (testing "gateway resolution keeps verifiability"
      (is (= {:url (str "https://kotobase.net/ipfs/" cid "/index.html")
              :cid cid :verifiable? true}
             (app/resolve-embed-url p {:gateway "https://kotobase.net"})))
      (is (= {:error :gateway-required} (app/resolve-embed-url p {})))))
  (testing "https is reachable but not content-verifiable"
    (is (= {:url "https://aozora.app/studio" :verifiable? false}
           (app/resolve-embed-url (app/parse-embed-url "https://aozora.app/studio") {}))))
  (testing "ipns = 署名済み可変ポインタ"
    (let [p (app/parse-embed-url (str "ipns://" ipns))]
      (is (true? (:verifiable? (app/resolve-embed-url p {:gateway "https://kotobase.net"}))))))
  (is (= :unknown-scheme (:error (app/parse-embed-url "ftp://x"))))
  (is (= :invalid-cid (:error (app/parse-embed-url "ipfs://nope")))))

;; ── app: manifest ────────────────────────────────────────────────────────────

(def mangaka-manifest
  {:kotoba.app/id "net.kotoba.mangaka"
   :kotoba.app/version "0.1.0"
   :kotoba.app/kind "embed"
   :kotoba.app/bundle-cid cid
   :kotoba.app/entry "index.html"
   :kotoba.app/embed-url "https://aozora.app/studio"
   :kotoba.app/caps ["sha256-hex" "graph/query" "llm/complete"]
   :kotoba.app/latest ipns})

(deftest manifest-validation
  (is (= [] (app/validate-manifest mangaka-manifest)))
  (testing "kind requirements"
    (is (some #(= :one-of-required (:error %))
              (app/validate-manifest (dissoc mangaka-manifest
                                             :kotoba.app/bundle-cid
                                             :kotoba.app/embed-url))))
    (is (some #(= :missing-for-kind (:error %))
              (app/validate-manifest (assoc mangaka-manifest
                                            :kotoba.app/kind "appview"))))
    (is (= [] (app/validate-manifest
               (assoc mangaka-manifest
                      :kotoba.app/kind "appview"
                      :kotoba.app/appview-of {:graphs ["genko"]})))))
  (testing "actor kind needs wasm modules"
    (is (some #(= :missing-for-kind (:error %))
              (app/validate-manifest {:kotoba.app/id "net.kotoba.x"
                                      :kotoba.app/version "0.0.1"
                                      :kotoba.app/kind "actor"})))
    (is (= [] (app/validate-manifest {:kotoba.app/id "net.kotoba.x"
                                      :kotoba.app/version "0.0.1"
                                      :kotoba.app/kind "actor"
                                      :kotoba.app/wasm [{:cid cid :imports ["sha256-hex"]}]}))))
  (testing "caps must come from the registry"
    (is (= [{:cap "fs/read" :error :unknown-cap}]
           (app/validate-manifest (assoc mangaka-manifest
                                         :kotoba.app/caps ["fs/read"]))))))

;; ── app: manifest 署名 metadata (ADR-2607182600 d1b) ─────────────────────────

(deftest sig-metadata
  (testing "well-formed sig metadata"
    (is (vocab/sig-meta? {:suite-id "ed25519+ml-dsa-65" :key-id "kagi:fleet-owner-key" :epoch 0}))
    (is (vocab/sig-meta? {:suite-id "ed25519" :key-id "k1" :epoch 3})))
  (testing "malformed sig metadata"
    (is (not (vocab/sig-meta? nil)))
    (is (not (vocab/sig-meta? "signed")))
    (is (not (vocab/sig-meta? {:suite-id "" :key-id "k1" :epoch 0})))
    (is (not (vocab/sig-meta? {:suite-id "ed25519" :epoch 0})))
    (is (not (vocab/sig-meta? {:suite-id "ed25519" :key-id "k1" :epoch -1})))
    (is (not (vocab/sig-meta? {:suite-id "ed25519" :key-id "k1" :epoch "0"}))))
  (testing "manifest with sig validates; bad sig is reported"
    (is (= [] (app/validate-manifest
               (assoc mangaka-manifest
                      :kotoba.app/sig {:suite-id "ed25519+ml-dsa-65"
                                       :key-id "kagi:mangaka-actor" :epoch 1}))))
    (is (= :invalid-value
           (:error (first (app/validate-manifest
                           (assoc mangaka-manifest :kotoba.app/sig {:epoch 1}))))))))

;; ── app: bundle-cid / embed-url consistency (ADR-2607071500 Addendum 4) ──────

(def raw-cid "bafkreibm6jg3ux5qumhcn2b3flc3tyu6dmlb4xa7u5bf44yegnrjhc4yeq")
(def other-raw-cid "bafkreidbwa6hrvssu64krf2fxcstyepdmfswrwstjpctxbzgv7u2oku2eu")

(deftest bundle-cid-consistency
  (testing "no embed-url at all — vacuously consistent"
    (is (true? (app/bundle-cid-consistent? {:kotoba.app/bundle-cid raw-cid}))))
  (testing "https embed-url — bundle-cid can't be cross-checked against it"
    (is (true? (app/bundle-cid-consistent? {:kotoba.app/bundle-cid raw-cid
                                            :kotoba.app/embed-url "https://aozora.app/studio"}))))
  (testing "ipfs embed-url matching bundle-cid"
    (is (true? (app/bundle-cid-consistent? {:kotoba.app/bundle-cid raw-cid
                                            :kotoba.app/embed-url (str "ipfs://" raw-cid)}))))
  (testing "ipfs embed-url NOT matching bundle-cid — desync detected"
    (is (false? (app/bundle-cid-consistent? {:kotoba.app/bundle-cid raw-cid
                                             :kotoba.app/embed-url (str "ipfs://" other-raw-cid)}))))
  (testing "validate-manifest reports the mismatch"
    (let [m {:kotoba.app/id "net.kotoba.x" :kotoba.app/version "0.0.1" :kotoba.app/kind "embed"
             :kotoba.app/bundle-cid raw-cid
             :kotoba.app/embed-url (str "ipfs://" other-raw-cid)}]
      (is (some #(= :bundle-cid-embed-url-mismatch (:error %)) (app/validate-manifest m)))
      (is (= [] (app/validate-manifest (assoc m :kotoba.app/embed-url (str "ipfs://" raw-cid))))))))

;; ── cid: digest extraction / verification ────────────────────────────────────

(def sha256-hello
  [44 242 77 186 95 176 163 14 38 232 59 42 197 185 226 158
   27 22 30 92 31 167 66 94 115 4 51 98 147 139 152 36])

(deftest cid-digest-roundtrip
  (is (= sha256-hello (:digest (cid-ns/parse-raw-cid raw-cid))))
  (is (true? (cid-ns/digest-matches? raw-cid sha256-hello)))
  (is (false? (cid-ns/digest-matches? raw-cid (assoc sha256-hello 0 0))))
  (testing "dag-pb (non-raw) CID can't be digest-verified this way — fail-closed, not an exception"
    (is (= :not-raw-sha256 (:error (cid-ns/parse-raw-cid cid))))
    (is (false? (cid-ns/digest-matches? cid sha256-hello))))
  (testing "malformed input degrades to an error map, never throws"
    (is (= :bad-base32 (:error (cid-ns/parse-raw-cid "bnope"))))
    (is (= :not-base32-cidv1 (:error (cid-ns/parse-raw-cid "Qm123"))))))

;; ── cid: multicodec-general parsing (ADR-2607071500 Addendum 6 — dag-pb) ─────

(def dag-pb-digest
  [107 236 249 153 170 112 97 69 8 150 15 67 189 2 251 141
   244 253 208 125 13 86 116 240 121 48 228 241 11 21 37 62])

(deftest parse-cid-any-codec
  (testing "raw codec (0x55)"
    (is (= {:codec :raw :digest sha256-hello} (cid-ns/parse-cid raw-cid))))
  (testing "dag-pb codec (0x70) — the same `cid` var used elsewhere is already dag-pb"
    (is (= {:codec :dag-pb :digest dag-pb-digest} (cid-ns/parse-cid cid))))
  (testing "digest-matches-cid? works across codecs, unlike digest-matches?"
    (is (true? (cid-ns/digest-matches-cid? raw-cid sha256-hello)))
    (is (true? (cid-ns/digest-matches-cid? cid dag-pb-digest)))
    (is (false? (cid-ns/digest-matches? cid dag-pb-digest))
        "digest-matches? stays raw-only — dag-pb must go through parse-cid/digest-matches-cid?")
    (is (false? (cid-ns/digest-matches-cid? cid (assoc dag-pb-digest 0 0)))))
  (testing "malformed input still degrades to an error map"
    (is (= :bad-base32 (:error (cid-ns/parse-cid "bnope"))))
    (is (= :not-base32-cidv1 (:error (cid-ns/parse-cid "Qm123"))))))

(deftest base32-round-trip-and-cid-bytes-conversion
  (testing "base32-encode is the exact inverse of base32-decode"
    (is (= raw-cid (str "b" (cid-ns/base32-encode (cid-ns/base32-decode (subs raw-cid 1))))))
    (is (= cid (str "b" (cid-ns/base32-encode (cid-ns/base32-decode (subs cid 1)))))))
  (testing "parse-cid-bytes / cid-bytes->string mirror parse-cid on raw CID bytes"
    (let [raw-bytes (cid-ns/base32-decode (subs raw-cid 1))]
      (is (= {:codec :raw :digest sha256-hello} (cid-ns/parse-cid-bytes raw-bytes)))
      (is (= raw-cid (cid-ns/cid-bytes->string raw-bytes))))
    (let [dagpb-bytes (cid-ns/base32-decode (subs cid 1))]
      (is (= {:codec :dag-pb :digest dag-pb-digest} (cid-ns/parse-cid-bytes dagpb-bytes)))
      (is (= cid (cid-ns/cid-bytes->string dagpb-bytes))))))

(deftest capability-registry
  (is (= 8 (count app/actor-host-imports)))
  (is (contains? app/known-caps "http-post"))
  (is (contains? app/known-caps "net/http-post")
      "同期 ABI 制約のため net 系は host bridge 代行 (ADR-2607062400)")
  (is (contains? app/known-caps "oauth/graph.write")
      "P2 OAuth 委任 cap (ADR-2607182600 d4 axis 2) — 他の bridge-caps と違い
      app に実クレデンシャル (scope 限定 CACAO) が渡る"))

;; ── bridge ───────────────────────────────────────────────────────────────────

(deftest bridge-grant-is-intersection-in-request-order
  (is (= ["graph/query" "llm/complete"]
         (kotoba.protocol.bridge/grant ["graph/query" "fs/read" "llm/complete"]
                                       ["llm/complete" "graph/query"])))
  (is (= [] (kotoba.protocol.bridge/grant nil ["graph/query"]))))

;; ── labeler trust (ADR-2607182600 d5/P3) ─────────────────────────────────────

(def a-verified-label {:src "did:web:labeler.aozora.app" :uri "at://did:web:app1/net.kotoba.app.manifest/self" :val "verified"})

(deftest verified-requires-trusted-source-nonnegated-verified-val
  (is (true? (kotoba.protocol.bridge/verified? [a-verified-label] #{"did:web:labeler.aozora.app"})))
  (testing "wrong val"
    (is (false? (kotoba.protocol.bridge/verified? [(assoc a-verified-label :val "spam")]
                                                  #{"did:web:labeler.aozora.app"}))))
  (testing "negated"
    (is (false? (kotoba.protocol.bridge/verified? [(assoc a-verified-label :neg true)]
                                                  #{"did:web:labeler.aozora.app"}))))
  (testing "src not trusted by THIS host — a manifest can't self-attest via a colluding labeler"
    (is (false? (kotoba.protocol.bridge/verified? [(assoc a-verified-label :src "did:web:evil-labeler.example")]
                                                  #{"did:web:labeler.aozora.app"}))))
  (testing "no labels at all"
    (is (false? (kotoba.protocol.bridge/verified? [] #{"did:web:labeler.aozora.app"}))))
  (testing "no trusted labelers configured — everything is untrusted"
    (is (false? (kotoba.protocol.bridge/verified? [a-verified-label] #{})))))

(deftest grant-with-trust-gates-only-risky-caps
  (testing "unverified: risky caps dropped, graph/query passes through unaffected"
    (is (= ["graph/query"]
           (kotoba.protocol.bridge/grant-with-trust
            ["graph/query" "graph/transact" "llm/complete" "oauth/graph.write"]
            ["graph/query" "graph/transact" "llm/complete" "oauth/graph.write"]
            [] #{"did:web:labeler.aozora.app"}))))
  (testing "verified: risky caps pass through too, in original request order"
    (is (= ["graph/query" "graph/transact" "llm/complete" "oauth/graph.write"]
           (kotoba.protocol.bridge/grant-with-trust
            ["graph/query" "graph/transact" "llm/complete" "oauth/graph.write"]
            ["graph/query" "graph/transact" "llm/complete" "oauth/graph.write"]
            [a-verified-label] #{"did:web:labeler.aozora.app"}))))
  (testing "still capped by host-supported — verified doesn't grant caps the host never offered"
    (is (= ["graph/query"]
           (kotoba.protocol.bridge/grant-with-trust
            ["graph/query" "graph/transact"] ["graph/query"]
            [a-verified-label] #{"did:web:labeler.aozora.app"})))))

(deftest bridge-request-validation-is-fail-closed
  (let [granted ["graph/query"]]
    (is (= [] (kotoba.protocol.bridge/validate-request
               (kotoba.protocol.bridge/request "r1" "graph/query" {:nsid "x"})
               granted)))
    (is (= [{:error :cap-not-granted :cap "llm/complete"}]
           (kotoba.protocol.bridge/validate-request
            (kotoba.protocol.bridge/request "r2" "llm/complete" {})
            granted)))
    (is (= [{:error :unknown-cap :cap "fs/read"}]
           (kotoba.protocol.bridge/validate-request
            (kotoba.protocol.bridge/request "r3" "fs/read" {})
            granted)))
    (is (some #(= :missing-id (:error %))
              (kotoba.protocol.bridge/validate-request
               {:kotoba/bridge "request" :cap "graph/query"} granted)))))

(deftest bridge-messages-survive-string-keys
  (is (= "hello" (kotoba.protocol.bridge/message-type
                  {"kotoba/bridge" "hello" "v" 1 "granted" []})))
  (is (nil? (kotoba.protocol.bridge/message-type {:type "unrelated"}))))

(deftest wasm-import-cap-mapping
  (is (= "sha256_hex" (app/cap->wasm-import "sha256-hex")))
  (is (= "clock_monotonic" (app/cap->wasm-import "clock-monotonic")))
  (is (= "sha256-hex" (app/wasm-import->cap "sha256_hex")))
  (is (= "gen-keypair" (app/wasm-import->cap (app/cap->wasm-import "gen-keypair")))))
