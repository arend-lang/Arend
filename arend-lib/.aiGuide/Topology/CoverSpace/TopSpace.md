### Topology.CoverSpace.TopSpace

Construction of a cover space from a regular topological space.

This module bridges classical point-set topology and the cover space framework: every regular topological space carries a canonical cover space structure where a family is "Cauchy" iff every point has an open neighborhood contained in some member of the family. Regularity is required to ensure the resulting cover space itself satisfies the regularity axiom (covers can be refined by covers whose closures still refine). The companion lemma exhibits the identity map as continuous from the topology to the induced cover space, witnessing that this construction is a left adjoint / unit on points.

#### Constructions

- **`TopCover`**: Given a regular topological space `X` (`Xr : X.IsRegular`), produces a `CoverSpace` on the same underlying set whose Cauchy families are those covers `C` such that for every point `x` there exist `U ∈ C` and an open `V` with `x ∈ V ⊆ U`. Implements the cover space axioms (`cauchy-cover`, `cauchy-top`, `cauchy-refine`, `cauchy-glue`, `isRegular`) using the open-neighborhood basis and regularity of `X`.

#### Continuity

- **`top-cover-unit`**: The identity function `\lam x => x` is a continuous map `ContMap X (TopCover Xr) ...` from the original topological space to its induced cover space, exhibiting `TopCover` as a canonical lift from topological spaces into cover spaces.
