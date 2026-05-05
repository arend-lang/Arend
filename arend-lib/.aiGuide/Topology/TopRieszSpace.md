### Topology.TopRieszSpace

Topological Riesz spaces: Riesz spaces (lattice-ordered vector spaces) equipped with a compatible topological abelian group structure where the lattice operations are continuous, expressed via existence of solid neighborhoods of zero.

#### Classes

- **`TopRieszSpace`**: Extends `TopAbGroup` and `RieszSpace`. A Riesz space with a topological abelian group structure such that every neighborhood `U` of `0` contains a solid neighborhood `V` of `0` (i.e., `V` is downward-closed under absolute value: if `|x| ≤ |y|` and `y ∈ V`, then `x ∈ V`). The field `solid-neighborhood` provides this property: given `single 0 <=< U`, produces a solid set `V` with `single 0 <=< V` and `V ⊆ U`. This condition ensures that the lattice operations (meet, join, absolute value) are continuous, making the topology compatible with the Riesz space order structure.
