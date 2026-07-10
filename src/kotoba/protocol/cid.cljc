(ns kotoba.protocol.cid
  "CIDv1/sha2-256 の digest 抽出 + 比較 (ADR-2607071500 Addendum 4/6:
  bundle-cid integrity + dag-pb ディレクトリ検証)。

  ハッシュ計算そのものは host の仕事 (browser の js/crypto.subtle.digest,
  JVM の MessageDigest) — ここは pure な CID⇄digest bytes の変換だけを持つ。
  `parse-raw-cid` / `digest-matches?` は kotobase.archive-put (net-kotobase)
  が本番で運用しているロジックの移植 (byte-exact)。`parse-cid` /
  `digest-matches-cid?` はその一般化 — raw/dag-pb/dag-cbor いずれの
  multicodec でも digest 位置は同じ (CIDv1 sha2-256 の byte layout は
  `[version-varint codec-varint hash-fn-varint digest-len-varint …digest]`
  で、codec varint だけが変わる) ため、codec を問わず digest 比較できる。
  dag-pb 自体の protobuf decode はここでは行わない (zero deps を保つ —
  decode は host 側 `@ipld/dag-pb` 等の仕事、ここは digest 一致の判定のみ)。"
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

(def ^:private known-codecs
  {0x55 :raw
   0x70 :dag-pb
   0x71 :dag-cbor})

(defn parse-cid
  "CIDv1/sha2-256、任意の multicodec → {:codec <keyword-or-int> :digest
  [32 bytes]} | {:error …}。`parse-raw-cid` と違い codec を raw に限定しない
  — dag-pb ディレクトリノード等、複数 codec を横断して DAG を辿る場合に使う。
  既知 codec (raw/dag-pb/dag-cbor) は keyword、未知 codec は生の int を返す
  (unknown でも digest 抽出自体は失敗させない — codec の解釈は呼び出し側の
  責務)。"
  [cid]
  (cond
    (not (string? cid)) {:error :not-a-string}
    (not (str/starts-with? cid "b")) {:error :not-base32-cidv1}
    :else
    (let [bs (base32-decode (subs cid 1))]
      (cond
        (nil? bs) {:error :bad-base32}
        (not= 36 (count bs)) {:error :unexpected-length :length (count bs)}
        (not= 0x01 (first bs)) {:error :not-cidv1 :version (first bs)}
        (not= [0x12 0x20] (subvec bs 2 4)) {:error :not-sha256 :hash-fn (subvec bs 2 4)}
        :else (let [codec-byte (nth bs 1)]
                {:codec (get known-codecs codec-byte codec-byte)
                 :digest (subvec bs 4)})))))

(defn digest-matches-cid?
  "digest-matches? の codec 非限定版 — raw/dag-pb/dag-cbor いずれの CID でも
  computed-digest との一致を判定する (dag-pb ノード自身の block bytes も、
  UnixFS file leaf の raw bytes も、同じ関数で検証できる)。"
  [cid computed-digest]
  (let [{:keys [digest error]} (parse-cid cid)]
    (boolean (and (nil? error) (= digest (vec computed-digest))))))
