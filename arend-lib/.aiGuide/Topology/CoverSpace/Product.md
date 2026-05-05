### Topology.CoverSpace.Product

Constructs the product cover space `X ⨯ Y` of two cover spaces, with projections, tupling, and preservation of regularity, separatedness, and completeness.

#### Product Instances

- **`CoverSpaceHasProduct`**: `HasProduct` instance for `CoverSpace`, dispatching to `ProductCoverSpace`.
- **`ProductCoverSpace`**: The product cover space on `\Sigma X Y`, defined as the join of the two coordinate-projection cover transfers; its underlying topology is `ProductTopSpace X Y`.
- **`ProductStronglyRegularCoverSpace`**: Product preserves strong regularity.
- **`StronglyRegularCoverSpaceHasProduct`**: `HasProduct` instance for `StronglyRegularCoverSpace`.
- **`ProductSeparatedCoverSpace`**: Product preserves separatedness.
- **`ProductCompleteCoverSpace`**: Product of complete cover spaces is complete, established via `complete-char` using `completion-lift` of the projections.
- **`PrecoverSpaceHasProduct`**: `HasProduct` instance for `PrecoverSpace`.
- **`ProductPrecoverSpace`**: The product precover space on `\Sigma X Y`, defined analogously to `ProductCoverSpace` via `PrecoverTransfer`.

#### Projections and Tupling

- **`ProductCoverSpace.proj1'`**, **`proj2'`**: Cover maps from the auxiliary `Prod X Y` to `X` and `Y`.
- **`ProductCoverSpace.proj1`**, **`proj2`**: Cover maps from `X ⨯ Y` to the components, given by `__.1` and `__.2`.
- **`ProductCoverSpace.tuple`**: Universal property; given `f : CoverMap Z X` and `g : CoverMap Z Y`, produces `CoverMap Z (X ⨯ Y)` sending `z ↦ (f z, g z)`.
  - **`tuple.precover`**: Same construction at the precover level.
- **`ProductCoverSpace.prod`**: Functorial action on morphisms: `CoverMap X Y → CoverMap X' Y' → CoverMap (X ⨯ X') (Y ⨯ Y')`, defined as `tuple (f ∘ proj1) (g ∘ proj2)`.
  - **`prod.isEmbedding`**: Product of two embeddings is an embedding.
  - **`prod.isDenseEmbedding`**: Product of two dense embeddings is a dense embedding.
  - **`prod.isWeaklyDenseEmbedding`**: Product of two weakly dense embeddings is a weakly dense embedding.
- **`ProductPrecoverSpace.proj1`**, **`proj2`**: Precover-map versions of the projections.

#### Cauchy Covers and Neighborhoods

- **`ProductCoverSpace.prodCover'`**: Given Cauchy covers `C` of `X` and `D` of `Y`, the family of rectangles `U × V` (for `U ∈ C`, `V ∈ D`) is Cauchy on `Prod X Y`.
- **`ProductCoverSpace.prodCover`**: Same statement on `X ⨯ Y` — rectangles of Cauchy covers form a Cauchy cover of the product.
- **`ProductPrecoverSpace.prodCover`**: Precover-space analogue of `prodCover`.
- **`ProductCoverSpace.prod-neighborhood'`**: If `single (x,y) <=< W` in `Prod X Y`, then there exist neighborhoods `U` of `x` and `V` of `y` with `U × V ⊆ W`.
- **`ProductCoverSpace.prod-neighborhood`**: Bi-implication form of the above for `X ⨯ Y`: a rather-below neighborhood of `(x,y)` is exactly a product of rather-below neighborhoods.

#### Cauchy Filters

- **`prodCF`**: Product of Cauchy filters; given `F : CauchyFilter X` and `G : CauchyFilter Y`, builds a `CauchyFilter (X ⨯ Y)` whose sets are those containing some rectangle `U × V` with `U ∈ F` and `V ∈ G`.
