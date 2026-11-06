### Category.Topos.Sheaf.Site

Presieves, sites and coverings: the covering data underlying sheaf theory.

A `Presieve` on an object `x` is a set of morphisms into `x` (a subset of `ObOver x`). A `Site` equips a category with a predicate `isCover` selecting covering presieves, stable under pullback along arbitrary morphisms. `Covering` is the closure of `isCover` under retracts and composition of covers, and is the notion of cover used to state the sheaf condition.

#### Presieves

- **`Presieve`**: The type of presieves on `x : C`: sets of pairs `(y, f)` with `f : Hom y x`, i.e. `Set (ObOver x)`.

#### Sites

- **`Site`**: A `Precat` together with a predicate `isCover x : Presieve x -> \Prop` satisfying `cover-stable`: for every `h : Hom x y` and covering presieve `S` of `y` there is a covering presieve `T` of `x` each of whose members factors through a member of `S` compatibly with `h`.
- **`Site.IsCoverFam`**: A family `f : J -> ObOver x` is a covering family, i.e. its set of members is a covering presieve of `x`.
- **`Site.isCover_IsCoverFam`**: A presieve `P` is covering if and only if the family of its members (indexed by `Given P`) is a covering family.

#### Coverings

- **`Covering`**: Propositional inductive closure of `isCover`, for a presieve `U` of `x`: either `U` contains a retraction `p : Hom y x` of some section `s : Hom x y` (`covering-inj`), or there is a covering presieve `T` of `x` such that, for each `y` in `T`, the morphisms into `y.1` that factor through `U` form a `Covering` of `y.1` (`covering-trans`).
