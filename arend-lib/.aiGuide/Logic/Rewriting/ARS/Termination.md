### Logic.Rewriting.ARS.Termination

Termination and normal forms for abstract reduction systems.

- **`isNormalForm`**: `a` has no reducts.
- **`HasNormalForm`**: `a` reduces to some normal form.
- **`isNormalizing`**: Every element has a normal form.
- **`Acc`**: Accessibility predicate (well-founded induction).
- **`Acc-Trans`**: Accessibility under transitive closure.
- **`Acc=>AccTrans`**: `Acc R a` implies `Acc (transitive-closure R) a`.
