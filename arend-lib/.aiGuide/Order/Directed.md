### Order.Directed

Directed sets: preorders in which every finite subset has an upper bound.

A `DirectedSet` is a preorder equipped with proofs that the carrier is inhabited and that any two elements have a common upper bound. These are the canonical index sets for directed colimits and nets, ensuring that "eventual" properties behave well. The module also shows directed sets are closed under binary products, packaged as a `HasProduct` instance so directed sets form a setting compatible with the library's generic product machinery.

#### Main Class

- **`DirectedSet`**: Extends `Preorder` with `isInhabitted : ∃ E` (the carrier is nonempty) and `isDirected (x y : E) : ∃ (z : E) (x <= z) (y <= z)` (any two elements have a common upper bound). Captures the standard notion of a directed preorder used to index nets and directed (co)limits.

#### Products

- **`ProductDirectedSet`**: Instance making `\Sigma X Y` a `DirectedSet` whenever `X Y : DirectedSet`, with the underlying preorder given by `ProductPreorder X Y`. Inhabitation and directedness are obtained componentwise from the factors.
- **`DirectedSetHasProduct`**: `HasProduct` instance for `DirectedSet`, with `Product := ProductDirectedSet`. Lets generic product-based constructions apply uniformly to directed sets.
