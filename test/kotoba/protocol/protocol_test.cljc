(ns kotoba.protocol.protocol-test
  (:require [clojure.test :refer [deftest is testing]]
            [kotoba.protocol.app :as app]
            [kotoba.protocol.bridge]
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

(deftest capability-registry
  (is (= 8 (count app/actor-host-imports)))
  (is (contains? app/known-caps "http-post"))
  (is (contains? app/known-caps "net/http-post")
      "同期 ABI 制約のため net 系は host bridge 代行 (ADR-2607062400)"))

;; ── bridge ───────────────────────────────────────────────────────────────────

(deftest bridge-grant-is-intersection-in-request-order
  (is (= ["graph/query" "llm/complete"]
         (kotoba.protocol.bridge/grant ["graph/query" "fs/read" "llm/complete"]
                                       ["llm/complete" "graph/query"])))
  (is (= [] (kotoba.protocol.bridge/grant nil ["graph/query"]))))

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
