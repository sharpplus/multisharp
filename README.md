## MultiSharp: Efficient Range and Multiplication Proofs for Extended Arithmetic Circuits

MultiSharp is a zero-knowledge proof system for arithmetic circuits extended with range-dependent operators (min, max, division, square root), built for proof generation on low-capacity devices like smartphones and compatible with pre-generated Pedersen commitments. It extends the Sharp protocol with multiplication proofs and full knowledge soundness, generating proofs 26.5× faster and verifying 2.46× faster than aggregated Bulletproofs for a batch of 1024 range and multiplication proofs, at the cost of a larger transcript.

The sections below describe how to build, run, and reproduce the benchmarks from the source code.

The source code for our Java implementation is the only artifact needed to validate our experiments. The harnesses that produce every figure in this paper have no build dependencies beyond a JDK and the two jars in `lib/`, and compile with:

```bash
javac -d build -cp "lib/*" @srcs.txt
```

The handset module described below additionally requires the Android SDK, and nothing else depends on it.

### Layout

- The MultiSharp protocol of Algorithm 1 and Algorithm 2 is implemented in `src/zk/multisharp/`, with the prover, the verifier and the Fiat-Shamir transcript in `MultiSharpProver`, `MultiSharpVerifier` and `MultiSharpTranscript` respectively, and the compiler of Section 3.1 in `CircuitBuilder`.
- The Sharp baseline of Appendix J, together with the two attacks against it, is in `src/zk/sharp/`.
- The benchmark harnesses are in `src/experiments/`, and the BPs baseline of Section 6.2 — one aggregated proof over the whole workload, committing only to the `S` externally committed values — is in `AggregatedBulletproofs`.
- The per-protocol measurement loops are in `MultiSharpExperiments` and `BulletproofBenchmark`; the latter runs only under `CombinedTables` and `NmTables`, which own the grid, the repetitions and the output, so the classes to invoke are those listed below rather than the loops themselves.

### What the Implementation Fixes

Two things are narrower in the code than in Algorithm 1 and Algorithm 2, and neither affects a figure we report.

- The first-phase challenges are drawn as single bits, so the implementation realizes Γ = 1 only; this is the setting Section 7.1 argues for and the one every measurement below uses, but a reader wanting Γ > 1 would have to widen the challenge expansion.
- The masks that Algorithm 2 draws uniformly from `Z_p` are sampled 64 bits wide of the group order and reduced, which leaves them within 2⁻⁶⁴ of uniform rather than exactly uniform; the perfect zero knowledge of Theorem 2 is a property of the protocol, and the code attains it up to that distance.

### The BPs Baseline

Two choices determine what the baseline is charged for, and both were made in its favor.

**Aggregation.** The whole workload is placed in a single constraint system and discharged by one proof, following the aggregation of [6, §4.3] carried over to the arithmetic circuit protocol of [6, §5]. This matters: the inner product argument contributes 2⌈log₂ n⌉ group elements for a circuit of `n` gates, so proving the batch as one circuit replaces a factor of `N` by an additive 2 log₂ N and pays the fixed per-circuit cost once. The library already implements the inner product argument; what was absent is aggregation across statements, which we supply. The baseline is also given the same statement as MultiSharp: it commits only to the `S` externally committed values, pinning each to the circuit by one linear constraint, and carries derived values as internal wires, as any circuit-aware use of BPs would.

**The range statement.** The baseline proves `x ∈ [0, 2^K − 1]` with `K = 11` by a single K-wide bit decomposition, at one multiplication gate per bit [6, §4.1]. This is the right counterpart to a MultiSharp range proof, which establishes `x ≥ 0` with `B` entering as a completeness and zero-knowledge parameter rather than as part of the relation. Charging it instead for the two-sided `x ∈ [0, B]` would double its range-proof gate count against an upper bound MultiSharp never proves, so the aggregated circuit has `KN + M` gates rather than `2KN + M`.

### Timing Methodology

Every harness shares the policy of Section 6.1, which is implemented once in `Timing` and applied identically to both protocols: a fixed six seconds of representative work once per JVM, one discarded timed block before the reported one, and a repetition count raised until a block is expected to last three seconds. Each reported time is the mean of the last block, with the half-width of a 95% confidence interval from the normal approximation, and each table entry is the median of three such passes over the whole grid.

The defaults can be overridden with `-Dbench.burnInMs`, `-Dbench.discardBlocks`, `-Dbench.minBlockMs` and `-Dbench.passes`. The burn-in and the discarded block are not optional on the desktop of Section 6.4: a freshly started process runs some 15% faster than its own steady state for the first several seconds, and without the three-second minimum the cheapest rows read up to 30% too fast. The median across passes is needed because the operating system grants the foreground process a scheduling boost, which biases every repetition within a measurement equally and so survives a longer warm-up.

`MsmComparison` isolates the bucket method of Section 6.1: evaluating the multi-commitments as one scalar multiplication per coordinate instead makes generation 3.0 to 7.8 times slower and verification 2.4 to 5.7 times slower over the grid on the desktop, and 3.4 to 9.4 times and 2.7 to 7.0 times respectively on the handset, the largest gains on both being at large `N` and small `R`.

### What Reproduces

The ratios are the robust quantity; the millisecond columns are a property of the machine as much as of the protocol. Windows grants the foreground process a scheduling boost, and on the desktop the two states differ by around a third, so the same command can return absolute times a factor of 1.3 to 1.5 apart depending on nothing but which window has focus. Both arms of a comparison move together, so a ratio taken inside one process is far less sensitive: the generation ratio at `N = 1024` reads between 24.7 and 25.0 across five invocations that differ in heap limit and in the width of the scalability grid. This is the reason Table 4 and Tables 5–7 are produced together, and the reason Section 7.2 and Section 6.1 insist on their two arms sharing a process.

A reader reproducing our figures should therefore expect the ratios of Section 6.2 to hold and the millisecond columns to sit wherever their own scheduling puts them. Ours were taken with the benchmark in the foreground on an otherwise idle desktop; under those conditions five separate invocations agree to 1.6%, against the factor of 1.5 that an interrupted machine can produce. A run left to compete with interactive work is measuring the operating system.

### Reproducing the Tables

Each of the commands below is self-contained, and the first produces Table 4 and Tables 5–7 in a single process, which is what makes the `R = 256` rows of the two groups of tables the same measurement rather than independent ones.

| Output | Command |
|---|---|
| Tables 4, 5–7 | `CombinedTables 10 1024 256 128,256,512,1024 11` |
| Table 3 | `CircuitWorkloads measure 256 10` |
| Baseline figures of Section 7.2 | `BaselineBenchmark 10 1024 256` |
| Bucket method of Section 6.1 | `MsmComparison 10 1024 128,256,512,1024` |
| Tables 8–12 | `NmTables 10 1024 1024 256 11` |

The arguments shown are the defaults, and are those used for the figures reported in this paper. `CombinedTables` takes roughly 85 minutes on the desktop of Section 6.4, `MsmComparison` roughly 33 and `NmTables` roughly 70; the other two are a matter of seconds.

`NmTables` takes its two axes either as a comma-separated list or as a maximum expanded to the grid above, and admits zero on either, `M = 0` being a batch of range proofs alone and `N = 0` one of multiplication proofs alone. Its final argument is the BPs bit width `K`; passing `0` measures MultiSharp alone and omits the two speed-up tables, which takes roughly 11 minutes instead, the baseline being much the more expensive of the two arms. Both of the latter measure their two arms inside one process, which Section 7.2 and Section 6.1 respectively rely on: the quantities compared there differ by less than the run-to-run variation of the machine, so a comparison across invocations would measure the machine rather than the protocol. The system properties `-Dmultisharp.noMul` and `-Dmultisharp.naiveMsm` expose the same two variations to the general harness for exploratory use, but the reported figures come from the dedicated classes.

`CircuitWorkloads` compiles the use-case circuits and reads their dimensions off the compiler rather than evaluating the closed forms of Section 6.3, checking the two against each other on every row; the transcript composition then follows from the dimensions, and the times are measured under the same policy as the other harnesses. Passing `dims` in place of `measure` emits the composition alone, which takes a second rather than a few minutes, and `-Dworkloads.B=20` re-runs the measurement at a larger `B`, which is the check that Section 6.3 reports when it compares those times against Table 2.

### Android Devices

The use cases of Appendix D put the prover on a smartphone, and `android/` builds the same harnesses for one. The Gradle module compiles `src/` in place rather than against a copy of it, so a handset and the machine of Section 6.1 run identical protocol code and anything that builds for one builds for the other; only the driver that selects a harness and captures its output is Kotlin.

Three differences from the desktop are worth recording, none of which changes what is computed:

- The Java Cryptography Architecture is not asked for SHA3, which Android's default providers do not reliably supply; the `keccakj` implementation already in `lib/` is constructed directly instead, on both platforms, so that the hash is the same code everywhere rather than whatever a given device happens to install.
- The precomputed two-square table travels inside the package, an Android process having no working directory against which to resolve `precomp/`, and is parsed once and shared rather than once per prover.
- The heap does not distort the comparison, which is worth recording because a memory-poor device is the obvious thing to suspect. The whole grid fits in 192 MB, and constraining the heap to that from the desktop's default of 16 GB costs both protocols the same 6% of generation time at `N = 1024`, leaving their ratio within 0.7% of itself across the range — 24.8, 24.7, 24.9 and 24.8 at 16 GB, 512 MB, 256 MB and 192 MB respectively. Those four runs use `CombinedTables 10 1024 256 256 11`, which measures the comparison alone; the small offset from the ratio of Table 4 is the scalability grid that the full command measures alongside it. Neither protocol is the more allocation-bound of the two at the sizes we measure.

The timing policy above should not be carried over unexamined. It is calibrated against a transient of the desktop JVM, and a phone runs a different virtual machine on hardware that throttles; `TimingProbe` reports consecutive timed blocks and should be re-run, and the four `bench.*` properties retuned, before any figure taken on a device is believed. Those properties are set in the driver rather than on a command line, since the harnesses read them into constants when their classes are first loaded.

### Tests

- `MultiSharpTests` checks completeness, the transcript accounting of Appendix F, and rejection of the attacks of Appendix J.
- `SharpExperiments` runs the same two attacks against the collapsed baseline, where both are accepted: that is the outcome the section claims, and the one that makes the baseline unsound. It prints one verdict per attack and suppresses the attackers' own work, which `-Dsharp.verboseAttacks=true` restores.
- `CircuitTests` covers each gadget of Section 3.1 and proves and verifies the polygon and biometric statements end to end, including the dimension and `T` claims that Section 6.3 tabulates.
