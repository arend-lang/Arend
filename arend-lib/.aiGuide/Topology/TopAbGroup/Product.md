### Topology.TopAbGroup.Product

Product construction for topological abelian groups and continuity/uniformity lemmas for the standard group operations.

#### Product Structure

- **`ProductTopAbGroup`**: The product `\Sigma X Y` of two topological abelian groups, equipped with the product uniform space structure and componentwise zero, addition, and negation.
- **`TopAbGroupHasProduct`**: `HasProduct` instance for `TopAbGroup`, witnessing that the category of topological abelian groups has binary products via `ProductTopAbGroup`.

#### Uniform Continuity of Group Operations

- **`negative-uniform`**: Negation `negative : X -> X` is a uniformly continuous topological abelian group map.
- **`+-uniform`**: Addition `\lam s => s.1 + s.2 : X ⨯ X -> X` is a uniformly continuous topological abelian group map.
- **`subtract-uniform`**: Subtraction `\lam s => s.1 - s.2 : X ⨯ X -> X` is a uniformly continuous topological abelian group map.
- **`*n-uniform`**: Multiplication by a natural number `n X.*n : X -> X` is uniformly continuous.
  - **`*n-uniform.*n-cont`**: The same map as a `ContMap` (continuous, without uniform structure).
- **`*i-uniform`**: Multiplication by an integer `n X.*i : X -> X` is uniformly continuous.

#### Sums of Cover Maps

- **`BigSum-cover`**: Given an array `fs` of cover maps `X -> Y` into a topological abelian group, the pointwise big sum `\lam x => Y.BigSum (\lam j => fs j x)` is itself a cover map.
