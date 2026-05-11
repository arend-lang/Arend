### Set

Foundations for sets equipped with decidable propositions, apartness relations, and decidable equality.

This module builds the basic hierarchy of set-like structures used throughout the library. It starts from `Dec` (decidable propositions) and `Decide` (its class form), then layers `BaseSet`, `SubSet`, and `SeparatedSet` on top. The key constructive distinction is between equality-based reasoning and apartness (`Set#`): a tight apartness recovers separated equality, and a `DecSet` provides decidable equality which automatically induces an apartness via inequality. Combinators like `SigmaDecide`, `ProductDecSet`, and `ArrayDec` lift decidability through standard type formers.

#### Decidable Propositions

- **`Dec`**: Inductive type `yes E | no (Not E)` for a proposition `E`; lives in `\Prop` (proven via `levelProp`).
- **`Dec.rec`**: Eliminator for `Dec E` into an arbitrary type.
- **`negated-dec`**: Extracts a `NegatedProp` conclusion from a function out of `Dec E` (double-negation elimination at negated props).
- **`decToBool`**: Converts `Dec E` into a `Bool`.
- **`Decide`**: Class wrapping a `Dec E` field, used as a typeclass for decidable propositions.
- **`dec_decide`**: Promotes a `Dec E` value to a `Decide E` instance.
- **`dec_yes_reduce`** / **`dec_no_reduce`**: Normalize a `Dec E` to `yes e` / `no q` given evidence.

#### Decidability Combinators

- **`Dec_||`**: Decidability of disjunction from decidability of both sides.
- **`SigmaDecide`**: Decidability of `\Sigma (a : A) (B a)` from decidability of `A` and each `B a`.
- **`ProductDecide`**: Instance form of pair decidability.
- **`NotDec`**: Decidability of `Not P` from decidability of `P`.
- **`NotDecide`**: Instance form of negation decidability.

#### Base Sets and Subsets

- **`BaseSet`**: Class carrying a single `\Set` field `E`; the foundation for all set classes.
- **`SubSet`**: A predicate `contains : S -> \Prop` on a `BaseSet S`; provides `ISet`, the induced sigma `BaseSet`.
- **`DecSubSet`**: A `SubSet` whose membership is decidable (`isDec`).
- **`DecSubSet.max`**: The total subset (always `\Sigma`), with trivial decidability.

#### Separated and Apartness Sets

- **`SeparatedSet`**: Extends `BaseSet` with `separatedEq : Not (Not (x = y)) -> x = y` (¬¬-stable equality).
- **`Set#`**: Extends `SeparatedSet` with a tight apartness `#`: irreflexive, symmetric, satisfying comparison `x # z -> x # y || y # z`, and `tightness : Not (x # y) -> x = y`. Derives `separatedEq` from tightness.
- **`Set#.apartNotEqual`**: Apart points are unequal (`x # y -> x /= y`).

#### Decidable Equality

- **`DecSet`**: Extends `BaseSet, Set#` with `decideEq (x y : E) : Dec (x = y)` and a boolean equality `==`. Provides default implementations of `#` as `/=`, with the apartness laws derived from decidable equality.
- **`SigmaDecSet`**: Decidable equality on dependent pairs.
- **`SubDecSet`**: Decidable equality on a subset of a `DecSet` (membership proofs are propositional).
- **`DecBool`**: Instance making `Bool` a `DecSet`; `compare` provides the explicit case analysis.
- **`ProductDecSet`**: Instance for binary products of `DecSet`s.
- **`EqualityDecide`**: Each equality `a = a'` in a `DecSet` is itself a `Decide` instance.
- **`ArrayDec`**: `DecSet` instance on fixed-length arrays, via `aux` performing pointwise decidable equality.

#### Reduction Lemmas

- **`decideEq=_reduce`**: `decideEq x y = yes p` when `p : x = y`.
- **`decideEq/=_reduce`**: `decideEq x y = no p` when `p : x /= y`.
- **`==_=`** / **`=-dec`**: Recover `a = a'` from `So (a == a')`.
- **`/=-dec`**: Recover `a /= a'` from `So (not (a == a'))`.

#### Set Truncation

- **`Trunc0`**: Set-truncation of a `\Type`, with constructor `in0` forcing the result to be a `\Set`.
- **`Trunc0.map`**: Functorial action of `Trunc0` on functions.
