### Category

Foundational definitions of (pre)categories, isomorphisms, and related structure used throughout the library.

This module sets up the core categorical vocabulary on which the rest of the library builds: precategories with associative composition and identities, the univalence axiom upgrading a precategory to a (univalent) category, and the standard menagerie of morphism classes — monomorphisms, split monos, and isomorphisms — packaged as records that can be extended. Univalence is expressed via `idtoiso` being an `Equiv`, which gives `isotoid` together with transport-along-iso lemmas that let path-based reasoning be replaced by isomorphism-based reasoning. A `Structure Identity Principle` (`SIP`) and a free-category construction over a `Graph` round out the toolkit, providing the standard ways to build new categories from a base category plus extra structure or from a directed graph.

#### Precategory

- **`Precat`**: Class of precategories over `Ob : \hType obj`, with hom-sets `Hom : Ob -> Ob -> \Set hom`, identity `id`, composition `o` (alias `∘`), and the unit/associativity laws `id-left`, `id-right`, `o-assoc`. Uses two universe levels (`obj >= hom`).
- **`Precat.>>`**: Diagrammatic-order composition `f >> g = g ∘ f`.
- **`Precat.op`**: The opposite precategory, swapping the direction of `Hom` and composition.
- **`Precat.idtoiso`**: Sends a path `a = b` to the corresponding identity isomorphism; the basis for univalence.
- **`SmallPrecat`**: Abbreviation for `Precat \lp` (precategories at the predicative level).

#### Maps and Morphism Classes

- **`Map`**: Record packaging a morphism `f : Hom dom cod` in some ambient `C : Precat`; coerces to its underlying `f`.
- **`Mono`**: Extends `Map` with the left-cancellation property `isMono`. The companion `Mono.comp` shows monos are closed under composition.
- **`isEpi`**: The dual right-cancellation property, stated as a plain function on a morphism.
- **`SplitMono`**: Extends `Mono` with a chosen left inverse `hinv` and proof `hinv_f : hinv ∘ f = id dom`; `isMono` is derived from these. `adjointMap` rewrites `f ∘ g = h` as `g = hinv ∘ h`.
- **`Iso`**: Extends `SplitMono` with the second inversion law `f_hinv : f ∘ hinv = id cod`. Provides:
  - **`adjointMapInv`**, **`adjointMap'`**: Variants of the adjoint rewriting in both directions.
  - **`-o_Equiv`**, **`o-_Equiv`**: Pre/post-composition with an iso is an equivalence on hom-sets.
  - **`reverse`**: The inverse iso. **`op`**: The same iso viewed in the opposite category.
- **`Iso.equals`**: Two isos with equal underlying maps are equal.
- **`Iso.levelProp`**: `Iso f` is a proposition (uniqueness of inverses).
- **`Iso.hinv-unique`**: A `SplitMono` and `Iso` sharing the same forward map share the same inverse.
- **`Iso.rightFactor`**, **`Iso.leftFactor`**: 2-out-of-3-style results: if `e2 ∘ f` is iso and `e2` is mono, then `f` is iso; dually for epis.
- **`Iso.composite`**: Isomorphisms compose to an isomorphism.
- **`idIso`**: The identity morphism as an `Iso`.
- **`oIso`**: Composition of two `Iso` values, packaging both forward and inverse legs.

#### Categories (Univalent Precategories)

- **`Cat`**: Extends `Precat` with `univalence`: `idtoiso : a = b -> Iso a b` is an `Equiv`.
- **`Cat.op`**: Univalence is preserved by taking the opposite category.
- **`Cat.isotoid`**: The inverse of `idtoiso`, turning an iso into a path.
- **`Cat.transport_iso`**: Transporting `id` along `isotoid e` recovers `e`.
- **`Cat.univalenceToTransport`**: Repackages univalence as a transport-form statement, useful for builders.
- **`Cat.transport_Hom`**, **`transport_Hom-left`**, **`transport_Hom-right`**: Compute transport of a hom along base-object paths via the corresponding identity transports.
- **`Cat.transport_Hom_iso`**, **`transport_Hom_iso-left`**, **`transport_Hom_iso-right`**: Iso-flavored versions, replacing a path with `isotoid e` and reducing to commuting squares involving `e.f`.
- **`Cat.univalenceFromEquiv`**: Builds univalence from any equivalence `(a = b) ≃ Iso a b` that sends `idp` to `id`.
- **`Cat.makeUnivalence`**: Builds univalence from a transport-form witness for every iso.
- **`SmallCat`**: Abbreviation `Cat \lp`.

#### Structure Identity Principle

- **`SIP`**: Given a univalent category `C`, a structure type-family `Str`, a notion of structure-preserving morphism `isHom`, and a "rigidity" condition on the identity, transports a structured iso `(e, S1, S2, hom-data)` to a path of objects together with a path of structures over it. Used to derive univalence for categories of structured objects.

#### Discrete and Free Categories

- **`DiscretePrecat`**: For a type `X`, the precategory whose hom from `x` to `y` is `Trunc0 (x = y)` (set-truncated paths) — i.e., the propositional-equality groupoid as a precategory.
- **`DiscretePrecat.map`**: Lifts a function `X -> D` (for `D` a precategory) to a functorial action on discrete homs.
- **`Graph`**: Class of directed graphs: a vertex set `V` and edge family `E : V -> V -> \Set`.
- **`Graph.Paths`**: Inductive type of finite paths between vertices, with constructors `empty` (a vertex equality) and `cons` (prepend an edge).
- **`Graph.concat`**: Concatenation of paths.
- **`Graph.concat_idp`**, **`Graph.concat-assoc`**: Right unit and associativity of concatenation.
- **`Graph.FreeCat`**: The free (univalent) category on a graph, with `Hom = Paths`, identity `empty idp`, and composition `concat`.
- **`TrivialCat`**: The terminal category, built as `Graph.FreeCat` over the singleton graph with no edges.
