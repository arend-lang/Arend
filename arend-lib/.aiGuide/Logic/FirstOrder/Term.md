### Logic.FirstOrder.Term

Many-sorted first-order terms over an arbitrary signature, with capture-free substitution.

This module formalizes terms of a many-sorted algebraic theory by parameterizing over a signature `TermSig` that fixes the set of sorts, the function symbols at each sort, and each symbol's argument arity (a list of sorts). Terms are built as a free construction over a sort-indexed family of variables `V : S -> \Set`, using either a variable or an application of a symbol to a dependent array of subterms whose sorts match the symbol's domain. Substitution is the standard recursive map sending variables to terms; the two basic lemmas (associativity of substitution and identity for the variable substitution) express that `Term` together with `subst` forms the Kleisli structure of a monad on sort-indexed families, i.e. the free term-algebra monad.

#### Signatures

- **`TermSig`**: Class describing a many-sorted first-order signature. Provides `Sort : \Set` (coerced), `Symb : Sort -> \Set` listing function symbols at each output sort, and `domain : Symb s -> Array Sort` giving the sorts of each symbol's arguments.

#### Terms

- **`Term`**: Inductive family `Term {S : TermSig} (V : S -> \Set) (s : S)` of terms of sort `s` with variables drawn from `V`. Constructors:
  - **`var`**: Embeds a variable `V s` as a term.
  - **`apply`**: Applies a symbol `f : Symb s` to a dependent array of subterms whose sorts match `domain f`.

#### Substitution

- **`subst`**: Capture-free substitution. Given `t : Term U s` and `rho : \Pi {s} -> U s -> Term V s`, produces `subst t rho : Term V s` by replacing each variable according to `rho` and recursing through `apply`.

#### Monad Laws

- **`subst-assoc`**: Associativity of substitution: `subst (subst t rho) tau = subst t (\lam u => subst (rho u) tau)`. Expresses Kleisli associativity of the term monad.
- **`subst_var`**: Identity law: `subst t var = t`. Substituting each variable by itself returns the original term.
