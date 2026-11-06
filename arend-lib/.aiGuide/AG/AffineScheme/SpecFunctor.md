### AG.AffineScheme.SpecFunctor

The functor `Spec` from the opposite of the category of commutative rings into saturated scheme sites, shown to be fully faithful, and its reflectivity into locally ringed locales.

For a commutative ring `R`, the scheme site `Spec.satSite R` (from `AG.AffineScheme`) is the saturation of the basic-open site of `R`. This module makes `R ↦ Spec.satSite R` a functor `CRingCat.op -> SatSchemeSitePrecat` and proves that it is fully faithful: scheme-site morphisms into `Spec.site R` correspond to ring homomorphisms `R -> S`. Composing with the fully faithful functor from saturated scheme sites to locally ringed locales (`SchemeSiteFunctor`) gives `SpecLRLFunctor`, which exhibits `CRingCat.op` as a reflective subcategory of locally ringed locales: the reflector sends a locally ringed locale to its ring of global sections, so `Spec` is right adjoint to global sections.

#### Spec as a functor on rings

- **`SpecFunctor`**: The fully faithful functor `CRingCat.op -> SatSchemeSitePrecat` sending a ring `R` to `Spec.satSite R` and a ring homomorphism to the induced morphism of saturated scheme sites (via `satFunc` of `SpecFunctor.siteHom`).

#### Spec as a reflective subcategory

- **`SpecLRLFunctor`**: The reflective embedding of `CRingCat.op` into `LocallyRingedLocaleCat`, the composite of `SpecFunctor` with `SchemeSiteFunctor`. The reflector is `X ↦ X.GlobalSections` with unit `specUnit`; morphisms from a locally ringed locale `X` into `Spec R` correspond to ring homomorphisms `R -> X.GlobalSections`.
