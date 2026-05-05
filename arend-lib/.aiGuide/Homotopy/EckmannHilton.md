### Homotopy.EckmannHilton

Algebraic and path-theoretic ingredients for the Eckmann-Hilton argument, showing that two compatible monoid structures sharing a unit must coincide and be commutative.

#### Algebraic Structure

- **`Algebraic-Eckmann-Hilton`**: Class capturing two binary operations `o` and `#` on a type `X`, each with its own unit (`id_o`, `id_#`), satisfying the interchange law `rel : (a # b) o (c # d) = (a o c) # (b o d)`. This is the abstract setting in which the Eckmann-Hilton argument applies.

#### Whiskering Operations

- **`RightHorizontalWhiskering`**: Given `alp : p = q` between paths `p q : a = b` and a path `r : b = c`, produces the 2-path `p *> r = q *> r`.
- **`RightHorizontalWhiskering-relation`**: Right whiskering by `idp` is the identity: `RightHorizontalWhiskering alp idp = alp`.
- **`LeftHorizontalWhiskering`**: Given a path `q : a = b` and `bet : r = s` between paths `r s : b = c`, produces the 2-path `q *> r = q *> s`.

#### Commutativity of the Second Loop Space

- **`Omega^2-Commutative`**: Class parameterized by a pointed type `X : HPointed`, the carrier for stating/deriving commutativity of the double loop space `Ω²X` via the Eckmann-Hilton argument.
