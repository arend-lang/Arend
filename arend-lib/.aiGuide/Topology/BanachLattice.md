### Topology.BanachLattice

Banach lattices: complete normed Riesz spaces where the norm interacts with the lattice structure.

This module combines the order-theoretic structure of Riesz spaces (vector lattices) with the metric/normed structure of Banach spaces. The central abstraction is `ExPseudoNormedRieszSpace`, which requires the norm to be monotone with respect to the absolute value `abs`, automatically yielding a solid neighborhood basis (open balls are absorbing under `|·| ≤ |·|`). Specializations `RieszLSpace` (L-space, additive norm on the positive cone) and `RieszMSpace` (M-space, sublinear norm on joins) capture the two classical extremes of Banach lattice geometry.

#### Normed Riesz Spaces

- **`ExPseudoNormedRieszSpace`**: Class extending `ExPseudoNormedAbGroup` and `TopRieszSpace`. Adds the axiom `norm_<=` requiring `abs x <= abs y -> norm x <= norm y` (monotonicity of norm with respect to absolute value). Provides a default implementation of `solid-neighborhood` derived from this monotonicity, building solid open balls around 0 from the metric ball structure.

#### Banach Lattices

- **`BanachLattice`**: Class extending `ExPseudoNormedRieszSpace` and `RealBanachSpace`. A Riesz space that is simultaneously a real Banach space and whose norm is monotone with respect to the lattice's absolute value — the standard setting for Banach lattice theory.

#### L- and M-Spaces

- **`RieszLSpace`**: Class extending `BanachLattice` with the axiom `norm_+_>=`: `norm x + norm y <= norm (x + y)` (in `ExUpperReal`). Together with the triangle inequality this forces additivity of the norm on the positive cone, characterizing abstract L-spaces (e.g. `L¹` spaces).
- **`RieszMSpace`**: Class extending `BanachLattice` with the axiom `norm_join`: `norm (x ∨ y) <= norm x ∨ norm y` (the right-hand `∨` taken in `ExUpperRealLattice`). Captures abstract M-spaces (e.g. `L∞` and `C(K)` spaces) where the norm is sublinear on joins.
