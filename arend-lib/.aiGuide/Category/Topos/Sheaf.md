### Category.Topos.Sheaf

Presheaves and sheaves on sites, valued in arbitrary cocomplete categories or in `Set`.

A `VPresheaf` is a contravariant functor from a small category `C` to a category `D`; `Presheaf` is the special case `D = Set`. A `VSheaf` adds the sheaf condition: for every covering sieve `s` of an object `x`, the diagram `Comp F s.diagram.op` is a limit (with cone `Cone.map F s.cone`). The module sets up the categories of (V)presheaves and (V)sheaves as full subcategories of functor categories, and provides the standard tools for working with sheaves — direct image along site morphisms, transfer through (co)limit-preserving/reflecting functors, equivalent characterizations on sites with a basis (matching families and equalizer presentations), and extension of sheaves from a frame presentation `framePresSite P` to its presented frame `PresentedFrame P`.

#### Presheaves

- **`VPresheaf`**: Record of a contravariant functor `F : C.op -> D` from a small category `C` to a `Cat (\suc \lp)` `D`. Coerces to `F`.
- **`Presheaf`**: A `VPresheaf` with `D := SetCat`.
- **`VPresheafCat`**: The category of `VPresheaf D C`, built as a full subcategory of `FunctorCat C.op D` via an `Embedding`.
- **`PresheafCat`**: `VPresheafCat SetCat C` — the category of presheaves of sets on `C`.

#### Sheaves

- **`VSheaf`**: Class extending `VPresheaf`, with `C : Site`. Adds `isSheaf : isCover x s -> Limit { | Cone => Cone.map F s.cone }` requiring `F` to send every covering sieve to a limit cone.
- **`Sheaf`**: Class extending both `Presheaf` and `VSheaf` — set-valued sheaves on a site.
- **`VSheafCat`**: Category of `VSheaf D C` as a full subcategory of `VPresheafCat D C`.
- **`SheafCat`**: `VSheafCat SetCat C`.

#### Restriction and Direct Image

- **`VSheaf.restrict`**: Restricts a sheaf on a locale `L` to a sheaf on `L.restrict a` by composing with the restriction functor.
- **`VSheaf.direct_image`**: Pushforward of a sheaf along a `SiteWithBasisPrehom f : C -> C'` — yields `Comp S f.op` as a sheaf on `C`.
- **`VSheaf.direct_image_framePres`**: Direct image along a `FramePresPrehom`, producing a sheaf on `framePresSite P` from one on `framePresSite P'`.
- **`VSheaf.direct_image_locale`**: Direct image along a `FrameHom L' -> L`, producing a sheaf on `L'` from one on `L`.
- **`VSheaf.direct_image_locale.map`**: Functoriality on natural transformations: lifts `a : NatTrans S S'` to `NatTrans (direct_image_locale f S) (direct_image_locale f S')`.
- **`VSheaf.direct_image_id`**: Direct image along the identity locale map is the identity sheaf.
- **`VSheaf.transport_direct_image`**: Transporting a sheaf along a path `p : L = L'` of locales equals taking the direct image along `Iso.f {Precat.idtoiso p}`.
- **`VSheaf.transport_direct_image-iso`**: Combined with univalence: transport along `Cat.isotoid e` of an iso `e` of locales equals direct image along `e.f`.

#### Transfer Along Functors

- **`sheaf-preserve`**: If `G : D -> E` preserves all relevant limits, then `Comp G F` is a sheaf whenever `F : VSheaf D C` is.
- **`sheaf-reflect`**: If `G` reflects all relevant limits and `Comp G F` is an `E`-valued sheaf, then `F` itself is a `D`-valued sheaf.

#### Sites with a Basis

- **`vsheafOnSiteWithBasis`**: Builds a `VSheaf D C` on a `SiteWithBasis` from a functor `F : C.op -> D` whose `matchingFamily` map is an equivalence on every basic cover.
- **`vsheafOnSiteWithBasis.MatchingFamily`**: Record of compatible families `family j : Hom z (F (g j).1)` over a basic cover `g : J -> SlicePrecat x`, satisfying `F.Func pbProj1 ∘ family j = F.Func pbProj2 ∘ family j'` on all pullbacks.
- **`vsheafOnSiteWithBasis.matchingFamily`**: The canonical map sending `h : Hom z (F x)` to its matching family `Func (g j).2 ∘ h`.
- **`vsheafOnSiteWithBasis.cone-isMatching`**: Any cone over `Comp F s.diagram.op` satisfies the matching-family condition on pullbacks.
- **`vsheafMatchingFamily`**: Conversely, every `VSheaf D C` on a `SiteWithBasis` has the matching-family map as an equivalence on basic covers.
- **`vsheafOnSiteWithBasis-equalizer`**: Equalizer presentation of the sheaf condition for `D` complete: `F` is a sheaf iff `F x` is the equalizer of `leftMap`/`rightMap` between products `∏ F (g j).1` and `∏ F (pullback (g j₁).2 (g j₂).2)`. Provides `product1`, `product2`, `leftMap`, `rightMap`, `eqMap` as the components of the equalizer diagram.
- **`sheafOnSiteWithBasis`**: Set-valued analogue of `vsheafOnSiteWithBasis` — promotes `F : C.op -> Set` to a `Sheaf C` from the equivalence of `matchingFamily x g`.
- **`sheafOnSiteWithBasis.MatchingFamily`** / **`matchingFamily`**: Set-valued matching family record and canonical map.
- **`sheafOnSiteWithBasis.piEquiv`**: Lifts an `Equiv {A} {B}` to `Equiv {Z -> A} {Z -> B}` pointwise, used in the construction.

#### Extension to Presented Frames

- **`sheafOnPresentedFrame`**: Extends a sheaf `F` on `framePresSite P` to a sheaf on the presented frame `PresentedFrame P`, valued in any complete `D`. The extension functor is built as a limit over the diagram of generators below each element.
- **`sheafOnPresentedFrame.genSieve`**: For `a : P` and `g : J -> P`, the sieve on `a` in `framePresPreorder P` consisting of all `b` with `Cover1 b (g j)` for some `j`.
- **`sheafOnPresentedFrame.cover1-lem`**: If `Cover1 a (g j)` for some `j`, then `F` already satisfies the limit condition on `genSieve a g`.
- **`sheafOnPresentedFrame.cover-lem`** / **`cover'-lem`**: Limit conditions on `genSieve` for elements with `Cover a g` / `Cover' a g`, used to verify the sheaf axiom on the extended functor.
- **`sheafOnPresentedFrame.extend`**: The extended functor `Precat.op {PresentedFrame P} -> D`, sending `b` to the limit of `F` over generators `x` with `embed x <= b` and morphisms induced by inclusions.
- **`sheafOnPresentedFrame.extend.limFunctor`** / **`lim`** / **`cone`**: The diagram, its limit object, and the cone induced by an inequality `b <= a` used to define `extend.Func`.
- **`sheafOnPresentedFrame.extend-proj`**: The structural projection `extend (embed y) -> F y`.
- **`sheafOnPresentedFrame.extend-proj-nat''`**, **`coneMap-nat`**, **`extend-proj-nat'`**, **`extend-proj-nat`**: Naturality lemmas relating `coneMap`, `extend.Func`, `F.Func`, and `extend-proj` along inclusions and covers — used to verify functoriality and the sheaf condition for `extend`.
