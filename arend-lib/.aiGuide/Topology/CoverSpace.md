### Topology.CoverSpace

Cover spaces and precover spaces: topological structures defined via Cauchy covers (families of subsets that "cover" the space), with notions of refinement, regularity, and the rather-below relation.

#### Core Classes

- **`PrecoverSpace`**: Extends `TopSpace` with a predicate `isCauchy : Set (Set E) -> \Prop` selecting Cauchy covers. Required axioms: `cauchy-cover` (every Cauchy cover hits each point), `cauchy-top` (the singleton `{top}` is Cauchy), `cauchy-refine` (closure under refinement), `cauchy-glue` (closure under intersection-gluing), and `cauchy-open` (open sets characterized via Cauchy covers). The induced topology is built from the Cauchy structure.
- **`CoverSpace`**: Extends `PrecoverSpace` with `isRegular`: every Cauchy cover can be refined to one whose elements are rather-below (`<=<`) members of the original.
- **`StronglyRegularCoverSpace`**: `CoverSpace` with `isStronglyRegular` using the strong rather-below `s<=<`.
- **`OmegaRegularCoverSpace`**: `CoverSpace` regular with respect to the iterated rather-below `<=<o`.
- **`CompletelyRegularCoverSpace`**: Extends `OmegaRegularCoverSpace` with `isCompletelyRegular` using `<=<c` (continuous-scale rather-below).
- **`CompletelyStronglyRegularCoverSpace`**: Combines strong and complete regularity.
- **`PrecoverMap`**: Extends `ContMap` between precover spaces; requires `func-cover` (Cauchy covers pull back to Cauchy covers up to refinement). Continuity is derived.

#### Basic Cauchy Cover Lemmas

- **`cauchy-subset`**: A superset of a Cauchy cover (pointwise on members) is Cauchy.
- **`top-cauchy`**: Any cover containing `top` is Cauchy.
- **`cauchy-inter`**: Pairwise intersections of two Cauchy covers form a Cauchy cover.
- **`cauchy-array-inter`**: Generalizes `cauchy-inter` to an array of Cauchy covers; intersections indexed by selections from each.
- **`PrecoverSpace.PrecoverSpace-ext`**: Extensionality — two precover structures with the same `isCauchy` are equal.
- **`CoverSpace.CoverSpace-ext`**: Extensionality for cover spaces.

#### PrecoverMap Operations

- **`PrecoverMap.id`**: Identity precover map.
- **`PrecoverMap.compose` (`∘`)**: Composition of precover maps.
- **`PrecoverMap.const`**: Constant precover map at a point.
- **`PrecoverMap.id-denseEmbedding`**: The identity is a dense embedding.
- **`PrecoverMap.embedding-left`**: If `g ∘ f` is an embedding, so is `f`.

#### The Rather-Below Relations

- **`<=<`**: `V <=< U` means `isCauchy` of `{W | V ∧ W ≠ ∅ ⇒ W ⊆ U}` — `V` is rather-below `U`.
- **`<=<_single`**: Characterization of `single x <=< U` via Cauchy covers refining around `x`.
- **`<=<_<=`**: `V <=< U` implies `V ⊆ U`.
- **`<=<_^-1`**: Rather-below is preserved by precover map preimages.
- **`<=<-cont`**: For continuous `f` into a cover space, `single (f x) <=< U` lifts to `single x <=< f^-1 U`.
- **`s<=<`**: Strong rather-below: `isCauchy` of `{W | W = Compl V ∨ W = U}`.
- **`s<=<_<=<`**: Strong implies ordinary rather-below.
- **`s<=<_<=`**, **`s<=<_bottom`**, **`s<=<_^-1`**: Basic properties of `s<=<`.
- **`RegularRatherBelow`**: Instance making `<=<` a `RatherBelow` on `SetLattice X`.
- **`StronglyRatherBelow`**: Instance making `s<=<` a `RatherBelow`.

#### Density and Interpolation

- **`<=<-inter`**: Interpolation: `single x <=< U` factors through some intermediate `V`.
- **`s<=<-inter`**: Strong interpolation in a strongly regular cover space.
- **`denseSet-char`**: TFAE characterization of dense subsets via point-rather-below membership and Cauchy refinements.
- **`dense-char`**: A precover map into a cover space is dense iff every `single y <=< U` has a preimage point landing in `U`.

#### Concrete Cover Spaces

- **`AntiDiscreteCover`**: The indiscrete cover space — only covers containing `top` are Cauchy. Completely strongly regular.
- **`DiscreteCover`**: The discrete cover space — every pointwise cover is Cauchy. Completely regular, with discrete topology.
- **`PrecoverTransfer`**: Initial precover structure on `X` induced by `f : X -> Y` from a precover space `Y`.
- **`PrecoverTransfer.makeCauchy`**: Pulls a Cauchy cover of `Y` to a Cauchy cover on the transferred space.
- **`PrecoverTransfer-map`**: The transfer map `PrecoverTransfer f -> Y` is a precover map.
- **`PrecoverTransfer-char`**: Characterizes Cauchy covers on the transferred space when `f` is itself a precover map.
- **`PrecoverTransfer-univ`**: Universal property — maps factor through the transfer.
- **`CoverTransfer`**: Cover-space version of `PrecoverTransfer`.
- **`CoverSub`**: Subspace cover structure on a subset `S ⊆ X` via inclusion.

#### Closure Construction

- **`ClosurePrecoverSpace`**: Builds a precover space from a generator predicate `A` on covers; `isCauchy` is the closure of `A` under top, refinement, and glue.
- **`Closure`**: Inductive type generating Cauchy covers from a seed `A` via constructors `closure`, `closure-top`, `closure-refine`, `closure-trans`.
- **`closure-inter`**: Closure is closed under pairwise intersection.
- **`closure-subset`**: Closure is monotone in the cover.
- **`closure-filter`**: Any set filter compatible with `A` meets every closure cover.
- **`closure-cauchy`**: If `A`-covers are Cauchy in `S`, so are all closure covers.
- **`closure-univ-cover`**: Closure-universal lifting of preimage Cauchy property.
- **`closure-univ`**: Builds a precover map into `S` from preimage-Cauchy data on `A`.
- **`closure-univ-closure`**, **`closure-univ-closure-id`**: Transfer between two closures.
- **`closure-map`**: Functorial closure — push covers along a set map preserving top, monotonicity, and intersections.
- **`closure-embedding`**: Sufficient condition for a precover map to be an embedding.
- **`ClosureCoverSpace`**: Builds a cover space from a generator with a regularity-style hypothesis.
- **`ClosureCoverSpace.closure-regular`**: Regularity transfers through closure for any `RatherBelow`.
- **`ClosureCoverSpace.closure-pred`**: Pushes a meet-stable predicate `P` through closure.
- **`ClosureRegularCoverSpace`**: Builds a `CompletelyRegularCoverSpace` from an interpolation-style generator.

#### Lattices of (Pre)Cover Structures

- **`PrecoverLattice`**: `CompleteLattice` instance on `PrecoverSpace X`. Order is "more covers are Cauchy"; top is `DiscreteCover`; joins via `ClosurePrecoverSpace` over the union of generators.
- **`CoverLattice`**: `CompleteLattice` instance on `CoverSpace X`, with binary `join` and arbitrary `Join` constructed by closing the precover join under regularity.

#### Regularization

- **`RegPrecoverSpace`**: The largest cover-space refinement below a given precover space — supremum of all cover spaces coarser than `X`.
- **`regPrecoverSpace`**: Canonical precover map `X -> RegPrecoverSpace X`.
- **`regPrecoverSpace-extend`**: Universal property — any precover map from `X` to a cover space `Y` factors through `RegPrecoverSpace X`.
