### Homotopy.EckmannHilton

The Eckmann–Hilton argument: two unital binary operations satisfying the interchange law on a single set must coincide and be commutative.

This module provides both an abstract algebraic version and a concrete homotopical application. The class `Algebraic-Eckmann-Hilton` axiomatizes the classical algebraic statement — given two unital operations `o` and `#` on a set `X` related by the interchange law `(a # b) o (c # d) = (a o c) # (b o d)`, the two units agree, the operations coincide, and the resulting single operation is commutative and associative. The class `Omega^2-Commutative` instantiates this pattern in homotopy theory: the two ways of horizontally composing 2-paths (vertical-then-horizontal vs horizontal-then-vertical, via left/right whiskering) play the role of `o` and `#`, yielding commutativity of the second loop space `Ω²X`.

#### Algebraic Eckmann–Hilton

- **`Algebraic-Eckmann-Hilton`**: Class on a type `X` with two binary operations `o` and `#`, each with two-sided units (`id_o`, `id_#`), connected by the interchange law `rel : (a # b) o (c # d) = (a o c) # (b o d)`. Captures the abstract setup needed for the Eckmann–Hilton argument.
- **`units-coincide`**: `id_o = id_#` — the two units agree, derived from interchange and unit laws.
- **`binop_rels_1`**: `a o b = b # a` — relates the two operations via swap.
- **`binop_rels_2`**: `b # a = b o a` — the converse relation.
- **`comm`**: `a o b = b o a` — commutativity of `o`, obtained by chaining the two binop relations.
- **`binops_coincide`**: `a o b = a # b` — the two operations are equal.
- **`comm-#`**: `a # b = b # a` — commutativity of `#`.
- **`rel-o`**: `(a o b) o (c o d) = (a o c) o (b o d)` — interchange law restated for `o` alone.
- **`assoc`**: `(a o b) o c = a o (b o c)` — associativity of `o`, derived from `rel-o` plus units.

#### Whiskering of 2-Paths

- **`RightHorizontalWhiskering`**: Given `alp : p = q` between paths `p, q : a = b` and `r : b = c`, produces `p *> r = q *> r` by whiskering on the right.
- **`RightHorizontalWhiskering-relation`**: Right-whiskering by `idp` is the identity on `alp`.
- **`LeftHorizontalWhiskering`**: Given `q : a = b` and `bet : r = s` between paths `r, s : b = c`, produces `q *> r = q *> s` by whiskering on the left.

#### Commutativity of Ω²

- **`Omega^2-Commutative`**: Class over a pointed type `X` providing the Eckmann–Hilton argument for the second loop space.
- **`Omega^2_X`**: Abbreviation for `Omega^ 2 X`, the second loop space of `X`.
- **`LeftHorizontalWhiskering-relation`**: Computes `LeftHorizontalWhiskering idp bet` as `idp_*> r *> bet *> inv (idp_*> s)`, identifying left-whiskering on the trivial path with conjugation by the left-unit isomorphism.
- **`st1`**: First star-product of 2-cells: right-whisker `alp` then left-whisker `bet`, giving `p *> r = q *> s`.
- **`st2`**: Second star-product of 2-cells: left-whisker `bet` then right-whisker `alp`, giving `p *> r = q *> s`.
- **`Relation`**: `alp st1 bet = alp st2 bet` — the two star-products agree (proved by path induction on both arguments).
- **`Commutative`**: For 2-loops `alp bet : Omega^2_X`, `alp *> bet = bet *> alp`. The proof identifies `st1` with `alp *> bet` (`Relation1`), `st2` with `bet *> alp` (`Relation2`), and combines them via `Relation` (`Relation3`).
