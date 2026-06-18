### Topology.TopSpace.Product

Product topology on `\Sigma X Y` and continuity properties of pairing.

This module equips the Cartesian product of two topological spaces with its standard product topology, characterized via the existence of open boxes around each point. Projections, pairing (`tuple`), and the bifunctorial product `prod` of continuous maps are provided, along with stability of density, weak density, and embeddings under products. The module also derives compatibility of pairing with limits and continuity-at-a-point, and gives a Hausdorff characterization in terms of the diagonal being closed in the product.

#### Product Topology Instance

- **`TopSpaceHasProduct`**: `HasProduct` instance making `⨯` on `TopSpace` denote the product topological space.
- **`ProductTopSpace`**: The product topological space on `\Sigma X Y`. A set `W` is open iff every point of `W` is contained in an open box `U × V ⊆ W` with `U` open in `X` and `V` open in `Y`.

#### Projections, Pairing, and Functoriality

- **`proj1`**: First projection `ContMap (X ⨯ Y) X`.
- **`proj2`**: Second projection `ContMap (X ⨯ Y) Y`.
- **`tuple`**: Pairing of continuous maps: given `f : ContMap X Y` and `g : ContMap X Z`, produces `ContMap X (Y ⨯ Z)` sending `x` to `(f x, g x)`.
- **`prod`**: Functorial product of continuous maps `f : X → Y`, `f' : X' → Y'` giving `ContMap (X ⨯ X') (Y ⨯ Y')`, defined as `tuple (f ∘ proj1) (f' ∘ proj2)`.

#### Preservation Properties of `prod`

- **`prod.isDense`**: The product of two dense continuous maps is dense.
- **`prod.isWeaklyDense`**: The product of two weakly dense continuous maps is weakly dense.
- **`prod.isEmbedding`**: The product of two topological embeddings is a topological embedding.
- **`prod.isDenseEmbedding`**: The product of two dense topological embeddings is a dense topological embedding.

#### Open Boxes and Continuity at a Point

- **`Prod-open`**: The Cartesian product `Set.Prod U V` of two open sets is open in `X ⨯ Y`.
- **`contAt-tuple`**: If `f` and `g` are continuous at `x`, then so is `\lam x => (f x, g x)`.

#### Limits in Products

- **`limit-tuple`**: If `f → x` in `X` and `g → y` in `Y` (over a directed set `I`), then `\lam n => (f n, g n) → (x, y)` in `X ⨯ Y`.
- **`cont2-limit`**: A continuous binary map `h : ContMap (X ⨯ Y) Z` preserves limits along directed sets componentwise: `h (f n, g n) → h (lx, ly)`.

#### Comparing Continuous Maps via Density

- **`denseSet_<=`**: For a Hausdorff join-semilattice target `Y` with continuous join, if `S ⊆ X` is dense and `f x ≤ g x` for all `x ∈ S`, then `f x ≤ g x` for all `x : X`.
- **`dense_<=`**: Variant where the density is provided by a dense continuous map `f : X → Y`: pointwise inequality on the image extends to all of `Y`.
- **`weaklyDenseSet_<=`**: Same as `denseSet_<=` but for strongly Hausdorff targets and weakly dense subsets.
- **`weaklyDense_<=`**: Same as `dense_<=` but for strongly Hausdorff targets and weakly dense maps.

#### Hausdorff Characterization

- **`hausdorff-char`**: A space `X` is Hausdorff (any two points whose every neighborhood pair meets must be equal) iff the diagonal `\lam s => s.1 = s.2` is closed in `ProductTopSpace X X`.
