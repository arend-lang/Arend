### Category.Yoneda

The Yoneda embedding, the category of elements, and the universal property of presheaf categories as free cocompletions.

This module formalizes three interlocking constructions central to category theory. First, the **Yoneda embedding** `C → PSh(C)` sends each object `c` to its representable presheaf `Hom(-, c)`, and the **Yoneda lemma** identifies natural transformations out of a representable with elements of the target presheaf. Second, the **category of elements** `∫P` of a presheaf `P` packages pairs `(c, p ∈ P(c))` into a category fibered over `C`, providing the indexing diagram for the canonical colimit decomposition. Third, every presheaf is exhibited as a colimit of representables (the **density theorem**), which yields the universal property: any functor `A : C → E` into a cocomplete category extends essentially uniquely to a colimit-preserving functor `PSh(C) → E`, presenting `PSh(C)` as the free cocompletion of `C`.

#### Hom Functors

- **`HomLeft`**: The contravariant hom functor `C(-,c) : C^op → Set` for an object `c`; on morphisms it acts by precomposition.
- **`HomRight`**: The covariant hom functor `C(c,-) : C → Set` for an object `c`; on morphisms it acts by postcomposition.
- **`homRight-preservesLimit`**: The covariant hom functor `HomRight c` preserves every limit of a diagram `F : J → C`.
- **`HomPresheaf`**: The representable presheaf `C(-,c)`, i.e. `HomLeft c` packaged as a `Presheaf C`.

#### Yoneda Embedding

- **`Yoneda-trans`**: Given an element `x : F a` of a presheaf `F`, the natural transformation `HomLeft a → F` sending `f` to `F.Func f x`.
- **`Yoneda-lemma`**: The Yoneda lemma: natural transformations `HomLeft a → F` correspond to elements of `F a` via `nat ↦ nat a id`, with inverse `Yoneda-trans`.
- **`Yoneda-apply`**: Composing a natural transformation `f : S → T` with `Yoneda-trans x` gives `Yoneda-trans (f a x)`.
- **`Yoneda-trans_o`**: Naturality of the Yoneda correspondence in the object: `Yoneda-trans x ∘ Yoneda.Func g = Yoneda-trans (F.Func g x)`.
- **`Yoneda`**: The Yoneda embedding `C → PSh(C)`, sending `c` to its representable presheaf `HomPresheaf c` and a morphism `f` to postcomposition with `f`; it is fully faithful.
- **`presheaf-mono-char`**: A natural transformation of presheaves is a monomorphism in `PresheafCat` iff each of its components is injective.

#### Category of Elements

- **`PrecategoryOfElements`**: The category of elements `∫P` of a presheaf `P`: objects are pairs `(c, p)` with `p : P c`, and morphisms `(c, p) → (c', p')` are morphisms `u : c → c'` with `p = P.Func u p'`.
- **`PrecategoryOfElements.projection`**: The forgetful functor `∫P → C` projecting onto the first component.
- **`PrecategoryOfElements.functorial`**: Functoriality of the construction in `P`: a natural transformation `nat : P → F` induces a functor `∫P → ∫F` sending `(c, p)` to `(c, nat_c(p))`.

#### Density Theorem

- **`PresheafColimit`**: Every presheaf `P` is the colimit of the diagram of representables `Yoneda ∘ projection P` over its category of elements.

#### Universal Property of Presheaf Categories

- **`YonedaLift`**: Extension of a functor `A : C → E` into a cocomplete category `E` to a functor `PSh(C) → E`, sending a presheaf `P` to the colimit of `A` over `∫P`.
- **`YonedaLift.induced-natural`**: The natural transformation between opposite diagrams used to induce the morphism `L(X) → L(Y)` from a presheaf morphism `f : X → Y`.
- **`YonedaLift.cone-in-induced`**: The auxiliary cone produced by `Limit.transFuncMap` and used in the universality proof.
- **`YonedaLift_Yoneda`**: The extension `YonedaLift A` restricts along the Yoneda embedding to `A`: `A` is isomorphic to `Comp (YonedaLift A) Yoneda`.
