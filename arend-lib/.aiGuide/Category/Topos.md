### Category.Topos

Defines elementary toposes as finitely complete cartesian closed precategories equipped with a subobject classifier, and instantiates the topos structure on the category of sets.

#### Topos Structure

- **`ToposPrecat`**: Class of elementary toposes, extending `FinCompletePrecat` and `CartesianClosedPrecat`. Adds a subobject classifier with characteristic-map machinery.
  - **`omega`** (alias `subobj-classifier`): The subobject classifier object.
  - **`true-map`**: The truth morphism `Hom terminal omega`.
  - **`char-map`**: Characteristic morphism `B -> omega` of a mono `m : S >-> B`.
  - **`char-pullback`**: Witnesses that `m` is the pullback of `true-map` along `char-map m`.
  - **`char-unique`**: Uniqueness of the characteristic morphism: any `phi` whose pullback against `true` recovers `m` must equal `char-map m`.
  - **`p-exponential`**: Exponentiability of `omega`, derived from cartesian closure.

#### Propositional Equality Helpers

- **`sigma-prop-ext`**: Any inhabited proposition `A` is equal to the unit type `\Sigma`.
- **`sigma-prop-ext-inv`**: Conversely, an equality `A = \Sigma` produces an inhabitant of `A`.

#### Set Topos Instance

- **`SetTopos`**: Instance making `\Set` into a `ToposPrecat`. Inherits finite completeness from `SetBicat` and cartesian closure from `SetCartesianClosed`. Uses `\Prop` as the subobject classifier, the unit type as `true`, and `IsElement` (membership in the image of a mono) as the characteristic map.
- **`SetTopos.mono-is-inj`**: A mono in `SetBicat` is an injective function; proven by transposing through global elements via `name'` / `unname'`.
- **`SetTopos.IsElement`**: Data type expressing `b ∈ image(m)` for a mono `m : A >-> B`, with constructor `isContained (a : A) (m.f a = b)`. Equipped with `\use \level isProp` showing it is a proposition (uniqueness of preimage from injectivity).
- **`SetTopos.isElement-char`**: Given a proof that `IsElement m ∘ p1` is constantly `\Sigma`, recovers an actual element witness for each input — the bridge used to construct the pullback mediating map.
