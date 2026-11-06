### Category.Topos.Sheaf.SiteHom

- **`SitePrehom`**: A functor between sites, extending `Functor`, that preserves covers: `Func-cover` says it sends covering presieves `P` of `x` to presieves whose pushforward `Presieve.pushforward` covers `F x`.
- **`SitePrehom.Func-covering`**: The stronger form of `Func-cover`: it sends every `Covering x P` (the closure of the covers under the covering rules) to a `Covering` of `F x` by the pushforward of `P`.
- **`SiteHom`**: A morphism of sites: a `SitePrehom` which is flat in the sense that `Func-flat-terminal` (every object of `D` is covered by objects with a map to some `F w`) and `Func-flat-pullback` (a commuting square of maps `u -> F y1`, `u -> F y2` over `Func p1`, `Func p2` is covered by squares that come from commuting squares over `p1`, `p2` in `C`).
- **`SiteHom.id`**: The identity morphism of a site.
- **`SiteDensePrehom`**: A `SitePrehom` which is dense: `Func-dense-image` (every object of `D` is covered by objects isomorphic to some `F x`) and `Func-dense-map` (every map `F a -> F b` is covered, over `a`, by maps coming from `C`).
- **`SiteDensePrehom.Func-dense-map-image`**: A map `a -> F b` from any `a : D` is, on a cover of `a` by objects isomorphic to some `F x`, the image under `Func` of a map `x -> b`.
- **`SiteDensePrehom.Func-dense-map2-image`**: The same for two maps `a -> F b` and `a -> F b'` simultaneously, on a common cover.
- **`SiteDenseHom`**: A dense morphism of sites: both a `SiteDensePrehom` and a `SiteHom`, with `Func-dense-cover` (reflecting covers: if the pushforward of `P` covers `F x`, then `P` covers `x`) and `Func-dense-equal` (maps with equal images are equal on a cover). The flatness fields of `SiteHom` are derived from the density fields.
