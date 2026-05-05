### Topology.CoverSpace.Complete

Cauchy filters, completions, and complete cover spaces — the metric-free analogue of Cauchy completion via filters that respect the cover structure.

#### Cauchy Filters

- **`IsCauchyFilter`**: Predicate on a `SetFilter F`: every cauchy cover `C` contains some `U` with `F U`.
- **`filter-limit-cauchy`**: Any filter that has a limit point in a cover space is Cauchy.
- **`WeaklyCauchyFilter`**: Class extending `WeaklyProperFilter` with the Cauchy condition `isCauchyFilter`.
- **`CauchyFilter`**: Class extending `WeaklyCauchyFilter` and `ProperFilter`.
- **`CauchyFilterPoset`**: Poset structure on `CauchyFilter S` ordered by subset inclusion `F ⊆ G`.

#### Cauchy and Cover Maps

- **`CauchyMap`**: Continuous map between cover spaces that pushes forward Cauchy filters; provides `func-cauchy` and derives continuity (`func-cont`).
- **`CauchyMap.fromContMap`**: Every continuous map out of a complete cover space is automatically Cauchy.
- **`StronglyCauchyMap`**: Cauchy map that also preserves weakly Cauchy filters via `func-weak-cauchy`.
- **`CoverMap`**: Class extending `PrecoverMap` and `StronglyCauchyMap`; preserves cover structure in both directions.
- **`CoverMap.id`**, **`CoverMap.compose`** (`∘`), **`CoverMap.const`**: Identity, composition, and constant cover maps.
- **`CoverMap.closure-univ`**: Universal property for cover maps into closure-generated cover spaces.
- **`CoverMap.id-denseEmbedding`**: The identity is a dense embedding.
- **`func-cauchy_<=`**: `func-cauchy` is monotone with respect to filter inclusion.

#### Cover Transfer

- **`CoverTransfer-map`**: The canonical map `CoverTransfer f -> Y` is a cover map.
- **`CoverTransfer-univ`**: Universal property: a cover map factoring through `Y` lifts to a cover map into `CoverTransfer g`.
- **`CoverTransfer_<=<`**: Characterizes the rather-below relation in a transferred cover space via preimages.

#### Cauchy Filter Equivalence

- **`CF~`**: Equivalence relation on weakly Cauchy filters: every cauchy cover has a member containing both filters.
- **`CF~-sym`**: Symmetry of `CF~`.
- **`CF~_meet`**: When `F CF~ G`, their meet (in the proper filter semilattice) is again a Cauchy filter.
- **`CF~_<=`**: Filter inclusion implies `CF~`-equivalence.
- **`CF~_<=<`**: If `F CF~ G`, `U <=< V`, and `F U`, then `G V`.
- **`CauchyFilterEquivalence`**: `Equivalence` instance on `CauchyFilter S` using `CF~`.

#### Regular Cauchy Filters

- **`RegularCauchyFilter`**: Class extending `CauchyFilter` with `isRegularFilter`: every member contains a `<=<`-smaller member of the filter.
- **`RegularCauchyFilter.Reg_CF~_<=`**: For regular `F`, `F CF~ G` upgrades to `F ⊆ G`.
- **`RegularCauchyFilter.equality`**: Two regular Cauchy filters are equal iff they are `CF~`-equivalent.
- **`RegularCauchyFilter.ratherBelow`**: Refinement principle: regular Cauchy filters can replace any member with one in a given rather-below-style relation `R`.
- **`regCF`**: Regularization of a Cauchy filter — the filter of `U` such that every `G ⊆ F` contains `U`.
- **`regCF_<=`**: `regCF F ⊆ F`.
- **`pointCF`**: The principal regular Cauchy filter at `x`: sets `U` with `single x <=< U`.

#### Separated and Complete Cover Spaces

- **`SeparatedCoverSpace`**: Class extending `CoverSpace` and `HausdorffTopSpace`; points are determined by the Cauchy structure (`isSeparatedCoverSpace`).
- **`SeparatedCoverSpace.separated-char`**: TFAE characterizing when two points are equal via `pointCF` inclusion, equivalence, equality, neighborhoods, or shared cauchy members.
- **`embedding-inj`**: Embeddings out of separated cover spaces are injective.
- **`IsCompleteCoverSpace`**: Predicate: every regular Cauchy filter has a point whose `pointCF` is contained in it.
- **`IsCompleteCoverSpace.cauchyFilterToPoint`**: In a complete space, every Cauchy filter (not only regular) has such a point.
- **`CompleteCoverSpace`**: Class extending `SeparatedCoverSpace` with the completeness axiom `isComplete`.

#### Dense Lifts to Complete Spaces

- **`dense-filter-lift`**: Pulls a Cauchy filter on `Y` back along a dense embedding `f : X -> Y` to a Cauchy filter on `X`.
- **`dense-filter-lift.map-equiv`**: The pushforward of the lift is `CF~`-equivalent to the original filter.
- **`cauchy-lift`**: For dense embedding `f`, lifts a Cauchy map `g : X -> Z` (with `Z` complete) to a function `Y -> Z` via filter-point.
- **`dense-cauchy-lift`**: Packages `cauchy-lift` as a `CauchyMap Y Z`.
- **`dense-lift`**: Packages `cauchy-lift` as a `CoverMap Y Z` — the universal extension along a dense embedding.
- **`dense-lift-char`**: `cauchy-lift f fd g (f x) = g x` — the lift agrees with `g` on the dense subset.
- **`dense-lift-neighborhood`**: Characterizes neighborhoods of `cauchy-lift f fd g y` via preimages under `f` and `g`.
- **`dense-lift-natural`**: Naturality of the dense lift with respect to filter-points.
- **`dense-complete`**: A dense embedding into `Y` plus a lifting condition implies `Y` is complete.

#### The Completion

- **`completion`**: The unit map `S -> Completion S` sending `x` to `pointCF x`, packaged as a `CoverMap`.
- **`completion.dense-aux`**: Technical lemma relating `<=<` in the completion to membership in the underlying filter.
- **`completion.isDenseEmbedding`**: The unit map is a dense embedding.
- **`Completion`**: Instance making `RegularCauchyFilter X` a `CompleteCoverSpace`.
- **`Completion.mkSet`**: Lifts a set `U ⊆ X` to a set on `RegularCauchyFilter X` (filters containing `U`).
- **`Completion.mkSet_<=`**, **`Completion.mkSet-open`**: Monotonicity and openness preservation of `mkSet`.
- **`Completion.pointCF_^-1_<=<`**, **`Completion.mkSet_<=<-point`**: Characterizations of `<=<` in the completion via membership.
- **`Completion.isCCauchy`**: The cauchy structure on the completion: covers refining `mkSet`-images of cauchy covers on `X`.
- **`Completion.makeCover`**: Every cauchy cover on `X` lifts to a cauchy cover on the completion.
- **`Completion.coverSpace`**: The underlying `CoverSpace` structure on `RegularCauchyFilter X`.

#### Completion Universal Property

- **`completion-lift`**: For `Z` complete, lifts `g : CoverMap X Z` to a `PrecoverMap Completion X -> Z`.
- **`completion-lift-char`**: `completion-lift g (pointCF x) = g x`.
- **`completion-lift-unique`**: Two lifts to a separated space agreeing on `pointCF` images are equal.
- **`completion-lift-neighborhood`**: Neighborhood characterization of `completion-lift g y`.
- **`completion-lift-natural`**: Naturality with respect to filter-points.

#### Characterizations

- **`complete-char`**: TFAE for completeness of `X`: being a `CompleteCoverSpace`, having a `PrecoverMap`-section of `completion` agreeing on points, or such a section that is also a one-sided inverse.
- **`Separated-char`**: TFAE for separatedness: `SeparatedCoverSpace`, embeddings being injective, the unit `completion` being injective, or admitting an injective map into some separated space.

#### Regular Precover Spaces

- **`regPrecoverCauchyFilter`**: Promotes a proper filter satisfying the cauchy condition to a `CauchyFilter` on the regular precover space `RegPrecoverSpace X`.
- **`regPrecoverSpace-extend-coverMap`**: Any `PrecoverMap X -> Y` (with `Y` a cover space) extends to a `CoverMap (RegPrecoverSpace X) -> Y`.
