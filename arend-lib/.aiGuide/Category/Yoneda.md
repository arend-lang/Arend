### Category.Yoneda

The Yoneda embedding, the category of elements, and the universal property of presheaf categories as free cocompletions.

This module formalizes three interlocking constructions central to category theory. First, the **Yoneda embedding** `C → PSh(C)` sends each object `c` to its representable presheaf `Hom(-, c)`, and the **Yoneda lemma** identifies natural transformations out of a representable with elements of the target presheaf. Second, the **category of elements** `∫P` of a presheaf `P` packages pairs `(c, p ∈ P(c))` into a category fibered over `C`, providing the indexing diagram for the canonical colimit decomposition. Third, every presheaf is exhibited as a colimit of representables (the **density theorem**), which yields the universal property: any functor `A : C → E` into a cocomplete category extends essentially uniquely to a colimit-preserving functor `PSh(C) → E`, presenting `PSh(C)` as the free cocompletion of `C`.

#### Yoneda Embedding

- **`YonedaEmbedding`**: The fully faithful functor `C → PSh(C)` sending objects to their representable presheaves.
- **`YonedaEmbedding.hom-presheaf`**: For `c : C`, the representable presheaf `Hom(-, c) : C^op → Set`, with functorial action by precomposition.
- **`YonedaEmbedding.functor`**: The underlying functor of the Yoneda embedding; on morphisms `f : a → b` it produces the natural transformation given by postcomposition with `f`.
- **`YonedaEmbedding.yoneda-lemma`**: The Yoneda lemma as a `QEquiv` between `Hom(よ A, F)` and `F(A)`, with forward map `nat ↦ nat_A(id_A)` and inverse sending `p ∈ F(A)` to the natural transformation `f ↦ F(f)(p)`.

#### Category of Elements

- **`Precategory-of-elements`**: The category `∫P` of elements of a presheaf `P`, whose objects are pairs `(c, p)` with `p ∈ P(c)` and whose morphisms `(c, p) → (c', p')` are arrows `u : c → c'` satisfying `p = P(u)(p')`.
- **`Precategory-of-elements.projection`**: The forgetful functor `∫P → C` projecting onto the first component.
- **`Precategory-of-elements.functorial`**: Functoriality of the construction in `P`: a natural transformation `nat : P → F` induces a functor `∫P → ∫F` sending `(c, p)` to `(c, nat_c(p))`.

#### Density Theorem

- **`presheaf-colimit`**: Every presheaf `P` is the colimit of the diagram `∫P → C → PSh(C)` formed by composing the projection from the category of elements with the Yoneda embedding. The cone maps are the natural transformations `Hom(-, c) → P` corresponding under Yoneda to the elements `p ∈ P(c)`.
- **`presheaf-colimit.diagram-functor`**: The canonical diagram `∫P → PSh(C)` whose colimit recovers `P`.

#### Universal Property of Presheaf Categories

- **`embedding-universal`**: The free-cocompletion property: given a small category `C`, a cocomplete category `E`, and a functor `A : C → E`, produces a functor `L : PSh(C) → E` together with an equality `A = L ∘ よ`, exhibiting `PSh(C)` as the free cocompletion of `C`.
- **`embedding-universal.L-limit`**: For each presheaf `P`, the colimit in `E` of the composite diagram `∫P → C → E`.
- **`embedding-universal.L-Functor`**: The left Kan extension `L : PSh(C) → E` of `A` along the Yoneda embedding, defined on objects by `L-limit` and on morphisms via colimit functoriality.
- **`embedding-universal.L-Functor.induced-natural`**: The natural transformation between opposite diagrams used to induce the morphism `L(X) → L(Y)` from a presheaf morphism `f : X → Y`.
- **`embedding-universal.L-Functor.induced-map`**: The action of `L` on morphisms, obtained by transferring along the functor `∫X → ∫Y` induced by `f`.
- **`embedding-universal.L-Functor.cone-in-induced`**: The auxiliary cone produced by `Limit.transFuncMap` and used in the universality proof.
- **`embedding-universal.image-of-representable`**: Identifies `A(x)` as the colimit in `E` of the diagram `∫(Hom(-, x)) → E`, using the terminal object `(x, id_x)` of the category of elements of a representable.
- **`embedding-universal.image-of-rep-iso`**, **`image-of-rep-iso-op`**: The induced isomorphism `A(x) ≅ L(よ x)` in `E` (and its variant in `E^op`) coming from uniqueness of colimits.
- **`embedding-universal.image-of-rep-eq`**: The equality `A(x) = L(よ x)` obtained from univalence of `E`.
- **`embedding-universal.diagram-functor`**: The diagram `∫P → E` defined as `A ∘ projection`, whose colimit defines `L(P)`.
- **`embedding-universal.functors-iso`**: The natural isomorphism `A ≅ L ∘ よ` in the functor category, assembled pointwise from the `image-of-rep-iso` family.
