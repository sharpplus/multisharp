package experiments;

import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.util.Random;

import zk.bulletproofs.BpProof;
import zk.bulletproofs.BulletProofGenerators;
import zk.bulletproofs.PedersenCommitment;

public class BulletproofBenchmark {

	private static final DecimalFormat DF1 = new DecimalFormat("0.0");

	private static final int[] ALL_NS = { 1, 4, 16, 64, 256, 1024 };

	private final Random generator = new Random(20260805);

	private static BulletProofGenerators aggGens(int capacity) {
		try {
			return new BulletProofGenerators(capacity, 1);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static void burnIn() {
		if (!Timing.needsBurnIn()) {
			return;
		}
		Random rnd = new Random(1);
		int n = 2;
		int m = 2;
		int bitsize = 11;
		long min = 0;
		long max = 1024;
		PedersenCommitment pc;
		try {
			pc = PedersenCommitment.getDefault();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		BigInteger[] values = new BigInteger[n];
		for (int i = 0; i < n; i++) {
			values[i] = BigInteger.valueOf(min + rnd.nextInt((int) (max - min)));
		}
		BigInteger[] xp1 = new BigInteger[m];
		BigInteger[] xp2 = new BigInteger[m];
		for (int i = 0; i < m; i++) {
			xp1[i] = BigInteger.valueOf(rnd.nextInt(65) - 32);
			xp2[i] = BigInteger.valueOf(rnd.nextInt(65) - 32);
		}
		final int s = 2;
		int capacity = AggregatedBulletproofs.requiredCapacity(n, m, s, bitsize);
		Timing.burnIn(() -> {
			BpProof p = AggregatedBulletproofs.generate(n, m, s, values, min, max, bitsize, xp1,
					xp2, pc, aggGens(capacity));
			if (p == null || !AggregatedBulletproofs.verify(n, m, s, min, max, bitsize, p, pc,
					aggGens(capacity))) {
				throw new IllegalStateException("burn-in aggregated proof failed to verify");
			}
		});
	}

	public static class AggResult {
		public int N, M, gates, capacity, commitments;
		public double genMean, genCi, vrfMean, vrfCi;
		public double proofKb, commitKb, totalKb;
		public int reps;
	}

	public AggResult measureAggregated(int N, int M, int S, int reps, int bitsize, long min,
			long max) throws NoSuchAlgorithmException {
		burnIn();
		PedersenCommitment pc = PedersenCommitment.getDefault();

		BigInteger[] values = new BigInteger[N];
		for (int i = 0; i < N; i++) {
			values[i] = BigInteger.valueOf(min + generator.nextInt((int) (max - min)));
		}
		BigInteger[] xp1 = randomSignedValues(M, 32);
		BigInteger[] xp2 = randomSignedValues(M, 32);

		int capacity = AggregatedBulletproofs.requiredCapacity(N, M, S, bitsize);
		int gensCapacity = capacity;

		long warmNanos = 0;
		for (int w = 0; w < 2; w++) {
			BulletProofGenerators wp = aggGens(gensCapacity);
			BulletProofGenerators wv = aggGens(gensCapacity);
			long s = System.nanoTime();
			BpProof warm = AggregatedBulletproofs.generate(N, M, S, values, min, max, bitsize,
							xp1, xp2, pc, wp);
			boolean ok = warm != null && AggregatedBulletproofs.verify(N, M, S, min, max, bitsize, warm, pc,
							wv);
			warmNanos += System.nanoTime() - s;
			if (!ok) {
				throw new IllegalStateException("warm-up aggregated proof failed");
			}
		}
		reps = Timing.scaleReps(reps, warmNanos / 2e6);
		System.gc();

		double[] gen = new double[reps];
		double[] vrf = new double[reps];
		AggResult r = new AggResult();
		r.reps = reps;

		for (int blk = 0; blk < Timing.BLOCKS; blk++) {
			for (int t = 0; t < reps; t++) {
				BulletProofGenerators gp = aggGens(gensCapacity);
				BulletProofGenerators gv = aggGens(gensCapacity);

				long g0 = System.nanoTime();
				BpProof proof = AggregatedBulletproofs.generate(N, M, S, values, min, max,
								bitsize, xp1, xp2, pc, gp);
				long g1 = System.nanoTime();

				long v0 = System.nanoTime();
				boolean ok = AggregatedBulletproofs.verify(N, M, S, min, max, bitsize, proof,
								pc, gv);
				long v1 = System.nanoTime();

				if (!ok) {
					throw new IllegalStateException("honest aggregated proof failed to verify");
				}

				gen[t] = (g1 - g0) / 1e6;
				vrf[t] = (v1 - v0) / 1e6;

				int proofOnly = AggregatedBulletproofs.proofOnlySize(proof);
				r.proofKb = proofOnly / 1024.0;
				r.commitKb = (proof.size() - proofOnly) / 1024.0;
				r.totalKb = proof.size() / 1024.0;
				r.commitments = proof.getCommitments().size();
			}
		}

		r.N = N;
		r.M = M;
		r.gates = AggregatedBulletproofs.gateCount(N, M, S, bitsize);
		r.capacity = capacity;
		r.genMean = Timing.mean(gen);
		r.genCi = Timing.ci95(gen, r.genMean);
		r.vrfMean = Timing.mean(vrf);
		r.vrfCi = Timing.ci95(vrf, r.vrfMean);
		return r;
	}

	private BigInteger[] randomSignedValues(int n, int maxVal) {
		BigInteger[] v = new BigInteger[n];
		for (int i = 0; i < n; i++) {
			v[i] = BigInteger.valueOf(generator.nextInt(2 * maxVal + 1) - maxVal);
		}
		return v;
	}

	public static int[] resolveMs(int[] Ns, String mSpec) {
		int[] Ms = new int[Ns.length];
		if (mSpec == null || "=N".equalsIgnoreCase(mSpec)) {
			System.arraycopy(Ns, 0, Ms, 0, Ns.length);
			return Ms;
		}
		String[] parts = mSpec.split(",");
		if (parts.length == 1) {
			int m = Integer.parseInt(parts[0].trim());
			for (int i = 0; i < Ns.length; i++) {
				Ms[i] = m;
			}
			return Ms;
		}
		if (parts.length != Ns.length) {
			throw new IllegalArgumentException("Mspec has " + parts.length + " entries but there are "
					+ Ns.length + " values of N");
		}
		for (int i = 0; i < parts.length; i++) {
			Ms[i] = Integer.parseInt(parts[i].trim());
		}
		return Ms;
	}

	public static int[] gridNs(int maxN) {
		int count = 0;
		for (int n : ALL_NS) {
			if (n <= maxN) {
				count++;
			}
		}
		int[] Ns = new int[count];
		System.arraycopy(ALL_NS, 0, Ns, 0, count);
		return Ns;
	}

}
