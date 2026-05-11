### Topology.TopPoset

Topological partially ordered sets where the order relation is closed.

This module combines the Hausdorff topological space structure with a partial order, requiring the order relation `<=` to be a closed subset of the product space. This closure condition automatically implies the Hausdorff property (via the antisymmetry-based characterization of equality) and ensures that limits of monotone nets in directed sets coincide with joins or meets. The abelian group variant additionally aligns the order with the group structure, where closedness of `<=` reduces to closedness of the positive cone.

#### Hausdorff Topological Posets

- **`HausdorffTopPoset`**: A class extending `HausdorffTopSpace` and `Poset` where the order relation is closed in the product topology.
  - **`<=-closed`**: The set `{(s.1, s.2) | s.1 <= s.2}` is closed in `ProductTopSpace \this \this`.
  - **`isHausdorff`**: Derived automatically — the diagonal equals `<=` intersected with its swap, hence closed, giving the Hausdorff property.

#### Closed Order Intervals

- **`<=-closed-left`**: For fixed `b`, the lower set `(<= b) = {x | x <= b}` is closed.
- **`<=-closed-right`**: For fixed `a`, the upper set `(a <=) = {x | a <= x}` is closed.

#### Limits and Order

- **`limit-Join`**: A monotone increasing net `f : I -> E` over a directed set with topological limit `l` has `l` as its supremum (`IsJoin f l`).
- **`limit-Meet`**: A monotone decreasing net `f : I -> E` over a directed set with topological limit `l` has `l` as its infimum (`IsMeet f l`).

#### Topological Ordered Abelian Groups

- **`HausdorffTopPosetAbGroup`**: A class extending `HausdorffTopAbGroup`, `HausdorffTopPoset`, and `PosetAbGroup`, combining a Hausdorff topological abelian group with a compatible partial order.
  - **`positive-closed`**: The positive cone `{x | 0 <= x}` is closed.
  - **`<=-closed`**: Derived from `positive-closed` by pulling back along the continuous subtraction map `(a, b) |-> b - a`.
