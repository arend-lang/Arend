### Homotopy.Cube

Higher-dimensional cubes of paths in a type, built on top of the basic path infrastructure.

This module formalizes 2-dimensional and 3-dimensional cubes (squares and cubes) as iterated path types. A `Cube2` is a square whose four sides are paths between four corners, encoded as a path between two paths over a path of endpoints. A `Cube3` is built analogously: it is a path between two `Cube2` faces, with the remaining four faces witnessing that the boundary edges align. The `equality` lemma reduces a square to an ordinary path equation `p_0 = p0_ <* p_1 *> inv p1_`, which gives a practical way to construct squares from algebraic identities between their boundary paths.

#### Squares

- **`Cube2`**: A 2-dimensional square with four corners `a00, a01, a10, a11` and four boundary paths `p_0, p_1, p0_, p1_`, defined as a dependent path between `p_0` and `p_1` over the path-family `p0_ @ j = p1_ @ j`.
- **`Cube2.equality`**: Reduces a square to a path equation: `(p_0 = p0_ <* p_1 *> inv p1_) = Cube2 p_0 p_1 p0_ p1_`. Lets one prove squares by showing that the boundary paths compose appropriately.
- **`Cube2.map`**: Constructs a `Cube2` from a proof `p_0 = p0_ <* p_1 *> inv p1_` by transporting along `equality`. The standard way to produce a square from an algebraic identity between its sides.

#### Cubes

- **`Cube3`**: A 3-dimensional cube with eight corners `v000, ..., v111`, twelve edge paths `e..._...`, and six face squares `f__0, f__1, f_0_, f_1_, f0__, f1__`, defined as a path between the two `Cube2` faces `f__0` and `f__1` over a path-family of squares whose vertical sides come from `f0__`, `f1__` and whose horizontal sides come from `f_0_`, `f_1_`.
