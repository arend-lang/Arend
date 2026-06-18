### Topology.UniformSpace

Uniform spaces and their preuniform/regular variants, presented via families of uniform covers.

This module formalizes uniform structures by axiomatizing a predicate `isUniform` on covers (sets of subsets) rather than via entourages. A `PreuniformSpace` extends `PrecoverSpace` with a uniformity whose Cauchy covers are exactly the closure of `isUniform` under refinement and gluing. Stronger classes — `RegularPreuniformSpace`, `UniformSpace`, and their strongly-regular counterparts — add star-refinement axioms that yield regularity and complete regularity of the underlying cover space. The order relations `<=*` and `s<=*` (rather-below relative to uniform star-refinement) generalize the classical "entourage smaller than" comparisons and feed into `RatherBelow` instances used elsewhere in the topology library.

#### Preuniform Spaces

- **`PreuniformSpace`**: Class extending `PrecoverSpace`. Carries `isUniform : Set (Set E) -> \Prop` with axioms: every uniform cover covers each point (`uniform-cover`), the singleton top cover is uniform (`uniform-top`), uniformity is closed under refinement (`uniform-refine`) and pairwise intersection (`uniform-inter`), and the Cauchy structure is the closure of uniformity (`uniform-cauchy`). Provides default implementations of `PrecoverSpace` axioms via the closure.
- **`PreuniformSpace.makeCauchy`**: Promotes a uniform cover to a Cauchy cover.
- **`PreuniformSpace.IsProperUniform`**: Property that every uniform cover refines to one consisting of inhabited sets.
- **`PreuniformSpace.IsWeaklyProperUniform`**: Weaker variant: a cover containing the empty set plus members of `C` being uniform forces `C` itself to be uniform.
- **`PreuniformSpace.proper_weaklyProper`**: Proper uniformity implies weakly proper.
- **`PreuniformSpace.inhabited_weaklyProper`**: Inhabited carrier implies weakly proper uniformity.
- **`PreuniformSpace.uniform-inter-big`**: Finite intersection of uniform covers is uniform.
- **`PreuniformSpace.uniform-separated`**: Two-point separation by Cauchy covers is equivalent to separation by uniform covers.

#### Star Operation and Rather-Below Relations

- **`PreuniformSpace.star`**: For `V : Set X` and a cover `C`, the star `star V C` is the union of members of `C` that meet `V`.
- **`PreuniformSpace.star-monotone`**: `star` is monotone in both `V` (subset) and `C` (refinement).
- **`<=*`**: `V <=* U` iff some uniform cover `C` has `star V C ⊆ U` — the uniform "rather below".
- **`<=*-char`**: Characterization: `V <=* U` iff the cover `\lam W => Given (V ∧ W) -> W ⊆ U` is uniform.
- **`<=*_<=<`**: Uniform rather-below implies cover-space rather-below.
- **`StarRatherBelow`**: `RatherBelow` instance for `<=*` over a `PreuniformSpace`.
- **`s<=*`**: Strong variant: `V s<=* U` iff `\lam W => W = Compl V \/ W = U` is uniform.
- **`s<=*_s<=<`**, **`s<=*_<=*`**: `s<=*` is stronger than both `s<=<` and `<=*`.
- **`StronglyStarRatherBelow`**: `RatherBelow` instance for `s<=*`.

#### Regular Preuniform and Uniform Spaces

- **`RegularPreuniformSpace`**: Class extending `PreuniformSpace` and `CoverSpace` with `uniform-regular`: every uniform cover is refined by a Cauchy cover whose members are `<=*`-below members of the original. Derives `isRegular` from this axiom.
- **`RegularPreuniformSpace.<=*-cauchy-regular`**: Promotes regularity from uniform covers to arbitrary Cauchy covers.
- **`RegularPreuniformSpace.fromCoverSpace`**: Builds a `RegularPreuniformSpace` from any `CoverSpace` by taking `isUniform := isCauchy`.
- **`UniformSpace`**: Class extending `RegularPreuniformSpace` and `CompletelyRegularCoverSpace`. Adds `uniform-star`: every uniform cover admits a uniform star-refinement. Derives `uniform-regular` and complete regularity.
- **`UniformSpace.<=*-inter`**: `<=*` interpolation: `V <=* U` factors as `V <=* V' <=* U`.
- **`UniformSpace.<=*-regular`**, **`UniformSpace.<=<-regular`**: Uniform-cover refinement by `<=*`- or `<=<`-below sets.

#### Strongly Regular Variants

- **`StronglyRegularPreuniformSpace`**: Extends `RegularPreuniformSpace` and `StronglyRegularCoverSpace` with `uniform-strongly-regular` using `s<=*`. Derives `uniform-regular` and `isStronglyRegular`.
- **`StronglyRegularPreuniformSpace.s<=*-cauchy-regular`**: Strong-regularity refinement at the Cauchy level.
- **`StronglyRegularPreuniformSpace.fromCoverSpace`**: Constructs a strongly regular preuniform space from a `StronglyRegularCoverSpace`.
- **`StronglyRegularUniformSpace`**: Extends `UniformSpace` and `StronglyRegularPreuniformSpace` with `uniform-strongly-star` (star-refinement using a disjoint-or-contained dichotomy). Derives both `uniform-star` and `uniform-strongly-regular`.
- **`StronglyRegularUniformSpace.s<=*-regular`**, **`StronglyRegularUniformSpace.s<=<-regular`**: Refinement lemmas for `s<=*` and `s<=<`.

#### General Lemmas

- **`uniform-subset`**: Pointwise enlargement of a uniform cover stays uniform.
- **`top-uniform`**: Any cover containing the top set is uniform.
- **`uniform-embedding-char`**: Characterizes embeddings `f : PrecoverMap X Y` between a preuniform space and a precover space via lifting of uniform covers to Cauchy covers of preimage-refinements.

#### Maps Between Uniform Spaces

- **`LocallyUniformMap`**: Record extending `CoverMap` between `RegularPreuniformSpace`s. Carries `func-locally-uniform`: each uniform cover of the codomain pulls back, after restricting to a uniform partition of the domain, to a uniform cover. Derives `func-cover`.
- **`UniformMap`**: Record extending `LocallyUniformMap` with the stronger `func-uniform`: preimages of uniform covers are uniform.
- **`UniformMap.IsUniformEmbedding`**: Property that uniform covers of the domain are refined by preimages of codomain sets.
- **`UniformMap.embedding->coverEmbedding`**: Uniform embedding implies cover-space embedding.
- **`UniformMap.IsDenseUniformEmbedding`**, **`UniformMap.IsWeaklyDenseUniformEmbedding`**: Combine density variants with uniform embedding.
- **`UniformMap.id`**, **`UniformMap.compose`** (`∘`), **`UniformMap.const`**: Identity, composition, and constant uniform maps.
- **`UniformMap.lift`**: If `g : Y → Z` is a uniform embedding and `g ∘ f` is uniform, then `f : X → Y` is uniform.

#### Transfer Constructions

- **`RegularPreuniformTransfer`**: Pulls back a `RegularPreuniformSpace` structure along `f : X -> Y` by declaring `C` uniform iff its preimage-refinement is uniform in `Y`.
- **`UniformTransfer`**: Pulls back a `UniformSpace` along `f`, extending the regular preuniform transfer with `uniform-star`.
- **`StronglyRegularUniformTransfer`**: Pulls back a `StronglyRegularUniformSpace` along `f`, adding `uniform-strongly-star`.
