package experiments;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;

public class CombinedTables {

	private static final DecimalFormat DF1 = new DecimalFormat("0.0");

	private static final int S = Integer.getInteger("multisharp.S", 2);

	private static final int PASSES = Integer.getInteger("bench.passes", 3);

	private static String ci(double v, double w) {
		return "\\confintv{" + DF1.format(v) + "}{" + DF1.format(w) + "}";
	}

	private BulletproofBenchmark.AggResult[][] acr;
	private MultiSharpExperiments.Result[][][] msr;

	public void run(int reps, int maxN, int rCompare, int[] rScale, int bitsize)
			throws NoSuchAlgorithmException, IOException {

		int[] Ns = BulletproofBenchmark.gridNs(maxN);
		int compareRow = indexOf(rScale, rCompare);
		if (compareRow < 0) {
			throw new IllegalArgumentException(
					"the comparison R must appear in the scalability list");
		}

		System.out.println("Combined tables: reps=" + reps + ", bitsize=" + bitsize
				+ ", B=1024, S=" + S + ", M=N, passes=" + PASSES);
		System.out.println("Comparison at R=" + rCompare + "; scalability over R="
				+ join(rScale) + ". One process; each cell is the median of "
				+ PASSES + " passes.");
		System.out.println();

		acr = new BulletproofBenchmark.AggResult[PASSES][Ns.length];
		msr = new MultiSharpExperiments.Result[PASSES][rScale.length][Ns.length];

		BulletproofBenchmark bp = new BulletproofBenchmark();
		MultiSharpExperiments ms = new MultiSharpExperiments();

		long t0 = System.nanoTime();
		for (int pass = 0; pass < PASSES; pass++) {
			System.out.printf("  pass %d of %d (%.1f min elapsed)%n", pass + 1, PASSES,
					(System.nanoTime() - t0) / 6e10);

			for (int i = 0; i < Ns.length; i++) {
				int N = Ns[i];
				acr[pass][i] = bp.measureAggregated(N, N, S, reps, bitsize, 0, 1024);
				msr[pass][compareRow][i] = ms.measure(N, N, rCompare, reps);
			}
			for (int r = 0; r < rScale.length; r++) {
				if (r == compareRow) {
					continue;
				}
				for (int i = 0; i < Ns.length; i++) {
					msr[pass][r][i] = ms.measure(Ns[i], Ns[i], rScale[r], reps);
				}
			}
		}
		System.out.printf("  done in %.1f min%n", (System.nanoTime() - t0) / 6e10);

		emit(Ns, rScale, rCompare, compareRow);
	}

	private double agGen(BulletproofBenchmark.AggResult[][] a, int i) {
		double[] v = new double[PASSES];
		for (int p = 0; p < PASSES; p++) {
			v[p] = a[p][i].genMean;
		}
		return Timing.median(v);
	}

	private double agGenCi(BulletproofBenchmark.AggResult[][] a, int i) {
		double[] v = new double[PASSES];
		for (int p = 0; p < PASSES; p++) {
			v[p] = a[p][i].genCi;
		}
		return Timing.median(v);
	}

	private double agVrf(BulletproofBenchmark.AggResult[][] a, int i) {
		double[] v = new double[PASSES];
		for (int p = 0; p < PASSES; p++) {
			v[p] = a[p][i].vrfMean;
		}
		return Timing.median(v);
	}

	private double agVrfCi(BulletproofBenchmark.AggResult[][] a, int i) {
		double[] v = new double[PASSES];
		for (int p = 0; p < PASSES; p++) {
			v[p] = a[p][i].vrfCi;
		}
		return Timing.median(v);
	}

	private double agSize(BulletproofBenchmark.AggResult[][] a, int i) {
		double[] v = new double[PASSES];
		for (int p = 0; p < PASSES; p++) {
			v[p] = a[p][i].totalKb;
		}
		return Timing.median(v);
	}

	private double msGen(int r, int i) {
		double[] v = new double[PASSES];
		for (int p = 0; p < PASSES; p++) {
			v[p] = msr[p][r][i].genMean;
		}
		return Timing.median(v);
	}

	private double msGenCi(int r, int i) {
		double[] v = new double[PASSES];
		for (int p = 0; p < PASSES; p++) {
			v[p] = msr[p][r][i].genCi;
		}
		return Timing.median(v);
	}

	private double msVrf(int r, int i) {
		double[] v = new double[PASSES];
		for (int p = 0; p < PASSES; p++) {
			v[p] = msr[p][r][i].vrfMean;
		}
		return Timing.median(v);
	}

	private double msVrfCi(int r, int i) {
		double[] v = new double[PASSES];
		for (int p = 0; p < PASSES; p++) {
			v[p] = msr[p][r][i].vrfCi;
		}
		return Timing.median(v);
	}

	private double msSize(int r, int i) {
		double[] v = new double[PASSES];
		for (int p = 0; p < PASSES; p++) {
			v[p] = msr[p][r][i].sizeKb;
		}
		return Timing.median(v);
	}

	private void emit(int[] Ns, int[] rScale, int rCompare, int compareRow) {
		System.out.println();
		System.out.println("%%% Tables I-III: comparison at R=" + rCompare);
		System.out.println();

		System.out.println("  generation:");
		row("Bulletproofs", Ns.length, i -> ci(agGen(acr, i), agGenCi(acr, i)));
		row("\\textbf{\\name{}}", Ns.length,
				i -> ci(msGen(compareRow, i), msGenCi(compareRow, i)));

		System.out.println();
		System.out.println("  verification:");
		row("Bulletproofs", Ns.length, i -> ci(agVrf(acr, i), agVrfCi(acr, i)));
		row("\\textbf{\\name{}}", Ns.length,
				i -> ci(msVrf(compareRow, i), msVrfCi(compareRow, i)));

		System.out.println();
		System.out.println("  size:");
		row("Bulletproofs", Ns.length, i -> DF1.format(agSize(acr, i)));
		row("\\textbf{\\name{}}", Ns.length, i -> DF1.format(msSize(compareRow, i)));

		System.out.println();
		System.out.println("%%% Tables IV-VI: \\name{} scalability. The R=" + rCompare
				+ " row is the same measurement as the comparison tables above.");

		System.out.println();
		System.out.println("  generation:");
		for (int r = 0; r < rScale.length; r++) {
			final int rr = r;
			row(String.valueOf(rScale[r]), Ns.length, i -> ci(msGen(rr, i), msGenCi(rr, i)));
		}
		System.out.println();
		System.out.println("  verification:");
		for (int r = 0; r < rScale.length; r++) {
			final int rr = r;
			row(String.valueOf(rScale[r]), Ns.length, i -> ci(msVrf(rr, i), msVrfCi(rr, i)));
		}
		System.out.println();
		System.out.println("  size:");
		for (int r = 0; r < rScale.length; r++) {
			final int rr = r;
			row(String.valueOf(rScale[r]), Ns.length, i -> DF1.format(msSize(rr, i)));
		}

		System.out.println();
		System.out.println("%%% ratios at R=" + rCompare + " (BP aggregated circuit / \\name{})");
		for (int i = 0; i < Ns.length; i++) {
			System.out.printf("  N=%-5d gen=%6.2fx  vrf=%6.2fx  size(MS/BP)=%6.1fx%n", Ns[i],
					agGen(acr, i) / msGen(compareRow, i),
					agVrf(acr, i) / msVrf(compareRow, i),
					msSize(compareRow, i) / agSize(acr, i));
		}

		System.out.println();
		System.out.println("%%% \\name{} generation at R=" + rCompare
				+ ", per pass (max/min across passes)");
		for (int i = 0; i < Ns.length; i++) {
			double lo = Double.MAX_VALUE;
			double hi = 0;
			StringBuilder sb = new StringBuilder();
			for (int p = 0; p < PASSES; p++) {
				double g = msr[p][compareRow][i].genMean;
				lo = Math.min(lo, g);
				hi = Math.max(hi, g);
				sb.append(String.format("%8.1f", g));
			}
			System.out.printf("  N=%-5d%s   spread=%.2fx%n", Ns[i], sb, hi / lo);
		}
	}

	private interface Cell {
		String get(int i);
	}

	private static void row(String label, int n, Cell c) {
		StringBuilder sb = new StringBuilder(label);
		for (int i = 0; i < n; i++) {
			sb.append(" & ").append(c.get(i));
		}
		System.out.println("  " + sb + " \\\\");
	}

	private static int indexOf(int[] v, int x) {
		for (int i = 0; i < v.length; i++) {
			if (v[i] == x) {
				return i;
			}
		}
		return -1;
	}

	private static String join(int[] v) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < v.length; i++) {
			sb.append(i > 0 ? "," : "").append(v[i]);
		}
		return sb.toString();
	}

	public static void main(String[] args) throws NoSuchAlgorithmException, IOException {
		int reps = args.length > 0 ? Integer.parseInt(args[0]) : 10;
		int maxN = args.length > 1 ? Integer.parseInt(args[1]) : 1024;
		int rCompare = args.length > 2 ? Integer.parseInt(args[2]) : 256;
		String rSpec = args.length > 3 ? args[3] : "128,256,512,1024";
		int bitsize = args.length > 4 ? Integer.parseInt(args[4]) : 11;

		String[] parts = rSpec.split(",");
		int[] rScale = new int[parts.length];
		for (int i = 0; i < parts.length; i++) {
			rScale[i] = Integer.parseInt(parts[i].trim());
		}
		new CombinedTables().run(reps, maxN, rCompare, rScale, bitsize);
	}
}
