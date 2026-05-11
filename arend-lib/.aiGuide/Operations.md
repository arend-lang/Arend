### Operations

Type-class abstractions for binary product and coproduct operations on a carrier type.

This module introduces two minimal operator classes, `HasProduct` and `HasCoproduct`, which equip a type `E` with binary product (`⨯`) and coproduct (`⨿`) operations. The design is intentionally lightweight: rather than committing to categorical or algebraic structure, these classes only fix the operator notation, allowing diverse instances (set-theoretic, categorical, lattice-theoretic) to share the same syntax. Default instances are provided for the universe `\Type`, where the product is interpreted as the dependent pair `\Sigma A B` and the coproduct as the disjoint union `Or A B`.

#### Product

- **`HasProduct`**: Class over a carrier `E : \hType` providing a binary operation `Product` (alias `⨯`, infixl 7): `E -> E -> E`. Used to overload product notation across different mathematical structures.
- **`TypeHasProduct`**: Instance of `HasProduct \Type` interpreting `A ⨯ B` as the sigma type `\Sigma A B`.

#### Coproduct

- **`HasCoproduct`**: Class over a carrier `E : \hType` providing a binary operation `Coproduct` (alias `⨿`, infixl 6): `E -> E -> E`. Used to overload coproduct notation.
- **`TypeHasCoproduct`**: Instance of `HasCoproduct \Type` interpreting `A ⨿ B` as the disjoint sum `Or A B` from `Data.Or`.
