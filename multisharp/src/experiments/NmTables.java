package experiments;

import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;

public class NmTables {

	private static final DecimalFormat DF1 = new DecimalFormat("0.0");
	private static final DecimalFormat DF2 = new DecimalFormat("0.00");

	private static final int S = Integer.getInteger("multisharp.S", 2);

	private static final int PASSES = Integer.getInteger("bench.passes", 3);

	private MultiSharpExperiments.Result[][][] res;
	private BulletproofBenchmark.AggResult[][][] bpr;
	private boolean withBp;

	private static String ci(double v, double w) {
		return "\\confintv{" + DF1.format(v) + "}{" + DF1.format(w) + "}";
	}

	public void run(int reps, int[] Ns, int[] Ms, int R, int bitsize)
			throws NoSuchAlgorithmException {

		withBp = bitsize > 0;

		System.out.println("N against M at fixed R: reps=" + reps + ", R=" + R
				+ ", B=1024, S=" + S + ", passes=" + PASSES
				+ (withBp ? ", Bulletproofs arm at K=" + bitsize : ", MultiSharp only"));
		System.out.println("N = " + join(Ns));
		System.out.println("M = " + join(Ms));
		System.out.println("One process; each cell is the median of " + PASSES + " passes.");
		System.out.println();

		res = new MultiSharpExperiments.Result[PASSES][Ns.length][Ms.length];
		bpr = new BulletproofBenchmark.AggResult[PASSES][Ns.length][Ms.length];
		MultiSharpExperiments ms = new MultiSharpExperiments();
		BulletproofBenchmark bp = new BulletproofBenchmark();

		long t0 = System.nanoTime();
		for (int pass = 0; pass < PASSES; pass++) {
			System.out.printf("  pass %d of %d (%.1f min elapsed)%n", pass + 1, PASSES,
					(System.nanoTime() - t0) / 6e10);
			for (int i = 0; i < Ns.length; i++) {
				for (int j = 0; j < Ms.length; j++) {
					if (withBp) {
						bpr[pass][i][j] =
								bp.measureAggregated(Ns[i], Ms[j], S, reps, bitsize, 0, 1024);
					}
					res[pass][i][j] = ms.measure(Ns[i], Ms[j], R, reps);
				}
			}
		}
		System.out.printf("  done in %.1f min%n", (System.nanoTime() - t0) / 6e10);
		System.out.println();

		emit(Ns, Ms, R, bitsize);
	}

	private double gen(int i, int j) {
		double[] v = new double[PASSES];
		for (int p = 0; p < PASSES; p++) {
			v[p] = res[p][i][j].genMean;
		}
		return Timing.median(v);
	}

	private double genCi(int i, int j) {
		double[] v = new double[PASSES];
		for (int p = 0; p < PASSES; p++) {
			v[p] = res[p][i][j].genCi;
		}
		return Timing.median(v);
	}

	private double vrf(int i, int j) {
		double[] v = new double[PASSES];
		for (int p = 0; p < PASSES; p++) {
			v[p] = res[p][i][j].vrfMean;
		}
		return Timing.median(v);
	}

	private double vrfCi(int i, int j) {
		double[] v = new double[PASSES];
		for (int p = 0; p < PASSES; p++) {
			v[p] = res[p][i][j].vrfCi;
		}
		return Timing.median(v);
	}

	private double size(int i, int j) {
		double[] v = new double[PASSES];
		for (int p = 0; p < PASSES; p++) {
			v[p] = res[p][i][j].sizeKb;
		}
		return Timing.median(v);
	}

	private double genSpeedup(int i, int j) {
		double[] v = new double[PASSES];
		for (int p = 0; p < PASSES; p++) {
			v[p] = bpr[p][i][j].genMean / res[p][i][j].genMean;
		}
		return Timing.median(v);
	}

	private double vrfSpeedup(int i, int j) {
		double[] v = new double[PASSES];
		for (int p = 0; p < PASSES; p++) {
			v[p] = bpr[p][i][j].vrfMean / res[p][i][j].vrfMean;
		}
		return Timing.median(v);
	}

	private double bpGen(int i, int j) {
		double[] v = new double[PASSES];
		for (int p = 0; p < PASSES; p++) {
			v[p] = bpr[p][i][j].genMean;
		}
		return Timing.median(v);
	}

	private double bpVrf(int i, int j) {
		double[] v = new double[PASSES];
		for (int p = 0; p < PASSES; p++) {
			v[p] = bpr[p][i][j].vrfMean;
		}
		return Timing.median(v);
	}

	private void emit(int[] Ns, int[] Ms, int R, int bitsize) {
		String cols = "r|";
		for (int j = 0; j < Ms.length; j++) {
			cols += "c";
		}

		header("generation time (ms)", R, Ms, cols);
		for (int i = 0; i < Ns.length; i++) {
			StringBuilder sb = new StringBuilder("  " + Ns[i]);
			for (int j = 0; j < Ms.length; j++) {
				sb.append(" & ").append(ci(gen(i, j), genCi(i, j)));
			}
			System.out.println(sb.append(" \\\\").toString());
		}
		footer();

		header("verification time (ms)", R, Ms, cols);
		for (int i = 0; i < Ns.length; i++) {
			StringBuilder sb = new StringBuilder("  " + Ns[i]);
			for (int j = 0; j < Ms.length; j++) {
				sb.append(" & ").append(ci(vrf(i, j), vrfCi(i, j)));
			}
			System.out.println(sb.append(" \\\\").toString());
		}
		footer();

		header("transcript size (kB)", R, Ms, cols);
		for (int i = 0; i < Ns.length; i++) {
			StringBuilder sb = new StringBuilder("  " + Ns[i]);
			for (int j = 0; j < Ms.length; j++) {
				sb.append(" & ").append(DF1.format(size(i, j)));
			}
			System.out.println(sb.append(" \\\\").toString());
		}
		footer();

		if (withBp) {
			header("generation speed-up over aggregated Bulletproofs, K=" + bitsize,
					R, Ms, cols);
			for (int i = 0; i < Ns.length; i++) {
				StringBuilder sb = new StringBuilder("  " + Ns[i]);
				for (int j = 0; j < Ms.length; j++) {
					sb.append(" & ").append(speed(genSpeedup(i, j)));
				}
				System.out.println(sb.append(" \\\\").toString());
			}
			footer();

			header("verification speed-up over aggregated Bulletproofs, K=" + bitsize,
					R, Ms, cols);
			for (int i = 0; i < Ns.length; i++) {
				StringBuilder sb = new StringBuilder("  " + Ns[i]);
				for (int j = 0; j < Ms.length; j++) {
					sb.append(" & ").append(speed(vrfSpeedup(i, j)));
				}
				System.out.println(sb.append(" \\\\").toString());
			}
			footer();

			System.out.println("%%% Bulletproofs raw times (ms), generation then"
					+ " verification, for checking");
			for (int i = 0; i < Ns.length; i++) {
				StringBuilder sb = new StringBuilder("  N=" + Ns[i] + " gen:");
				for (int j = 0; j < Ms.length; j++) {
					sb.append(" ").append(DF1.format(bpGen(i, j)));
				}
				sb.append("   vrf:");
				for (int j = 0; j < Ms.length; j++) {
					sb.append(" ").append(DF1.format(bpVrf(i, j)));
				}
				System.out.println(sb.toString());
			}
			System.out.println();

			System.out.println("%%% crossover: smallest M at which MultiSharp leads,"
					+ " per row");
			for (int i = 0; i < Ns.length; i++) {
				System.out.printf("  N=%-5d generation: %-8s verification: %s%n", Ns[i],
						crossover(Ms, i, true), crossover(Ms, i, false));
			}
			System.out.println();
		}

		System.out.println("%%% diagonal (M=N), for checking against the R=" + R
				+ " row of the scalability tables");
		System.out.print("  generation:  ");
		for (int i = 0; i < Ns.length; i++) {
			int j = indexOf(Ms, Ns[i]);
			System.out.print(j < 0 ? " & --" : " & " + DF1.format(gen(i, j)));
		}
		System.out.println(" \\\\");
		System.out.print("  verification:");
		for (int i = 0; i < Ns.length; i++) {
			int j = indexOf(Ms, Ns[i]);
			System.out.print(j < 0 ? " & --" : " & " + DF1.format(vrf(i, j)));
		}
		System.out.println(" \\\\");
		System.out.print("  size:        ");
		for (int i = 0; i < Ns.length; i++) {
			int j = indexOf(Ms, Ns[i]);
			System.out.print(j < 0 ? " & --" : " & " + DF1.format(size(i, j)));
		}
		System.out.println(" \\\\");
		System.out.println();

		if (Ns.length > 1 && Ms.length > 1) {
			int iLo = 0;
			int iHi = Ns.length - 1;
			int jLo = 0;
			int jHi = Ms.length - 1;
			double perN = (gen(iHi, jLo) - gen(iLo, jLo)) / (Ns[iHi] - Ns[iLo]);
			double perM = (gen(iLo, jHi) - gen(iLo, jLo)) / (Ms[jHi] - Ms[jLo]);
			System.out.printf("%%%%%% marginal generation cost at the grid edges:"
					+ " %.4f ms per range proof (M=%d), %.4f ms per multiplication"
					+ " proof (N=%d)%n", perN, Ms[jLo], perM, Ns[iLo]);
			System.out.printf("%%%%%% fixed cost at N=%d, M=%d: %.1f ms generation,"
					+ " %.1f ms verification, %.1f kB%n",
					Ns[iLo], Ms[jLo], gen(iLo, jLo), vrf(iLo, jLo), size(iLo, jLo));
		}
	}

	private static String speed(double x) {
		return x < 10 ? DF2.format(x) : DF1.format(x);
	}

	private String crossover(int[] Ms, int i, boolean generation) {
		for (int j = 0; j < Ms.length; j++) {
			double s = generation ? genSpeedup(i, j) : vrfSpeedup(i, j);
			if (s >= 1.0) {
				return "M=" + Ms[j];
			}
		}
		return "none";
	}

	private static void header(String what, int R, int[] Ms, String cols) {
		System.out.println("%%% " + what + " at R=" + R);
		System.out.println("\\begin{tabular}{" + cols + "}");
		System.out.println(" & \\multicolumn{" + Ms.length + "}{c}{$M$} \\\\");
		StringBuilder sb = new StringBuilder(" $N$");
		for (int m : Ms) {
			sb.append(" & ").append(m);
		}
		System.out.println(sb.append(" \\\\ \\toprule").toString());
	}

	private static void footer() {
		System.out.println("\\bottomrule");
		System.out.println("\\end{tabular}");
		System.out.println();
	}

	private static int indexOf(int[] a, int v) {
		for (int i = 0; i < a.length; i++) {
			if (a[i] == v) {
				return i;
			}
		}
		return -1;
	}

	private static String join(int[] a) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < a.length; i++) {
			sb.append(i == 0 ? "" : ",").append(a[i]);
		}
		return sb.toString();
	}

	private static int[] axis(String spec) {
		if (spec.indexOf(',') < 0) {
			return BulletproofBenchmark.gridNs(Integer.parseInt(spec.trim()));
		}
		String[] parts = spec.split(",");
		int[] v = new int[parts.length];
		for (int i = 0; i < parts.length; i++) {
			v[i] = Integer.parseInt(parts[i].trim());
			if (v[i] < 0) {
				throw new IllegalArgumentException("negative axis value: " + v[i]);
			}
		}
		return v;
	}

	public static void main(String[] args) throws NoSuchAlgorithmException {
		int reps = args.length > 0 ? Integer.parseInt(args[0]) : 10;
		int[] Ns = axis(args.length > 1 ? args[1] : "1024");
		int[] Ms = axis(args.length > 2 ? args[2] : "1024");
		int R = args.length > 3 ? Integer.parseInt(args[3]) : 256;
		int bitsize = args.length > 4 ? Integer.parseInt(args[4]) : 0;
		new NmTables().run(reps, Ns, Ms, R, bitsize);
	}
}
