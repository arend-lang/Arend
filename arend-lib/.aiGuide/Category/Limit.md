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

- **`Limit`**: Extends `Cone`. A limit cone: `isLimit : IsLimitCone \this`, i.e. `conePullback` is an equivalence at every `z`. Provides `limMap`, `limBeta`, `limUnique` as defaults derived from `isLimit`.
- **`IsLimitCone`**: The proposition that a cone `C` is a limit cone: `conePullback C z` is an equivalence for every `z : D`.
- **`Limit.limUniqueBeta`**: If `c.coneMap j = coneMap j ∘ g` for all `j`, then `limMap c = g`.
- **`Limit.levelProp`**: Being a limit (with fixed cone data) is a proposition.
- **`Limit.iso_lim`**: A cone whose comparison map into a known limit is an isomorphism is itself a limit.
- **`Limit.lim_iso`**: Any two limits of the same diagram are canonically isomorphic.
- **`Limit.transFuncMap`**: Map between limits induced by a functor `H : L.J -> L'.J` and a natural transformation `Comp L'.G H ⇒ L.G`.
- **`Colimit`**: Macro defining colimits as limits in the opposite category.

#### Diagrams (Graph-indexed)

- **`Diagram`**: A graph `G : Graph` mapped to `D` with edge images; carries a derived `functor : Functor G.FreeCat D`.
- **`Diagram.functor`**: The functor from the free category on the graph `G` to `D` extending the diagram.
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
- **`Equalizer.Arrows`**, **`Equalizer.Shape`**, **`Equalizer.functor`**: Two-object shape category `{false, true}` with two parallel arrows, used to encode equalizer diagrams.
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
- **`Pullback.unique`**, **`p-map`**, **`hinv'`**: Canonical iso between any two pullbacks of the same cospan.

#### Limits from Products + Equalizers

- **`limits<=pr+eq`**: Construct limits of diagrams indexed by `Precat.{u, u}` (with products over `\Type u`) from arbitrary products and binary equalizers via the standard equalizer-of-two-products formula.
- **`pullback-pr+eq`**: A pullback of `f` and `g` as an equalizer of `f ∘ proj1` and `g ∘ proj2` in a category with binary products; the apex is the equalizer's apex.

#### Categories with Specific Limits

- **`PrecatWithPullbacks`**: A `Precat` with a chosen pullback for every cospan.
- **`pullbackFunctor`**: For `f : Hom x y` in a `PrecatWithPullbacks`, the base-change functor `SlicePrecat y -> SlicePrecat x`.
- **`terminal-obj`**: Macro: `Product` over the empty type, i.e. a terminal object.
- **`terminal-obj-iso`**: Any two terminal objects are canonically iso.
- **`terminalMap'`**, **`terminal-unique'`**: Unique morphism into a terminal and its uniqueness.
- **`isTerminal`**: Build a terminal object from contractibility of each `Hom b a`.
- **`terminal-prop`**: In a (univalent) `Cat`, terminal objects form a proposition.
- **`IsTerminal`**: The proposition that `t` is terminal: `Hom c t` is contractible for every `c`.
- **`PrecatWithTerminal`**: Class with a chosen terminal object `terminal : terminal-obj \this`.
- **`PrecatWithTerminal.terminalMap`**, **`PrecatWithTerminal.terminal-unique`**: The unique morphism `x -> terminal` and its uniqueness.
- **`PrecatWithTerminal.point-splitMono`**: Any global element `p : Hom terminal c` is a split mono.
- **`terminal-isTerminal`**: The chosen terminal object of a `PrecatWithTerminal` satisfies `IsTerminal`.
- **`PrecatWithBprod`**: Class with a chosen binary product `Bprod x y` (a `Product` of the pair `x :: y :: nil`) for all `x y`.
- **`PrecatWithBprod.proj1`**, **`PrecatWithBprod.proj2`**: The projections `Bprod x y -> x` and `Bprod x y -> y`.
- **`PrecatWithBprod.pair`**: The tupling map `pair f g : Hom z (Bprod x y)` of `f : Hom z x` and `g : Hom z y`.
- **`PrecatWithBprod.beta1`**, **`PrecatWithBprod.beta2`**: Computation rules `proj1 ∘ pair f g = f` and `proj2 ∘ pair f g = g`.
- **`PrecatWithBprod.pair-unique`**: Two maps into `Bprod x y` with equal projections are equal.
- **`PrecatWithBprod.pair-comp`**: Precomposition: `pair g h ∘ f = pair (g ∘ f) (h ∘ f)`.
- **`PrecatWithBprod.pair-eta`**: `pair proj1 proj2 = id`.
- **`PrecatWithBprod.prodMap`**: The product `f × f'` of two morphisms, as a map `Bprod x x' -> Bprod y y'`.
- **`PrecatWithBprod.prodMap-comp`**: `prodMap` is compatible with composition.
- **`PrecatWithBprod.prod-id`**: `prodMap id id = id`.
- **`PrecatWithBprod.prodMap-split-right`**: `prodMap f g` factors as `prodMap f id ∘ prodMap id g`.
- **`PrecatWithBprod.prod-id-left`**, **`PrecatWithBprod.prod-id-right`**: Functoriality of `prodMap id _` and `prodMap _ id` with respect to composition.
- **`PrecatWithBprod.prodMap-pair`**: `prodMap f g ∘ pair a b = pair (f ∘ a) (g ∘ b)`.
- **`PrecatWithBprod.diagonal`**: The diagonal `x -> Bprod x x`.
- **`PrecatWithBprod.diagonal-splitMono`**: The diagonal is a split mono, with `proj1` as retraction.
- **`PrecatWithBprod.associator`**: The associativity map `Bprod (Bprod x y) z -> Bprod x (Bprod y z)`.
- **`PrecatWithBprod.associator-iso`**: `associator` is an isomorphism.
- **`PrecatWithBprod.associator-pair`**, **`PrecatWithBprod.associator-inv-pair`**: How `associator` and its inverse act on iterated pairs.
- **`PrecatWithBprod.associator-prod`**, **`PrecatWithBprod.associator-inv-prod`**: Naturality of `associator` and of its inverse with respect to `prodMap`.
- **`PrecatWithBprod.swap`**: The symmetry `Bprod x y -> Bprod y x`.
- **`PrecatWithBprod.swap-inv`**: `swap ∘ swap = id`.
- **`PrecatWithBprod.swap-iso`**, **`PrecatWithBprod.bprod-comm`**: `swap` is an isomorphism, i.e. `Bprod x y ≅ Bprod y x`.
- **`PrecatWithBprod.swap-pair`**: `swap ∘ pair f g = pair g f`.
- **`PrecatWithBprod.swap-prod`**: Naturality of `swap`: `swap ∘ prodMap f g = prodMap g f ∘ swap`.
- **`PrecatWithBprod.bprodBiFunctor`**: The binary-product bifunctor `ProductPrecat \this \this -> \this`.
- **`PrecatWithBprod.bprodFunctorRight`**: Endofunctor `_ × Y` for fixed `Y`.
- **`CartesianPrecat`**: Extends `PrecatWithTerminal` and `PrecatWithBprod`.
- **`CartesianPrecat.Bprod_terminal-left`**: `X ≅ 1 × X`, where `1` is the terminal object.
- **`FinCompletePrecat`**: Extends `PrecatWithPullbacks` and `CartesianPrecat`; binary products are derived from terminals and pullbacks.
- **`FinCompletePrecat.equalizer`**: Binary equalizers, derived from pullbacks and products: a pullback of `pair f g` along the diagonal gives a mono satisfying the universal property (via `Equalizer.mono=>equalizer`).
- **`CompletePrecat`**: Extends `FinCompletePrecat`; `limit G` for every functor from a `Precat.{u, u}`. Derives pullback, terminal and binary product from `limit` as defaults.
- **`CompletePrecat.product`**: The product of any family `G : J -> Ob` with `J : \Type u`.
- **`CompletePrecat.op`**: The dual, a `CocompletePrecat`.
- **`CompletePrecat.applyEquiv`**: Transport completeness along a categorical equivalence `E`: every diagram in `E.C` has a limit.
- **`CompleteCat`**: `CompletePrecat` that is a (univalent) `Cat`.
- **`CompleteCat.op`**: The dual, a `CocompleteCat`.
- **`CocompletePrecat`**, **`CocompleteCat`**, **`BicompleteCat`**: Duals and combination: `CocompletePrecat` has `colimit G` for every small diagram, `CocompleteCat` is a univalent one, and `BicompleteCat` is both complete and cocomplete.
- **`CocompletePrecat.op`**, **`CocompleteCat.op`**, **`BicompleteCat.op`**: The dual categories (complete, respectively bicomplete, with swapped roles).

#### Preservation, Reflection, Creation

- **`PreservesLimit`**: `G` preserves limits of `F` if `Cone.map G c` is a limit cone whenever `c` is.
- **`ReflectsLimit`**: `G` reflects limits if a cone whose image is a limit cone is itself a limit cone.
- **`PreservesColimit`**: The dual of `PreservesLimit`, defined via opposite categories.
- **`preservesLimit-fromLimit`**: If `G` sends one limit of `F` to a limit cone, then it preserves all limits of `F`.
- **`fullyFaithful-reflectsLimit`**: A fully faithful functor reflects limits.
- **`IsFiniteCat`**: A category is finite: finitely many objects up to a surjection from `Fin n`, and for each pair of objects finitely many morphisms in the same sense.
- **`isFiniteCat-op`**: The opposite of a finite category is finite.
- **`PreservesFiniteLimits`**, **`PreservesFiniteColimits`**: `G` preserves limits (respectively colimits) of all functors from finite categories.
- **`preservesFiniteLimits-comp`**: Preservation of finite limits is closed under composition.
- **`PreservesTerminal`**: `G` maps the terminal object to a terminal object.
- **`preservesTerminal-comp`**: Preservation of terminal objects is closed under composition.
- **`PreservesBprod`**: `G` preserves binary products: the canonical map `G (Bprod x y) -> Bprod (G x) (G y)` is an isomorphism.
- **`preservesBprod-comp`**: Preservation of binary products is closed under composition.
- **`PreservesEqualizer`**: `H` maps every equalizer to an equalizer.
- **`FunctorCartesianPrecat`**: The functor category `[J, C]` into a cartesian category is cartesian, with terminal object and products computed pointwise.
- **`evalFunctor-preservesTerminal`**, **`evalFunctor-preservesBprod`**: Evaluation at an object `j` from `FunctorCartesianPrecat J C` to `C` preserves terminal objects, respectively binary products.
- **`CreatesLimit`**: A limit of `Comp G F` lifts to a limit of `F` together with preservation and reflection by `G`.

#### Regular Monos/Epis and Pullback Pasting

- **`Mono_pullback`**: Monos are stable under pullback: if `P.g` is mono then so is `P.pbProj1`.
- **`IsRegularMono`**: `f` is a regular mono iff it is (merely) the equalizer morphism of some pair of parallel arrows.
- **`regularMono_Mono`**: Regular monos are monos.
- **`regularMono_pullback`**: Regular monos are stable under pullback.
- **`splitMono_regular`**: Split monos are regular.
- **`isRegularEpi`**: Defined dually: `f` is a regular epi iff it is a regular mono in the opposite category.
- **`pullback-lemma`**: Pasting lemma — composing two pullback squares horizontally gives a pullback.
- **`pullback-lemma-conv`**: Converse pasting — if the outer and right squares are pullbacks, so is the left.
