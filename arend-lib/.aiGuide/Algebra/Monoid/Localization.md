### Algebra.Monoid.Localization

Localization of a commutative monoid `B` at a submonoid `S`: an abstract characterization and a concrete fraction construction.

#### Abstract localization

- **`Localization`**: A record characterizing `L` with `inL : MonoidHom B L` as a localization of `B` at `S`: elements of `S` become invertible (`localization-inv`), `inL x = inL y` forces `x * s = y * s` for some `s` in `S` (`localization-inj`), and every element of `L` has the form `inL y / inL c` with `c` in `S` (`localization-surj`).
- **`Localization.inv-char`**: `inL x` is invertible iff `x * y` lies in `S` for some `y`.
- **`Localization.trivial-char`**: For an absorbing `z` (`z * x = z` for all `x`), `z` lies in `S` iff `L` is trivial (every element equals `1`) iff `inL z = 1`.
- **`Localization.isEpi`**: Two homomorphisms `L -> K` that agree on `inL a` for all `a : B` are equal.
- **`Localization.inL-inj`**: If every element of `S` is cancellable, then `inL` is injective.
- **`Localization.quot-equals`**: If `a * inL c = inL x` and `a * inL d = inL y` with `inL c`, `inL d` invertible, then `x * d * e = y * c * e` for some `e` in `S`.
- **`Localization.lift`**: The universal property: a homomorphism `f : B -> K` sending `S` to invertibles extends to a map `L -> K`, sending `a` with `a * inL c = inL x` to `f x / f c`.
- **`Localization.lift-char`**: For `a : L`, there are `y, c` with `c` in `S`, `a * inL c = inL y` and `lift f l a * f c = f y`.
- **`Localization.lift_inL-quot`**: If `a * inL c = inL x` with `c` in `S`, then `lift f l a * f c = f x`.
- **`Localization.lift-quot-char`**: If `a * inL c = inL x` with `c` in `S` and `r * f c = f x`, then `lift f l a = r`.
- **`Localization.lift_inL`**: `lift f l` extends `f`: `lift f l (inL x) = f x`.
- **`Localization.lift-inv`**: If `a * inL c = inL x` with `c` in `S` and `f x` is invertible, then `lift f l a` is invertible.
- **`Localization.liftMonoidHom`**: `lift f l` packaged as a `MonoidHom L K`.

#### Concrete construction

- **`LocType`**: The carrier of the localization `S⁻¹M`: the quotient of triples `(x, y, _ : S y)` (fractions `x / y`) by `(x, y) ~ (x', y')` iff `x * y' = x' * y`.
- **`inl~`**: The class in `LocType S` of a fraction `(x, y, S y)`.
- **`~-lequiv`**: Fractions `a`, `b` with `a.1 * b.2 * c = b.1 * a.2 * c` for some `c` in `S` have equal classes.
- **`~-lequiv1`**: Fractions with `a.1 * b.2 = b.1 * a.2` have equal classes (the case `c = 1`).
- **`~-unlequiv`**: Converse of `~-lequiv`: equal classes imply `a.1 * b.2 * c = b.1 * a.2 * c` for some `c` in `S`.
- **`LocMonoid`**: The concrete localization of `M` at `S` as a `CMonoid` on `LocType S` with componentwise multiplication of fractions; `LocMonoid.isLocalization` shows it is a `Localization` along `locM`.
- **`locM`**: The canonical homomorphism `M -> LocMonoid S`, `x ↦ x / 1`.
- **`div_loc`**: If `b | a` with `b` in `S`, then the fraction `a / b` equals `locM` of the quotient.
- **`localiaztion-inv-char`**: A fraction `a` is invertible in `LocMonoid S` iff `a.1 * c` lies in `S` for some `c` (the name is spelled this way in the source).
