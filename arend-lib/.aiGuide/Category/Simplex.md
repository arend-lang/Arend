### Category.Simplex

The simplex category Δ, whose objects are natural numbers and morphisms are monotone maps between finite linear orders.

This module formalizes the simplex category by taking objects as natural numbers `n` (representing the finite linear order `Fin (suc n)`) and morphisms as poset homomorphisms between these orders. The construction proceeds in stages: first a `Precat` instance is built from monotone maps, then key rigidity lemmas establish that monotone bijections between finite linear orders are unique (forced to be the identity), which yields `Iso`-propositionality and ultimately a univalent `Cat` structure. The diagonal-fixpoint argument `monotone-diagonal-fin` is the technical core, ensuring that strictly monotone self-maps of `Fin (suc n)` satisfy `k ≤ f k`.

#### Monotone Map Lemmas

- **`monotone-inj`**: An injective monotone map between a biordered set and a decidable linear order is strictly monotone — promotes a `PosetHom` to a `StrictPosetHom` given injectivity.
- **`monotone-diagonal-fin`**: For `f : Fin (suc n) -> Fin (suc n)` strictly monotone on successors (`f k < f (suc k)`), every index satisfies `k <= f k`. Key rigidity lemma underlying uniqueness of monotone automorphisms of finite linear orders.

#### Uniqueness of Monotone Isomorphisms

- **`fin_monotone_iso_unique`**: Given mutually inverse poset homomorphisms `f, g` on `FinOrder (suc n)` (with `f ∘ g = id` and `g ∘ f = id`), both must equal the identity. Returns the pair of equalities `id = f` and `id = g`. The proof combines `monotone-inj` (to get strict monotonicity from injectivity, derived from invertibility) with `monotone-diagonal-fin` applied to both `f` and `g`, then uses antisymmetry to pin both maps to the identity.

#### The Simplex Precategory

- **`SimplexPrecat`**: The precategory Δ with `Ob = Nat`, morphisms `Hom n m` given by `PosetHom (FinOrder (suc n)) (FinOrder (suc m))` (so the object `n` stands for the finite order on `suc n` elements), identity as the identity poset map, and composition as functional composition (preserving monotonicity).

#### Isomorphism Structure

- **`Simplex_iso`**: An isomorphism `n ≅ m` in `SimplexPrecat` forces `n = m` — objects are determined up to equality by their isomorphism class.
- **`iso_iso`**: Extracts from an iso `f : n ≅ n` in `SimplexPrecat` the pair of identity equalities for `Iso.f` and `f.hinv`, by applying `fin_monotone_iso_unique` to `f.f` and `f.hinv`.
- **`iso_isProp`**: The type `Iso {SimplexPrecat} {n} {n}` is a proposition. Handled by case analysis: it is shown contractible with the identity iso as center, using `iso_iso` for the contraction.

#### The Simplex Category

- **`SimplexCat`**: The category Δ: a `Cat` whose underlying precategory is `SimplexCat.SimplexPrecat` (defined in the `\where` block of `SimplexCat`), with univalence obtained from `Simplex_iso` and `iso_isProp`, so isomorphism coincides with equality of objects.
