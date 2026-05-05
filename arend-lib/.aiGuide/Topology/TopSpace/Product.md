### Topology.TopSpace.Product

Product topology on `\Sigma X Y`, with projections, pairing, and lemmas about continuity, limits, and density in products.

#### Product Topology

- **`TopSpaceHasProduct`**: `HasProduct` instance for `TopSpace`, making `⨯` denote the product topological space.
- **`ProductTopSpace`**: The product topology on `\Sigma X Y`: a set `W` is open iff every `s ∈ W` has a basic open rectangle `U × V ⊆ W` with `s.1 ∈ U`, `s.2 ∈ V`.

#### Projections and Pairing

- **`proj1`**: Continuous first projection `ContMap (X ⨯ Y) X`.
- **`proj2`**: Continuous second projection `ContMap (X ⨯ Y) Y`.
- **`tuple`**: Universal pairing: given `f : ContMap X Y` and `g : ContMap X Z`, produces `ContMap X (Y ⨯ Z)` with components `(f x, g x)`.
- **`prod`**: Product of maps: `f ⨯ f' : ContMap (X ⨯ X') (Y ⨯ Y')`, defined as `tuple (f ∘ proj1) (f' ∘ proj2)`.

#### Density and Embedding Preservation

- **`prod.isDense`**: The product of two dense maps is dense.
- **`prod.isWeaklyDense`**: The product of two weakly dense maps is weakly dense.
- **`prod.isEmbedding`**: The product of two topological embeddings is a topological embedding.
- **`prod.isDenseEmbedding`**: The product of two dense topological embeddings is a dense topological embedding.

#### Open Rectangles and Pointwise Continuity

- **`Prod-open`**: The product `U × V` of open sets is open in `X ⨯ Y`.
- **`contAt-tuple`**: If `f` and `g` are continuous at `x`, then `\lam x => (f x, g x)` is continuous at `x`.

#### Limits in Products

- **`limit-tuple`**: If `f` has limit `x` and `g` has limit `y`, then the pair sequence `\lam n => (f n, g n)` has limit `(x, y)`.
- **`cont2-limit`**: A binary continuous map preserves componentwise limits: `h (f n, g n) -> h (lx, ly)` whenever `f -> lx` and `g -> ly`.

#### Density and Order Comparisons

- **`denseSet_<=`**: If `f x ≤ g x` holds on a dense subset `S` (with `Y` Hausdorff and a continuous join), the inequality extends to all `x : X`.
- **`dense_<=`**: Variant for a dense map `f : ContMap X Y`: pointwise `g ∘ f ≤ h ∘ f` on `X` extends to `g ≤ h` on `Y`.
- **`weaklyDenseSet_<=`**: Strongly Hausdorff analogue of `denseSet_<=` for weakly dense subsets.
- **`weaklyDense_<=`**: Strongly Hausdorff analogue of `dense_<=` for weakly dense maps.

#### Hausdorff Characterization

- **`hausdorff-char`**: A space `X` satisfies the open-neighborhood Hausdorff condition iff the diagonal `\lam s => s.1 = s.2` is closed in `X ⨯ X`.
