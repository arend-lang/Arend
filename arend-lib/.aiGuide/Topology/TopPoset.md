### Topology.TopPoset

Hausdorff topological partially ordered sets, where the order relation is closed in the product topology, and their abelian group counterparts.

#### Classes

- **`HausdorffTopPoset`**: Extends `HausdorffTopSpace` and `Poset`. A Hausdorff topological poset whose order relation `{(s₁, s₂) | s₁ ≤ s₂}` is closed in the product topology. The Hausdorff property is automatically derived from closedness of the order, since the diagonal equals the intersection of `≤` and its flip.
- **`HausdorffTopPosetAbGroup`**: Extends `HausdorffTopAbGroup`, `HausdorffTopPoset`, and `PosetAbGroup`. A Hausdorff topological abelian group equipped with a compatible partial order. Closedness of `≤` and closedness of the positive cone `{x | 0 ≤ x}` are interderivable via continuity of subtraction.

#### Fields

- **`<=-closed`**: Witness that `{(s₁, s₂) | s₁ ≤ s₂}` is closed in `ProductTopSpace \this \this`. In `HausdorffTopPosetAbGroup`, derived from `positive-closed` using continuity of `subtract` precomposed with the swap `(proj2, proj1)`.
- **`positive-closed`**: Witness that the nonnegative cone `{x | 0 ≤ x}` is closed. Derived from `<=-closed` via `<=-closed-right`.
