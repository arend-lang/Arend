### AG.SchemeSite.SchemeSiteHom

Morphisms of scheme sites, their composition and saturation, and the adjunction-like relation between a scheme site and its saturation `SatSite`.

A `SchemeSitePrehom Dom Cod` is a relation `b <=f* a` between opens of `Dom` and `Cod` ("`b` lies over `a`") with ring maps `f# : Cod.R a -> Dom.R b`, compatible with restriction on both sides and satisfying covering conditions (the images cover `Dom`, meets are covered, and localizations are sent locally to localizations). Prehomomorphisms compose through existential relations (`SchemeSitePrehom.∘`). A `SchemeSiteHom` is a prehomomorphism whose relation `<=f*` is closed under covers (`<=f*-cover-left`); `satPrehom` turns any prehomomorphism into one by closing `<=f*` under covers, and `satPrehom.isIdempotent` and `satPrehom_o` make it compatible with composition, so scheme sites with `SchemeSiteHom` form a category (`AG.SchemeSite.SchemeSitePrecat`). Morphisms are determined by their action on a family of maximal opens (`equals-maximal`, `equals-affine`). The saturation `SatSite S` of a scheme site comes with a counit `satCounit : SatSite S -> S`, which is a monomorphism on the right (`satCounit.isMono`); morphisms from a saturated scheme site into `Y` correspond to morphisms into `SatSite Y` (`satLift-equiv`), and `satFunc` extends saturation to a functor.

#### Prehomomorphisms

- **`SchemeSitePrehom`**: A morphism between scheme sites `Dom` and `Cod`: a relation `b <=f* a` on opens (monotone, closed under `<=` in both variables), maps `f# : Cod.R a -> Dom.R b` compatible with restrictions (`f#-left`, `f#-right`), and covering conditions `<=f*-top`, `<=f*-meet`, `<=f*-loc` (opens of `Dom` are covered by those lying over some `a`, intersections lie over intersections locally, and localizations of `Cod` are sent locally to localizations of `Dom`).
- **`SchemeSitePrehom.f#_f*-left`**: Restricting `f# b<=a x` along `b' <= b` gives `f#` at the composite relation `b' <=f* a`.
- **`SchemeSitePrehom.f#_f*-right`**: `f#` applied to the restriction of `x : Cod.R a'` to `a <= a'` equals `f#` at the relation `b <=f* a'`.
- **`SchemeSitePrehom.f#-cover`**: If `b` is covered by opens lying over `a`, the maps `f#` glue (using the sheaf property) to a ring homomorphism `Cod.R a -> Dom.R b` compatible with the `f#` on the covering opens.
- **`SchemeSitePrehom.<=f*-loc-forall`**: Strengthening of `<=f*-loc`: if `x` localizes `a'` in `Cod.R a`, then locally over `b <=f* a` the opens `b'` lying over `a'` are localizations of `f# x`.
- **`SchemeSitePrehom.<=f*_Cover`**: If `b <=f* a` and `a` is covered by `U`, then `b` is covered by opens lying over members of `U`.
- **`SchemeSitePrehom.f#-cover_f#`**: When `b <=f* a` already holds, the glued map `f#-cover` agrees with `f# b<=a`.
- **`SchemeSitePrehom.id`**: The identity prehomomorphism, with `<=f*` equal to `<=` and `f#` the restriction maps.
- **`SchemeSitePrehom.∘`**: Composition of prehomomorphisms (alias of `SchemeSitePrehom.compose`): `c` lies over `a` iff some `b` has `c <=f* b` for `f` and `b <=f* a` for `g`.
- **`SchemeSitePrehom.compose.f#-coh`**: Independence of the middle element: the two composites of `f#` agree for different intermediate opens `b1` and `b2`.
- **`SchemeSitePrehom.compose.f#-coh-map`**: The same independence stated as an equality of ring homomorphisms.
- **`SchemeSitePrehom.compose.f#`**: The `f#` of the composite for a truncated witness of the relation, obtained by eliminating the truncation.
- **`SchemeSitePrehom.compose.f#-eval`**: The `f#` of a composite on a given witness `(b, c<=b, b<=a)` is `f.f# c<=b ∘ g.f# b<=a`.
- **`SchemeSitePrehom.compose.f#-evalAt`**: The evaluation of the composite's `f#` at any witness, for a truncated relation.
- **`SchemeSitePrehom.equals`**: Two prehomomorphisms with logically equivalent relations `<=f*` and equal `f#` are equal.

#### Morphisms and saturation of prehomomorphisms

- **`SchemeSiteHom`**: A prehomomorphism whose relation `<=f*` is closed under covers: if `b` is covered by opens lying over `a`, then `b <=f* a` (`<=f*-cover-left`).
- **`satPrehom`**: Closes a prehomomorphism under covers, giving a `SchemeSiteHom` with `b <=f* a` iff `b` is covered by opens lying over `a` and `f#` given by `f#-cover`.
- **`satPrehom.isIdempotent`**: Saturating a morphism that already is a `SchemeSiteHom` changes nothing.
- **`SchemeSiteHom.id`**: The identity morphism, the saturation of `SchemeSitePrehom.id`.
- **`SchemeSiteHom.∘`**: Composition of a prehomomorphism `g` after `f`, saturated: `satPrehom (g ∘ f)` (alias of `SchemeSiteHom.compose`).
- **`SchemeSiteHom.satPrehom_o-left`**: Saturating `g ∘ f` is the same as saturating `(satPrehom g) ∘ f`.
- **`SchemeSiteHom.satPrehom_o-right`**: Saturating `g ∘ f` is the same as saturating `g ∘ (satPrehom f)`.
- **`SchemeSiteHom.satPrehom_o`**: Saturating `g ∘ f` is the same as saturating `(satPrehom g) ∘ (satPrehom f)`.

#### Properties of morphisms

- **`SchemeSiteHom.<=f*-restrict`**: For `a' <= a` and `b`, `b <=f* a'` holds iff `b <=f* a` and `f# b<=a x` is invertible for a localization element `x` of `a'` in `a`.
- **`SchemeSiteHom.topElement`**: If `a0` is a greatest element of the codomain, every open `b` of the domain lies over `a0`.
- **`SchemeSiteHom.<=f*-loc-forall-sat`**: For a morphism out of a saturated scheme site, if `x` localizes `a'` in `Cod.R a` and `b <=f* a`, there is `b' <= b` over `a'` with `b'` the localization of `b` at `f# x` (no covering needed).
- **`SchemeSiteHom.unequals`**: Equal morphisms have equivalent relations `<=f*` and agree on `f#`.
- **`SchemeSiteHom.unequals_compose`**: If `h ∘ f = h ∘ g`, then for `c` over `b` over `a` (through `h`), `f` and `g` agree on elements `h.f# ba x`.
- **`SchemeSiteHom.equalsHom`**: Two morphisms are equal if their relations `<=f*` are equivalent and their `f#` agree as ring homomorphisms.
- **`SchemeSiteHom.equals`**: Two morphisms are equal if their relations `<=f*` are equivalent and their `f#` agree on every element.
- **`SchemeSiteHom.equals-maximal`**: Two morphisms are equal if they agree over a set `M` of maximal opens of the codomain (every open lies below a member of `M`).
- **`SchemeSiteHom.equals-affine`**: Two morphisms are equal if they agree over a greatest open `a0` of the codomain.
- **`SchemeSiteHom.equals-affine2`**: Two morphisms between sites with greatest elements `b0` and `a0` are equal if their ring maps `R a0 -> R b0` agree.

#### Saturation of a scheme site

- **`satCounit`**: The counit morphism `SatSite S -> S`, the saturation of `satCounit.prehom`: `(a, s)` lies over `b` iff `a <= b`, with ring map the localization map composed with restriction.
- **`satCounit.prehom`**: The prehomomorphism `SatSite S -> S` with `(a, s) <=f* b` iff `a <= b` and `f#` the restriction followed by the localization map `locR`.
- **`satCounit.isMono`**: `satCounit` is a monomorphism on the right: if two morphisms `X -> SatSite Y` are equal after composing with `satCounit`, they are equal.
- **`satLift`**: Lifts a morphism `f : X -> Y` from a saturated scheme site `X` to `SatSite Y`: `b <=f* (a, s)` iff `b <=f* a` for `f` and `f#` of `s` is invertible over `b`.
- **`satCounit_satLift`**: Composing `satLift f` with `satCounit` returns `f`.
- **`satLift-equiv`**: For `X` saturated, `SchemeSiteHom X Y` and `SchemeSiteHom X (SatSite Y)` are quasi-equivalent via `satLift`.
- **`satFunc`**: The functoriality of saturation: a morphism `X -> Y` induces `SatSite X -> SatSite Y`.
- **`satCounit_satFunc`**: Naturality of `satCounit`: `satCounit ∘ satFunc f = f ∘ satCounit`.
