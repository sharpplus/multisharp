package experiments;

import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

import zk.multisharp.MultiSharpParams;
import zk.sharp.SharpProof;
import zk.sharp.SharpProver;
import zk.sharp.SharpVerifier;

public class BaselineBenchmark {

	private final Random generator = new Random(20260805);

	public void run(int reps, int N, int R) throws NoSuchAlgorithmException {
		BigInteger B = BigInteger.valueOf(1024);
		BigInteger L = MultiSharpParams.maxL(N, 0, B, 1);

		BigInteger[] x = new BigInteger[N];
		for (int i = 0; i < N; i++) {
			x[i] = BigInteger.valueOf(generator.nextInt(1024));
		}
		BigInteger r_x = new BigInteger(128, generator);

		SharpProver prover = new SharpProver();
		SharpVerifier verifier = new SharpVerifier();

		if (Timing.needsBurnIn()) {
			Timing.burnIn(() -> {
				SharpProof p = prover.generateRangeProof(x, r_x, B, R, L);
				if (!verifier.verify(p, prover.getCx(), B, L)) {
					throw new IllegalStateException("burn-in baseline proof failed to verify");
				}
			});
		}

		long warmStart = System.nanoTime();
		int warmIters = 0;
		for (int w = 0; w < 2 || System.nanoTime() - warmStart < 1_500_000_000L; w++) {
			SharpProof warm = prover.generateRangeProof(x, r_x, B, R, L);
			if (!verifier.verify(warm, prover.getCx(), B, L)) {
				throw new IllegalStateException("warm-up baseline proof failed");
			}
			warmIters++;
		}
		reps = Timing.scaleReps(reps, (System.nanoTime() - warmStart) / (warmIters * 1e6));
		System.gc();

		double[] gen = new double[reps];
		double[] vrf = new double[reps];
		long size = 0;

		for (int blk = 0; blk < Timing.BLOCKS; blk++) {
			for (int t = 0; t < reps; t++) {
				long t0 = System.nanoTime();
				SharpProof proof = prover.generateRangeProof(x, r_x, B, R, L);
				long t1 = System.nanoTime();

				long t2 = System.nanoTime();
				boolean ok = verifier.verify(proof, prover.getCx(), B, L);
				long t3 = System.nanoTime();

				if (!ok) {
					throw new IllegalStateException("honest baseline proof failed to verify");
				}
				gen[t] = (t1 - t0) / 1e6;
				vrf[t] = (t3 - t2) / 1e6;
				size = proof.size();
			}
		}

		double gm = Timing.mean(gen);
		double vm = Timing.mean(vrf);
		System.out.printf(
				"collapsed baseline: N=%d R=%d  gen=%.1f (+-%.1f) ms  vrf=%.1f (+-%.1f) ms"
						+ "  size=%.1f kB%n",
				N, R, gm, Timing.ci95(gen, gm), vm, Timing.ci95(vrf, vm), size / 1024.0);

		MultiSharpExperiments.Result ms = new MultiSharpExperiments().measure(N, 0, R, reps);
		System.out.printf(
				"\\name{} (M=0):      N=%d R=%d  gen=%.1f (+-%.1f) ms  vrf=%.1f (+-%.1f) ms"
						+ "  size=%.1f kB%n",
				N, R, ms.genMean, ms.genCi, ms.vrfMean, ms.vrfCi, ms.sizeKb);
		System.out.printf("ratio (\\name{}/baseline): gen=%.1fx  vrf=%.1fx  size=%.1fx%n",
				ms.genMean / gm, ms.vrfMean / vm, ms.sizeKb / (size / 1024.0));
	}

	public static void main(String[] args) throws NoSuchAlgorithmException {
		int reps = args.length > 0 ? Integer.parseInt(args[0]) : 10;
		int N = args.length > 1 ? Integer.parseInt(args[1]) : 1024;
		int R = args.length > 2 ? Integer.parseInt(args[2]) : 256;
		new BaselineBenchmark().run(reps, N, R);
	}
}
