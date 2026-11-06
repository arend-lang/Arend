### Equiv

Equivalences between types, expressed as functions equipped with sections and retractions.

This module formalizes the standard hierarchy of "invertibility-like" structures on a function `f : A -> B`: a `Section` provides a left inverse `ret`, a `Retraction` provides a right inverse `sec`, and a `QEquiv` is the strict variant extending both where the section and retraction coincide (`sec = ret`). Each notion is a record extending `TypeMap`, and the file proves `Section` and `Retraction` are propositions in suitable situations. Equivalence-hood is packaged as the proposition `IsEquiv f` (the truncation of `QEquiv f`), and `Equiv A B` is the type of equivalences built from it. The module also develops the standard package of constructions — identity, symmetry, composition, two-out-of-three, action on Π/Σ types, path equivalences — together with `Embedding`s and their relation to surjections.

#### Basic Records

- **`TypeMap`**: A wrapped function `f : A -> B` between `\Cat`-objects, used as the common base for all equivalence-like records (with `f` as a coercion).
- **`Section`**: A function with a left inverse: fields `ret : B -> A` and `ret_f : ret (f x) = x`. The `Section~Fiber` lemma identifies `Section f` with the fiber of precomposition `(-o f)` over `id`. `levelProp` shows `Section f` is a proposition once `f` has a retraction.
- **`Retraction`**: Dual to `Section`: fields `sec : B -> A` and `f_sec : f (sec y) = y`. `Retraction~Fiber` identifies it with the fiber of postcomposition `(f `o-)` over `id`. `levelProp` shows propositionality given a section, and `isContr` shows `Retraction e` is contractible when `e` is a `QEquiv`.
- **`Equiv`**: The type of equivalences `A -> B`, a data type with constructor `mkEquiv {f} (IsEquiv f)` that coerces to the underlying function via `toFunc`. `toQEquiv` extracts a `QEquiv`, `equals` reduces equality of equivalences to equality of underlying functions, and `toFunc-embedding` shows `toFunc` is an embedding.
- **`QEquiv`**: Quasi-equivalence — a record extending both `Section` and `Retraction` with `sec => ret`, i.e. a function with a two-sided inverse. Its `where` block provides `fromInjSurj` (build one from injectivity and surjectivity on sets), `equals` (equality of quasi-equivalences between sets from equality of functions), and `leftFactor`/`rightFactor`.
- **`QEquiv.f_ret`**: The right-inverse law `f (ret y) = y` of a `QEquiv`, derived from `f_sec`.
- **`QEquiv.isInj`**: A `QEquiv` is injective: `f a = f a'` implies `a = a'`.
- **`QEquiv.isSurj`**: A `QEquiv` is surjective: `IsSurj f`.
- **`QEquiv.splitSurj`**: Constructive surjectivity of a `QEquiv`: for each `b : B` gives `a : A` with `f a = b`.
- **`QEquiv.adjoint`**: Move a `QEquiv` across an equation: from `f a = b` obtain `a = ret b`.
- **`QEquiv.adjointInv`**: Converse of `adjoint`: from `a = ret b` obtain `f a = b`.
- **`QEquiv_=`**: The path of types `A = B` over `i : I` determined by a `QEquiv` (via `iso e.f e.ret e.ret_f e.f_sec i`), i.e. univalence for a quasi-equivalence.
- **`IsEquiv`**: The proposition that `f : A -> B` is an equivalence, defined as the propositional truncation of `QEquiv f`. Its `where` block provides `toQEquiv`, `ret`/`ret_f`/`f_ret`, `isInj`, `isSurj`, `splitSurj`, `adjoint`, constructors such as `fromEmbSurj` and `fromInjSurj`, closure under `trans`, `retract`, and the factor lemmas `leftFactor`/`rightFactor`/`parallelEquiv`.

#### Standard Constructions

- **`idEquiv`**: Identity equivalence on any type.
- **`symQEquiv`**: Symmetry — swap `f` and `ret` of a `QEquiv`.
- **`transQEquiv`**: Composition of quasi-equivalences (right-associative, `\fixr 3`).
- **`transEmbedding`**: Composition of embeddings.
- **`-o_IsEquiv`**: If `f : A -> B` is an equivalence, precomposition `(-o f) : (B -> C) -> (A -> C)` is an equivalence.
- **`o-_IsEquiv`**: If `f : A -> B` is an equivalence, postcomposition `(f `o-) : (C -> A) -> (C -> B)` is an equivalence.

#### Two-Out-Of-Three

`QEquiv` and `IsEquiv` each collect the standard 2-of-3 results for equivalences in a composition `g ∘ f`.

- **`QEquiv.leftFactor`**: If `g` is a section and `g ∘ f` is a `QEquiv`, then `f` is a `QEquiv`.
- **`QEquiv.rightFactor`**: If `f` is a retraction and `g ∘ f` is a `QEquiv`, then `g` is a `QEquiv`.
- **`IsEquiv.leftFactor`**: If `g` and `g ∘ f` are equivalences, then so is `f`.
- **`IsEquiv.rightFactor`**: If `f` and `g ∘ f` are equivalences, then so is `g`.
- **`IsEquiv.parallelEquiv`**: Given a commuting square `bd ∘ ab = cd ∘ ac` with both verticals `ac`, `bd` equivalences, `IsEquiv ab <-> IsEquiv cd`.

#### Equivalences for Π, Σ and Path Types

- **`piEquiv`**: Function-extensionality as an equivalence: `(f = f') ≃ Π a, f a = f' a`.
- **`sigmaEquiv`**: Equality in a Σ-type as a Σ of an equality in the base and a transported equality in the fiber.
- **`piSigmaEquiv`**: `(Π a : A, B (h a)) ≃ Σ (g : A -> Σ a' B a') ((λ a => (g a).1) = h)` — reorganizes a dependent function as a section over `h`.
- **`piSigmaIdEquiv`**: Specialization of `piSigmaEquiv` at `h = id`.
- **`emptyEquiv`**: Any two empty types are equivalent.
- **`pathEquiv-right`**: A path `ab : a = b` induces a `QEquiv` between `a = c` and `b = c`.
- **`inv-equiv`**: Path inversion is a `QEquiv` `(a = b) ≃ (b = a)`.
- **`pathOver-transport-equiv`**: For a family `B` and a path `e : x = x'`, dependent paths `Path (λ k => B (e k)) b b'` are equivalent to `transport B e b = b'`.
- **`transport-isEquiv`**: Transport `transport F p` along any path `p : a = a'` is an equivalence.

#### Embeddings and Surjections

- **`Embedding`**: A `TypeMap` whose action `pmap f` on each path space is a retraction (so `pmap f` is itself an equivalence, exposed via `pmap-isEquiv`). `levelProp` makes it a proposition; `fromInjection` builds an embedding from injectivity into a set; `diag-equiv` exhibits the diagonal `a ↦ (a, a, idp)` as an equivalence; `projection` shows the first projection out of `Σ a, B a` with `B` valued in `\Prop` is an embedding; `fibers`/`embeddingFiber-isProp` characterize embeddings via propositional fibers.
- **`Embedding.pmap-isEquiv`**: For an embedding `f`, `pmap f : a = a' -> f a = f a'` is a `QEquiv`.
- **`Embedding.surj-split`**: For an embedding `f` and `s : IsSurj f`, the fiber over each `y : B` is inhabited (the truncation is removed, since fibers of an embedding are propositions).
- **`leftEmbedding`**: If `g` is an embedding and `g ∘ f` is an embedding, then `f` is an embedding.
- **`piEmbedding`**: A family of embeddings `B a >-> C a` induces an embedding `(Π a, B a) -> (Π a, C a)` by pointwise application.
