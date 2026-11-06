### Category.Topos.Sheaf.LocalPredicate

Local predicates on a presheaf over a site: prelocal predicates that can be checked on covers.

A `LocalPredicate` extends `PrelocalPredicate` with a locality condition for the covering sieves of the site: if the predicate holds for all restrictions of a section along a covering presieve, it holds for the section. The module gives a convenient form of this condition for coverings and proves that existential projection along a sheaf with unique witnesses, equality on a separated presheaf, and invertibility in a sheaf of commutative rings are local predicates.

#### Local predicates

- **`LocalPredicate`**: A `PrelocalPredicate` on a presheaf `S` over a `Site` such that for a cover `a<=U : isCover a U` of `a`, if `pred (S.F.Func y.2 x)` for all `y` in `U` then `pred x`.
- **`LocalPredicate.byCovering`**: The locality condition stated for a `Covering a U` of `a`: if a section restricts to satisfy the predicate along every member of `U`, it satisfies the predicate.

#### Examples

- **`exists-localPredicate`**: For a local predicate `P` on the product of a presheaf `S` and a sheaf `T` such that the second component is unique (`P (x, y)` and `P (x, y')` imply `y = y'`), the predicate "there exists `y : T a` with `P (x, y)`" is local on `S`.
- **`equality-localPredicate`**: For a separated presheaf `S` of sets, equality `s.1 = s.2` is a local predicate on the product of `S` with itself.
- **`inv-localPredicate`**: For a sheaf `S` of commutative rings over a site, the predicate `Monoid.Inv` (invertibility of a section) is a local predicate on the underlying presheaf.
