### Algebra.Monoid.FreeMonoid

The universal property of the free monoid on a set, realized as the list monoid, plus a notion of monoid generation.

#### Universal Map

- **`universalFreeMonoidMap`**: Given a set map `in : S -> M` into a monoid `M`, produces the unique `MonoidHom (ListMonoid {S}) M` extending `in`. Witnesses the universal property of `ListMonoid` as the free monoid on `S`.
  - **`extension`**: The underlying function on lists, sending `nil` to `M.ide` and `a :: l` to `in a * extension l`.
  - **`helper`**: Proves multiplicativity: `extension (x ++ y) = extension x * extension y`.

#### Monoid Generation

- **`isMonoidGenerated`**: Predicate stating that a family `j : S -> M` generates the monoid `M`, defined as surjectivity of the induced homomorphism `universalFreeMonoidMap j`.
