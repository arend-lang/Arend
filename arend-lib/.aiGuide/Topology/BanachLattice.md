### Topology.BanachLattice

Banach lattices: Banach spaces equipped with a compatible Riesz (vector lattice) structure where the norm respects the order, including the L-space and M-space variants.

#### Normed Riesz Spaces

- **`ExPseudoNormedRieszSpace`**: Extends `ExPseudoNormedAbGroup` and `TopRieszSpace`. A topological Riesz space with an extended pseudo-norm that is solid (monotone with respect to absolute value).
  - **`norm_<=`**: Solidity of the norm: `abs x <= abs y -> norm x <= norm y`.
  - **`solid-neighborhood`**: Derives the solid-neighborhood property of the Riesz topology from the norm balls.

#### Banach Lattices

- **`BanachLattice`**: Extends `ExPseudoNormedRieszSpace` and `RealBanachSpace`. A real Banach space whose norm is solid with respect to the lattice order — i.e., a complete normed Riesz space.

#### L-spaces and M-spaces

- **`RieszLSpace`**: Extends `BanachLattice`. An L-space: the norm is additive on the positive cone via `norm_+_>=`: `norm x + norm y <= norm (x + y)` (combined with the triangle inequality this gives equality on disjoint/positive elements).
- **`RieszMSpace`**: Extends `BanachLattice`. An M-space: the norm satisfies `norm_join`: `norm (x ∨ y) <= norm x ∨ norm y`, so the norm of a join is bounded by the join of the norms.
