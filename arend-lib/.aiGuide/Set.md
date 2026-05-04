### Set (root file)

Decidability, set-level structures, and decidable sets.

#### Decidability

- **`Dec`**: `yes E | no (Not E)` — decidable proposition. Has `levelProp`, `rec`.
- **`decToBool`**: Converts `Dec E` to `Bool`.
- **`Decide`**: Class with `E : \Prop` and `decide : Dec E`.
- **`dec_decide`**: Constructs `Decide` from `Dec`.
- **`dec_yes_reduce`**, **`dec_no_reduce`**: Reduction lemmas for `Dec`.
- **`Dec_||`**: Decidability of `||` from decidability of components.
- **`SigmaDecide`**: Decidability of `\Sigma (a : A) (B a)` from decidability of `A` and `B`.
- **`ProductDecide`**: Decidability of products.
- **`NotDec`**: `Dec P -> Dec (Not P)`.
- **`NotDecide`**: `Decide` instance for `Not A`.
- **`negated-dec`**: Derives a `NegatedProp` from `Dec`.

#### Base Sets and Subsets

- **`BaseSet`**: Class with `E : \Set`.
- **`SubSet`**: Class with `S : BaseSet` and `contains : S -> \Prop`.
- **`DecSubSet`**: Extends `SubSet` with `isDec`. Has `max` (full subset).

#### Separation and Apartness

- **`SeparatedSet`**: Extends `BaseSet` with `separatedEq : ¬¬(x = y) -> x = y`.
- **`Set#`**: Extends `SeparatedSet` with apartness `#`, `#-irreflexive`, `#-symmetric`, `#-comparison`, `tightness`.

#### Decidable Sets

- **`DecSet`**: Extends `BaseSet` and `Set#` with `decideEq : \Pi (x y) -> Dec (x = y)` and boolean equality `==`.
- **`SigmaDecSet`**, **`SubDecSet`**: `DecSet` for sigma types and subtypes.
- **`DecBool`**: `DecSet` instance for `Bool`.
- **`ProductDecSet`**: `DecSet` for products.
- **`EqualityDecide`**: `Decide` instance for `a = a'` in a `DecSet`.
- **`ArrayDec`**: `DecSet` instance for `Array A n`.
- **`decideEq=_reduce`**, **`decideEq/=_reduce`**: Reduction lemmas.
- **`==_=`**, **`=-dec`**, **`/=-dec`**: Boolean equality conversions.

#### Set-Level Truncation

- **`Trunc0`**: 0-truncation (set truncation) with `in0` constructor. Has `map`.
