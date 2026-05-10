### Equiv

Equivalences between types, expressed as functions equipped with sections and retractions.

This module formalizes the standard hierarchy of "invertibility-like" structures on a function `f : A -> B`: a `Section` provides a left inverse `ret`, a `Retraction` provides a right inverse `sec`, an `Equiv` combines both (a half-adjoint equivalence), and a `QEquiv` is the strict variant where the section and retraction coincide. Each notion is a record extending `Map`, and the file proves they are propositions over a fixed `f` (so equivalence-hood is a property, not extra structure). The module also develops the standard package of constructions — identity, symmetry, composition, two-out-of-three, action on Π/Σ types, function-space currying — together with `Embedding`/`Surjection` and the equivalence between embedding-and-surjection and equivalence (`ESEquiv`).

#### Basic Records

- **`Map`**: A wrapped function `f : A -> B`, used as the common base for all equivalence-like records (with `f` as a coercion).
- **`Section`**: A function with a left inverse: fields `ret : B -> A` and `ret_f : ret (f x) = x`. The `=Fiber` lemma identifies `Section f` with the fiber of precomposition `(-o f)` over `id`. `levelProp` shows `Section f` is a proposition once `f` has a retraction.
- **`Retraction`**: Dual to `Section`: fields `sec : B -> A` and `f_sec : f (sec y) = y`. `=Fiber` identifies it with the fiber of postcomposition `(o- f)` over `id`. `levelProp` shows propositionality given a section, and `isContr` shows `Retraction e` is contractible when `e` is an `Equiv`.
- **`Equiv`**: Extends both `Section` and `Retraction`. Provides `f_ret` (the derived adjoint coherence `f (ret y) = y`), `isInj`, `isSurj`, and `adjoint`/`adjointInv` for moving across the equivalence. `levelProp` makes `Equiv f` a proposition; `equals` reduces equality of equivalences to equality of underlying functions; `fromInjSurj` builds an `Equiv` from injectivity and surjectivity on sets.
- **`QEquiv`**: Quasi-equivalence — an `Equiv` with `sec = ret`. Coerces to/from `Equiv` via `fromEquiv` (using `f_ret`) and `fromEquiv'` (using `e.sec`).

#### Standard Constructions

- **`idEquiv`**: Identity equivalence on any type.
- **`symQEquiv`**: Symmetry — swap `f` and `ret` of a `QEquiv`.
- **`transQEquiv`**: Composition of quasi-equivalences (right-associative, `\fixr 3`).
- **`transEmbedding`**: Composition of embeddings.
- **`transEquiv`**: Composition of equivalences (right-associative, `\fixr 3`).
- **`-o_Equiv`**, **`o-_Equiv`**: Precomposition `(-o e)` and postcomposition `(o- e)` by an equivalence are themselves equivalences on function spaces.

#### Two-Out-Of-Three

`\module TwoOutOfThree` collects the standard 2-of-3 results for equivalences in a composition `g ∘ f`.

- **`leftFactor`**, **`leftFactorPath`**: If `f` is a retraction and `g ∘ f` is a `QEquiv`, then `g` is a `QEquiv`.
- **`rightFactor`**, **`rightFactorPath`**: If `g` is a section and `g ∘ f` is a `QEquiv`, then `f` is a `QEquiv`.
- **`rightEmbedding`**: If `g` is an embedding and `g ∘ f` is a `QEquiv`, then `f` is an `Equiv`.
- **`parallelEquiv`**: Given a commuting square with both verticals equivalences, `Equiv ab = Equiv cd`.
- **`leftEquiv`**, **`rightEquiv`**: Pre/post-composition with an equivalence preserves the property of being an equivalence (as a type-level equality).
- **`compositeEquiv`**: If `g ∘ f` is an equivalence, then `Equiv f = Equiv g`.

#### Equivalences for Π and Σ Types

- **`piEquiv`**: Function-extensionality as an equivalence: `(f = f') ≃ Π a, f a = f' a`.
- **`sigmaEquiv`**: Equality in a Σ-type as a Σ of an equality in the base and a transported equality in the fiber.
- **`piSigmaEquiv`**: `(Π a : A, B (h a)) ≃ Σ (g : A -> Σ a' B a') ((λ a => (g a).1) = h)` — reorganizes a dependent function as a section over `h`.
- **`piSigmaIdEquiv`**: Specialization of `piSigmaEquiv` at `h = id`.
- **`emptyEquiv`**: Any two empty types are equivalent.

#### Embeddings and Surjections

- **`Embedding`**: A `Map` whose action `pmap f` on each path space is a retraction (so `pmap f` is itself an equivalence, exposed via `pmap-isEquiv`). `levelProp` makes it a proposition; `fromInjection` builds an embedding from injectivity into a set; `diag-equiv` exhibits the diagonal `a ↦ (a, a, idp)` as an equivalence; `projection` shows the first projection out of `Σ a, B a` with `B` valued in `\Prop` is an embedding; `fibers`/`embeddingFiber-isProp` characterize embeddings via propositional fibers.
- **`>->`**: Notation `A >-> B` for `Embedding {A} {B}`.
- **`Surjection`**: A `Map` with `isSurjMap : Π y, ∃ x, f x = y` (image-surjective via the propositional truncation).
- **`->>`**: Notation `A ->> B` for `Surjection {A} {B}`.
- **`ESEquiv`**: A function that is simultaneously an `Embedding`, `Surjection`, and `Equiv`; the equivalence data is built from the embedding's path retraction together with a chosen preimage from `isSurjMap`. `levelProp` makes it a proposition, and `fromEquiv` shows every `Equiv` is an `ESEquiv` — establishing that "embedding + surjection" coincides with "equivalence".
