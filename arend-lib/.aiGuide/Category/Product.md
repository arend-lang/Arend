### Category.Product

Product of two (pre)categories.

This module constructs the categorical product `C × D` of precategories and categories. Objects are pairs of objects, morphisms are pairs of morphisms, and composition/identity act componentwise. The `Cat` instance lifts univalence from the factors by decomposing isomorphisms in the product into pairs of component isomorphisms.

#### Product Categories

- **`ProductPrecat`**: The product precategory `C × D` of two precategories. Objects are `\Sigma C.Ob D.Ob`, morphisms are pairs `\Sigma (C.Hom X.1 Y.1) (D.Hom X.2 Y.2)`, with identity and composition defined componentwise.
- **`ProductCat`**: The product category of two categories `C` and `D`, extending `ProductPrecat` with the univalence axiom inherited from the factors.

#### Decomposing Isomorphisms

- **`ProductPrecat.iso-first`**: Projects an isomorphism in `ProductPrecat C D` between `a` and `b` to an isomorphism in `C` between `a.1` and `b.1`. Used to recover component-wise structure from product-level data.
- **`ProductPrecat.iso-second`**: Projects an isomorphism in `ProductPrecat C D` to an isomorphism in `D` on the second components. Companion to `iso-first`, together exhibiting the product's universal property at the level of isomorphisms.
