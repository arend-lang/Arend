### Paths (root file)

Core path (identity type) operations and lemmas for the cubical type theory underlying Arend.

This module provides the foundational vocabulary for working with the path type `a = a'`, including congruence (`pmap`), transport along type families, and concatenation operators in both right- and left-biased forms. The dual concatenation operators (`*>` and `<*`) eliminate on different arguments, making them definitionally convenient in different proof contexts; lemmas like `<*_*>` mediate between them. The module also sets up equational reasoning (`==<`, `>==`, `qed`), J-eliminators (`Jl`, `Jr`), and a comprehensive set of transport/coe lemmas that characterize how transport interacts with sigma types, function types, and path types — these are essential for nearly all subsequent reasoning about dependent types in the library.

#### Basic Path Operations

- **`idpe`**: `idp` as a function: `a = a`.
- **`pmap`**: Congruence: `(a = a') -> f a = f a'`.
- **`pmap2`**: Binary congruence for two-argument functions.
- **`transport`**: Transport along a path in a type family `B : A -> \Type`.
- **`transportInv`**: Transport in the reverse direction.
- **`transport2`**: Transport along two paths simultaneously in a binary family.
- **`inv`**: Path inversion: `(a = a') -> (a' = a)`.
- **`*>`**: Right-biased path concatenation, eliminating on the second argument.
- **`<*`**: Left-biased path concatenation, eliminating on the first argument.
- **`psqueeze`**: Squeeze a path: `a = p @ i`.

#### J-Eliminators

- **`Jl`**: Based path induction (left-based J).
- **`Jl.Jr`**: Right-based variant of path induction.
- **`Jl.def`**: Defined (non-pattern-matching) version of `Jl` via `coe`.

#### Path Algebra

- **`inv_inv`**: `inv (inv p) = p`.
- **`inv_*>`**: `inv p *> p = idp`.
- **`*>_inv`**: `p *> inv p = idp`.
- **`idp_*>`**: `idp *> p = p`.
- **`<*_idp`**: `p <* idp = p`.
- **`<*_*>`**: `p <* q = p *> q` — bridges the two concatenation operators.
- **`*>_inv-comm`**: `inv (p *> q) = inv q *> inv p`.
- **`pmap_inv-comm`**: `pmap f (inv p) = inv (pmap f p)`.
- **`*>-assoc`**: Associativity of `*>`.
- **`pmap_*>-comm`**, **`pmap_<*-comm`**, **`pmap2_*>-comm`**: Congruence distributes over concatenation.
- **`rotatePathLeft`**: Rearranges `q = p *> r` into `inv p *> q = r`.

#### Equational Reasoning

- **`qed`**: Reflexivity for closing equational reasoning chains.
- **`>==`**: Transitivity step in a chain.
- **`==<`**: Start of an equational reasoning chain.

#### Dependent Paths

- **`idpOver`**: `Path A a (coe A a right)` — canonical dependent path along `coe`.
- **`pathOver`**: Constructs a dependent path from `coe A a right = a'`.
- **`coe_path`**: Characterizes `coe` on path types: `coe (\lam i => p @ i = r @ i) q right = inv p *> q *> r`.
- **`coe_path.alt`**: Alternative form of `coe_path` with reversed orientation.
- **`path-sym`**: `(a = a') = (a' = a)` as a path between types, via `iso`.

#### Naturality

- **`homotopy-isNatural`**: For a homotopy `h : f ~ g`, `pmap f p *> h a' = h a *> pmap g p`.
- **`homotopy_app-comm`**: For `f` with fixpoint homotopy `h`, `h (f a) = pmap f (h a)`.

#### Dependent Map and Transport Lemmas

- **`pmapd`**: Dependent congruence: `transport B p (f a) = f a'`.
- **`pmapd_pathOver`**: Characterizes `pmapd` via a `pathOver` decomposition.
- **`transport_*>`**: Transport over concatenation: `transport B (p *> q) x = transport B q (transport B p x)`.
- **`transport_pi`**: Transport in a function type family `\lam y => B y -> C y`, applied.
- **`transport_pi2`**: Transport in a function type family characterized by a pointwise condition.
- **`transport_depPi`**: Transport in a dependent function family `\lam y => \Pi b, C y b`.
- **`transport_path-right`**: Transport in `\lam x => a0 = x` is post-concatenation.
- **`transport_path-left`**: Transport in `\lam x => x = a0` is pre-concatenation with inverse.
- **`transport_path_pmap`**: Transport in `\lam x => f x = g x` characterized by `q *> pmap g p = pmap f p *> s`.
- **`transport_path_pmap.conv`**: Converse of `transport_path_pmap`.
- **`transport_path_pmap-right`**: Specialization where `f` is constant.
- **`transport_path_pmap-right.conv`**: Converse direction.
- **`transport_inv_id`**: `transport B (inv p) (transport B p x) = x`.
- **`transport_id_inv`**: `transport B p (transport B (inv p) x) = x`.
- **`transport-rotate`**: Rearranges `transport B p x = y` into `x = transport B (inv p) y`.
- **`transport_inv_func`**: Identifies `transport B (inv p)` with a given left-inverse `g`.

#### Coe Lemmas

- **`coe_sigma`**: `coe` on sigma types splits componentwise.
- **`coe_pi`**: `coe` on function types: characterizes the action of `coe` on `B i -> C i` via `coe2`.
