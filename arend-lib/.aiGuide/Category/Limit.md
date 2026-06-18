### Category.Limit

Limits, colimits, and finite-limit structures (products, equalizers, pullbacks, terminal objects) for precategories, plus their universal mapping properties.

This module formalizes limits via the `Cone`/`Limit` pattern: a cone over a diagram `G : Functor J D` is an apex with compatible projection maps, and a limit is a cone such that pulling back along any morphism into the apex is an equivalence with the cone space. Concrete shapes (products, equalizers, pullbacks, terminals) are presented both as standalone universal records and as instances of `Limit` over specific shape categories, with mutual conversions (`fromLimit`/`toLimit`) so users can mix styles. Diagrams over arbitrary graphs are handled via `Diagram`/`DiagramCone`, which freely generates a small category from a graph. Completeness is layered: `PrecatWithPullbacks` ⊂ `CartesianPrecat` ⊂ `FinCompletePrecat` ⊂ `CompletePrecat` ⊂ `CompleteCat`, with `Colimit`/`Cocomplete*` defined dually via `op`. The classical reduction "products + equalizers ⇒ all limits" appears as `limits<=pr+eq`.

#### Cones

- **`Cone`**: A cone over `G : Functor J D` with apex `apex : D`: maps `coneMap j : Hom apex (G j)` satisfying naturality `G.Func h ∘ coneMap j = coneMap j'`.
- **`Cone.map`**: Push a cone forward along a functor `F : C -> D`, yielding a cone over `Comp F G`.
- **`Cone.premap`**: Reindex a cone along a functor `F : J -> J'`, yielding a cone over `Comp c.G F`.
- **`Cone.mapEquiv`**: For a fully faithful `F`, the map `c ↦ map F c` is an equivalence between cones in `C` and cones in `D`.
- **`conePullback`**: Precompose a cone with `f : Hom z apex` to get a cone with apex `z`.

#### Limits

- **`Limit`**: Extends `Cone`. A limit cone: `conePullback` is an equivalence at every `z`. Provides `limMap`, `limBeta`, `limUnique` as defaults derived from `isLimit`, plus `limUniqueBeta`.
- **`Limit.levelProp`**: Being a limit (with fixed cone data) is a proposition.
- **`Limit.iso_lim`**: A cone whose comparison map into a known limit is an isomorphism is itself a limit.
- **`Limit.lim_iso`**: Any two limits of the same diagram are canonically isomorphic.
- **`Limit.transFuncMap`**: Map between limits induced by a functor `H : L.J -> L'.J` and a natural transformation `Comp L'.G H ⇒ L.G`.
- **`Colimit`**: Macro defining colimits as limits in the opposite category.

#### Diagrams (Graph-indexed)

- **`Diagram`**: A graph `G : Graph` mapped to `D` with edge images; carries a derived `functor : Functor G.FreeCat D`.
- **`DiagramCone`**: Extends `Diagram` and `Cone`. A cone whose coherence is checked only on graph edges (`diagramCoh`); full functorial coherence is reconstructed via `coneCoh-lem`.
- **`DiagramCone.pullback`**: Diagram-cone version of `conePullback`.
- **`DiagramCone.equiv`**: Equivalence between `DiagramCone`s and `Cone`s over the freely generated functor.
- **`LimitDiagram`**: Extends `DiagramCone` and `Limit`; specifying limit-ness on the graph-cone formulation suffices.

#### Products

- **`Product`**: A `J`-indexed product with projections `proj`, tupling `tupleMap`, `tupleBeta`, and uniqueness `tupleEq`.
- **`Product.isProduct`**: Hom-set bijection: `Hom Z apex ≃ ∏ j, Hom Z (G j)`.
- **`Product.tupleMapComp`**: `tupleMap f ∘ h = tupleMap (λ j, f j ∘ h)`.
- **`Product.tupleEta`**: η-rule: `tupleMap (proj j ∘ f) = f`.
- **`Product.toLimit`**: A product is a limit over `DiscretePrecat J`.
- **`Product.functor`**: Builds the discrete functor `DiscretePrecat J -> D` from a family `J -> D`.
- **`Product.fromLimit`**: Coercion from limit over a discrete category back to a `Product`.

#### Equalizers

- **`Equalizer`**: Apex with `eql : Hom apex X` satisfying `f ∘ eql = g ∘ eql`, classified by an equivalence `Hom Z apex ≃ Σ (h : Hom Z X) (f ∘ h = g ∘ h)`.
- **`Equalizer.eqMap`**, **`eqBeta`**, **`eqMono`**: Universal map, computation rule, and uniqueness (mono property of `eql`).
- **`Equalizer.toLimit`**: Equalizer as a limit over the parallel-arrows shape.
- **`Equalizer.Arrows` / `Shape` / `functor`**: Two-object shape category `{false, true}` with two parallel arrows, used to encode equalizer diagrams.
- **`Equalizer.fromLimit`**: Coercion from a limit over `functor f g` to an `Equalizer`.
- **`Equalizer.unique`**, **`unique-map`**: Canonical iso between any two equalizers, compatible with `eql`.
- **`Equalizer.mono=>equalizer`**: A mono with the universal "fills any factorization" property is an equalizer.
- **`Equalizer.id-equalizer`**: `id X` is an equalizer of `f, f`.
- **`Equalizer.equalizer-iso`**: Any equalizer of `f` with itself is iso to `X` via `eql`.

#### Pullbacks

- **`Pullback`**: Square `f ∘ pbProj1 = g ∘ pbProj2` with universal `pbMap`, `pbBeta1`, `pbBeta2`, `pbEta`.
- **`Pullback.pbMap-comp`**: Compatibility of `pbMap` with precomposition.
- **`Pullback.flip`**: Swap the two legs of a pullback.
- **`Pullback.toLimit`**: Pullback as a `LimitDiagram` over the cospan shape.
- **`Pullback.Shape` / `diagram`**: Cospan graph (3 vertices, two edges into the apex) and its diagram in `D`.
- **`Pullback.fromLimit`**: Coercion: limit over a cospan gives a `Pullback`.
- **`Pullback.fromIso`**: Transfer a pullback structure along an iso into the apex.
- **`Pullback.unique`**, **`p-map`**, **`p-beta1/2`**, **`hinv'`**: Canonical iso between any two pullbacks of the same cospan.
- **`Pullback.pullback-of-mono`**, **`pullback-of-mono'`**: Monos are stable under pullback (on either leg).

#### Limits from Products + Equalizers

- **`limits<=pr+eq`**: Construct limits of arbitrary small diagrams from arbitrary products and binary equalizers via the standard equalizer-of-two-products formula.

#### Categories with Specific Limits

- **`PrecatWithPullbacks`**: A `Precat` with a chosen pullback for every cospan.
- **`pullbackFunctor`**: For `f : Hom x y` in a `PrecatWithPullbacks`, the base-change functor `SlicePrecat y -> SlicePrecat x`.
- **`terminal-obj`**: Macro: `Product` over the empty type, i.e. a terminal object.
- **`terminal-obj-iso`**: Any two terminal objects are canonically iso.
- **`terminalMap'`**, **`terminal-unique'`**: Unique morphism into a terminal and its uniqueness.
- **`isTerminal`**: Build a terminal object from contractibility of each `Hom b a`.
- **`terminal-prop`**: In a (univalent) `Cat`, terminal objects form a proposition.
- **`PrecatWithTerminal`**: Class with a chosen terminal and `terminalMap`, `terminal-unique`, plus `global-section` (a global element is a split mono).
- **`PrecatWithBprod`**: Class with binary products `Bprod x y`. Exposes `proj1`, `proj2`, `pair`, `prodMap`, `diagonal`, `associator`, `change` (symmetry), and the bifunctor `bprodBiFunctor`, plus algebraic lemmas (`prodMap-comp`, `prod-id`, `pair-comp`, `pair-proj`, `associator-iso`, `change-inv`, `change-prod`, `bprod-comm`, `associtor-prod`).
- **`PrecatWithBprod.bprodFunctorRight`**: Endofunctor `_ × Y` for fixed `Y`.
- **`CartesianPrecat`**: Extends `PrecatWithTerminal` and `PrecatWithBprod`; provides `terminal-prod-left` (`X ≅ 1 × X`).
- **`FinCompletePrecat`**: Extends `PrecatWithPullbacks` and `CartesianPrecat`; binary products are derived from terminals and pullbacks.
- **`CompletePrecat`**: Extends `FinCompletePrecat`; `limit G` for every functor from a small category. Derives pullback, terminal, binary product, arbitrary `product`, and `equalizer`. Has dual `op : CocompletePrecat`.
- **`CompletePrecat.applyEquiv`**: Transport completeness along a categorical equivalence.
- **`CompleteCat`**: `CompletePrecat` that is a (univalent) `Cat`.
- **`CocompletePrecat`**, **`CocompleteCat`**, **`BicompleteCat`**: Duals and combination.

#### Preservation, Reflection, Creation

- **`PreservesLimit`**: `G` preserves limits of `F` if `Cone.map G L` is again a limit.
- **`ReflectsLimit`**: `G` reflects limits if a cone whose image is a limit is itself a limit.
- **`CreatesLimit`**: A limit of `Comp G F` lifts to a limit of `F` together with preservation and reflection by `G`.

#### Regular Monos/Epis and Pullback Pasting

- **`isRegularMono`**: `f` is a regular mono iff it occurs as some equalizer.
- **`regularMono_Mono`**: Regular monos are monos.
- **`regularMono_pullback`**: Regular monos are stable under pullback.
- **`splitMono_regular`**: Split monos are regular.
- **`isRegularEpi`**: Defined dually as a regular mono in the opposite category.
- **`pullback-lemma`**: Pasting lemma — composing two pullback squares horizontally gives a pullback.
- **`pullback-lemma-conv`**: Converse pasting — if the outer and right squares are pullbacks, so is the left.
