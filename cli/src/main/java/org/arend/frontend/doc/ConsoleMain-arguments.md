### Program Arguments Supported by `ConsoleMain`

The Arend console app uses Apache Commons CLI for argument parsing. Below is the complete list of supported arguments:

---

#### Named Options

| Short    | Long                            | Has Arg | Description                                                                          |
|----------|---------------------------------|---------|--------------------------------------------------------------------------------------|
| `-h`     | `--help`                        | No | Print help message and exit                                                          |
| `-v`     | `--version`                     | No | Print language version and exit                                                      |
| `-L`     | `--libdir`                      | Yes (`dir`) | Directory containing libraries (can be specified multiple times)                     |
| `-s`     | `--sources`                     | Yes (`dir`) | Project source directory                                                             |
| `-e`     | `--extensions`                  | Yes (`dir`) | Language extensions directory                                                        |
| `-m`     | `--extension-main`              | Yes (`class`) | Main extension class name                                                            |
| `-c`     | `--double-check`                | No | Double-check correctness of the typechecking result                                  |
| `-i`     | `--interactive`                 | Optional (`type`) | Start an interactive REPL; `type` can be `plain` or `jline` (default is `jline`)     |
| `-p`     | `--print`                       | Yes (`target`) | Print a definition or a module; format: `Module.Path` or `Module.Path:DefinitionName` |
| `-ps`    | `--proof-search`                | Yes (`pattern`) | Run proof searcher                                                                   |
| `-r`     | `--recompile`                   | No | Recompile all modules from source, ignoring binary caches (`.arc` files)             |
| *(none)* | `--serialize`                   | No | After typechecking, persist typechecked modules as `.arc` binary caches              |
| `-t`     | `--test`                        | No | Run tests                                                                            |
| `-d`     | `--daemon`                      | No | Start a daemon for the library and leave it serving future calls                     |
| *(none)* | `--daemon-stop`                 | No | Stop the daemon serving the library                                                  |
| *(none)* | `--daemon-ping`                 | No | Report whether the daemon is IDLE or BUSY                                            |
| *(none)* | `--daemon-status`               | No | Dump the daemon's queue depth, uptime, current task, protocol version, locked flags  |
| *(none)* | `--daemon-refresh`              | No | Re-run the bootstrap pipeline on the daemon's warm context to pick up source edits   |
| *(none)* | `--no-daemon`                   | No | Run in this process even if a daemon serves the library                              |
| *(none)* | `--show-times`                  | No | Show typechecking times per definition                                               |
| *(none)* | `--show-sizes`                  | No | Show sizes (expression node counts) of typechecked definitions, sorted descending    |
| *(none)* | `--show-modules`                | No | Show module dependency cycles                                                        |
| *(none)* | `--show-modules-with-instances` | No | Show module dependency cycles, filtered to modules that contain instances            |

---

#### Positional Arguments (FILES)

Usage: `arend [FILES]`

Each positional argument is interpreted as one of the following:
- **A directory path** — treated as a library root (looks for `arend.yaml` inside)
- **A path ending in `arend.yaml`** — loaded directly as a library config file
- **A `.zip` file** — loaded as a zipped library
- **A library name** (without path) — searched in the library directories specified by `-L` (or the default libraries root)
- **A module path** (e.g., `Category.Functor`) — a specific module to typecheck within the loaded libraries
- **A path to a module and a definition within it, separated by colon, e.g. `Category.Functor:FullyFaithfulFunctor.inverse` - a specific definition to typecheck within the loaded libraries**

---

#### Notes
- If `-L` is not specified, the default libraries root directory is used automatically.
- If no files/modules are specified and no `-s` is given, the tool looks for `arend.yaml` in the current directory.
- `-i` (`--interactive`) launches a REPL and skips typechecking entirely.
- `-h` and `-v` cause the program to exit immediately after printing output.
- Persistence of typechecked definitions to `.arc` files (in the library's `binariesDir` from `arend.yaml`) is **opt-in**: pass `--serialize` to enable it. Without the flag, typechecking runs but no `.arc` files are written.
- Deserialization of existing `.arc` files is **on by default**: on subsequent runs, any `.arc` caches already present are loaded automatically (regardless of whether `--serialize` is set). Pass `-r` / `--recompile` to ignore them and re-typecheck from source.

## The daemon

A daemon is a long-lived JVM holding a warm `ArendServer` for one library, so that a
second `arend` call over the same sources does not repeat the load, the resolve and the
typecheck it already did.

- `arend LIBRARY -d` starts one. It does the normal load + typecheck + persist once, then
  idles. `arend -s <dir> -d` serves a bare source directory instead of an `arend.yaml`.
- After that an ordinary `arend LIBRARY ...` is **routed to the daemon automatically**; the
  output and exit code are the same as running in this process, and a relative path in the
  command means the directory you typed it in, not the daemon's. `--no-daemon` opts out.
- Two commands are never routed, because a daemon cannot serve them: `-i` (the REPL is
  interactive) and `-s <dir>` (it names the sources to check, which a served command would
  ignore and then typecheck the daemon's own library instead). Both run in this process.
- `--daemon-ping`, `--daemon-status`, `--daemon-refresh` and `--daemon-stop` control it.
  All of them take the same single positional library reference, defaulting to `./arend.yaml`.
- Per-daemon state lives in `<library>/.arend/`: `daemon.lock`, `daemon.sock`, `daemon.log`
  (and `daemon.starting` while a start is in flight, which is what stops two `-d` calls from
  both spawning). A synthetic daemon uses `daemon-synthetic.*` for the same files, so it and a
  real daemon for the same directory can coexist. The directory and socket are created private
  to the owner, which is the whole of the access control — anything that can reach the socket
  can run commands against the library.
- A socket path has a length limit the rest of the filesystem does not (about 104 bytes), and
  `<library>/.arend/daemon.sock` passes it for a checkout only a few directories deep. The
  daemon then puts its socket in a short private directory instead — `$XDG_RUNTIME_DIR`, else
  an `arend-<user>` directory (mode `700`) under the temp dir — named for a hash of the library,
  and says so. `daemon.lock` records whichever address was bound, so clients follow either way.
- Where no Unix socket can be bound at all, the only fallback is a loopback TCP port with **no
  access control**: any local process could then drive the daemon or shut it down. That is not
  taken on its own — the daemon refuses to start and says why. Pass
  `-Darend.daemon.allowTcp=true` to accept it. Windows has no Unix sockets here and uses the
  TCP port directly; `arend -d` warns when the daemon it started is reachable this way.
- Ctrl-C on a routed command cancels the work on the daemon and exits 130, printing
  `[CANCELLED]` (the exit code is the daemon's own, passed through unchanged). A cancelled run writes no `.arc` caches: the modules it never reached were not
  checked, and caching that would leave the gap on disk for later runs to load as if they were.
- The daemon inherits the JVM options this process was started with (heap, stack), so it does
  the same work under the same limits. The ones that cannot exist twice are not forwarded: an
  agent or `jdwp` transport, a fixed JMX port, a flight recording or log pinned to a filename.
  Two JVMs cannot share those, and a child that inherits one dies during VM init.

The flags that shape the server itself are fixed when the daemon starts, so a daemon-served
command cannot change them: `-L`, `-s`, `-e`, `-m`, `-c`, `-r`, `--serialize`.
Passing one is warned about and ignored; use `--no-daemon` to get a process
that honours it. `-i` and the `--daemon-*` flags are rejected outright inside a served command.
