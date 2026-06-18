### Category.Topos.Sheaf.Sub

Criteria for showing that a presheaf embedded into a sheaf is itself a sheaf.

This module provides two convenience lemmas for constructing sub-sheaves: given a natural transformation `e : F -> G` from a presheaf `F` into a sheaf `G` that is pointwise injective, `F` inherits the sheaf condition provided every section of `G` whose local restrictions all lift to `F` itself lifts to `F`. The first lemma works with the full Grothendieck topology via `Sieve` and `isCover`, while the second specializes to a `SiteWithBasis` so users can verify the closure condition only on basic covering families. The `conv` helpers in each `\where`-block construct the actual amalgamation by lifting the cone of local sections through `F`'s sheaf-induced limit and using injectivity of `e` to identify it with the original element of `G`.

#### Sub-sheaf Constructors

- **`subSheaf`**: Given a `Presheaf F`, a `Sheaf G`, a natural transformation `e : F -> G` whose components are injective (`isEmb`), and a closure condition `cl` saying that whenever `s` covers `a` and `x : G a` has every restriction lifting to `F`, then `x` itself lifts to `F`, produces a `Sheaf` structure on `F`. Used to recognize subobjects of a sheaf as sheaves.
- **`subSheafWithBasis`**: Variant of `subSheaf` for a `SiteWithBasis`, where the closure hypothesis is required only for basic covering families `g : J -> SlicePrecat a` satisfying `isBasicCover a g`. More convenient in practice since one rarely needs to check the condition against all sieves.

#### Amalgamation Helpers

- **`subSheaf.conv`**: The core construction: given the embedding `e`, a cover `s` of `a`, an element `x : G a`, and compatible local lifts to `F` over each `f : s`, produces the amalgamated `(y : F a)` together with `e a y = x`. Builds a cone over `Comp F s.diagram.op` and uses `F`'s sheaf limit, then applies `e`'s injectivity together with `G`'s limit uniqueness to identify the amalgamation with `x`.
- **`subSheafWithBasis.conv`**: Reduces the basis-form amalgamation to `subSheaf.conv` by passing through `C.genSieve a g`, lifting each `f` in the generated sieve to a basic factor via `F.Func` and naturality of `e`.
