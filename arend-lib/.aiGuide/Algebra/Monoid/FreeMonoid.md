### Algebra.Monoid.FreeMonoid

Universal property of the free monoid on a set, realized concretely via lists.

This module exhibits `ListMonoid {S}` as the free monoid on a set `S`: any function from `S` into a monoid `M` extends uniquely to a monoid homomorphism out of the list monoid. The extension is defined by recursion on lists (sending `nil` to the identity and `a :: l` to `in a * extension l`), with a helper lemma showing it respects concatenation. The notion of a monoid being "generated" by a family `j : S -> M` is then defined as surjectivity of this universal extension map, providing a uniform way to express that `S` is a set of generators for `M`.

#### Universal Map

- **`universalFreeMonoidMap`**: Given `in : S -> M` into a monoid `M`, builds the unique `MonoidHom (ListMonoid {S}) M` extending `in`. This witnesses the universal property of the free monoid.
  - **`extension`**: The underlying function on lists, defined by `nil ↦ ide` and `a :: l ↦ in a * extension l`.
  - **`helper`**: Multiplicativity lemma: `extension (x ++ y) = extension x * extension y`, used to establish `func-*`.

#### Generation

- **`isMonoidGenerated`**: Predicate stating that `j : S -> M` generates `M`, defined as surjectivity of `universalFreeMonoidMap j`. Equivalently, every element of `M` is a product of elements in the image of `j`.
