### Category.Simplex

The simplex category Δ, whose objects are natural numbers and morphisms are monotone maps between finite linear orders.

#### Monotonicity Lemmas

- **`monotone-inj`**: An injective monotone map between a biordered set and a decidable linear order is strictly monotone, producing a `StrictPosetHom` from a `PosetHom`.
- **`monotone-diagonal-fin`**: For a function `f : Fin (suc n) -> Fin (suc n)` that is strictly increasing on consecutive elements, every index satisfies `k <= f k`.

#### Uniqueness of Monotone Isomorphisms

- **`fin_monotone_iso_unique`**: Any pair of mutually inverse monotone endomaps on `Fin (suc n)` must both be the identity. Returns a Σ-pair of equalities `id = f` and `id = g`, the key fact that makes Δ skeletal at each object.

#### Simplex Category Structure

- **`SimplexPrecat`**: The precategory Δ. Objects are natural numbers; morphisms `n -> m` are monotone maps `Fin n -> Fin m` (`PosetHom (FinOrder n) (FinOrder m)`). Identity and composition are inherited from functions.
- **`SimplexPrecat.hom`**: Abbreviation `hom n m = PosetHom (FinOrder n) (FinOrder m)` for the morphism type.

#### Skeletality and Univalence

- **`Simplex_iso`**: An isomorphism `n ≅ m` in Δ implies `n = m`; the simplex precategory is skeletal.
- **`iso_iso`**: Given a self-isomorphism of `suc n` in Δ, extracts the proof that both its forward and inverse components equal the identity, via `fin_monotone_iso_unique`.
- **`iso_isProp`**: The type `Iso {SimplexPrecat} {n} {n}` is a proposition — there is at most one self-isomorphism at each object (vacuously for `0`, by contractibility centered at the identity for `suc n`).
- **`SimplexCat`**: The full category Δ, upgrading `SimplexPrecat` with univalence: the equivalence between `n = m` and `Iso n m` is built from `Simplex_iso` and `iso_isProp`.
