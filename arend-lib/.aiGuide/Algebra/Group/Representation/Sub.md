### Algebra.Group.Representation.Sub

Subrepresentations of linear group representations, presented as injective intertwining maps from a smaller representation into a larger one.

#### Subrepresentation Class

- **`SubLRepres`**: A subrepresentation of a linear representation `E : LinRepres R G`, packaging a representation `S`, an intertwining map `in : InterwiningMap S E`, and a proof `in-mono` that `in` is injective.

#### Canonical Subrepresentations

- **`KernelSubLRepres`**: Given an intertwining map `f : InterwiningMap A B`, constructs the kernel of `f` as a subrepresentation of `A`, using `KerLRepres f` with its canonical inclusion `KerLRepresHom f`.
- **`ImageSubLRepres`**: Given an intertwining map `f : InterwiningMap A B`, constructs the image of `f` as a subrepresentation of `B`, using `ImageLRepres f` with its canonical inclusion `ImageLRepresRightHom f`.
