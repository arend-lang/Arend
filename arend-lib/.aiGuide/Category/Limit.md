### Category.Limit

Cones, limits, and standard limit shapes (products, equalizers, pullbacks, terminal objects) for categories, plus completeness classes.

#### Cones

- **`Cone`**: A cone over a functor `G : J -> D` with given `apex`, projections `coneMap j : Hom apex (G j)`, and naturality `coneCoh`.
- **`Cone.map`**: Pushes a cone through a functor `F : C -> D` to a cone over `Comp F G`.
- **`Cone.premap`**: Reindexes a cone along a functor `F : J -> J'`.
- **`Cone.mapEquiv`**: For a fully faithful `F`, cones over `G` at `X` are equivalent to cones over `Comp F G` at `F X`.
- **`conePullback`**: Precomposes a cone's projections with `f : Hom z apex` to produce a cone with apex `z`.

#### Limits

- **`Limit`**: A `Cone` whose `conePullback` is an equivalence; equivalently provides `limMap`, `limBeta`, and `limUnique`. Includes default implementations relating these formulations.
- **`Limit.levelProp`**: Limits over the same data form a proposition.
- **`Limit.iso_lim`**: Transports a limit structure across an iso `L.limMap c : L -> c`.
- **`Limit.lim_iso`**: The mediating map between two limits is an iso.
- **`Limit.transFuncMap`**: Mediating morphism between limits induced by a functor `H : L.J -> L'.J` and a natural transformation `Comp L'.G H => L.G`.
- **`Colimit`**: Defined as a limit in the opposite category.

#### Diagrams

- **`Diagram`**: A graph `G` together with vertex/edge data `F`, `Func` into a precategory `D` (free-cat-style diagram).
- **`DiagramCone`**: Extends `Diagram` and `Cone`, using `G.FreeCat` as the indexing precategory; coherence reduces to per-edge `diagramCoh`.
- **`DiagramCone.coneCoh-lem`**: Extends edge-wise coherence to morphisms in the free category.
- **`DiagramCone.pullback`**: Precomposition of a diagram cone with `f : Hom z apex`.
- **`DiagramCone.equiv`**: Equivalence between `DiagramCone`s and `Cone`s over the corresponding functor.
- **`LimitDiagram`**: A `DiagramCone` that is also a `Limit`, characterized by `isLimitDiagram`.

#### Products

- **`Product`**: An apex with projections `proj j` satisfying `tupleMap`/`tupleBeta`/`tupleEq` (universal property of a product over a type `J`).
- **`Product.functor`**: Turns a family `G : J -> D` into a functor from `DiscretePrecat J`.
- **`Product.fromLimit`**: Coercion: a limit over a discrete diagram is a product.
- **`terminal-obj`**: Defined as `Product` of the empty family.
- **`terminal-obj-iso`**: Any two terminal objects are isomorphic.
- **`terminalMap'`**: Canonical map into a terminal object.
- **`isTerminal`**: Builds a `terminal-obj` from contractibility of `Hom b a` for all `b`.
- **`terminal-unique'`**: Maps into a terminal object are unique.
- **`terminal-prop`**: In a (univalent) `Cat`, terminal objects form a proposition.

#### Equalizers

- **`Equalizer`**: Apex with `eql : Hom apex X` equalizing `f, g : Hom X Y`, characterized by an `Equiv` to `\Sigma h, f ∘ h = g ∘ h`.
- **`Equalizer.Arrows`** / **`Shape`** / **`map`** / **`functor`**: The walking-parallel-pair shape and its functor into `D`.
- **`Equalizer.fromLimit`**: Coercion: a limit over the parallel-pair functor is an equalizer.
- **`Equalizer.unique`**: Canonical iso between two equalizers of the same parallel pair.
- **`Equalizer.unique-map`**: Compatibility of `unique` with the equalizing arrow.
- **`Equalizer.mono=>equalizer`**: A mono `eql` with the universal factorization property is an equalizer.
- **`Equalizer.id-equalizer`**: `id X` is an equalizer of `f, f`.
- **`Equalizer.equalizer-iso`**: An equalizer of `f, f` is iso to `X` via `eql`.

#### Pullbacks

- **`Pullback`**: Apex over `f : Hom x z`, `g : Hom y z` with projections, coherence `pbCoh`, mediating map `pbMap`, betas, and uniqueness `pbEta`.
- **`Pullback.Shape`** / **`diagram`**: The walking cospan graph and its diagram in `D`.
- **`Pullback.fromLimit`**: Coercion: a limit over a cospan diagram is a pullback.
- **`Pullback.fromIso`**: Transports a pullback along an iso of apexes.
- **`Pullback.unique`** (with `p-map`, `p-beta1`, `p-beta2`, `hinv'`): Canonical iso between two pullbacks of the same cospan.
- **`Pullback.pullback-of-mono`** / **`pullback-of-mono'`**: Pullback of a mono is a mono (on either leg).
- **`pullback-lemma`**: Pasting lemma — composing two pullback squares yields a pullback of the composite.
- **`pullback-lemma-conv`**: Converse pasting — given the outer and right squares as pullbacks, the left square is a pullback.

#### Constructing Limits from Products and Equalizers

- **`limits<=pr+eq`**: Builds an arbitrary small limit from products of objects, products of arrows, and equalizers.

#### Pullback and Slice Functoriality

- **`PrecatWithPullbacks`**: Precategory equipped with a chosen `pullback` for every cospan.
- **`pullbackFunctor`**: Pullback along `f : Hom x y` as a functor `SlicePrecat y -> SlicePrecat x`.

#### Completeness Classes

- **`PrecatWithTerminal`**: Precategory with a chosen terminal object.
- **`PrecatWithBprod`**: Precategory with binary products via `Bprod`.
- **`CartesianPrecat`**: Combines `PrecatWithTerminal` and `PrecatWithBprod`.
- **`FinCompletePrecat`**: Finitely complete: extends `PrecatWithPullbacks` and `CartesianPrecat`, deriving `Bprod` from pullbacks over the terminal.
- **`CompletePrecat`**: Has a `limit` for every functor from a small `J`; derives pullbacks, terminal, and binary products from limits.
- **`CompletePrecat.applyEquiv`**: Transports completeness across a categorical equivalence.
- **`CompleteCat`**: A complete `Cat`.
- **`CocompletePrecat`** / **`CocompletePrecat.applyEquiv`**: Cocomplete dual; transport via dualizing.
- **`CocompleteCat`**, **`BicompleteCat`**: Cocomplete (resp. both complete and cocomplete) `Cat`.

#### Preservation, Reflection, Creation

- **`PreservesLimit`**: `G : C -> D` sends every limit of `F` to a limit of `Comp G F`.
- **`ReflectsLimit`**: A cone is a limit if its image under `G` is.
- **`CreatesLimit`**: From a limit of `Comp G F`, produces a limit of `F` together with preservation and reflection.

#### Regular Mono/Epi

- **`isRegularMono`**: Truncated existence of an equalizer presentation of `f`.
- **`regularMono_Mono`**: Regular monos are monos.
- **`regularMono_pullback`**: Regular monos are stable under pullback.
- **`splitMono_regular`**: Split monos are regular.
- **`isRegularEpi`**: Regular epi defined as regular mono in the opposite category.
