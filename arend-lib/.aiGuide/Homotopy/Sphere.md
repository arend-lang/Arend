### Homotopy.Sphere

Defines the n-dimensional sphere as iterated suspensions of the empty type.

#### Sphere Construction

- **`Sphere`**: The n-sphere `Sⁿ`, defined as `Susp (iterr Susp n Empty)` — applying the suspension functor `n+1` times to the empty type.
- **`Sphere.pointed`**: Canonical pointed structure on `Sphere n` with basepoint `north`, providing an instance of `Pointed (Sphere n)`.
