### Algebra.Group

Group structures: groups, abelian groups, additive (abelian) groups, and their decidable and apartness-equipped variants.

#### Group Class

- **`Group`**: Extends `CancelMonoid`. A group with operation `*`, identity `ide`, and an `inverse` function satisfying `inverse-left` (`inverse x * x = ide`) and `inverse-right` (`x * inverse x = ide`). Cancellation laws are derived from inverses; default implementations show that providing one of `inverse-left`/`inverse-right` (and either `ide-left` or `ide-right`) suffices.
- **`Group.Dec`**: A group with decidable equality; extends `Group` and `DecSet`.

#### Group Helpers and Lemmas

- **`inverse-equality`**: Two group structures on the same underlying set with the same identity and operation have the same inverse function.
- **`equals`**: Equality of groups reduces to equality of their underlying monoid structures.
- **`make-inverse-left`**: Derives `inverse x * x = ide` from `inverse-right`, `ide-right`, and associativity (in any semigroup with the relevant data).
- **`make-ide-left`**: Derives the left identity law from the right identity together with `inverse-left` and `inverse-right`.
- **`translate-is-Equiv`**: Left translation `(h *)` is an equivalence; the inverse is `(inverse h *)`.

#### Group Operations

- **`/`**: Group division `x / y = x * inverse y` (infix, level 7).
- **`conjugate`**: `conjugate g h = g * h * inverse g`.
- **`conjugate-via-id`**: Conjugation by the identity is a no-op: `conjugate ide g = g`.

#### Additive Groups

- **`AddGroup`**: Extends `AddMonoid` with `negative : E -> E` and laws `negative-left` (`negative x + x = zro`) and `negative-right` (`x + negative x = zro`).
- **`AddGroup.fromGroup` / `toGroup`**: Coercions between multiplicative `Group` and `AddGroup` formulations.
- **`AddGroup.negative-equality`**: Two `AddGroup` structures on the same set with the same zero and addition share the same negation.
- **`-`**: Subtraction `x - y = x + negative y` (infix, level 6).

#### Apartness Structures

- **`AddGroup.With#`**: Extends `AddGroup` and `Set#`. Equips an additive group with a tight apartness `#0 : E -> \Prop` (apart-from-zero) satisfying: `#0` excludes `zro`, is preserved by `negative`, satisfies a comparison/splitting property under `+`, and is tight (`Not (#0 x) -> x = zro`). The set apartness `#` is defined as `#0 (x - y)`.
- **`AddGroup.Dec`**: Extends `With#` and `DecSet`. For decidable additive groups, `#0` is automatically supplied via `nonZeroApart : x /= zro -> #0 x`, and the `With#` axioms are derived from decidability.

#### Commutative Groups

- **`CGroup`**: Extends `Group` and `CancelCMonoid`; commutative group, with `inverse-right` derived from `*-comm` and `inverse-left`.
- **`AbGroup`**: Extends `AddGroup` and `AbMonoid`; additive abelian group, with `negative-right` derived from `+-comm` and `negative-left`.
- **`AbGroup.fromCGroup` / `toCGroup`**: Coercions between multiplicative `CGroup` and additive `AbGroup`.
- **`AbGroup.equals`**: Equality of abelian groups reduces to equality of their underlying additive group structures.

#### Finite Groups

- **`FinGroup`**: Extends `Group`, `Group.Dec`, and `FinSet`; a finite group with decidable equality.
