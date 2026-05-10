### Algebra.Ring.QPoly

Quotient construction of polynomials over a pointed additive structure as arrays modulo trailing-zero equivalence.

This module defines `QPoly R` — the type of polynomials over an `AddPointed` `R` — as the quotient of finite arrays of coefficients by the relation that pads with trailing zeros. The equivalence relation `~` allows extending a coefficient list by any number of zeros on the right, identifying lists like `[a, b]` and `[a, b, 0, 0]` so that the resulting quotient does not distinguish polynomials by spurious leading zeros. The construction provides the underlying carrier on which ring operations and degree-related lemmas can later be defined, with explicit characterizations of when quotient elements are equal, decomposed, or trivial.

#### Main Type

- **`QPoly`**: `Quotient {Array R} (__ = __ ++ 0 :: nil)` — polynomials over `R : AddPointed`, defined as arrays quotiented by appending a single trailing zero.

#### Equivalence Relation

- **`~`**: Symmetric trailing-zero relation on arrays, with two constructors:
  - **`eq-left`**: `l ++ replicate n zro = l'` — `l'` extends `l` by `n` zeros.
  - **`eq-right`**: `l' ++ replicate (suc n) zro = l` — `l` extends `l'` by `suc n` zeros.
- **`~.lists-lem1`**: Two zero-padding lengths producing the same list must be equal (uniqueness of pad length).
- **`~.lists-lem2`**: One list cannot be both a left- and right-extension of the other in nontrivial ways.
- **`~.levelProp`**: `~` is a proposition (any two proofs `x ~ y` are equal), making it suitable for use in quotient constructions.

#### Equivalence Structure

- **`~-isEquiv`**: Witnesses that `~` is an `Equivalence` on `Array R` (reflexive, symmetric, transitive).
- **`~-isEquiv.symm`**: Symmetry of `~`.
- **`~-isEquiv.lists-lem1`**: If `x ++ replicate n zro = z ++ replicate m zro` with `m <= n`, then `x ~ z`.
- **`~-isEquiv.lists-lem2`**: Two arrays both obtainable from `y` by zero-padding are related by `~`.

#### Bridging Quotient Equality and `~`

- **`from=to~`**: From `in~ l = in~ l'` in `QPoly R`, extracts `l ~ l'`.
- **`from~to=`**: From `l ~ l'`, produces `in~ l = in~ l'` in `QPoly R`.

#### Cons-style Construction and Decomposition

- **`qadd`**: Prepends a coefficient `e : R` to a polynomial `p : QPoly R`, yielding `in~ (e :: l)` and respecting the quotient.
- **`nil_cons`**: If `in~ nil = in~ (e :: l)` in `QPoly R`, then `e = 0` and `in~ nil = in~ l` (head must vanish, tail still represents zero).
- **`qadd_nil`**: If `qadd p e = in~ nil`, then `p = in~ nil` and `e = 0` (a `qadd` is zero only when both arguments are zero).
- **`qadd_cons`**: If `qadd p e = in~ (a :: l')`, then `e = a` and `p = in~ l'` (head and tail of `qadd` are uniquely determined modulo the quotient).
