### Order.Directed

Directed sets: preorders that are inhabited and in which every pair of elements has a common upper bound.

#### Classes

- **`DirectedSet`**: Extends `Preorder` with two conditions: `isInhabitted` (the carrier `E` is merely inhabited, `∃ E`) and `isDirected` (for any `x y : E`, there merely exists `z` with `x <= z` and `y <= z`). Captures the standard notion of a directed preorder used as the indexing set for directed colimits/filtered diagrams.

#### Instances

- **`ProductDirectedSet`**: The product of two directed sets `X` and `Y` is a directed set on `\Sigma X Y`, with the product preorder, componentwise inhabitation, and componentwise directedness.
- **`DirectedSetHasProduct`**: Registers `ProductDirectedSet` as the binary `Product` operation, witnessing that `DirectedSet` `HasProduct`.
