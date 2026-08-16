(ns kotoba.protocol.sealed
  "Client-held confidentiality over IPLD/IPNS (ADR-2608161600).

  IPFS, IPLD, and IPNS have no Storj-Uplink-shaped client encryption spec.
  This ns is the composition algebra. It does not hash, wrap, ratchet, or
  open sockets. Bytes live in:

    :object  — kotoba-lang/envelope. One content key, many wraps.
    :session — kotoba-lang/org-signal. X3DH + Double Ratchet.
    :hop     — kotoba-lang/noise. Adjacent-peer only.

  Mixing those constructions is :construction-mismatch. Putting a session
  ciphertext under an object CID is composition, not mixing.

  Noise is not E2EE. IPNS authenticates a pointer; it does not hide it.
  DAG-JOSE is a draft container; it is not a ratchet. One-time prekeys
  cannot be consumed-once on a content-addressed store alone.

  Convergent encryption is forbidden (ADR-2608070400 D5)."
  (:require [kotoba.protocol.vocab :as vocab]))

(def version 1)

(def constructions
  "What each construction actually promises. `spec-in-ipfs?` is the
  question this algebra exists to answer: none of the confidentiality
  constructions are in the IPFS/IPLD/IPNS core specs."
  {:object {:confidential? true
            :forward-secret? false
            :authentic? true
            :scope :at-rest
            :impl "envelope"
            :spec-in-ipfs? false}
   :session {:confidential? true
             :forward-secret? true
             :authentic? true
             :scope :sender-to-recipient
             :impl "org-signal"
             :spec-in-ipfs? false}
   :hop {:confidential? true
         :forward-secret? true
         :authentic? true
         :scope :adjacent-peer
         :impl "noise"
         :spec-in-ipfs? :libp2p-only}
   :ipns {:confidential? false
          :forward-secret? false
          :authentic? true
          :scope :pointer
          :impl "tech-ipfs-specs-ipns"
          :spec-in-ipfs? true}
   :dag-jose {:confidential? true
              :forward-secret? false
              :authentic? true
              :scope :container
              :impl nil
              :spec-in-ipfs? :draft-container-only}})

(defn protection
  "Declared protection for `kind`, or nil."
  [kind]
  (get constructions kind))

(defn spec-in-ipfs?
  "True only when the IPFS/IPLD/IPNS core specs themselves define it."
  [kind]
  (true? (:spec-in-ipfs? (protection kind))))

(defn hop-is-e2ee?
  "libp2p Noise encrypts the hop. It does not encrypt sender-to-recipient."
  []
  false)

(defn dag-jose-is-ratchet?
  "DAG-JOSE is a JWE/JWS container. It does not ratchet, rotate devices,
  or give forward secrecy."
  []
  false)

(defn convergent-allowed?
  "Forbidden. Identical plaintext must not produce identical ciphertext
  CIDs (ADR-2608070400 D5, envelope README)."
  []
  false)

(defn opk-once-on-content-addressed?
  "A one-time prekey published as an IPFS object can be fetched many
  times. Pure IPFS/IPNS cannot enforce single use."
  []
  false)

(defn e2ee-is-not-session-plane?
  "Signal E2EE is content-protocol (the ciphertext is the object).
  The session plane is mux / Noise / Yamux / head — hop and causal
  coordinates, not sender-to-recipient secrecy."
  []
  true)

;; ── key roles ────────────────────────────────────────────────────────────────

(defn key-roles
  "IPNS naming key, libp2p PeerID key, and Signal identity key must be
  three distinct secrets. Same bytes in two roles is :key-role-collision."
  [{:keys [ipns peer signal] :as roles}]
  (cond
    (not (map? roles)) {:error :invalid-roles :value roles}
    (not (and (string? ipns) (seq ipns)
              (string? peer) (seq peer)
              (string? signal) (seq signal)))
    {:error :missing-role :value roles}
    (or (= ipns peer) (= ipns signal) (= peer signal))
    {:error :key-role-collision
     :collided (cond
                 (= ipns peer) #{:ipns :peer}
                 (= ipns signal) #{:ipns :signal}
                 :else #{:peer :signal})}
    :else {:ok? true :roles {:ipns ipns :peer peer :signal signal}}))

;; ── prekey bundle (IPNS value) ───────────────────────────────────────────────

(defn prekey-bundle
  "Public prekey material a recipient publishes under their IPNS name.

  Structural only — signature verify is org-signal's job. PQ prekey is
  optional until org-signal grows PQXDH; its absence is recorded, not
  silently upgraded to hybrid."
  [{:keys [identity-pub signed-prekey pq-prekey expires-at signature]}]
  (cond
    (not (string? identity-pub)) {:error :missing-identity-pub}
    (not (map? signed-prekey)) {:error :missing-signed-prekey}
    (not (string? signature)) {:error :missing-signature}
    :else
    (cond-> {:version version
             :kind :prekey-bundle
             :identity-pub identity-pub
             :signed-prekey signed-prekey
             :signature signature
             :confidential? false}
      (some? pq-prekey) (assoc :pq-prekey pq-prekey)
      (some? expires-at) (assoc :expires-at expires-at))))

(defn bundle?
  [b]
  (boolean (and (map? b)
                (not (:error b))
                (= :prekey-bundle (:kind b))
                (false? (:confidential? b)))))

;; ── attachment (file key travels in the Signal message) ──────────────────────

(defn attachment
  "A large object is encrypted under a fresh file key, stored as an IPFS
  CID, and the wrapped key travels inside the session ciphertext.

  `:plaintext` on this map is always :plaintext-in-attachment — the
  content-protocol object must not carry bytes the mailbox can read."
  [{:keys [cid wrapped-key size digest alg] :as m}]
  (cond
    (contains? m :plaintext) {:error :plaintext-in-attachment}
    (not (vocab/cid? cid)) {:error :invalid-cid :value cid}
    (not (string? wrapped-key)) {:error :missing-wrapped-key}
    (and (some? size) (not (nat-int? size))) {:error :invalid-size :value size}
    (and (some? digest) (not (string? digest))) {:error :invalid-digest :value digest}
    :else
    (cond-> {:cid cid
             :wrapped-key wrapped-key
             :alg (or alg :xchacha20-poly1305)}
      (some? size) (assoc :size size)
      (some? digest) (assoc :digest digest))))

;; ── sealed message ───────────────────────────────────────────────────────────

(defn message
  "A Signal ciphertext plus optional attachment descriptors.

  `:construction` must be :session. Storing this map under a CID uses
  the :object construction on already-ciphertext — `store` records that
  composition. Using :object here as the message construction is
  :construction-mismatch (envelope has no per-message ratchet)."
  [{:keys [construction header ciphertext attachments]}]
  (cond
    (not= :session construction) {:error :construction-mismatch
                                  :expected :session
                                  :got construction}
    (not (map? header)) {:error :missing-header}
    (not (and (string? ciphertext) (seq ciphertext))) {:error :missing-ciphertext}
    (some :error (or attachments [])) (first (filter :error attachments))
    :else
    {:version version
     :kind :sealed-message
     :construction :session
     :header header
     :ciphertext ciphertext
     :attachments (vec (or attachments []))}))

(defn message?
  [m]
  (boolean (and (map? m)
                (not (:error m))
                (= :sealed-message (:kind m))
                (= :session (:construction m)))))

(defn store
  "Compose: put session ciphertext on the object plane. The CID is
  identity of ciphertext bytes. This is not mixing constructions."
  [msg cid]
  (cond
    (:error msg) msg
    (not (message? msg)) {:error :not-a-sealed-message}
    (not (vocab/cid? cid)) {:error :invalid-cid :value cid}
    :else
    {:kind :stored-ciphertext
     :construction :object
     :body-is :session-ciphertext
     :cid cid
     :message msg}))

;; ── mailbox (append-only CID log, IPNS head) ─────────────────────────────────

(defn mailbox
  []
  {:kind :sealed-mailbox
   :entries []
   :head nil
   :dirty? false})

(defn append
  "Append a stored-ciphertext CID. Previous entry CIDs do not change.
  The mailbox head is naming; identity of each message is its own CID."
  [mb cid]
  (cond
    (:error mb) mb
    (not= :sealed-mailbox (:kind mb)) {:error :not-a-mailbox}
    (not (vocab/cid? cid)) {:error :invalid-cid :value cid}
    :else
    (-> mb
        (update :entries conj cid)
        (assoc :dirty? true))))

(defn entries-prefix?
  "True iff `before` entries are a prefix of `after` — append-only."
  [before after]
  (let [a (:entries before)
        b (:entries after)]
    (boolean (and (vector? a) (vector? b)
                  (<= (count a) (count b))
                  (= a (subvec b 0 (count a)))))))

(defn commit-head
  "Seal a dirty mailbox as a head CID through an injected hasher.

  `hash-fn` is `(fn [entries] cid)`. Production binds whatever hashes
  the DAG-CBOR log. This ns does not hash. A dirty mailbox that hashes
  to the previous head is :head-unchanged."
  [mb hash-fn]
  (cond
    (:error mb) mb
    (not (:dirty? mb)) mb
    (not (ifn? hash-fn)) {:error :hash-fn-required}
    :else
    (let [prev (:head mb)
          cid (hash-fn (:entries mb))]
      (cond
        (not (vocab/cid? cid)) {:error :invalid-head-cid :value cid}
        (and (some? prev) (= prev cid)) {:error :head-unchanged :cid cid}
        :else (assoc mb :head cid :dirty? false)))))

(defn publish-head
  "IPNS name → mailbox head CID. Naming, not encryption. The name is
  unchanged (naming/publish-live invariant). Value CID may change."
  [name head-cid]
  (cond
    (not (vocab/ipns-name? name)) {:error :invalid-ipns-name :value name}
    (not (vocab/cid? head-cid)) {:error :invalid-cid :value head-cid}
    :else
    {:plane :naming
     :name name
     :value head-cid
     :confidential? false
     :mutates-name? false}))
