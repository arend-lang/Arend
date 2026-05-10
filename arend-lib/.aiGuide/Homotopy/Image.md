### Homotopy.Image

Constructs the homotopy image of a map as a sequential colimit of joins, factoring it as a surjection followed by an embedding.

This module formalizes Rijke's image factorization for arbitrary maps using a "modal" join construction. Given a relation `R` on the codomain that is sufficient to identify points (`ret : R b b' -> b = b'`), the image is built by iteratively joining along fibers of `R`, then taking the sequential colimit. The resulting `MImage` factors the original map as `dom-map` (a surjection from the domain) composed with `cod-map` (an embedding into the codomain), where the embedding property follows when `R` is reflexive with `ret refl = idp`. The `YImage` specialization uses the equivalence relation on type families (Yoneda-style), giving the image factorization for arbitrary functions via univalence.

#### Join Construction

- **`MJoin`**: The join of two types `A` and `B` along a relation `R : A -> B -> \Type`, defined as the pushout of the projections from `\Sigma (x : A) (y : B) (R x y)`. Identifies `a : A` with `b : B` whenever `R a b` holds.

#### MData: Modal Image Data

- **`MData`**: Class packaging the data needed for the iterated image construction: a map `f : A -> B`, a relation `R : B -> B -> \Type`, and a function `ret` extracting paths from `R`-related elements.
- **`MData.MImageAppr`**: Inductive approximation of the image at level `n`, returning a type together with a map to `B`. Step `n+1` joins the previous approximation with `A` along the lifted relation `R (f a) (g x)`.
- **`MData.MImage`**: The image, defined as the sequential colimit of the approximations.
- **`MData.seq`**: The underlying sequence whose colimit is `MImage`, with transition maps given by `pinr` (the right pushout inclusion).
- **`MData.dom-map`**: Inclusion of the domain `A` into `MImage` via the colimit at level 0.
- **`MData.cod-map`**: Projection from `MImage` to `B`, defined by `(MImageAppr n).2` on each level and constant on the colimit transitions.
- **`MData.dom-map-surj`**: `dom-map` is a surjection, given that `R` is reflexive.
- **`MData.cod-map-emb`**: `cod-map` is an embedding, given that `R` is reflexive and `ret refl = idp`.
- **`MData.cod-map-emb.fibers-contr`**: Auxiliary lemma showing each fiber of `cod-map` over `f a` is contractible.

#### YImage: Image via Type-Family Equivalences

- **`YImage`**: The image factorization of `A -> B`-valued type families, instantiated through `MData` with `R F G := \Pi (b : B) -> Equiv (F b) (G b)` and `ret` obtained from `Equiv-to-=` (univalence).
- **`YImage.data`**: The `MData` instance specifying the relation as fiberwise equivalences and the path-extraction via univalence.
- **`YImage.dom-map`**: Domain inclusion `A -> YImage S`.
- **`YImage.cod-map`**: The embedding `YImage S -> (B -> \Type)` recovering the original family.
- **`YImage.dom-map-surj`**: `dom-map` is a surjection.
- **`YImage.cod-map-emb`**: `cod-map` is an embedding.
