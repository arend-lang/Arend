### Algebra.Solver.BooleanRing

A reflective decision procedure for equalities in Boolean rings.

This module instantiates the generic `SolverModel` interface for `BooleanRing`, providing a normalization-based solver. Terms are represented as a syntactic algebra (`Term`), normalized to disjunctive-style normal forms (`NF`) — lists of monomials, where each monomial is a Boolean array picking out which variables appear in the product. Because Boolean rings are idempotent (`x * x = x`) and characteristic 2 (`x + x = 0`), monomials reduce to subsets of variables and pairs of equal monomials cancel: this is exploited by sorting and `collapse`-ing the list of monomials. The `terms-equality` lemma reduces an equation `t = s` to checking that the normal form of `t + s` collapses to zero.

#### Solver Model

- **`BooleanRingSolverModel`**: The `SolverModel` instance for a `BooleanRing B`, packaging together the term language, normal forms, normalization, interpretation, and the consistency proof linking them.

#### Syntactic Terms and Normal Forms

- **`Term`**: Inductive datatype of ring terms over `n` variables, with constructors `var`, `:zro`, `:negative`, `:+` (sum), and `:*` (product).
- **`NF`**: Normal form type — `List (Array Bool n)`. Each `Array Bool n` is a monomial encoded as a characteristic vector over the `n` variables; the outer list is a sum of such monomials.
- **`BoolOpPoset`**: `LinearOrder.Dec` instance on `Bool` (the opposite of `BoolPoset`), used to lexicographically sort monomials.

#### Normalization

- **`normalize`**: Converts a `Term n` to an `NF n`. Variables become singleton monomials, `:zro` becomes the empty sum, negation is the identity (since `-x = x` in a Boolean ring), `:+` concatenates, and `:*` multiplies.
- **`multiply'`**: Tail-recursive helper that multiplies two normal forms accumulating into `acc`. For each monomial `a` in `l1`, distributes it over `l2` by taking the pointwise `or` (union of variable supports) of `a` with each monomial in `l2`.
- **`multiply`**: Top-level multiplication of normal forms via `multiply'` with empty accumulator.
- **`collapse`**: Removes adjacent equal monomials from a (sorted) `NF` — implementing cancellation `x + x = 0`.

#### Interpretation

- **`interpret`**: Evaluates a `Term env.len` in a `BooleanRing` `B` under an environment `env : Array B`.
- **`toArray`**: Selects from `env` the entries whose corresponding `Bool` flag is `true`, producing the list of variables present in a monomial.
- **`sBigProd`**: Product of a list of ring elements, returning `B.zro` for the empty list (so an empty monomial — no variables selected — interprets as zero, not one).
- **`interpretMonomial`**: Interprets a single monomial `Array Bool n` as the product `sBigProd (toArray l env)`.
- **`interpretNF'`**: Sum interpretation of a normal form, summing `interpretMonomial` over the list.
- **`interpretNF`**: Public interpretation: sorts the normal form (using `Sort.RedBlack.sort`), collapses duplicates, then evaluates via `interpretNF'`.

#### Non-Emptiness Predicates

- **`NonEmpty`**: A monomial is non-empty if at least one variable flag is `true` — needed because `sBigProd nil = zro`, so empty monomials would evaluate incorrectly.
- **`AllNonEmpty`**: All monomials in an `NF` are non-empty; an invariant maintained by `normalize`.

#### Auxiliary Lemmas on `toArray` and `sBigProd`

- **`toArray_replicate`**: `toArray` of an all-`false` array yields the empty list.
- **`toArray_singleAt`**: `toArray` of a singleton-`true` array selects exactly that one variable.
- **`toArray_or`**: `Big ∧` on the union (pointwise `or`) of two flag arrays factors as the product of the individual `Big ∧`s.
- **`sBigProd_Big`**: For a non-empty array bounded above by `x0`, `sBigProd` agrees with the meet-with-`x0` big-product.
- **`toArray<=env`**: Each entry of `toArray l env` is bounded by `B.BigJoin env`.
- **`toArray/=nil`**: If a monomial is `NonEmpty`, its `toArray` is non-empty.

#### Consistency Lemmas for Operations

- **`interpretNF_++`**: Interpretation distributes over list concatenation as ring addition.
- **`interpretMonomial_or`**: Interpretation of a `or`-merged monomial equals the product of interpretations (when both are non-empty).
- **`interpretNF_map`**: Interpretation of `map (or a) l` factors as `interpretMonomial env a * interpretNF' env l`.
- **`interpretNF_multiply'`**, **`interpretNF_multiply`**: `multiply'` and `multiply` correctly compute the product of two normal-form interpretations.
- **`interpretNF'.cons`**: Cons case for `interpretNF'`.

#### Preservation of `AllNonEmpty`

- **`all-++`**: `AllNonEmpty` is preserved by concatenation.
- **`or-nonEmpty`**: Mapping `or a` over a list yields an `AllNonEmpty` list when `a` is non-empty.
- **`multiply'-nonEmpty`**, **`multiply-nonEmpty`**: Multiplication preserves `AllNonEmpty`.
- **`normalize-nonEmpty`**: All monomials produced by `normalize` are non-empty.

#### Sorting, Collapsing, and Final Consistency

- **`interpretNF'-consistent`**: `interpretNF' env (normalize t) = interpret env t`.
- **`collapse-consistent`**: `collapse` preserves the interpretation (relies on `x + x = 0`).
- **`perm-consistent`**: Permutations of `NF` preserve the interpretation (sum is commutative).
- **`sort-consistent`**: Sorting preserves interpretation.
- **`interpretNF=interpretNF'`**: `interpretNF` and `interpretNF'` agree on `AllNonEmpty` inputs after sort+collapse.

#### Top-Level Solver Lemmas

- **`terms-equality`**: If the normal form of `t :+ s` interprets to zero, then `t` and `s` are equal in `B` — the core soundness statement used by the solver.
- **`terms-equality-conv`**: The converse direction of `terms-equality`.

#### Axiom-Application Support

- **`nonEmpty-dec`**: Decidability of `NonEmpty` for a Boolean array.
- **`interpretNF_Big_++`**: Distributes interpretation over a `Big ++`-folded list of normal forms plus an extra summand.
- **`interpretNF_map_zro`**: If `interpretNF' env l = 0`, then mapping `or a` over `l` still gives `0`.
- **`interpretNF_multiply'_zro`**, **`interpretNF_multiply_zro`**: If one factor's interpretation is `0`, the product's is too.
- **`apply-axioms`**: Given a list of axioms `t = s` (each scaled by some monomial-list `s.1`), reduces the interpretation of the combined sum to the interpretation of `add` alone — used by the solver to apply user-supplied ring equalities during normalization.
