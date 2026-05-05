### Algebra.Solver.CSemiring

Solver model for commutative semirings, extending the semiring solver with monomial sorting to exploit commutativity of multiplication.

#### Solver Model

- **`CSemiringSolverModel`**: Constructs a `SolverModel` for any commutative semiring `R`. Reuses the term and normal-form types from `SemiringSolverModel`, but sorts monomial factors using red-black tree sorting before interpretation, so that products differing only in factor order normalize to equal expressions. Provides `interpret`, `interpretNF`, and the consistency proof `interpretNF-consistent` chaining `interpretNF-correct`, `interpretNF_sort`, and `normalize-consistent`.

#### Helpers

- **`sortMonomials`**: Given a normal form `NF C n` (a list of monomials paired with coefficients), sorts the variable list of each monomial via `Sort.RedBlack.sort`, canonicalizing factor order under commutativity.
- **`interpretNF_sort`**: Lemma stating that interpreting a sorted normal form equals interpreting the original: `interpretNF' (sortMonomials l) = interpretNF' l`. Justified by commutativity of multiplication in `R`.
- **`apply-axiom`**: Auxiliary lemma for applying a user-supplied semiring equation `p : interpret t = interpret s` within a larger normalized context, producing equality of `sortMonomials (multiply mul (normalize t)) ++ add` interpreted against the same with `s`. Used by the meta-solver to rewrite subterms by axioms while preserving the commutative-sorted normal form.
