### Algebra.Solver.CSemiring

A reflective solver model for commutative semirings, extending the semiring solver with monomial sorting to exploit commutativity of multiplication.

This module reuses the term language and normal forms from `SemiringSolverModel` but interprets normal forms after sorting the variable list of each monomial. Because multiplication is commutative in a `CSemiring`, two monomials with the same variables in different orders denote the same element, so sorting puts them in a canonical form on which structural equality of normal forms suffices. The sort uses a red-black tree algorithm, and a separate lemma establishes that sorting preserves the interpretation, allowing the framework to lift `SemiringSolverModel`'s machinery — including user-supplied axiom application — to the commutative setting.

#### Solver Model

- **`CSemiringSolverModel`**: Constructs a `SolverModel R` for a commutative semiring `R : CSemiring`. Reuses `Term Nat` and `NF Nat` from the semiring solver, and interprets normal forms by first sorting each monomial's variable list via `sortMonomials`.

#### Monomial Sorting

- **`sortMonomials`**: Given a normal form `l : NF C n`, sorts the variable list of every monomial using `Sort.RedBlack.sort`. Produces a canonicalized normal form where commutativity-equivalent monomials become syntactically equal.
- **`interpretNF_sort`**: Lemma showing `interpretNF' (sortMonomials l) = interpretNF' l`, i.e., sorting variables within monomials preserves the semantic value in any commutative semiring. Justifies using the sorted form for decision procedures.

#### Axiom Application

- **`apply-axiom`**: Applies a proven term equality `p : interpret t = interpret s` to rewrite a goal of the form `mul · t + add = mul · s + add` (in normal form). Multiplies the normalized terms by `mul`, sorts the resulting monomials, appends `add`, and concludes the two sides are equal.
- **`apply-axiom.apply-axiom'`**: The generic version parameterized by an arbitrary `Data R`, used internally to instantiate `apply-axiom` against the specific `SemiringData env` interpretation.
