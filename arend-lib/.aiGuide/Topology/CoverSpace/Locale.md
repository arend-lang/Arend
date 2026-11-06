### Topology.CoverSpace.Locale

Constructs the adjunction between cover spaces and locales, presenting a cover space's locale of opens via a frame presentation and recovering a cover space from the points of a locale.

This module formalizes the pointful/pointfree correspondence in constructive topology: every precover space `X` gives rise to a locale `CoverSpaceLocale X` whose frame is the `SiteLocale` of a preorder site on subsets of `X` whose basic covers are cauchy covers and strongly-below families, while every locale `L` yields a precover space `LocalePrecoverSpace L` on its complete filters (points). The site uses three kinds of basic covers — top covered by a cauchy family, any open covered by sets strongly below it, and bottom covered by anything — which together force the resulting frame to be regular. The locale-to-space construction is a functor `LocaleCat → PrecoverSpaceCat` with a unit `X → LocaleCoverSpace (CoverSpaceLocale X)` that is a weakly dense embedding when `X` has weakly dense points, providing the pieces of an adjunction between regular locales and strongly regular cover spaces.

#### Cover Space to Locale

- **`CoverSpaceLocale`**: The locale associated to a precover space `X`, defined as the `SiteLocale` of `CoverSpaceLocale.site`, the preorder site on `Set X` whose basic covers are cauchy covers of `U` (intersected with `U`), the family of sets strongly below `U`, and any family covering an empty `U`.
- **`CoverSpaceLocale.cover-cauchy`**: Any cauchy cover is a site cover of `top`.
- **`cover-reg`**: Any open `U` is covered in the site by all `V` with `V s<=< U` (regularity at the site level).
- **`cover-empty`**: An empty subset is covered in the site by any family.
- **`embed_<=<`**: The embedding `Set X → CoverSpaceLocale X` preserves the strongly-below relation.
- **`regular`**: `CoverSpaceLocale X` is a regular locale.
- **`cover-char`**: From a site cover `Cover U C` and `U' s<=< U`, extracts a cauchy cover consisting of `Compl U'` together with the members of `C`.
- **`cover-point-char`**: If `single x <=< U` and `U` is covered by `C`, then some `V` in `C` has `single x <=< V`.
- **`CoverSpaceLocale.hasWeaklyDensePoints`**: Weakly dense points of `X` give dense points of `CoverSpaceLocale X`.
- **`cover-inh`**, **`cover-reg-inh`**: Under dense points, `U` is covered in the site by the constant family on `U` provided some point lies in `U` (resp. some point strongly below `U`).
- **`hasStronglyDensePoints`**: Dense points of `X` give strongly dense points of `CoverSpaceLocale X`.

#### Locale to Cover Space

- **`LocalePrecoverSpace`**: The precover space on `CompleteFilter L` whose cauchy covers are families `C` with `top ≤ SJoin points_* C`, i.e. the open sets `points_* a` jointly cover `top`.
- **`points_<=<`**: The map `points^*` sends locale `<=<` to cover-space `s<=<`.
- **`LocalePrecoverSpace.cover-cauchy`**: A locale-theoretic cover `top ≤ L.Join C` translates to a cauchy cover by the sets `points^* a` for `a ∈ C`.
- **`single_<=<-char`**: Characterizes `single x <=< U` by the existence of `a` with `x a` and `points^* a ⊆ U`.
- **`LocalePrecoverSpace.hasWeaklyDensePoints`**: Locale-level dense points yield weakly dense points of the associated precover space.
- **`hasDensePoints`**: Strongly dense locale points yield dense points of the associated precover space.
- **`LocaleCoverSpace`**: From a regular locale `L`, builds a `StronglyRegularCoverSpace` whose underlying precover space is `LocalePrecoverSpace L`.

#### Functoriality and Fully Faithfulness

- **`LocaleCoverSpaceFunctor`**: The functor `LocaleCat → PrecoverSpaceCat` sending `L` to `LocalePrecoverSpace L` and a frame homomorphism to its filter-pullback as a `PrecoverMap`.
- **`LocaleCoverSpaceFunctor-fullyFaithful`**: When the source has dense points and the target is a regular locale, the action of the functor on hom-sets is an equivalence; injectivity uses a regularity argument and surjectivity reconstructs a frame homomorphism via `frameHom`.
- **`frameHom`**: Given a `PrecoverMap` between locale-induced cover spaces, builds the corresponding `FrameHom M L` by `a ↦ ⋁_{b <=< a} points_* (f ^-1 points^* b)`.

#### Unit of the Adjunction

- **`CoverSpaceLocale-unit`**: For a strongly regular cover space `X` with weakly dense points, the canonical `PrecoverMap` from `X` into `LocaleCoverSpace (CoverSpaceLocale X)`, sending `x` to the site point determined by `U ↦ single x <=< U`.
- **`CoverSpaceLocale-unit.isDense`**: The unit is weakly dense.
- **`CoverSpaceLocale-unit.isEmbedding`**: The unit is an embedding of cover spaces.
- **`CoverSpaceLocale-unit.isDenseEmbedding`**: Combines the above into a weakly dense embedding.

#### Completeness

- **`LocaleCompleteCoverSpace`**: Strengthens `LocaleCoverSpace` on a regular locale to a `StronglyCompleteCoverSpace`, witnessing that locales give complete and separated cover spaces.
- **`LocaleCompleteCoverSpace.makeFilter`**: From a weakly cauchy filter on `LocaleCoverSpace Lr`, constructs a `CompleteFilter L` by `a ↦ ∃ b, b <=< a ∧ F (points^* b)`, which serves as the limit point used to establish strong completeness.
