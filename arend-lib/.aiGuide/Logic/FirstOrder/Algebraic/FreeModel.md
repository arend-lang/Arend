### Logic.FirstOrder.Algebraic.FreeModel

The free structure on a set of variables over an algebraic theory, built from terms quotiented by provable equality.

- **`FreeStructure`**: The free structure for an algebraic theory `T` on a family of variable sets `X`: its carrier in each sort `s` is `QTerm X s`, operations are `qapply`, and a relation `P` holds of arguments when they are `qinj` of terms whose predicate `P` is a theorem of `T` (`IsTheorem nil (predicate P args')`).
- **`FreeModel`**: The free model of `T` on `X`: `FreeStructure X` is a model of `T`, i.e. it satisfies every axiom (an axiom holds under any assignment because, lifting the assignment along `qinj-surj`, truth in `FreeStructure X` corresponds to theoremhood by `interpretF=substF`).
- **`FreeStructure.QTerm`**: Higher inductive set of terms over variables `V` quotiented by theoremhood; constructors `qinj` (embed a term), `qapply` (apply an operation to `QTerm`s), `qquot` (identify injected terms that are provably equal via `IsTheorem nil`), and `qmerge` (commute `qinj` with `apply`).
- **`FreeStructure.qinj-surj`**: Every `QTerm V s` is in the image of `qinj`: `\exists (t : Term V s) (qinj t = q)`.
- **`FreeStructure.qinj-equality`**: Characterization of equality in `QTerm`: `qinj t = qinj t'` is equivalent to `IsTheorem nil (equality t t')`.
- **`FreeStructure.qinterpret`**: Interpretation of `QTerm V s` in a model `M` of the theory `T` given an assignment `rho` of variables to elements of `M`.
