### Category.Topos.Sheaf.Site

Sites, sieves, and presieves — the categorical foundations for sheaf theory and Grothendieck topologies.

#### Presieves and Sieves

- **`Presieve`**: A class capturing a family of morphisms into an object `x : C`, given by `S : Hom y x -> \Prop` for each `y : C`.
- **`Presieve.idLimit`**: If a presieve `S` contains `id x`, then `Cone.map F S.cone` is a limit cone for any functor `F : C.op -> D`.
- **`Presieve.transLimit`**: Transitivity of limits along presieves: given two presieves `S1`, `S2` and limits for `S2.pullback` along every `S1`-morphism (and along all morphisms via `S1` pullbacks), produces a limit cone for `S2`.
- **`Sieve`**: Extends `Presieve` with closure under precomposition (`isSieve`): if `S f` holds then `S (f ∘ g)` holds.
- **`Sieve.map`**: Pushforward of a sieve along a functor `F : C -> D`: at `y : D` contains morphisms factoring as `F g ∘ f` with `g` in the source sieve.

#### Sites

- **`Site`**: Extends `Precat` with a coverage `isCover : (x : Ob) -> Sieve x -> \Prop` satisfying stability under pullback (`cover-stable`).
- **`SitePrehom`**: Extends `Functor` between sites, required to send covering sieves to covering sieves (`F-cover`).
- **`inducedSite`**: Pulls back a site structure along a functor `F : C -> D`: a sieve `s` on `x` covers iff there is a covering sieve `s'` on `F x` with `s h ↔ s' (F h)`.

#### Sites with a Basis

- **`SiteWithBasis`**: Extends `Site` and `PrecatWithPullbacks` with a notion of `isBasicCover` indexed by a `\Set`; the induced coverage consists of sieves containing some basic cover, with `cover-stable` derived via pullback of the indexing family.
- **`SiteWithBasisPrehom`**: Extends `Functor` between sites with bases, requiring preservation of basic covers (`F-basicCover`) and that the comparison map `F(pullback f g) -> pullback (F f) (F g)` is an iso (`F-pullback`).
