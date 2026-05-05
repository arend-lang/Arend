### Topology.Locale.Points

The adjunction between locales and topological spaces via completely prime filters (points), and density conditions ensuring this adjunction is well-behaved.

#### Frame of Opens

- **`OpensFrame`**: The locale `Locale (Given S.isOpen)` of opens of a topological space `S`, with order by subset inclusion, joins as indexed unions, and meets as intersections.

#### Points as a Topological Space

- **`PointsSpace`**: The topological space `TopSpace (CompleteFilter L)` of points of a locale `L`, where points are completely prime filters and opens are sets of the form `{x | x a}` for some `a : L`.
- **`PointsSpaceFunctor`**: The functor `LocaleCat -> TopCat` sending a locale to its space of points.
- **`PointsSpaceFunctor.filter-map`**: Pulls a complete filter on `L` back to a complete filter on `M` along a frame homomorphism `f : FrameHom M L`.

#### The Adjunction `points^* ⊣ points_*`

- **`points`**: The frame homomorphism `FrameHom L (OpensFrame (PointsSpace L))` sending `a : L` to the open `{x | x a}`.
- **`points^*`**: The set `{x : CompleteFilter L | x a}` for `a : L`; the underlying map of `points` taking locale elements to subsets of points.
- **`points^*-mono`**: `points^*` is monotone: `a <= b` implies `points^* a ⊆ points^* b`.
- **`points^*_top>=`**, **`points^*_top`**: `points^*` preserves top.
- **`points^*_meet>=`**, **`points^*_meet`**: `points^*` preserves binary meets.
- **`points_*`**: The right adjoint sending a subset `U` of points to `L.SJoin (\lam a => points^* a ⊆ U)`, the largest locale element whose set of points lies in `U`.
- **`points_*-mono`**: `points_*` is monotone in `U`.
- **`points_*_meet>=`**, **`points_*_meet`**: `points_*` preserves binary meets.
- **`points^*-points_*`**: Adjunction transpose: `points^* a ⊆ U` implies `a <= points_* U`.
- **`points-unit`**: Unit of the adjunction: `a <= points_* (points^* a)`.
- **`points-counit`**: Counit of the adjunction: `points^* (points_* U) ⊆ U`.

#### Points from Frame Presentations

- **`framePres-point`**: Constructs a complete filter on `PresentedFrame P` from a predicate `F : P -> \Prop` that is inhabited, closed under conjunction, and respects basic covers.
- **`framePres-point.cover-filter`**: Auxiliary lemma: any cover of an `F`-element contains an `F`-element.

#### Density Conditions

- **`HasDensePoints`**: Property `points_* bottom <= L.bottom`, i.e. only the bottom element has empty point-set.
- **`hasDensePoints-char`**: Characterization: `HasDensePoints L` iff every `a` with no points satisfying it lies below bottom.
- **`hasDensePoints-fromPres`**: Sufficient condition for `HasDensePoints (PresentedFrame P)` from a cover-based criterion on the presentation.
- **`HasStronglyDensePoints`**: Stronger property: every `a` is below `L.pHat (∃ (x : CompleteFilter L) (x a))`, i.e. `a` is supported by the existence of a point above it.
- **`densePoints_cover`**: Under strong density, `a` is covered by the join indexed by points satisfying `a`.
- **`hasStronglyDensePoints-fromPres`**: Sufficient condition for `HasStronglyDensePoints (PresentedFrame P)` from a cover-based criterion on the presentation.
