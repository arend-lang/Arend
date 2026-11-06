### Topology.TopRieszSpace

Topological Riesz spaces: Riesz spaces equipped with a compatible topological abelian group structure where lattice operations and absolute value are uniformly continuous.

This module merges the order-theoretic structure of a Riesz space (vector lattice) with the topological structure of a topological abelian group, requiring that the topology has a basis of solid neighborhoods at zero. The solidity condition ensures that the lattice operations (join, meet, absolute value) interact well with the topology — specifically, they become uniformly continuous maps. Scalar multiplication by rationals is also required to be a topological abelian group map, providing the foundation for working with continuous linear functionals and convergence in spaces of integrable functions or continuous functions on compact sets.

#### Main Class

- **`TopRieszSpace`**: A class extending `TopAbGroup` and `RieszSpace`, requiring that the underlying topology admits a base of solid neighborhoods of zero and that the lattice/scalar operations are uniformly continuous.

#### Solidity Axiom

- **`solid-neighborhood`**: For any neighborhood `U` of `0` (i.e., `single 0 <=< U`), there exists a solid set `V` that is also a neighborhood of `0` and contained in `U`. This ensures the topology is compatible with the order structure.

#### Uniform Continuity of Operations

- **`abs-uniform`**: The absolute value map `abs : E -> E` is a `UniformMap` from the space to itself.
- **`*q-right-uniform`**: For any rational `q`, scalar multiplication `q *q : E -> E` is a `TopAbGroupMap`.
- **`*q-right-uniform.*q_<=1-right-uniform`**: Helper specialization for the case `0 <= q <= 1`, providing uniform continuity of contraction by a rational in the unit interval.
- **`join-uniform`**: The binary join `(s.1, s.2) ↦ s.1 ∨ s.2` is a `UniformMap` from the product space `E × E` to `E`.
- **`meet-uniform`**: The binary meet `(s.1, s.2) ↦ s.1 ∧ s.2` is a `UniformMap` from the product space `E × E` to `E`.
