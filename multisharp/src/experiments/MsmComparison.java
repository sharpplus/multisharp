package experiments;

import java.security.NoSuchAlgorithmException;

import zk.multisharp.MultiCommitment;

public class MsmComparison {

	private static final int PASSES = Integer.getInteger("bench.passes", 3);

	public static void main(String[] args) throws NoSuchAlgorithmException {
		int reps = args.length > 0 ? Integer.parseInt(args[0]) : 10;
		int maxN = args.length > 1 ? Integer.parseInt(args[1]) : 1024;
		String rSpec = args.length > 2 ? args[2] : "128,256,512,1024";

		int[] Ns = BulletproofBenchmark.gridNs(maxN);
		String[] parts = rSpec.split(",");
		int[] Rs = new int[parts.length];
		for (int i = 0; i < parts.length; i++) {
			Rs[i] = Integer.parseInt(parts[i].trim());
		}

		MultiSharpExperiments bucket = new MultiSharpExperiments();
		MultiSharpExperiments naive = new MultiSharpExperiments();

		double[][][] bGen = new double[PASSES][Rs.length][Ns.length];
		double[][][] nGen = new double[PASSES][Rs.length][Ns.length];
		double[][][] bVrf = new double[PASSES][Rs.length][Ns.length];
		double[][][] nVrf = new double[PASSES][Rs.length][Ns.length];

		System.out.println("Bucket method against naive evaluation, one scalar multiplication"
				+ " per coordinate. reps=" + reps + ", M=N, B=1024, S=2, passes=" + PASSES + ".");

		long t0 = System.nanoTime();
		for (int p = 0; p < PASSES; p++) {
			System.out.printf("  pass %d of %d (%.1f min elapsed)%n", p + 1, PASSES,
					(System.nanoTime() - t0) / 6e10);
			for (int r = 0; r < Rs.length; r++) {
				for (int i = 0; i < Ns.length; i++) {
					MultiCommitment.naiveMsm = false;
					MultiSharpExperiments.Result b = bucket.measure(Ns[i], Ns[i], Rs[r], reps);
					MultiCommitment.naiveMsm = true;
					MultiSharpExperiments.Result n = naive.measure(Ns[i], Ns[i], Rs[r], reps);
					MultiCommitment.naiveMsm = false;

					bGen[p][r][i] = b.genMean;
					nGen[p][r][i] = n.genMean;
					bVrf[p][r][i] = b.vrfMean;
					nVrf[p][r][i] = n.vrfMean;
				}
			}
		}
		System.out.printf("  done in %.1f min%n%n", (System.nanoTime() - t0) / 6e10);

		System.out.printf("%6s %6s  %10s %10s %7s  %10s %10s %7s%n", "R", "N", "gen buck",
				"gen naive", "ratio", "vrf buck", "vrf naive", "ratio");

		double worstGen = 0;
		double bestGen = Double.MAX_VALUE;
		double worstVrf = 0;
		double bestVrf = Double.MAX_VALUE;

		for (int r = 0; r < Rs.length; r++) {
			for (int i = 0; i < Ns.length; i++) {
				double[] bg = new double[PASSES];
				double[] ng = new double[PASSES];
				double[] bv = new double[PASSES];
				double[] nv = new double[PASSES];
				for (int p = 0; p < PASSES; p++) {
					bg[p] = bGen[p][r][i];
					ng[p] = nGen[p][r][i];
					bv[p] = bVrf[p][r][i];
					nv[p] = nVrf[p][r][i];
				}
				double b = Timing.median(bg);
				double n = Timing.median(ng);
				double bvm = Timing.median(bv);
				double nvm = Timing.median(nv);
				double gr = n / b;
				double vr = nvm / bvm;

				worstGen = Math.max(worstGen, gr);
				bestGen = Math.min(bestGen, gr);
				worstVrf = Math.max(worstVrf, vr);
				bestVrf = Math.min(bestVrf, vr);

				System.out.printf("%6d %6d  %10.1f %10.1f %6.2fx  %10.1f %10.1f %6.2fx%n",
						Rs[r], Ns[i], b, n, gr, bvm, nvm, vr);
			}
		}

		System.out.println();
		System.out.printf("naive/bucket over the whole grid: generation %.1f--%.1f times"
				+ " slower, verification %.1f--%.1f times slower%n",
				bestGen, worstGen, bestVrf, worstVrf);
	}
}
