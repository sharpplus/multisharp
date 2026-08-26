package experiments;

import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.util.Random;

import ec.ECPoint;
import zk.multisharp.CircuitBuilder;
import zk.multisharp.CircuitSpec;
import zk.multisharp.MultiSharpParams;
import zk.multisharp.MultiSharpProof;
import zk.multisharp.MultiSharpProver;
import zk.multisharp.MultiSharpVerifier;

public class MultiSharpExperiments {

	private static final DecimalFormat DF1 = new DecimalFormat("0.0");

	private static final int S = Integer.getInteger("multisharp.S", 2);

	private final Random generator = new Random(20260805);

	public static class Result {
		public double genMean, genCi, vrfMean, vrfCi, sizeKb;
		public int commitments, scalars;
		public int reps;
	}

	private void burnIn() throws NoSuchAlgorithmException {
		if (!Timing.needsBurnIn()) {
			return;
		}
		Random rnd = new Random(1);
		CircuitBuilder cb = MultiSharpTests.benchmarkCircuit(S, 8, 8, rnd);
		CircuitSpec spec = cb.spec();
		BigInteger[] V = cb.witness();
		int T = cb.widestProductCount();
		BigInteger B = BigInteger.valueOf(1024);
		MultiSharpParams pp = MultiSharpParams.forCircuit(spec, 64, B,
				MultiSharpParams.maxL(8, 8, B, 1, T), T);
		MultiSharpProver prover = new MultiSharpProver(pp, rnd);
		MultiSharpVerifier verifier = new MultiSharpVerifier(pp);
		BigInteger[] rx = new BigInteger[spec.S];
		for (int i = 0; i < rx.length; i++) {
			rx[i] = new BigInteger(128, rnd);
		}
		Timing.burnIn(() -> {
			MultiSharpProof p = prover.prove(spec, V, rx);
			if (!verifier.verify(p, prover.getCx(), spec)) {
				throw new IllegalStateException("burn-in proof failed to verify");
			}
		});
	}

	public Result measure(int N, int M, int R, int reps) throws NoSuchAlgorithmException {
		burnIn();
		BigInteger B = BigInteger.valueOf(1024);

		CircuitBuilder cb = MultiSharpTests.benchmarkCircuit(S, N, M, generator);
		CircuitSpec spec = cb.spec();
		BigInteger[] V = cb.witness();
		int T = cb.widestProductCount();

		BigInteger L = MultiSharpParams.maxL(N, M, B, 1, T);
		MultiSharpParams pp = MultiSharpParams.forCircuit(spec, R, B, L, T);
		if (!pp.checkParameterConstraint()) {
			throw new IllegalStateException("parameter constraint violated for N=" + N);
		}

		MultiSharpProver prover = new MultiSharpProver(pp, generator);
		MultiSharpVerifier verifier = new MultiSharpVerifier(pp);

		BigInteger[] rx = MultiSharpTests.randomBlindings(spec.S, generator);

		long warmStart = System.nanoTime();
		for (int t = 0; t < 2; t++) {
			MultiSharpProof warm = prover.prove(spec, V, rx);
			if (!verifier.verify(warm, prover.getCx(), spec)) {
				throw new IllegalStateException("warm-up proof failed to verify");
			}
		}
		double perIterMs = (System.nanoTime() - warmStart) / 2e6;
		reps = Timing.scaleReps(reps, perIterMs);
		System.gc();

		double[] gen = new double[reps];
		double[] vrf = new double[reps];
		Result res = new Result();
		res.reps = reps;

		for (int blk = 0; blk < Timing.BLOCKS; blk++) {
			for (int t = 0; t < reps; t++) {
				long t0 = System.nanoTime();
				MultiSharpProof proof = prover.prove(spec, V, rx);
				long t1 = System.nanoTime();

				ECPoint[] Cx = prover.getCx();

				long t2 = System.nanoTime();
				boolean ok = verifier.verify(proof, Cx, spec);
				long t3 = System.nanoTime();

				if (!ok) {
					throw new IllegalStateException("honest proof failed to verify (N=" + N + ")");
				}

				gen[t] = (t1 - t0) / 1e6;
				vrf[t] = (t3 - t2) / 1e6;
				res.sizeKb = proof.size() / 1024.0;
				res.commitments = proof.commitmentCount();
				res.scalars = proof.scalarCount();
			}
		}

		res.genMean = Timing.mean(gen);
		res.genCi = Timing.ci95(gen, res.genMean);
		res.vrfMean = Timing.mean(vrf);
		res.vrfCi = Timing.ci95(vrf, res.vrfMean);
		return res;
	}

	public void run(int reps, int maxN, int[] rValues, String mSpec)
			throws NoSuchAlgorithmException {
		int[] Ns = BulletproofBenchmark.gridNs(maxN);
		int[] Ms = BulletproofBenchmark.resolveMs(Ns,
				Boolean.getBoolean("multisharp.noMul") ? "0" : mSpec);

		System.out.println("MultiSharp benchmarks: reps=" + reps + ", M=" + (mSpec == null ? "=N"
				: mSpec) + ", B=1024, S=" + S);
		System.out.println();

		for (int R : rValues) {
			Result[] rs = new Result[Ns.length];
			for (int i = 0; i < Ns.length; i++) {
				rs[i] = measure(Ns[i], Ms[i], R, reps);
				System.out.printf("  N=%-5d M=%-5d R=%-5d gen=%9.1f ms   vrf=%9.1f ms"
						+ "   size=%8.1f kB   (%d commitments, %d scalars, reps=%d)%n",
						Ns[i], Ms[i], R, rs[i].genMean, rs[i].vrfMean, rs[i].sizeKb,
						rs[i].commitments, rs[i].scalars, rs[i].reps);
			}
			System.out.println();
			System.out.println("  % LaTeX rows for R=" + R);
			System.out.print("  generation: " + R);
			for (Result r : rs) {
				System.out.print(" & \\confintv{" + DF1.format(r.genMean) + "}{"
						+ DF1.format(r.genCi) + "}");
			}
			System.out.println(" \\\\");
			System.out.print("  verification: " + R);
			for (Result r : rs) {
				System.out.print(" & \\confintv{" + DF1.format(r.vrfMean) + "}{"
						+ DF1.format(r.vrfCi) + "}");
			}
			System.out.println(" \\\\");
			System.out.print("  size: " + R);
			for (Result r : rs) {
				System.out.print(" & " + DF1.format(r.sizeKb));
			}
			System.out.println(" \\\\");
			System.out.println();
		}
	}

	public static void main(String[] args) throws NoSuchAlgorithmException {
		int reps = args.length > 0 ? Integer.parseInt(args[0]) : 10;
		int maxN = args.length > 1 ? Integer.parseInt(args[1]) : 64;
		int[] rValues = { 256 };
		if (args.length > 2) {
			String[] parts = args[2].split(",");
			rValues = new int[parts.length];
			for (int i = 0; i < parts.length; i++) {
				rValues[i] = Integer.parseInt(parts[i].trim());
			}
		}
		String mSpec = args.length > 3 ? args[3] : "=N";
		new MultiSharpExperiments().run(reps, maxN, rValues, mSpec);
	}
}
