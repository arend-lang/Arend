### Category.Product

Product of (pre)categories, with objects and morphisms taken componentwise.

#### Product Precategory

- **`ProductPrecat`**: The product precategory `C × D` of two precategories. Objects are pairs `\Sigma C.Ob D.Ob`, morphisms are pairs of morphisms, and identity/composition act componentwise. Categorical laws follow from the laws in each factor.
- **`ProductPrecat.iso-first`**: Projects an isomorphism in `C × D` onto its first component, yielding an `Iso` in `C`.
- **`ProductPrecat.iso-second`**: Projects an isomorphism in `C × D` onto its second component, yielding an `Iso` in `D`.

#### Product Category

- **`ProductCat`**: The product category of two univalent categories `C` and `D`. Extends `ProductPrecat` and proves `univalence` by combining the univalence equivalences of the factors via `iso-first` and `iso-second`.
