package experiments;

public final class Timing {

	public static final int DISCARDED_BLOCKS =
			Integer.getInteger("bench.discardBlocks", 1);

	public static final int BLOCKS = DISCARDED_BLOCKS + 1;

	public static final long BURN_IN_MS = Long.getLong("bench.burnInMs", 6000L);

	public static final long MIN_BLOCK_MS = Long.getLong("bench.minBlockMs", 3000L);

	public static final int MAX_REPS = Integer.getInteger("bench.maxReps", 200);

	public static int scaleReps(int reps, double perIterMs) {
		if (MIN_BLOCK_MS <= 0 || !(perIterMs > 0)) {
			return reps;
		}
		int needed = (int) Math.ceil(MIN_BLOCK_MS / perIterMs);
		return Math.max(reps, Math.min(MAX_REPS, needed));
	}

	private static boolean burnedIn = false;

	public static boolean needsBurnIn() {
		return !burnedIn && BURN_IN_MS > 0;
	}

	public static void burnIn(Runnable work) {
		if (!needsBurnIn()) {
			burnedIn = true;
			return;
		}
		long start = System.nanoTime();
		long budget = BURN_IN_MS * 1_000_000L;
		long iterations = 0;
		while (System.nanoTime() - start < budget) {
			work.run();
			iterations++;
		}
		burnedIn = true;
		System.gc();
		System.out.printf("burn-in: %.1f s over %d iterations; timings follow%n",
				(System.nanoTime() - start) / 1e9, iterations);
	}

	public static double mean(double[] v) {
		double s = 0;
		for (double d : v) {
			s += d;
		}
		return s / v.length;
	}

	public static double ci95(double[] v, double m) {
		if (v.length < 2) {
			return 0;
		}
		double s = 0;
		for (double d : v) {
			s += (d - m) * (d - m);
		}
		return 1.96 * Math.sqrt(s / (v.length - 1)) / Math.sqrt(v.length);
	}

	public static double median(double[] v) {
		double[] c = v.clone();
		java.util.Arrays.sort(c);
		return c[c.length / 2];
	}

	private Timing() {
	}
}
