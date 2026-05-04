### Category.Yoneda

The Yoneda embedding, the category of elements of a presheaf, the colimit-of-representables decomposition, and the universal extension of a functor along the Yoneda embedding into a cocomplete category.

#### Yoneda Embedding

- **`YonedaEmbedding`**: The fully faithful functor `C -> PresheafCat C` sending `c` to its representable presheaf `Hom(-, c)`. Builds the equivalence `Hom(F c, F c') ≃ Hom(よc, よc')` via the Yoneda lemma applied to the hom-presheaf.
- **`YonedaEmbedding.hom-presheaf`**: For `c : C`, the representable presheaf `Hom(-, c) : PresheafCat C` with action by precomposition.
- **`YonedaEmbedding.functor`**: The underlying `Functor C (PresheafCat C)` of the Yoneda embedding; on morphisms `f : x -> y` it gives the natural transformation `g ↦ f ∘ g`.
- **`YonedaEmbedding.yoneda-lemma`**: The Yoneda lemma as a `QEquiv` between `Hom(よA, F)` and `F A`, sending a natural transformation `nat` to `nat A (id A)` and reconstructing it from `p ∈ F A` via `f ↦ Func F f p`.

#### Category of Elements

- **`Precategory-of-elements`**: For a presheaf `P : PresheafCat C`, the precategory `∫P` whose objects are pairs `(c, p ∈ P c)` and whose morphisms `(c, p) -> (c', p')` are arrows `u : c -> c'` with `p = Func P u p'`.
- **`Precategory-of-elements.projection`**: The forgetful functor `∫P -> C` sending `(c, p) ↦ c`.
- **`Precategory-of-elements.functorial`**: Functoriality of `∫(-)`: a natural transformation `nat : P -> F` induces a functor `∫P -> ∫F` via `(c, p) ↦ (c, nat c p)`.

#### Colimit of Representables

- **`presheaf-colimit`**: Every presheaf `P` is the colimit of the diagram of representables indexed by its category of elements: `P = colim (よ ∘ projection)`. The cone maps are `(c, p) ↦ (h ↦ Func P h p)`, and the universal map is built from the Yoneda lemma applied componentwise.
- **`presheaf-colimit.diagram-functor`**: The canonical diagram `∫P -> PresheafCat C`, namely `よ ∘ projection`.

#### Universal Extension into Cocomplete Categories

- **`embedding-universal`**: For a small category `C`, a cocomplete `E`, and a functor `A : C -> E`, produces a functor `L : PresheafCat C -> E` together with a proof that `A = L ∘ よ`. This is the universal property of the presheaf category as the free cocompletion of `C`.
- **`embedding-universal.functors-iso`**: The natural isomorphism `A ≅ L ∘ YonedaEmbedding` in `FunctorCat C E`, used to produce the equality via functor-category univalence.
- **`embedding-universal.diagram-functor`**: For a presheaf `P`, the composite `A ∘ projection : ∫P -> E`.
- **`embedding-universal.image-of-representable`**: Exhibits `A x` as the colimit of `A ∘ projection` over `∫(よx)`, with cone map `(c, p : x -> c) ↦ Func A p` and universal map `coneMap (x, id x)`.
- **`embedding-universal.image-of-rep-iso-op`**, **`image-of-rep-iso`**, **`image-of-rep-eq`**: The induced isomorphism (and equality, via univalence) `A x ≅ L(よx)` from comparing the two colimit presentations.
- **`embedding-universal.L-limit`**: The colimit `E.colimit (A ∘ projection)` for a presheaf `P`, providing the value of `L` at `P`.
- **`embedding-universal.L-Functor`**: The extension functor `L : PresheafCat C -> E` defined by `P ↦ L-limit P`, with action on morphisms given by the induced map between colimits.
- **`embedding-universal.L-Functor.induced-natural`**: The natural transformation between opposite diagram functors used to induce the morphism on colimits from a presheaf morphism `f : X -> Y`.
- **`embedding-universal.L-Functor.induced-map`**: The morphism `L-limit X -> L-limit Y` induced by `f : X -> Y` via `Limit.transFuncMap`.
- **`embedding-universal.L-Functor.cone-in-induced`**: The cone over the `X`-diagram obtained by transporting the `Y`-cone along `induced-natural f`, used to compute `limBeta`.
