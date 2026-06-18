### Algebra.LatticeColimit

Colimits of algebraic structures indexed by a bottom-joined semilattice, computed on the underlying set colimit.

This module builds colimits of monoids, additive monoids, additive/abelian groups, rings, commutative rings, and discrete fields over a `BottomJoinSemilattice` `D`. The carrier is `SetColimit` of the composite functor through the forgetful functor; the bottom element supplies the unit/zero, and binary operations are defined by joining the two indices and transporting both arguments along `join-left`/`join-right` before combining. Each layer reuses the previous layer (e.g., `RingLatticeColimit` extends `AbGroupLatticeColimit` and `MonoidLatticeColimit`), and every level provides an `inMap` cocone with a coherence lemma `inMap-coh` showing it factors through the structure maps `F.Func p`.

#### Helper Lemmas (per instance)

- **`Func-app`**: Functoriality on composition: `F.Func (g ∘ f) a = F.Func g (F.Func f a)`.
- **`poset-app`**: Any two parallel order-arrows act identically: `F.Func f a = F.Func g a` for `f, g : j <= j'`.
- **`poset-id`**: An order-self-arrow acts as the identity: `F.Func p a = a` for `p : j <= j`.

#### Monoid Colimit

- **`MonoidLatticeColimit`**: `Monoid` instance on `SetColimit (Comp MonoidCat.forget F)` with unit `in~ (bottom, ide)` and product `in~ (a₁ ∨ b₁, F.Func join-left a₂ * F.Func join-right b₂)`; well-definedness across `~-equiv` is established via `~-cequiv`, `func-*`, `Func-app`, and `poset-app`.
- **`MonoidLatticeColimit.inMap`**: Cocone homomorphism `MonoidHom (F d) (MonoidLatticeColimit F)` sending `a` to `in~ (d, a)`.
- **`MonoidLatticeColimit.inMap-coh`**: Coherence: `inMap d = inMap d' ∘ F.Func p` for `p : d <= d'`.

#### Additive Monoid Colimit

- **`AddMonoidLatticeColimit`**: `AddMonoid` instance built analogously, with `zro = in~ (bottom, zro)` and addition combining transported summands at the join.
- **`AddMonoidLatticeColimit.inMap`**: `AddMonoidHom (F d) (AddMonoidLatticeColimit F)`.
- **`AddMonoidLatticeColimit.inMap-coh`**: Cocone coherence relation.

#### Additive Group Colimit

- **`AddGroupLatticeColimit`**: `AddGroup` instance extending `AddMonoidLatticeColimit` (composed with `AddGroupCat.forgetToAddMonoid`); negation is taken componentwise on representatives via `AddGroupHom.func-negative`.
- **`AddGroupLatticeColimit.inMap`**: `AddGroupHom (F d) (AddGroupLatticeColimit F)`.
- **`AddGroupLatticeColimit.inMap-coh`**: Cocone coherence.

#### Abelian Group Colimit

- **`AbGroupLatticeColimit`**: `AbGroup` instance extending `AddGroupLatticeColimit` via `AbGroupCat.forgetToAddGroup`, adding commutativity of `+`.
- **`AbGroupLatticeColimit.inMap`**: Embedding of each `F d` as an `AddGroupHom`.
- **`AbGroupLatticeColimit.inMap-coh`**: Cocone coherence.

#### Ring Colimit

- **`RingLatticeColimit`**: `Ring` instance extending both `AbGroupLatticeColimit` (via `forgetToAbGroup`) and `MonoidLatticeColimit` (via `forgetToMonoid`), with distributivity laws.
- **`RingLatticeColimit.inMap`**: Component `RingHom (F d) (RingLatticeColimit F)` of the cocone.
- **`RingLatticeColimit.inMap-coh`**: Cocone coherence.

#### Commutative Ring Colimit

- **`CRingLatticeColimit`**: `CRing` instance extending `RingLatticeColimit` (via `CRingCat.forgetToRing`) with `*-comm`.

#### Discrete Field Colimit

- **`DiscreteFieldLatticeColimit`**: Given a functor `F : Functor D CRingCat` together with a fiberwise discrete-field structure `p : \Pi (d : D) -> DiscreteField { | CRing => F d }`, produces a `DiscreteField` structure on `SetColimit (Comp CRingCat.forget F)` extending `CRingLatticeColimit F`, with `zro/=ide` and `eitherZeroOrInv` lifted from the fibers.
