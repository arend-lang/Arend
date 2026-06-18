### Topology.Locale.Uniform

Pointfree theory of uniform locales: locales equipped with a system of uniform covers, generalizing uniform spaces.

A `PreuniformLocale` enriches a locale with a predicate `isUniform` on families of opens (closed under refinement, intersection, and the star operation), where `star x U` denotes the union of `U`-elements positively meeting `x`. The "uniformly way-below" relation `a <=u b` (witnessed by some uniform `U` with `star a U <= b`) plays the role of strong inclusion, making every uniform locale weakly regular. A `UniformLocale` additionally satisfies admissibility — every element is the join of opens uniformly below it — and the module constructs a completion functor as a left adjoint along a coreflective embedding `Completion L -> L`, presented via `CompletionPres` using basic covers for top, positivity, uniform refinement, and `<=u`-decomposition.

#### Star Operation

- **`star`**: `star x U = ⋁ { y | U y ∧ IsPositive (x ∧ y) }` — the union of `U`-members that positively meet `x`.
- **`star.star_<=`**: Monotonicity of `star` in its first argument.
- **`star.star-refl`**: `x <= star x U` whenever `U` is uniform.
- **`nucleus-star`**: Variant of `star` along a nucleus `j`, taking joins of `U y` positive in the sublocale.
- **`nucleus-star.star_open`**: For an open nucleus `open a`, `nucleus-star (open a) U = star a U`.

#### Preuniform Locales

- **`PreuniformLocale`**: Class extending `Locale` with `isUniform : (E -> \Prop) -> \Prop` plus axioms: each uniform family covers, is downward closed, intersections of uniform families are uniform, refinement preserves uniformity, and every uniform `U` admits a star-refinement.
- **`PreuniformLocale.dClosure`**: Helper showing the downset closure of `∃ z, U z ∧ y <= f z` is preserved under `<=`.

#### Uniform Morphisms

- **`UniformHom`**: Record extending `FrameHom` between preuniform locales, requiring that the downset closure of the direct image of any uniform family is uniform.
- **`UniformEmbedding`**: Extension of `UniformHom` whose underlying frame map is surjective and which reflects uniformity (`isUniformEmbedding`).
- **`UniformEmbedding.direct-uniform`**: For an embedding, `\lam x. ∃ y, U y ∧ x <= direct y` is uniform.
- **`UniformEmbedding.comp`**: Composition of uniform embeddings.

#### Uniformly Way-Below Relation

- **`<=u`**: `a <=u b` iff there exists a uniform `U` with `star a U <= b`.
- **`<=u.func-<=u`**: Uniform homomorphisms preserve `<=u`.
- **`<=u.adjoint`**: `a <=u f.direct b` implies `f a <=u b` along a uniform homomorphism.
- **`<=u.dense`**: Interpolation: `a <=u c` factors as `a <=u b <=u c`.
- **`<=u.dense.meet_star-comm`**: Symmetry of positivity for `meet ∧ star` under overtness.
- **`<=u.uniform-refine`**: The `<=u`-refinement of a uniform family is uniform.
- **`<=u-trans-left`**, **`<=u-trans-right`**: Composition of `<=u` with `<=` on either side.
- **`<=u_meet`**: `<=u` is preserved by binary meets.
- **`<=u_<=`**: `a <=u b` implies `a <= b`.

#### Uniform Locales

- **`UniformLocale`**: Class extending `PreuniformLocale` with admissibility: `b <= ⋁ { a | a <=u b }`. Provides `isComplete` as being an iso to its completion.
- **`UniformLocale.star_wclosure`**: `nucleus-star j.map.wclosed-image U = nucleus-star j U` for a uniform cover.
- **`UniformLocale.top>=star`**: Under overtness, `open (nucleus-star j U) <= j`.
- **`UniformLocale.wclosure>=nucleus-star`**: Sharpening to the weak-closure image.
- **`UniformLocale.wclosure>=star`**: For an open nucleus, `open (star a U) <= wclosed-image (open a)`.
- **`uniform=>wregular`**: Every uniform locale is weakly regular.

#### Categories

- **`PreuniformCat`**: Category of preuniform locales with uniform homomorphisms.
- **`PreuniformCat.uniform-lem`**: A downward-closed `U` is uniform whenever its `<=`-closure is.
- **`UniformCat`**: Subcategory of `PreuniformCat` on uniform locales (uses `subCat`).

#### Completion

- **`CompletionPres`**: Frame presentation of the completion of `L`, with basic covers indexed by four cases (top, positivity, uniform refinement, `<=u`-decomposition).
- **`CompletionPres.<=_cover`**: Order in `L` lifts to a single cover in the presentation.
- **`CompletionLocale`**: The completion as a `PresentedFrame`.
- **`completionLocale`**: The frame map `L -> CompletionLocale L` as the adjoint of the canonical presentation.
- **`completionLocale.presentation`**: The presenting `FramePresHom` from `CompletionPres L` to `L`.
- **`completionLocale.sdense`**: The completion map is strongly dense.
- **`completionLocale.completion_embed`**: `completionLocale (embed a) = a`.
- **`Completion`**: The uniform locale structure on `CompletionLocale L`, with uniform families generated from uniform families on `L` via `embed`.
- **`Completion.make-covering`**: Constructs a uniform `V` on the completion such that `star (embed a) V <= embed (star a U)`.
- **`completion`**: The canonical `UniformEmbedding (Completion L) L`.
- **`completion.isMono`**: `completion` is a monomorphism in `UniformCat`.

#### Universal Property

- **`completion-factor`**: Any strongly dense uniform embedding `f : M -> L` factors uniquely through `completion`, yielding `g : Completion L -> M` with `f ∘ g = completion`.
- **`completion-factor.presentation`**: The presenting `FramePresHom` driving the factorization.
- **`completion-isComplete`**: `Completion L` is complete (iso to its own completion).
- **`completion-functor`**: Functorial action: a uniform map `f : M -> L` lifts to `Completion M -> Completion L`.
- **`completion-functor.presentation`**: Presents the lifted map via `closure` of `f` on `<=u`-pairs.
- **`completion-natural`**: Naturality square `completion ∘ completion-functor f = f ∘ completion`.
- **`completion-isReflector`**: For complete `M`, precomposition with `completion` is an equivalence `Hom(M, Completion L) ≃ Hom(M, L)` — completion is a reflector of `UniformCat` onto its complete objects.
