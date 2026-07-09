(ns kotoba.protocol.cid
  "CIDv1/raw/sha2-256 の digest 抽出 + 比較 (ADR-2607071500 Addendum 4:
  bundle-cid integrity)。

  ハッシュ計算そのものは host の仕事 (browser の js/crypto.subtle.digest,
  JVM の MessageDigest) — ここは pure な CID⇄digest bytes の変換だけを持つ。
  kotobase.archive-put (net-kotobase) が本番で運用している同じロジックの
  移植 (base32-decode / parse-raw-cid は byte-exact)。

  scope: raw codec (0x55) + sha2-256 の単一バイナリ CID のみを検証できる
  (wasm module 等)。dag-pb 等の UnixFS ディレクトリ CID (複数ファイルの
  Merkle DAG) はこの単純な sha256 一致では検証できない — mount 戦略ごと
  見直す別 follow-up。"
  (:require [clojure.string :as str]))

(def ^:private b32-alphabet "abcdefghijklmnopqrstuvwxyz234567")
(def ^:private b32-index (into {} (map-indexed (fn [i c] [c i]) b32-alphabet)))

(defn base32-decode
  "lower-case unpadded base32 → byte int vector | nil (不正文字)。"
  [s]
  (loop [cs (seq s) acc 0 bits 0 out []]
    (if-not cs
      (when (zero? (bit-and acc (dec (bit-shift-left 1 bits)))) out)
      (if-let [v (b32-index (first cs))]
        (let [acc (bit-or (bit-shift-left acc 5) v)
              bits (+ bits 5)]
          (if (>= bits 8)
            (recur (next cs) (bit-and acc (dec (bit-shift-left 1 (- bits 8))))
                   (- bits 8)
                   (conj out (bit-and (bit-shift-right acc (- bits 8)) 0xff)))
            (recur (next cs) acc bits out)))
        nil))))

(defn parse-raw-cid
  "CIDv1/raw/sha2-256 (bafkrei…) → {:digest [32 bytes]} | {:error …}。"
  [cid]
  (cond
    (not (string? cid)) {:error :not-a-string}
    (not (str/starts-with? cid "b")) {:error :not-base32-cidv1}
    :else
    (let [bs (base32-decode (subs cid 1))]
      (cond
        (nil? bs) {:error :bad-base32}
        (not= 36 (count bs)) {:error :unexpected-length :length (count bs)}
        (not= [0x01 0x55 0x12 0x20] (subvec bs 0 4))
        {:error :not-raw-sha256 :prefix (subvec bs 0 4)}
        :else {:digest (subvec bs 4)}))))

(defn digest-matches?
  "computed-digest (32-byte int seq, 例 js/crypto.subtle.digest の結果を
  byte 配列化したもの) が cid の埋め込み digest と一致するか。
  cid が raw/sha2-256 でなければ false (verify 不能を fail-closed で扱う —
  検証できない CID を検証済み扱いしない)。"
  [cid computed-digest]
  (let [{:keys [digest error]} (parse-raw-cid cid)]
    (boolean (and (nil? error) (= digest (vec computed-digest))))))
