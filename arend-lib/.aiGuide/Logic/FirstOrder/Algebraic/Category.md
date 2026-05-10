### Logic.FirstOrder.Algebraic.Category

The category of models of a first-order algebraic theory, equipped with all small limits and colimits.

For a fixed theory `T`, models and model homomorphisms form a bicomplete category `ModelCat T`. Limits are computed pointwise on the underlying carriers — a tuple of compatible elements indexed by the diagram, with operations and relations interpreted componentwise. Colimits are constructed presentation-style: extend the signature with a constant for every element of every model in the diagram, add axioms forcing those constants to respect operations, relations, and transition maps, and take the quotient term model of the resulting theory. The universal property of the colimit is then witnessed by interpreting these added constants in any cocone.

#### Homomorphisms

- **`ModelHom`**: Record of a homomorphism between two `T`-models `Dom` and `Cod`. Carries a sort-indexed family `funcs` of underlying functions, a `func-op` law witnessing preservation of operations, and a `func-rel` law witnessing that relations are reflected forward (preserved).

#### The Category

- **`ModelCat`**: Instance making `Model T` a `BicompleteCat`. Combines the underlying precategory with proofs of univalence, all small limits, and all small colimits.
- **`ModelPrecat`**: Underlying `Precat` structure on `Model T` with `ModelHom` as morphisms, identity homomorphisms, and composition that combines `func-op`/`func-rel` laws.

#### Limits

- **`limitStructure`**: Given a diagram `G : J -> ModelPrecat T`, builds the limit `T`-structure whose carrier at sort `s` is the type of compatible families `(P : ∏ j, G j s)` together with a coherence proof for every morphism `h : Hom j j'`. Operations act pointwise; a relation holds iff it holds in every component.
- **`limitStructureInterp`**: Term interpretation in the limit structure projects componentwise: `(interpret rho t).1 j = interpret (rho · proj j) t`.
- **`limitStructureTruth`**: Formula truth in the limit equals the conjunction of truth in every component, justifying that the limit structure is a model.

#### Colimits via Theory Extension

- **`ColimitData`**: Class packaging the colimit construction for a diagram `G : J -> ModelPrecat T`. Internally extends `T`'s signature with a constant for every element of every `G j` and adds axioms that make those constants behave like the diagram demands.
- **`ColimitData.sigExt`**: Extended signature whose function symbols are `Or (T.Symb s) (Trunc0 (Σ (j : J) (G j s)))` — original symbols on the left, new constants (one per diagram element) on the right; predicates and predicate domains are inherited.
- **`ColimitData.liftTerm`**, **`ColimitData.liftFormula`**, **`ColimitData.liftSequent`**: Embed terms, formulas, and sequents from `T` into the extended signature by tagging old symbols with `inl`.
- **`ColimitData.thExt`**: Extended theory. Axioms are the disjunction of: (1) lifted axioms of `T`, (2) the new constants commute with operations, (3) they witness the relations holding in their model, and (4) they identify along every morphism `h : Hom j j'`.
- **`ColimitData.ColimitStr`**: `T`-structure on the quotient term algebra `thExt.QTerm`. Operations are formal `qapply`; a predicate holds iff it is a theorem on some preimage tuple of `T`-terms.
- **`ColimitData.Colimit`**: The colimit `T`-model, packaging `ColimitStr` with the proof that it satisfies `T`'s axioms.
- **`ColimitData.colimitMap`**: For each `j : J`, the canonical homomorphism `G j -> Colimit` sending `x` to the constant `qapply (inr (in0 (j, x))) nil`.
- **`ColimitData.colimitCone`**: Bundles the `colimitMap`s into a `Cone` over `G.op` with apex `Colimit`.

#### Universal Property

- **`ColimitData.strExt`**: Given a cone `C : Cone G.op M` over a `T`-model `M`, extends `M`'s structure to the enlarged signature `thExt` by interpreting each new constant as the corresponding image under `C.coneMap`.
- **`ColimitData.strExtT`**, **`ColimitData.strExtF`**: Interpreting a lifted term/formula in `strExt C` agrees with interpreting the original in `M` — the extension is conservative on `T`-data.
- **`ColimitData.modelExt`**: Promotes `strExt C` to a `Model thExt`; the cone's coherence supplies the new axioms.
- **`ColimitData.modelExtHom`**: The induced homomorphism `Colimit -> M` from a cone `C`, defined by `qinterpret` in `modelExt C`.
- **`ColimitData.substInterpT`**, **`ColimitData.substInterpF`**: Bridge lemmas relating interpretation in `ColimitStr` under a substitution to provability in `thExt`, used to verify `Colimit` is a model.
- **`ColimitData.interpret=map`**, **`ColimitData.qinterpret=map`**: Any homomorphism `f : Colimit -> M` agrees with the interpretation map of the cone `f` pulls back from `colimitCone`, which forces uniqueness in the colimit universal property.
- **`ColimitData.isColimit`**: The map `conePullback colimitCone M : Hom Colimit M -> Cone G.op M` is an equivalence — i.e. `Colimit` is the colimit of `G`.

#### Auxiliary

- **`emptyFin`**: `\Sigma S Empty` is finite of cardinality 0; used to build the empty-context sequents that express the new colimit axioms.
