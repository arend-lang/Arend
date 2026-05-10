### Topology.CoverSpace

Cover spaces and precover spaces: a uniform-style approach to topology based on Cauchy families of covers.

A `PrecoverSpace` axiomatizes a set together with a predicate `isCauchy` selecting which families of subsets count as "uniform covers" (closed under refinement, gluing, and containing the singleton top cover). The induced topology declares `S` open iff every `x ∈ S` admits a Cauchy cover whose members containing `x` lie inside `S`. A `CoverSpace` strengthens this with a regularity axiom: every Cauchy cover can be refined by one whose members are "really inside" (`<=<`) members of the original, with stronger variants (strongly, ω-, completely regular) controlling how members shrink. The `Closure` machinery generates a precover space from a generating family of covers, providing a flexible way to define cover structures by closure under the precover axioms, and supports universal/embedding lemmas for maps out of such spaces.

#### Core Classes

- **`PrecoverSpace`**: Extends `TopSpace`. A set with a predicate `isCauchy : Set (Set E) -> \Prop` satisfying covering (`cauchy-cover`), top-cover (`cauchy-top`), refinement-closure (`cauchy-refine`), and gluing (`cauchy-glue`). The associated topology is given by `cauchy-open`.
- **`CoverSpace`**: Extends `PrecoverSpace` with `isRegular`: every Cauchy cover refines to one whose members satisfy `V <=< U` for some original member.
- **`StronglyRegularCoverSpace`**: Strengthens regularity using `s<=<` (strong rather-below).
- **`OmegaRegularCoverSpace`**: Uses the ω-iterated rather-below `<=<o`.
- **`CompletelyRegularCoverSpace`**: Extends `OmegaRegularCoverSpace`; uses the continuous-scale rather-below `<=<c`.
- **`CompletelyStronglyRegularCoverSpace`**: Combines strong and complete regularity.

#### Maps

- **`PrecoverMap`**: Extends `ContMap`; a function whose preimage pulls Cauchy covers back to Cauchy covers (`func-cover`). Continuity is automatic.
- **`PrecoverMap.IsEmbedding`**: The cover structure on the domain is the pullback — every domain Cauchy cover is refined by preimages of a codomain cover.
- **`PrecoverMap.IsWeaklyDenseEmbedding`**, **`PrecoverMap.IsDenseEmbedding`**: Embeddings that are additionally (weakly) dense.
- **`PrecoverMap.embedding-char`**: TFAE characterization of embeddings, including equality with the precover transfer structure.
- **`PrecoverMap.id`**, **`compose` / `∘`**, **`const`**: Identity, composition, and constant precover maps.
- **`id-denseEmbedding`**: The identity is a dense embedding.
- **`embedding-left`**: If `g ∘ f` is an embedding, so is `f`.

#### Rather-Below Relations

- **`<=<`**: `V <=< U` means there is a Cauchy cover such that every member meeting `V` lies in `U` — the basic "really inside" relation.
- **`s<=<`**: Strong variant using a two-element cover by `Compl V` and `U`.
- **`<=<_single`**: Characterization of `single x <=< U`.
- **`<=<_<=`**, **`s<=<_<=`**: Both relations imply ordinary inclusion.
- **`<=<_^-1`**, **`s<=<_^-1`**: Preserved by precover-map preimages.
- **`<=<-cont`**: A continuous map from a precover space to a cover space pulls back `<=<` at points.
- **`s<=<_<=<`**: `s<=<` refines `<=<`.
- **`s<=<_bottom`**: `bottom s<=< U` always holds.
- **`RegularRatherBelow`**, **`StronglyRatherBelow`**: `RatherBelow` instances on `SetLattice X` for `<=<` and `s<=<`.

#### Cauchy Cover Lemmas

- **`cauchy-subset`**: Cauchy covers are upward-closed under member-wise implication.
- **`top-cauchy`**: Any family containing `top` is Cauchy.
- **`cauchy-inter`**: Pairwise meet of two Cauchy covers is Cauchy.
- **`cauchy-array-inter`**: Generalizes `cauchy-inter` to a finite array of Cauchy covers.
- **`PrecoverSpace.cauchy-trans-dep`**: Dependent gluing variant of `cauchy-glue`.
- **`PrecoverSpace.open-char`**: An open set is one where every point is `<=<`-inside it.

#### Density Predicates

- **`PrecoverSpace.HasWeaklyDensePoints`**: Cauchy covers can drop empty members.
- **`PrecoverSpace.HasDensePoints`**: Every Cauchy cover refines to one whose members are inhabited.
- **`hasDensePoints_hasWeaklyDensePoints`**: The strong version implies the weak.
- **`<=<-inter`** (CoverSpace): Interpolation: `single x <=< U` factors through some `V`.
- **`s<=<-inter`** (StronglyRegularCoverSpace): Strong interpolation.
- **`denseSet-char`**: TFAE characterization of dense subsets in a cover space.
- **`dense-char`**: A precover map into a cover space is dense iff every neighborhood `<=<` of a codomain point hits the image.

#### Cover-Space Lemmas

- **`CoverSpace.cauchy-regular-cover`**: Every point lies in some `single x <=< U` with `U` in the cover.
- **`CoverSpace.interior`**: The interior `\lam x => single x <=< U` is open.
- **`CoverSpace.cauchy-open-cover`**: Every Cauchy cover refines to one consisting of interiors.

#### Extensionality

- **`PrecoverSpace.PrecoverSpace-ext`**: Two precover structures with the same Cauchy predicate are equal.
- **`CoverSpace.CoverSpace-ext`**: Same for cover spaces.

#### Constructions

- **`AntiDiscreteCover`**: The antidiscrete (indiscrete) cover space — only families containing `top` are Cauchy. Completely strongly regular.
- **`DiscreteCover`**: The discrete cover space — every pointwise cover is Cauchy. Completely regular.
- **`PrecoverTransfer`**: Pullback precover structure along `f : X -> Y`; `C` is Cauchy iff its preimage-refinement is Cauchy in `Y`.
  - **`PrecoverTransfer.makeCauchy`**: Cauchy covers of `Y` pull back to Cauchy covers under transfer.
- **`PrecoverTransfer-map`**: The transfer map is a precover map.
- **`PrecoverTransfer-char`**: Characterizes Cauchy covers under embedding by transfer.
- **`PrecoverTransfer-univ`**: Universal property: factoring through `PrecoverTransfer g`.
- **`CoverTransfer`**: Cover-space version of `PrecoverTransfer` (regularity is preserved).
- **`CoverSub`**: Subspace cover structure: `Set.Total S` with the transfer along the first projection.

#### Closure Construction

- **`ClosurePrecoverSpace`**: Builds a `PrecoverSpace` by closing a generating family `A` under the precover axioms.
- **`ClosurePrecoverSpace.Closure`**: Inductive predicate generating `isCauchy` from `A` via top, refinement, and transitive gluing.
- **`closure-inter`**: Closure is closed under pairwise intersection.
- **`closure-subset`**: Closure is upward-closed under set-of-sets inclusion.
- **`closure-filter`**: A `SetFilter` meeting every generating cover meets every closure cover.
- **`closure-cauchy`**: Closure under `A` lies inside any precover space recognizing `A`.
- **`closure-univ-cover`**, **`closure-univ`**: Universal mapping: a function pulling back generating covers extends to a precover map out of the closure.
- **`closure-univ-closure`**, **`closure-univ-closure-id`**: Closure-to-closure transport along a function or identity.
- **`closure-map`**: Pushforward of closures under a monotone meet-preserving set map.
- **`closure-embedding`** (with **`aux`**): Embedding criterion for closure-defined precover spaces.
- **`ClosureCoverSpace`**: Promotes a closure precover space to a `CoverSpace` given a regularity-style closure condition on `A`.
  - **`closure-regular`**: Lifts a `RatherBelow`-witnessed regularity from generators to the whole closure.
  - **`closure-pred`**: Refines closure covers by ones whose members satisfy a meet-stable predicate.
- **`ClosureRegularCoverSpace`**: Builds a `CompletelyRegularCoverSpace` from a closure family with a strong interpolation property `AI`.

#### Lattice Structure

- **`PrecoverLattice`**: `CompleteLattice` instance on `PrecoverSpace X`. Order is "more Cauchy covers ≤ fewer", `top` is `DiscreteCover`, joins are built by closure under the family of generators from each member.
- **`CoverLattice`**: `CompleteLattice` on `CoverSpace X`, with joins constructed via `closure-regular` to preserve regularity; binary `join` likewise.
- **`RegPrecoverSpace`**: The largest cover space below a given precover space — supremum (in `CoverLattice`) of all cover structures dominated by `X`.
- **`regPrecoverSpace`**: Identity precover map `X -> RegPrecoverSpace X`.
- **`regPrecoverSpace-extend`**: Universal property: any precover map from `X` to a cover space `Y` factors through `RegPrecoverSpace X`.
