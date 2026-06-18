### Topology.TopSpace

Foundational definitions for topological spaces, continuous maps, limits, density, and Hausdorff conditions.

This module sets up topology in the open-set style: a `TopSpace` extends `BaseSet` with an `isOpen` predicate satisfying the standard axioms (top, finite intersection, arbitrary union). On top of this, it builds the surrounding apparatus — continuous maps as a record `ContMap`, neighborhood filters, limits along directed sets and filters, closed sets via limit points, and dense/weakly-dense subsets. Hausdorff and strongly-Hausdorff variants give the uniqueness machinery used throughout: continuous maps agreeing on a dense subset must agree everywhere, with weakly-dense versions requiring the stronger separation condition. The `TopTransfer` construction (initial topology along a map) provides subspaces and is the engine for `TopSub` and for lifting maps into subspaces.

#### The TopSpace Class

- **`TopSpace`**: Extends `BaseSet`. A topology specified by `isOpen : Set E -> \Prop`, with axioms `open-top`, `open-inter` (binary intersection), and `open-Union` (arbitrary unions of open sets indexed by a subset of opens).
- **`cover-open`**: A set is open if it can be locally covered by open subsets at every point.
- **`open-IUnion`**: Arbitrary indexed union of opens (over any `\hType`) is open.
- **`open-bottom`**: The empty set is open.
- **`open-union`**: Binary union of opens is open.

#### Refinement, Regularity, and Limits

- **`<=<T`**: The "well-inside" relation `V <=<T U`: locally around every point there is an open `W` such that if `V ∧ W` is inhabited then `W ⊆ U`.
- **`IsRegular`**: Every open neighborhood of a point contains a smaller open neighborhood that is well-inside it.
- **`IsLimit`**: A net `f : I -> E` over a `DirectedSet` converges to `l` if every open neighborhood of `l` is eventually entered.
- **`IsFilterLimit`**: A `SetFilter` converges to `l` if it contains every open neighborhood of `l`.
- **`IsLimitPoint`**: `x` is a limit point of `U` if every open neighborhood of `x` meets `U`.
- **`IsClosed`**: A set containing all its limit points.
- **`closed-limit`**, **`closed-limit0`**: Limits of nets eventually (resp. always) in a closed set lie in the set.
- **`closed-inter`**: Intersection of two closed sets is closed.

#### Discrete Topology and Neighborhood Filter

- **`DiscreteTopSpace`**: The discrete topology on a `\Set` X (every subset is open).
- **`NFilter`**: The neighborhood filter at `x`, as a `ProperFilter`: sets containing some open neighborhood of `x`.

#### Density

- **`IsWeaklyDenseSet`**: For every open `U`, if no point of `S` lies in `U` then `U` is empty.
- **`IsDenseSet`**: Every open neighborhood of every point contains a point of `S`.
- **`denseSet->weaklyDense`**: Dense implies weakly dense.

#### Continuous Maps

- **`ContMap`**: Extends `SetHom` between `TopSpace`s with `func-cont`: preimage of open is open.
- **`ContMap.IsWeaklyDense`**, **`ContMap.IsDense`**: Density of the image.
- **`ContMap.dense->weaklyDense`**: Image-dense implies image-weakly-dense.
- **`ContMap.IsTopEmbedding`**: Every open in the domain is the preimage of some open in the codomain.
- **`ContMap.topEmbedding-char`**: Local characterization of topological embeddings.
- **`ContMap.IsDenseTopEmbedding`**: Combined dense embedding property.
- **`ContMap.id`**: Identity continuous map.
- **`ContMap.compose` / `∘`**: Composition of continuous maps.
- **`ContMap.const`**: Constant map is continuous.

#### Limits and Continuity Lemmas

- **`limit-transport`**: Limits transfer along pointwise-equal nets.
- **`const-limit`**: Constant net converges to its value.
- **`cont-limit`**: Continuous maps preserve limits of nets.
- **`IsCont`**: Predicate form of continuity (`ContMap X Y f`).
- **`IsContAt`**: Pointwise continuity at `x`: preimages of opens around `f x` contain opens around `x`.
- **`cont-char`**: A function is continuous iff it is continuous at every point.
- **`contAt-comp`**, **`contAt-left`**, **`contAt-right`**: Composition lemmas for pointwise continuity.
- **`contAt-limit`**: Pointwise continuity at the limit suffices to preserve net convergence.
- **`cont-limit-point`**: Continuous maps send limit points of preimages to limit points.
- **`cont-closed`**: Preimage of a closed set under a continuous map is closed.

#### Hausdorff Conditions

- **`HausdorffTopSpace`**: Extends `TopSpace` with `isHausdorff`: distinct-but-inseparable-by-opens points must be equal.
- **`HausdorffTopSpace.limit-unique`**: Limits of nets are unique in Hausdorff spaces.
- **`StronglyHausdorffTopSpace`**: Extends `HausdorffTopSpace` and `SeparatedSet` with `isStronglyHausdorff`, where the separation hypothesis uses double-negation; supplies `isHausdorff` and `separatedEq` automatically.

#### Dense Lift Uniqueness

- **`denseSet-lift-unique`**: Two continuous maps into a Hausdorff space agreeing on a dense subset agree everywhere.
- **`dense-lift-unique`**: Variant where density comes from a dense continuous map `f : X -> Y`.
- **`weaklyDenseSet-lift-unique`**: Same as `denseSet-lift-unique` but for weakly dense subsets into a strongly Hausdorff codomain.
- **`weaklyDense-lift-unique`**: Map-form of weakly-dense lift uniqueness.

#### Initial Topologies and Subspaces

- **`TopTransfer`**: Initial topology on `X` along `f : X -> Y`: opens are preimages of opens in `Y`.
- **`TopTransfer-map`**: The transfer map itself is continuous.
- **`TopTransfer-lift`**: Lift a continuous map to a transferred subspace given a pointwise-membership witness.
- **`TopSub`**: Subspace topology on `Set.Total S` via `TopTransfer` along the projection.
- **`TopSub-func`**: A continuous map restricts to subspaces when it sends `U` into `V`.
- **`TopSub-inc`**: Inclusion of nested subspaces is continuous.
- **`TopSub-limit`**: Net convergence in a subspace reduces to convergence of underlying points.

#### Relative Hausdorff Lifts

- **`IsRelativelyHausdorff`**: A map `f : X -> Y` is relatively Hausdorff if Hausdorff-style separation in `X` plus `f x = f x'` forces `x = x'`.
- **`denseSet-relative-lift-unique`**: Lift uniqueness on dense subsets when the codomain is only relatively Hausdorff via some `p : Y -> Z`.
- **`dense-relative-lift-unique`**: Map-form of the relative dense-lift uniqueness lemma.
