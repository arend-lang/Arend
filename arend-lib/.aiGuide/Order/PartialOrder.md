### Order.PartialOrder

Preorders and posets as categories, with product and subset constructions.

#### Core Classes

- **`Preorder`**: Extends `BaseSet` and `Precat`. A preorder structure with `<=` (reflexive, transitive) viewed as a category where objects are elements and morphisms are inequalities. Provides `<=-refl`, `<=-transitive` (alias `<=∘`), and the dual `>=`.
- **`Poset`**: Extends `Preorder` and `Cat`. Adds `<=-antisymmetric` (`x <= y -> y <= x -> x = y`) and derives univalence from antisymmetry plus proof-irrelevance of `<=`.

#### Path-Order Compatibility

- **`=_<=`**: Equality implies inequality: `x = y -> x <= y` in any preorder.

#### Product Constructions

- **`ProductPreorder`**: Componentwise preorder on `\Sigma P Q` for preorders `P`, `Q`.
- **`ProductPoset`**: Componentwise poset on `\Sigma P Q`, extending `ProductPreorder` with componentwise antisymmetry.

#### Subset Construction

- **`subPoset`**: Given a poset `P` and predicate `S : P -> \Prop`, the induced poset on `\Sigma (x : P) (S x)` with order inherited from `P`.
