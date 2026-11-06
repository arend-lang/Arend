### Category.Topos.Sheaf.DenseExtend

- **`SiteLocaleSheaf.extend`**: The extended functor `(SiteLocale P).op -> D`, sending `b` to the limit of `F` over the generators `x` of the site lying in `b` (the elements of `b.1`), with morphisms induced by `b <= a` via restriction along inclusions.
- **`SiteLocaleSheaf.extend.limFunctor`** / **`lim`**: The diagram over `b : Opens P` and its limit object, used to define `extend`.
- **`denseSite-FF`**: Full faithfulness of restriction along a dense morphism of sites `f : SiteDensePrehom`: if `G` is a sheaf, then precomposition with `f.op` is an equivalence `NatTrans F G -> NatTrans (Comp F f.op) (Comp G f.op)`.
- **`SiteLocaleSheaf`**: For a sheaf `F` on a preorder site `P` (`IsPreorderSheaf F`) with values in a complete category `D`, the extended functor `extend` is a `VSheaf` on the locale `SiteLocale P`.
