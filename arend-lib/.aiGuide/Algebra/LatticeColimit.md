### Algebra.LatticeColimit

Constructs colimits of algebraic structures (monoids, groups, rings, fields) indexed over a bottom-join-semilattice, where each instance is built on top of the underlying `SetColimit`.

#### Monoid Colimits

- **`MonoidLatticeColimit`**: `Monoid` instance on `SetColimit (Comp MonoidCat.forget F)` for a functor `F : D -> MonoidCat` from a `BottomJoinSemilattice` `D`. Identity is `(bottom, ide)`, multiplication lifts the underlying `*` along joins, with `ide-left`/`ide-right`/`*-assoc` proven by transporting along the lattice structure.
- **`MonoidLatticeColimit.inMap`**: For each `d : D`, the canonical `MonoidHom (F d) (MonoidLatticeColimit F)` sending `a` to `in~ (d, a)`.
- **`MonoidLatticeColimit.inMap-coh`**: Coherence: `inMap d = inMap d' ∘ F.Func p` whenever `p : d <= d'`.

#### Additive Monoid Colimits

- **`AddMonoidLatticeColimit`**: `AddMonoid` instance on `SetColimit (Comp AddMonoidCat.forget F)` for `F : Functor D AddMonoidCat`. Additive analogue of `MonoidLatticeColimit` with `zro = (bottom, zro)` and lifted `+`.
- **`AddMonoidLatticeColimit.inMap`**: Canonical `AddMonoidHom (F d) (AddMonoidLatticeColimit F)`.
- **`AddMonoidLatticeColimit.inMap-coh`**: Coherence for `inMap` along `<=`-arrows in `D`.

#### Additive Group Colimits

- **`AddGroupLatticeColimit`**: `AddGroup` instance on `SetColimit (Comp AddGroupCat.forget F)` extending `AddMonoidLatticeColimit` (composed with `forgetToAddMonoid`). Negation is lifted pointwise; `negative-left`/`negative-right` are proven via the bottom element.
- **`AddGroupLatticeColimit.inMap`**: Canonical `AddGroupHom (F d) (AddGroupLatticeColimit F)`.
- **`AddGroupLatticeColimit.inMap-coh`**: Coherence with `F.Func p`.

#### Abelian Group Colimits

- **`AbGroupLatticeColimit`**: `AbGroup` instance on `SetColimit (Comp AbGroupCat.forget F)` extending `AddGroupLatticeColimit` (via `forgetToAddGroup`), adding `+-comm` derived from `D.join-comm`.
- **`AbGroupLatticeColimit.inMap`**: Canonical `AddGroupHom` into the abelian group colimit.
- **`AbGroupLatticeColimit.inMap-coh`**: Coherence for `inMap`.

#### Ring Colimits

- **`RingLatticeColimit`**: `Ring` instance on `SetColimit (Comp RingCat.forget F)` extending both `AbGroupLatticeColimit` (via `forgetToAbGroup`) and `MonoidLatticeColimit` (via `forgetToMonoid`). Distributivity laws `ldistr`/`rdistr` are established by transporting through joins of three lattice elements.
- **`RingLatticeColimit.inMap`**: Canonical `RingHom (F d) (RingLatticeColimit F)`.
- **`RingLatticeColimit.inMap-coh`**: Coherence for the ring-level `inMap`.

#### Commutative Ring Colimits

- **`CRingLatticeColimit`**: `CRing` instance on `SetColimit (Comp CRingCat.forget F)` extending `RingLatticeColimit` (via `forgetToRing`), adding `*-comm` from `D.join-comm`.

#### Discrete Field Colimits

- **`DiscreteFieldLatticeColimit`**: Promotes `CRingLatticeColimit F` to a `DiscreteField` whenever every fiber `F d` is a `DiscreteField`. Proves `zro/=ide` by extracting an upper bound and reducing to the fiber, and `eitherZeroOrInv` by case-splitting in `F x.1` and lifting the inverse.

#### Internal Lemmas (per instance)

- **`Func-app`**: `F.Func (g ∘ f) a = F.Func g (F.Func f a)` — functoriality on elements.
- **`poset-app`**: `F.Func f a = F.Func g a` for any two `<=`-arrows `f g : j <= j'` — proof irrelevance for poset morphisms.
- **`poset-id`**: `F.Func p a = a` for `p : j <= j` — identity action of self-arrows.
