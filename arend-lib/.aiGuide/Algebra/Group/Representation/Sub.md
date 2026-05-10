### Algebra.Group.Representation.Sub

Subrepresentations of linear group representations as injective intertwining maps.

This module formalizes the notion of a subrepresentation `S \hookrightarrow E` of a linear `G`-representation over a ring `R`, encoded as an intertwining map together with a proof of injectivity. Rather than defining subrepresentations as predicate-cut subsets of the underlying module, the class wraps an injective `InterwiningMap`, which keeps the categorical structure explicit and reusable. The two canonical sources of subrepresentations — kernels and images of intertwining maps — are provided as constructors that package the corresponding `LinRepres` together with the inclusion map.

#### Subrepresentation Class

- **`SubLRepres`**: Class of subrepresentations of a linear representation `E : LinRepres R G`. Bundles a representation `S`, an intertwining inclusion `in : InterwiningMap S E`, and a proof `in-mono` that `in` is injective.
- **`SubLRepres.isTrivial`**: Predicate identifying trivial subrepresentations: either `S` is the zero module, or the inclusion `in` is surjective (i.e. `S = E`).
- **`SubLRepres.**'in`**: Restriction of the `G`-action `**` of `S` to the subrepresentation, exposing it under a local name for convenience.

#### Canonical Subrepresentations from Intertwining Maps

- **`KernelSubLRepres`**: Builds the kernel subrepresentation `Ker f \hookrightarrow A` from an intertwining map `f : A → B`, using `KerLRepres` as the underlying representation and `KerLRepresHom` as the inclusion.
- **`ImageSubLRepres`**: Builds the image subrepresentation `Im f \hookrightarrow B` from an intertwining map `f : A → B`, using `ImageLRepres` as the underlying representation and `ImageLRepresRightHom` as the inclusion into the codomain.
