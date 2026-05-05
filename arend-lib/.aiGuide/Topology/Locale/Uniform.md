### Topology.Locale.Uniform

Uniform locales: locales equipped with a system of uniform covers, the strong-neighbourhood relation `<=u`, and the completion construction making every uniform locale a reflective subcategory of complete uniform locales.

#### Star Operations

- **`star`**: For `x : L` and a predicate `U : L -> \Prop`, the join `⋁ { y | U y ∧ IsPositive (x ∧ y) }` — the union of `U`-elements that meet `x` positively. The fundamental construction underlying uniform covers.
- **`star.star_<=`**: Monotonicity of `star` in its first argument: `x <= y` implies `star x U <= star y U`.
- **`star.star-refl`**: For a uniform cover `U`, every point sits below its star: `x <= star x U`.
- **`nucleus-star`**: Variant of `star` taking a nucleus `j` instead of an element, joining over `U`-elements positive in the localized frame `j.locale`.
- **`nucleus-star.star_open`**: Compatibility with the open nucleus: `nucleus-star (open a) U = star a U`.

#### Preuniform Locales

- **`PreuniformLocale`**: Class extending `Locale` with overtness and a predicate `isUniform` on covers, satisfying covering, downward-closure, top, binary intersection, monotonicity, and the star-refinement axiom (every uniform cover has a uniform refinement `V` with `V x ⇒ U (star x V)`).
- **`PreuniformLocale.dClosure`**: Helper for proving downset-closure of images: if some `z : L` with `U z` lies below `f z` from above `y`, the same holds for any `x <= y`.

#### Uniform Morphisms

- **`UniformHom`**: Class extending `FrameHom` between preuniform locales, requiring that the downset-closure of the image of a uniform cover is uniform (`func-uniform`).
- **`UniformEmbedding`**: Class extending `UniformHom` with surjectivity (`isEmbedding`) and the property that every uniform cover of the codomain is refined by the image of a uniform cover of the domain.
- **`UniformEmbedding.comp`**: Composition of uniform embeddings.

#### The Strong-Neighbourhood Relation

- **`<=u`**: The "really inside" relation `a <=u b ⇔ ∃ U uniform, star a U <= b`. The auxiliary order driving completion and admissibility.
- **`<=u.func-<=u`**: Uniform morphisms preserve `<=u`.
- **`<=u.adjoint`**: Adjoint form: `a <=u f.direct b` implies `f a <=u b`.
- **`<=u.dense`**: Density/interpolation: `a <=u c` factors as `a <=u b <=u c`.
- **`<=u.dense.meet_star-comm`**: Symmetry of star-meets in overt locales: `IsPositive (a ∧ star b U) ⇒ IsPositive (b ∧ star a U)`.
- **`<=u.uniform-refine`**: The cover `{ b | ∃ a, U a ∧ b <=u a }` is uniform when `U` is.
- **`<=u-trans-left`**, **`<=u-trans-right`**: Transitivity of `<=u` against `<=` on either side.
- **`<=u_meet`**: `<=u` is preserved by binary meets.
- **`<=u_<=`**: `<=u` refines `<=`: `a <=u b` implies `a <= b`.

#### Uniform Locales

- **`UniformLocale`**: Class extending `PreuniformLocale` with admissibility — every element is the join of its `<=u`-predecessors.
- **`UniformLocale.star_wclosure`**: `nucleus-star` is invariant under taking the weakly closed image of the nucleus map.
- **`UniformLocale.top>=star`**: For overt nuclei, `open (nucleus-star j U) <= j` whenever `U` is uniform.
- **`UniformLocale.wclosure>=nucleus-star`**, **`UniformLocale.wclosure>=star`**: The weakly-closed image dominates the (nucleus-)star of a uniform cover.
- **`uniform=>wregular`**: Every uniform locale is weakly regular.

#### Categorical Structure

- **`PreuniformCat`**: The category of preuniform locales and uniform morphisms.
- **`PreuniformCat.uniform-lem`**: Reduces uniformity of a downward-closed `U` to uniformity of its upward-closure.
- **`UniformCat`**: The full subcategory of `PreuniformCat` on uniform locales.

#### Completion: Frame Presentation

- **`CompletionPres`**: The frame presentation underlying the completion of a uniform locale `L`: generators `L`, conjunction = meet, with four families of basic covers (top, positivity, uniform-cover joins, and `<=u`-decomposition).
- **`CompletionPres.<=_cover`**: `a <= b` lifts to a basic cover `Cover1 a b`.
- **`CompletionLocale`**: The frame presented by `CompletionPres L` — the underlying locale of the completion.
- **`completionLocale`**: The canonical frame map `L -> CompletionLocale L` from the reflective adjunction; the underlying morphism of the completion embedding.
- **`completionLocale.presentation`**: The `FramePresHom` from `CompletionPres L` to `L` exhibiting `completionLocale` via the universal property.
- **`completionLocale.sdense`**: `completionLocale` is strongly dense.
- **`completionLocale.completion_embed`**: `completionLocale (embed a) = a` — the embedding splits the projection on generators.

#### Completion: Uniform Locale Structure

- **`Completion`**: The completion of a uniform locale `L` as a uniform locale, with uniform covers, admissibility, and overtness inherited from `L`.
- **`Completion.make-covering`**: Builds a uniform cover `V` of `Completion L` from a uniform cover `U` of `L` such that `star (embed a) V <= embed (star a U)` — the key compatibility for uniformity transport.
- **`completion`**: The canonical uniform embedding `Completion L -> L`.
- **`completion.isMono`**: `completion` is a monomorphism: post-composition with it cancels.

#### Universal Property

- **`completion-factor`**: Universal factorization: any strongly-dense uniform embedding `f : M -> L` factors through `completion : Completion L -> L` via a unique `g : Completion L -> M` with `f ∘ g = completion`.
- **`completion-factor.presentation`**: The `FramePresHom` realizing the factoring map via direct images.
- **`completion-isComplete`**: `Completion L` is itself complete — completion is idempotent.
- **`completion-functor`**: Functoriality of completion on morphisms: lifts `f : M -> L` to `Completion M -> Completion L`.
- **`completion-functor.presentation`**: The presentation map sending each generator `a` to the closure of `{ f b | b <=u a }`.
- **`completion-natural`**: Naturality square: `completion ∘ completion-functor f = f ∘ completion`.
- **`completion-isReflector`**: Reflection theorem: for complete `M`, post-composition with `completion` is an equivalence `UniformHom M (Completion L) ≃ UniformHom M L`, exhibiting complete uniform locales as a reflective subcategory.
