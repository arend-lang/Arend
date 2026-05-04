### Logic.FirstOrder.Term

Multi-sorted first-order terms.

- **`TermSig`**: Class with `Sort`, `Symb : Sort -> \Set`, `domain` (arity).
- **`Term`**: Inductive type of terms over a signature and variables, with `var` and `apply` constructors.
- **`subst`**: Substitution: applies a variable-to-term map to a term.
- **`subst-assoc`**: Substitution is associative.
- **`subst_var`**: Substituting `var` is the identity.
