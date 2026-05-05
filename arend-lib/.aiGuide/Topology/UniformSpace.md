### Topology.UniformSpace

Uniform spaces and their morphisms, built on top of cover spaces via uniform covers, star refinements, and regularity conditions.

#### Core Classes

- **`PreuniformSpace`**: Extends `PrecoverSpace` with a predicate `isUniform : Set (Set E) -> \Prop` selecting uniform covers. Requires that uniform covers cover every point (`uniform-cover`), contain the trivial cover `{top}` (`uniform-top`), are closed under refinement (`uniform-refine`) and pairwise intersection (`uniform-inter`), and that Cauchy covers are exactly the closure of uniform ones (`uniform-cauchy`).
- **`RegularPreuniformSpace`**: Extends `PreuniformSpace` and `CoverSpace` with `uniform-regular`: every uniform cover has a uniform refinement by sets `<=*`-below members of the original.
- **`UniformSpace`**: Extends `RegularPreuniformSpace` and `CompletelyRegularCoverSpace` with `uniform-star`: every uniform cover admits a uniform star refinement, the standard axiom characterizing uniform spaces.
- **`StronglyRegularPreuniformSpace`**: Extends `RegularPreuniformSpace` and `StronglyRegularCoverSpace` with `uniform-strongly-regular` based on the strong refinement relation `s<=*`.
- **`StronglyRegularUniformSpace`**: Extends `UniformSpace` and `StronglyRegularPreuniformSpace` with `uniform-strongly-star`, a strong star-refinement axiom whose alternatives are disjointness or containment.

#### Star Operation and Refinement Relations

- **`PreuniformSpace.star`**: For a set `V` and cover `C`, the union of all `W ∈ C` meeting `V`; the standard star construction.
- **`PreuniformSpace.star-monotone`**: Star is monotone in both arguments.
- **`<=*`**: Star-below relation: `V <=* U` iff some uniform cover `C` satisfies `star V C ⊆ U`.
- **`<=*-char`**: Characterizes `V <=* U` as uniformity of the family of `W` with `Given (V ∧ W) -> W ⊆ U`.
- **`<=*_<=<`**: `V <=* U` implies `V <=< U` (rather-below relative to the cover space).
- **`StarRatherBelow`**: `RatherBelow` instance for `<=*`, providing standard left/right composition, top, and meet laws.
- **`s<=*`**: Strong star-below: uniformity of the two-element family `{Compl V, U}`.
- **`s<=*_s<=<`**, **`s<=*_<=*`**: `s<=*` implies both `s<=<` and `<=*`.
- **`StronglyStarRatherBelow`**: `RatherBelow` instance for `s<=*`.

#### Basic Lemmas

- **`uniform-subset`**: A pointwise-implied superfamily of a uniform cover is uniform.
- **`top-uniform`**: Any family containing `top` is uniform.
- **`uniform-embedding-char`**: Characterizes embeddings of `PrecoverMap`s in terms of pulling back uniform covers to Cauchy covers refined by preimages.

#### Maps

- **`LocallyUniformMap`**: Extends `CoverMap` between `RegularPreuniformSpace`s; requires `func-locally-uniform`: each uniform cover of the codomain is, after restricting to pieces of some uniform cover of the domain, uniform on each piece. Implements `func-cover` automatically.
- **`UniformMap`**: Extends `LocallyUniformMap` with the stronger `func-uniform`: preimages of uniform covers are uniform.
- **`UniformMap.id`**: Identity uniform map.
- **`UniformMap.compose`** (`∘`): Composition of uniform maps.
- **`UniformMap.const`**: Constant uniform map.
- **`UniformMap.lift`**: Given a uniform embedding `g : Y -> Z` and a uniform map `g ∘ f : X -> Z`, lifts `f` itself to a uniform map `X -> Y`.

#### Constructions from Cover Spaces

- **`RegularPreuniformSpace.fromCoverSpace`**: Builds a `RegularPreuniformSpace` from any `CoverSpace` by taking `isUniform := isCauchy`.
- **`StronglyRegularPreuniformSpace.fromCoverSpace`**: Same construction for strongly regular cover spaces.

#### Transfer Along a Function

- **`RegularPreuniformTransfer`**: Pulls back a `RegularPreuniformSpace` structure along `f : X -> Y` by declaring `C` uniform on `X` iff the family of `U ⊇ f^-1 V` for `V ∈ C` is uniform on `Y`; underlying cover space is `CoverTransfer f`.
- **`UniformTransfer`**: Extends `RegularPreuniformTransfer` to a `UniformSpace` structure on the domain.
- **`StronglyRegularUniformTransfer`**: Strongly regular variant of the transfer.
