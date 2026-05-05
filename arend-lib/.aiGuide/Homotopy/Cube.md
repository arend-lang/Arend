### Homotopy.Cube

Definitions of two- and three-dimensional cubes (squares and cubes) in homotopy type theory, expressed as iterated dependent paths between paths.

#### 2-Dimensional Cubes (Squares)

- **`Cube2`**: A square (2-cube) with four corners `a00, a01, a10, a11` and four edge paths `p_0, p_1, p0_, p1_`. Defined as a path between paths: `Path (\lam j => p0_ @ j = p1_ @ j) p_0 p_1`.
- **`Cube2.equality`**: Proves that `Cube2 p_0 p_1 p0_ p1_` is equivalent to the equation `p_0 = p0_ <* p_1 *> inv p1_`, characterizing squares by the boundary equation expressing one edge as the composition of the other three.
- **`Cube2.map`**: Constructs a `Cube2` from an equation `p_0 = p0_ <* p_1 *> inv p1_` by transporting along `equality`.

#### 3-Dimensional Cubes

- **`Cube3`**: A 3-cube with eight vertices `v000, ..., v111`, twelve edges, and six face squares (`f__0, f__1, f_0_, f_1_, f0__, f1__`). Defined as a path of squares between the two opposing faces `f__0` and `f__1`, ensuring the six faces fit together coherently as the boundary of a filled cube.
