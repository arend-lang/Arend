### Homotopy.Pushout

Homotopy pushouts: the universal property formulation, the HIT `PushoutData`, and the flattening lemma.

#### Pushout Class

- **`Pushout`**: A class consisting of a `Square` together with a proof that the square satisfies the pushout universal property — for every type `Z`, the map sending `f : Y -> Z` to the induced square is an `Equiv`.
- **`Pushout.map`**: Functoriality of pushouts; given a map between two pushout squares (component maps `f`, `g`, `h` and coherences `e1`, `e2`), produces the induced map `p.square.Y -> s.Y` between the apex types.
- **`Pushout.map_id`**: The identity map between a pushout and itself induces the identity on the apex.

#### Pushout HIT

- **`PushoutData`**: Higher inductive type realizing the pushout of `f : A -> B` and `g : A -> C`, with constructors `pinl : B -> PushoutData`, `pinr : C -> PushoutData`, and the gluing path `pglue (a : A) : pinl (f a) = pinr (g a)`.
- **`PushoutData.ppglue`**: The gluing path packaged via `path`, with explicit type annotation.
- **`PushoutData.rec`**: Recursion principle: given `lm : B -> Z`, `rm : C -> Z`, and `gm : \Pi a -> lm (f a) = rm (g a)`, defines a map `PushoutData f g -> Z`.
- **`PushoutData.rec.map`**: Naturality of `rec` under post-composition with `h : Y -> Z`.
- **`PushoutData.rec.equiv`**: The universal property as an `Equiv` between `PushoutData f g -> Z` and the sigma type of pushout cocones `(lm, rm, gm)`.

#### Pointed Structure

- **`PushoutPointed`**: Instance making `PushoutData f g` into a `Pointed` type whenever `B` is pointed, with basepoint `pinl base`.

#### Standard Pushout Construction

- **`pushoutData`**: Constructs a `Pushout` instance from `f : A -> B` and `g : A -> C` using the `PushoutData` HIT, with apex `Y = PushoutData f g` and the canonical commuting square.

#### Flattening Lemma

- **`PushoutData.flattening`**: The flattening lemma: for any type family `F : PushoutData f g -> \Type`, the total space of `F` is equivalent to a pushout of the pulled-back fibers.
- **`PushoutData.flattening.total`**: The pushout of the fibers `\Sigma (y : B) F (pinl y)` and `\Sigma (z : C) F (pinr z)` along the fiber over `pinl (f x)`.
- **`PushoutData.flattening.TotalPushout`**: A path-indexed HIT (parameter `j : I`) interpolating between `total` and the total space, with constructors `tinl`, `tinr`, and a glue constructor `tglue` whose endpoints depend on `j` via `coe2` over `F`.
- **`PushoutData.flattening.totalPushoutLeft`**: Equivalence `total ≃ TotalPushout left`.
- **`PushoutData.flattening.totalPushoutRight`**: Equivalence `TotalPushout right ≃ \Sigma (w : PushoutData f g) (F w)`.

#### Embedding Pushouts

- **`EmbeddingPushout`**: Class packaging the data of a pushout span `f : A -> B`, `g : A -> C` where `g` is required to be an `Embedding`.
