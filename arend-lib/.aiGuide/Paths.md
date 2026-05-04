### Paths (root file)

Core path (identity type) operations and lemmas.

#### Basic Path Operations

- **`idpe`**: `idp` as a function: `a = a`.
- **`pmap`**: Congruence: `a = a' -> f a = f a'`.
- **`pmap2`**: Binary congruence.
- **`transport`**: Transport along a path in a type family.
- **`transportInv`**: Transport in the reverse direction.
- **`transport2`**: Transport along two paths simultaneously.
- **`inv`**: Path inversion.
- **`*>`**: Path concatenation (right-biased): `(a = a') -> (a' = a'') -> (a = a'')`.
- **`<*`**: Path concatenation (left-biased).

#### Path Algebra

- **`inv_inv`**: `inv (inv p) = p`.
- **`inv_*>`**: `inv p *> p = idp`.
- **`*>_inv`**: `p *> inv p = idp`.
- **`idp_*>`**: `idp *> p = p`.
- **`<*_idp`**: `p <* idp = p`.
- **`<*_*>`**: `p <* q = p *> q`.
- **`*>_inv-comm`**: `inv (p *> q) = inv q *> inv p`.
- **`pmap_inv-comm`**: `pmap f (inv p) = inv (pmap f p)`.
- **`*>-assoc`**: `(p *> q) *> r = p *> (q *> r)`.
- **`pmap_*>-comm`**, **`pmap_<*-comm`**, **`pmap2_*>-comm`**: Congruence distributes over concatenation.

#### Equational Reasoning

- **`qed`**: Reflexivity for equational reasoning chains.
- **`>==`**: Transitivity step.
- **`==<`**: Start of an equational reasoning chain.

#### Dependent Paths

- **`idpOver`**: `Path A a (coe A a right)`.
- **`pathOver`**: Constructs a dependent path from `coe A a right = a'`.
- **`coe_path`**: Characterizes `coe` on path types.
- **`path-sym`**: `(a = a') = (a' = a)`.
- **`rotatePathLeft`**: Rearranges a path equation.

#### Naturality

- **`homotopy-isNatural`**: `pmap f p *> h a' = h a *> pmap g p` for a homotopy `h : f ~ g`.
- **`homotopy_app-comm`**: `h (f a) = pmap f (h a)` when `f` has a fixpoint homotopy.

#### Dependent Map and Transport Lemmas

- **`pmapd`**: Dependent congruence: `transport B p (f a) = f a'`.
- **`transport_*>`**: Transport over concatenation.
- **`transport_pi`**, **`transport_pi2`**, **`transport_depPi`**: Transport in function types.
- **`transport_path-right`**, **`transport_path-left`**: Transport in path types.
- **`transport_path_pmap`**, **`transport_path_pmap-right`**: Transport in `f x = g x` families.
- **`transport_inv_id`**, **`transport_id_inv`**: Round-trip transport lemmas.
- **`transport-rotate`**, **`transport_inv_func`**: Transport rearrangement.

#### Coe Lemmas

- **`coe_sigma`**: `coe` on sigma types splits componentwise.
- **`coe_pi`**: `coe` on function types.
- **`psqueeze`**: Squeeze a path: `a = p @ i`.
