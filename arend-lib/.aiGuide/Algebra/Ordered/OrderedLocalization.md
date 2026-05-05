### Algebra.Ordered.OrderedLocalization

Constructs ordered structures on monoid localizations, lifting partial orders, meet-semilattice structure, and the Grothendieck group construction to ordered settings.

#### Ordered Localization Instances

- **`LocPosetAbMonoid`**: Localization `LocAbType S` of a `PosetAbMonoid M` at a `SubAddMonoid S` is itself a `PosetAbMonoid`. The order is defined via the underlying localization order, with reflexivity, transitivity, antisymmetry, and `+`-monotonicity transferred from `M` using witness elements from `S`.
- **`GrothendieckOrderedAbGroup`**: Grothendieck group `MaxLocAbType M` of a `PosetAbMonoid M` is a `PosetAbGroup`, obtained by localizing at the maximal submonoid `SubAddMonoid.max`. Combines `GrothendieckAbGroup` with `LocPosetAbMonoid`.

#### Meet-Semilattice Localization

- **`LocPosetMeetSemilattice`**: Localization of a `MeetSemilatticeAbMonoid M` at `S : SubAddMonoid M` inherits the meet-semilattice structure. Provides `meet`, the universal property `meet-univ`, the projections `meet-left`/`meet-right`, and compatibility `meet_+-left` of meets with addition on equivalence classes.
- **`GrothendieckMeetLattice`**: Grothendieck group of a `MeetSemilatticeAbMonoid` is a `LatticeAbGroup.FromMeet`, combining `GrothendieckOrderedAbGroup` with the meet structure from `LocPosetMeetSemilattice` at the maximal submonoid.
