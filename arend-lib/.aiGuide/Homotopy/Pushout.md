### Homotopy.Pushout

Pushout types in homotopy type theory, both as a universal property and as a higher inductive type.

This module gives two complementary presentations of pushouts. The `Pushout` class axiomatizes a square as a pushout via the universal property — a `pushout-univ` equivalence between maps out of `Y` and cocones over the span — while `PushoutData` provides the concrete HIT realization with point constructors `pinl`, `pinr` and a path constructor `pglue`. The flattening lemma shows that a type family over the pushout is equivalent to a pushout of its total spaces, a key tool for computing path spaces. `EmbeddingPushout` uses encode/decode to characterize identity types in a pushout along an embedding, yielding that `pinl` is itself an embedding and giving an explicit description of mixed `pinl b = pinr c` paths.

#### Pushout as Universal Property

- **`Pushout`**: Class extending a `Square` with `pushout-univ`, an equivalence `(Y -> Z) ≃ Square` exhibiting `Y` as the universal cocone vertex.
- **`Pushout.map`**: Maps the pushout `Y` into another square's vertex `s.Y` given component maps `f, g, h` and coherences `e1, e2` for the two square sides.
- **`Pushout.map_id`**: The identity map on `Y` is recovered by `map` with identity components.

#### Pushout as Higher Inductive Type

- **`PushoutData`**: HIT pushout of `f : A -> B` and `g : A -> C` with constructors `pinl : B -> _`, `pinr : C -> _`, and path constructor `pglue (a : A) : pinl (f a) = pinr (g a)`.
- **`PushoutData.ppglue`**: Reflective wrapper around `pglue` returning a value-level path `pinl (f a) = pinr (g a)`.
- **`PushoutData.rec`**: Non-dependent eliminator into a type `Z` from `lm : B -> Z`, `rm : C -> Z`, and a coherence `gm`.
- **`PushoutData.rec.map`**: Postcomposition commutes with `rec`: `h (rec lm rm gm x) = rec (h ∘ lm) (h ∘ rm) (pmap h ∘ gm) x`.
- **`PushoutData.rec.equiv`**: Curried universal property — `(PushoutData f g -> Z) ≃ Σ(lm, rm, gm)`.

#### Pointedness

- **`PushoutPointed`**: Instance making `PushoutData f g` pointed at `pinl base` whenever `B` is pointed.

#### Constructing a Pushout from PushoutData

- **`pushoutData`**: Packages `PushoutData f g` into the `Pushout` class with the canonical square `A -> B`, `A -> C`, `B -> Y`, `C -> Y`.

#### Flattening Lemma

- **`PushoutData.flattening`**: Equivalence `total = Σ (w : PushoutData f g) (F w)` showing that the total space of a type family `F` over the pushout is itself a pushout.
- **`PushoutData.flattening.total`**: The pushout of `Σ` types built from `F` over the span, with the second leg using `transport F (ppglue _)`.
- **`PushoutData.flattening.TotalPushout`**: Two-parameter HIT (parametrized by `j : I`) interpolating between `total` (at `left`) and the total space of `F` (at `right`).
- **`PushoutData.flattening.totalPushoutLeft`**: Equivalence `total ≃ TotalPushout left`.
- **`PushoutData.flattening.totalPushoutRight`**: Equivalence `TotalPushout right ≃ Σ (w) (F w)`.

#### Path Spaces of Pushouts Along an Embedding

- **`EmbeddingPushout`**: Class for the case where `g : A -> C` is an embedding; computes path spaces in `PO := PushoutData f g`.
- **`EmbeddingPushout.PO`**: Abbreviation for the pushout `PushoutData f g`.
- **`EmbeddingPushout.code`**: Type family on `PO` whose values give the encoded form of paths from `pinl b0`: `b0 = b` on `pinl b`, `Σ (a : A) (b0 = f a) (c = g a)` on `pinr c`, glued via `equiv`.
- **`EmbeddingPushout.equiv`**: The local equivalence at a glue cell — `(b0 = f a) ≃ Σ (a' : A) (b0 = f a') (g a = g a')` — using that `g` is an embedding.
- **`EmbeddingPushout.encode`**: Sends `p : pinl b0 = w` to `code b0 w` by transport of `idp`.
- **`EmbeddingPushout.decode`**: Inverse direction, building a path `pinl b0 = w` from coded data; on `pinr c` it has the form `pmap pinl d.2 *> ppglue d.1 *> inv (pmap pinr d.3)`.
- **`EmbeddingPushout.encode_decode-left`**, **`EmbeddingPushout.encode_decode-right`**, **`EmbeddingPushout.decode_enode`**: Round-trip lemmas establishing `encode`/`decode` as mutual inverses on the two summands of `PO` and on paths.
- **`EmbeddingPushout.pushout-embedding`**: `pinl : B -> PO` is itself an embedding.
- **`EmbeddingPushout.pullback-path-equiv`**: Explicit equivalence `Σ (a : A) (b = f a) (c = g a) ≃ (pinl b = pinr c)` characterizing mixed paths in the pushout.
