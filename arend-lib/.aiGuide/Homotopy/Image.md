### Homotopy.Image

Constructs the homotopy image of a relation-valued map via a pushout-based join construction, factoring it as a surjection followed by an embedding.

#### Join Construction

- **`MJoin`**: Pushout-based join of two types `A` and `B` along a relation `R : A -> B -> \Type`, identifying `x` with `y` whenever `R x y` holds. Defined as `PushoutData` over the sigma `\Sigma (x : A) (y : B) (R x y)` with first and second projections.
- **`MJoin.MData`**: Class packaging the data needed for the image construction: types `A`, `B`, a map `f : A -> B`, a relation `R : B -> B -> \Type`, and a retraction `ret` proving `R b b' -> b = b'`.

#### Y-Image

- **`YImage`**: Homotopy image of a relation `S : A -> B -> \Type`, built from the `MImage` of an `MData` instance where the codomain relation is fiberwise equivalence of type families.
- **`YImage.data`**: The `MData` instance used to construct `YImage`, with `R F G` defined as `\Pi (b : B) -> Equiv {F b} {G b}` and `ret` obtained via univalence (`Equiv-to-=`).
- **`YImage.dom-map`**: The domain-side map `A -> YImage S` from the underlying `MData`.
- **`YImage.dom-map-surj`**: Proof that `dom-map` is a surjection.
- **`YImage.cod-map`**: The codomain-side map `YImage S -> (B -> \Type)` extracting the type family classified by a point of the image.
- **`YImage.cod-map-emb`**: Proof that `cod-map` is an embedding, completing the surjection-embedding factorization.
