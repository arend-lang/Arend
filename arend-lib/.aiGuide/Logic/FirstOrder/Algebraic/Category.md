### Logic.FirstOrder.Algebraic.Category

Category of models of an algebraic theory.

- **`ModelHom`**: Class with `Dom`, `Cod : Model T`, `funcs` (carrier map), `func-op` (preserves operations), `func-rel` (preserves relations).
- **`ModelCat`**: Instance of `BicompleteCat (Model T)` — the category of models is bicomplete (has all limits and colimits), with univalence via SIP.
