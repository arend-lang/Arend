### Algebra.Ordered.OrderedLocalization

Order structure on localizations of ordered abelian monoids, including the Grothendieck construction as an ordered group.

This module lifts the order from a `PosetAbMonoid` to its localization at a submonoid `S`, defining `a/s <= b/t` as existence of a witness `c ∈ S` with `a·t·c <= b·s·c` (the standard "up to clearing denominators" formulation needed because the submonoid elements need not be cancellative). Localizing at the maximal submonoid yields the Grothendieck group as an ordered group, and when the base monoid is a meet-semilattice the construction extends to a lattice-ordered abelian group via componentwise meets cleared by common denominators.

#### Ordered Localization

- **`LocPosetAbMonoid`**: Instance making `LocAbType S` a `PosetAbMonoid` for any sub-additive-monoid `S` of a `PosetAbMonoid M`. The order on equivalence classes `in~ a <= in~ b` is defined by `∃ (c ∈ S) (a.1 + b.2 + c <= a.2 + b.1 + c)`, with well-definedness on both arguments shown by absorbing the equivalence-witness into the inequality witness.

#### Grothendieck Group as Ordered Group

- **`GrothendieckOrderedAbGroup`**: Instance giving the Grothendieck group `MaxLocAbType M` of a `PosetAbMonoid M` the structure of a `PosetAbGroup`, by combining `GrothendieckAbGroup` with `LocPosetAbMonoid` at the maximal submonoid `SubAddMonoid.max`.

#### Meet-Semilattice Localization

- **`LocPosetMeetSemilattice`**: Instance making `LocAbType S` a `MeetSemilatticeAbMonoid` when `M` is one. The meet of `in~ a` and `in~ b` uses a common denominator `a.2 + b.2` and numerator `(a.1 + b.2) ∧ (a.2 + b.1)`; well-definedness uses `meet_+-right` (compatibility of meet with addition) to shuffle the equivalence witness.

#### Grothendieck Lattice-Ordered Group

- **`GrothendieckMeetLattice`**: Instance assembling `MaxLocAbType M` into a `LatticeAbGroup.FromMeet` for any `MeetSemilatticeAbMonoid M`, by combining `GrothendieckOrderedAbGroup` with `LocPosetMeetSemilattice` at the maximal submonoid — i.e., the Grothendieck group of a meet-semilattice abelian monoid is automatically a lattice-ordered abelian group.
