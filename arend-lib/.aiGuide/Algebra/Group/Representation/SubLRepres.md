### Algebra.Group.Representation.SubLRepres

- **`ImageLRepres`**: The image of an intertwining map `f`, as a `SubLRepres f.R f.G f.Cod`: a sub-representation of the codomain whose module part is `ImageLModule f` and whose action part is `ImageGSet f`.
- **`SubLRepres`**: Class of sub-representations of a linear representation `E : LinRepres R G`: a subset of `E` that is simultaneously a submodule (`SubLModule`) and a `G`-invariant subset (`SubGSet`), with the ambient `S` overridden to a `LinRepres R G`. `IsTrivial` says it is either zero or everything.
- **`SubLRepres.IsTrivial`**: A sub-representation is trivial if every element it contains is `0`, or it contains every element.
- **`ILinRepres`**: The linear representation induced on a sub-representation `S : SubLRepres`, with module part `IModule S` and action part `IGSet S`. `ILinRepres.embed` is the inclusion into `S.S`, an intertwining map.
- **`KernelLRepres`**: The kernel of an intertwining map `f`, as a `SubLRepres f.R f.G f.Dom` whose module part is `KernelLModule f`.
- **`ImLRepres`**: The image of an intertwining map `f` as a linear representation in its own right, `ILinRepres (ImageLRepres f)`.
- **`ImLRepresLeftHom`**: The intertwining map `f.Dom -> ImLRepres f` corestricting `f` to its image, with linear part `ImLModuleLeftHom f` and equivariant part `ImGSetLeftHom f`.
