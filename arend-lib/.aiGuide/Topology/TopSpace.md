### Topology.TopSpace

Foundational definitions of topological spaces, continuous maps, density, Hausdorff conditions, and subspace/transfer topologies.

#### Core Classes

- **`TopSpace`**: Topological space, extending `BaseSet`. Provides a predicate `isOpen` on subsets satisfying: the total set is open (`open-top`), open sets are closed under binary intersection (`open-inter`) and arbitrary union (`open-Union`).
- **`HausdorffTopSpace`**: Topological space satisfying the standard T₂ separation axiom (`isHausdorff`): if every pair of open neighborhoods of `x` and `y` meets, then `x = y`.
- **`StronglyHausdorffTopSpace`**: Extends `HausdorffTopSpace` and `SeparatedSet` with the constructive double-negation form (`isStronglyHausdorff`): equality follows when meeting cannot fail.

#### Basic Constructions

- **`DiscreteTopSpace`**: Discrete topology on any set `X` — every subset is open.
- **`TopTransfer`**: Initial topology induced by a map `f : X -> Y` into a topological space; opens are preimages of opens.
- **`TopSub`**: Subspace topology on `Set.Total S` for `S : Set X`, defined via `TopTransfer` along the first projection.
- **`NFilter`**: Neighborhood filter at a point `x : X`, as a `ProperFilter` whose elements are sets containing some open neighborhood of `x`.

#### Density

- **`IsDenseSet`**: A subset `S` is dense if every open neighborhood of any point meets `S`.
- **`IsWeaklyDenseSet`**: Constructive weakening: no open set is inhabited if it has no point of `S`.
- **`denseSet->weaklyDense`**: Every dense set is weakly dense.

#### Continuous Maps

- **`ContMap`**: Continuous map between topological spaces, extending `SetHom`. Carries `func-cont`: preimages of opens are open.
- **`ContMap.id`**: Identity continuous map.
- **`ContMap.compose`** (`∘`): Composition of continuous maps.
- **`ContMap.const`**: Constant map as a continuous map.
- **`IsCont`**: Predicate form: `f : X -> Y` is continuous (i.e., extends to a `ContMap`).
- **`IsContAt`**: Pointwise continuity at `x`: every open neighborhood of `f x` has an open preimage neighborhood at `x`.
- **`cont-char`**: Global continuity is equivalent to continuity at every point.
- **`contAt-comp`**: Composition preserves pointwise continuity.
- **`contAt-left`**, **`contAt-right`**: Pre/post-composition with a continuous map preserves pointwise continuity.

#### Limits

- **`limit-transport`**: Limits are invariant under pointwise equal sequences.
- **`const-limit`**: Constant nets converge to their value.
- **`cont-limit`**: Continuous maps preserve limits of directed nets.
- **`contAt-limit`**: Pointwise continuity at the limit point suffices to preserve a limit.
- **`cont-limit-point`**: Continuous maps reflect limit points along preimages.
- **`cont-closed`**: Preimages of closed sets under continuous maps are closed.

#### Lifting Uniqueness on Dense Subsets

- **`denseSet-lift-unique`**: Two continuous maps into a Hausdorff space agreeing on a dense subset are equal everywhere.
- **`dense-lift-unique`**: Same as above but stated for maps with dense image (`f.IsDense`) instead of a dense subset.
- **`weaklyDenseSet-lift-unique`**: Variant of `denseSet-lift-unique` for weakly dense subsets, requiring a strongly Hausdorff codomain.
- **`weaklyDense-lift-unique`**: Maps-with-weakly-dense-image variant for strongly Hausdorff codomain.

#### Relative Hausdorffness

- **`IsRelativelyHausdorff`**: A map `f : X -> Y` is relatively Hausdorff if points with `f x = f x'` whose neighborhoods always meet are equal.
- **`denseSet-relative-lift-unique`**: Lift-uniqueness on a dense subset where the codomain is only relatively Hausdorff via an auxiliary map `p`, given that `p ∘ f = p ∘ g`.
- **`dense-relative-lift-unique`**: Same uniqueness using a continuous map with dense image instead of a dense subset.

#### Subspace and Transfer Lemmas

- **`TopTransfer-map`**: The defining map `f : X -> Y` is continuous from `TopTransfer f` to `Y`.
- **`TopTransfer-lift`**: Universal property: a continuous map `f : X -> Y` factoring through `U ⊆ Y` lifts to `TopTransfer` on `Set.Total U`.
- **`TopSub-func`**: A continuous map `f : X -> Y` mapping `U` into `V` restricts to a continuous map between subspaces.
- **`TopSub-inc`**: Inclusion of subspaces `TopSub U -> TopSub V` for `U ⊆ V` is continuous.
- **`TopSub-limit`**: Limits in a subspace are detected componentwise via the inclusion into the ambient space.
