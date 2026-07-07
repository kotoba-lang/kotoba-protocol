(ns kotoba.protocol.bridge
  "host ↔ embed app の capability bridge 契約 (ADR-2607071500 L5)。

  wasm の同期 ABI ではブラウザで `http-post` 系が実装できない
  (ADR-2607062400) ため、ネットワーク/graph/LLM 系 capability は embed app
  から直接ではなく **host が代行**する。搬送は host 実装詳細 (Web では
  postMessage) — この ns は message の形と検証だけを定義する。

  Matrix widget-api の capability 交渉に相当するが、grant は app manifest
  (:kotoba.app/caps、actor 署名済み) ∩ host 対応 に限られ、鍵も token も
  app には渡らない。

  flow:
    host → app  hello   {granted caps, host info}   (mount 直後)
    app  → host request {id, cap, args}
    host → app  result  {id, ok, data|error}"
  (:require [clojure.string :as str]
            [kotoba.protocol.app :as app]))

(def protocol-version 1)

(def message-types #{"hello" "request" "result"})

(defn hello
  "host → app。granted は manifest の要求 caps ∩ host 対応 caps。"
  [granted & [{:keys [host]}]]
  (cond-> {:kotoba/bridge "hello"
           :v protocol-version
           :granted (vec granted)}
    host (assoc :host host)))

(defn request
  "app → host。"
  [id cap args]
  {:kotoba/bridge "request" :v protocol-version
   :id id :cap cap :args (or args {})})

(defn result-ok [id data]
  {:kotoba/bridge "result" :v protocol-version :id id :ok true :data data})

(defn result-error [id error]
  {:kotoba/bridge "result" :v protocol-version :id id :ok false
   :error (str error)})

(defn message-type
  "message → \"hello\" | \"request\" | \"result\" | nil。
  (JSON 経由で keyword が失われても読めるよう文字列キーも許す。)"
  [m]
  (let [t (or (:kotoba/bridge m) (get m "kotoba/bridge"))]
    (when (contains? message-types t) t)))

(defn validate-request
  "app からの request を検証。granted は hello で渡した grant 集合。
  → problems ([] = ok)。cap が registry 外/未 grant はここで落ちる —
  fail-closed (kototama validate-import-surface と同じ姿勢)。"
  [m granted]
  (let [{:keys [id cap]} m
        granted (set granted)]
    (cond-> []
      (not= "request" (message-type m)) (conj {:error :not-a-request})
      (not (and (string? id) (not (str/blank? id)))) (conj {:error :missing-id})
      (not (string? cap)) (conj {:error :missing-cap})
      (and (string? cap) (not (contains? app/known-caps cap)))
      (conj {:error :unknown-cap :cap cap})
      (and (string? cap) (contains? app/known-caps cap)
           (not (contains? granted cap)))
      (conj {:error :cap-not-granted :cap cap}))))

(defn grant
  "manifest の要求 caps → host が実際に許可する集合 (要求 ∩ 対応)。
  順序は要求順を保つ。"
  [requested-caps host-supported]
  (let [supported (set host-supported)]
    (vec (filter supported (or requested-caps [])))))
