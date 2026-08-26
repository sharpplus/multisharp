package experiments;

import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import ec.ECPoint;
import zk.multisharp.CircuitBuilder;
import zk.multisharp.CircuitSpec;
import zk.multisharp.MultiSharpParams;
import zk.multisharp.MultiSharpProof;
import zk.multisharp.MultiSharpProver;
import zk.multisharp.MultiSharpVerifier;

public class CircuitWorkloads {

	private static final DecimalFormat DF1 = new DecimalFormat("0.0");

	private static final int WORD = 32;

	private static final int PASSES = Integer.getInteger("bench.passes", 3);

	private static int failures = 0;

	private static void expect(String what, int got, int want) {
		if (got != want) {
			failures++;
			System.out.printf("  [FAIL] %s: compiler says %d, closed form says %d%n",
					what, got, want);
		}
	}

	private static int elems(CircuitSpec s) {
		return 6 + s.S;
	}

	private static int scalars(CircuitSpec s, int R) {
		return 3 + 3 * R + 3 * s.N() + 2 * s.S + s.U + s.E();
	}

	private static int pvElems(CircuitSpec s) {
		return 4 + s.S + 2 * s.U;
	}

	private static int pvScalars(CircuitSpec s, int R) {
		return 2 + 3 * R + 3 * s.N() + 2 * (s.S + s.U) + s.E();
	}

	private static String kb(int elements, int scalarCount) {
		return DF1.format((elements + scalarCount) * (double) WORD / 1024.0);
	}

	private static CircuitBuilder polygon(int n) {
		long[][] poly = new long[n][2];
		for (int i = 0; i < n; i++) {
			double a = 2 * Math.PI * i / n;
			poly[i][0] = 100 + Math.round(80 * Math.cos(a));
			poly[i][1] = 100 + Math.round(80 * Math.sin(a));
		}
		for (int d = 0; d < 64; d++) {
			try {
				return CircuitTests.polygonCircuit(poly, 100 + 2 * d + 1, 100 + d);
			} catch (ArithmeticException ex) {
			}
		}
		throw new IllegalStateException("no interior point in general position for n=" + n);
	}

	private static CircuitBuilder biometric(int n, String metric) {
		long[] base = new long[n];
		long[] fresh = new long[n];
		for (int i = 0; i < n; i++) {
			base[i] = i % 7;
			fresh[i] = (3 * i) % 7;
		}
		long manhattan = 0;
		long sumsq = 0;
		long chebyshev = 0;
		for (int i = 0; i < n; i++) {
			long diff = Math.abs(base[i] - fresh[i]);
			manhattan += diff;
			sumsq += diff * diff;
			chebyshev = Math.max(chebyshev, diff);
		}
		long theta;
		switch (metric) {
		case "manhattan":
			theta = manhattan;
			break;
		case "euclidean":
			theta = (long) Math.sqrt(sumsq);
			break;
		case "chebyshev":
			theta = chebyshev;
			break;
		default:
			throw new IllegalArgumentException(metric);
		}
		return CircuitTests.biometricCircuit(base, fresh, metric, theta);
	}

	private static final class Workload {
		final String family;
		final int n;
		final CircuitBuilder cb;
		double gen = -1;
		double vrf = -1;

		Workload(String family, int n, CircuitBuilder cb) {
			this.family = family;
			this.n = n;
			this.cb = cb;
		}
	}

	private static double[] measure(CircuitBuilder cb, BigInteger B, int R, int reps,
			Random rnd) throws NoSuchAlgorithmException {
		CircuitSpec spec = cb.spec();
		BigInteger[] V = cb.witness();
		int T = cb.widestProductCount();
		MultiSharpParams pp = MultiSharpParams.forCircuit(spec, R, B,
				MultiSharpParams.maxL(spec.N(), spec.M(), B, 1, T), T);
		if (!pp.checkParameterConstraint()) {
			throw new IllegalStateException("parameter constraint violated");
		}
		MultiSharpProver prover = new MultiSharpProver(pp, rnd);
		MultiSharpVerifier verifier = new MultiSharpVerifier(pp);
		BigInteger[] rx = new BigInteger[spec.S];
		for (int i = 0; i < rx.length; i++) {
			rx[i] = new BigInteger(128, rnd);
		}

		long warm = System.nanoTime();
		for (int t = 0; t < 2; t++) {
			MultiSharpProof w = prover.prove(spec, V, rx);
			if (!verifier.verify(w, prover.getCx(), spec)) {
				throw new IllegalStateException("warm-up proof failed to verify");
			}
		}
		reps = Timing.scaleReps(reps, (System.nanoTime() - warm) / 2e6);
		System.gc();

		double[] gen = new double[reps];
		double[] vrf = new double[reps];
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
					throw new IllegalStateException("honest proof failed to verify");
				}
				gen[t] = (t1 - t0) / 1e6;
				vrf[t] = (t3 - t2) / 1e6;
			}
		}
		return new double[] { Timing.mean(gen), Timing.mean(vrf) };
	}

	private static void emit(List<Workload> ws, int R, boolean timed) {
		String last = null;
		for (Workload w : ws) {
			if (!w.family.equals(last)) {
				System.out.println("% " + w.family);
				last = w.family;
			}
			CircuitSpec s = w.cb.spec();
			int pe = pvElems(s);
			int ps = pvScalars(s, R);
			int e = elems(s);
			int sc = scalars(s, R);
			System.out.printf(" & %-3d & %-3d & %-3d & %-3d & %-3d & %d & %-4d & %-4d & %-5s"
					+ " & \\textbf{%-3d} & %-4d & \\textbf{%s}", w.n, s.S, s.U, s.N(), s.M(),
					s.E(), pe, ps, kb(pe, ps), e, sc, kb(e, sc));
			if (timed) {
				System.out.printf(" & %s & %s", DF1.format(w.gen), DF1.format(w.vrf));
			}
			System.out.println(" \\\\");
		}
	}

	private static List<Workload> compile() {
		List<Workload> ws = new ArrayList<>();
		for (int n : new int[] { 8, 16, 32, 64 }) {
			CircuitBuilder cb = polygon(n);
			CircuitSpec s = cb.spec();
			expect("polygon n=" + n + " S", s.S, 2);
			expect("polygon n=" + n + " U", s.U, 4 * n + 1);
			expect("polygon n=" + n + " N", s.N(), 3 * n + 1);
			expect("polygon n=" + n + " M", s.M(), 4 * n);
			expect("polygon n=" + n + " E", s.E(), 1);
			expect("polygon n=" + n + " T", cb.widestProductCount(), 1);
			ws.add(new Workload("Polygon", n, cb));
		}
		for (int n : new int[] { 32, 64, 128 }) {
			CircuitBuilder cb = biometric(n, "manhattan");
			CircuitSpec s = cb.spec();
			expect("manhattan n=" + n + " S", s.S, 2 * n);
			expect("manhattan n=" + n + " U", s.U, n);
			expect("manhattan n=" + n + " N", s.N(), 2 * n + 1);
			expect("manhattan n=" + n + " M", s.M(), n);
			expect("manhattan n=" + n + " T", cb.widestProductCount(), 1);
			ws.add(new Workload("Manhattan", n, cb));
		}
		for (int n : new int[] { 32, 64, 128 }) {
			CircuitBuilder cb = biometric(n, "euclidean");
			CircuitSpec s = cb.spec();
			expect("euclidean n=" + n + " S", s.S, 2 * n);
			expect("euclidean n=" + n + " U", s.U, n + 3);
			expect("euclidean n=" + n + " N", s.N(), 3);
			expect("euclidean n=" + n + " M", s.M(), n + 2);
			expect("euclidean n=" + n + " T", cb.widestProductCount(), n + 1);
			ws.add(new Workload("Euclidean", n, cb));
		}
		for (int n : new int[] { 32, 64, 128 }) {
			CircuitBuilder cb = biometric(n, "chebyshev");
			CircuitSpec s = cb.spec();
			expect("chebyshev n=" + n + " S", s.S, 2 * n);
			expect("chebyshev n=" + n + " U", s.U, 2 * n - 1);
			expect("chebyshev n=" + n + " N", s.N(), 4 * n - 1);
			expect("chebyshev n=" + n + " M", s.M(), 2 * n - 1);
			expect("chebyshev n=" + n + " T", cb.widestProductCount(), 1);
			ws.add(new Workload("Chebyshev", n, cb));
		}
		return ws;
	}

	public static void main(String[] args) throws NoSuchAlgorithmException {
		boolean timed = args.length > 0 && args[0].equals("measure");
		int R = args.length > 1 ? Integer.parseInt(args[1]) : 256;
		int reps = args.length > 2 ? Integer.parseInt(args[2]) : 10;

		List<Workload> ws = compile();

		BigInteger need = BigInteger.ZERO;
		for (Workload w : ws) {
			need = need.max(w.cb.requiredB());
		}
		BigInteger B = BigInteger.ONE;
		while (B.compareTo(need) < 0) {
			B = B.shiftLeft(1);
		}
		String override = System.getProperty("workloads.B");
		if (override != null) {
			BigInteger forced = BigInteger.ONE.shiftLeft(Integer.parseInt(override));
			if (forced.compareTo(B) < 0) {
				throw new IllegalArgumentException("workloads.B below what the circuits need");
			}
			B = forced;
		}

		System.out.println("Circuit dimensions and transcript composition, R=" + R
				+ (timed ? "; timings are the median of " + PASSES + " passes" : ""));
		System.out.println("B = " + B + " (smallest power of two bounding every covered form,"
				+ " the largest of which is " + need + ")");
		System.out.println("Columns: n, S, U, N, M, E | per-value elem, scal, kB |"
				+ " MultiSharp elem, scal, kB" + (timed ? " | gen ms, vrf ms" : ""));
		System.out.println();

		if (timed) {
			Random rnd = new Random(20260805);
			if (Timing.needsBurnIn()) {
				CircuitBuilder small = biometric(8, "manhattan");
				BigInteger burnB = B;
				Timing.burnIn(() -> {
					try {
						measure(small, burnB, 32, 1, new Random(1));
					} catch (NoSuchAlgorithmException ex) {
						throw new IllegalStateException(ex);
					}
				});
			}
			double[][] g = new double[ws.size()][PASSES];
			double[][] v = new double[ws.size()][PASSES];
			for (int pass = 0; pass < PASSES; pass++) {
				System.out.printf("%% pass %d of %d%n", pass + 1, PASSES);
				for (int i = 0; i < ws.size(); i++) {
					double[] r = measure(ws.get(i).cb, B, R, reps, rnd);
					g[i][pass] = r[0];
					v[i][pass] = r[1];
				}
			}
			for (int i = 0; i < ws.size(); i++) {
				ws.get(i).gen = Timing.median(g[i]);
				ws.get(i).vrf = Timing.median(v[i]);
			}
			System.out.println();
		}

		emit(ws, R, timed);

		System.out.println();
		if (failures == 0) {
			System.out.println("closed forms agree with the compiler on every row");
		} else {
			System.out.println(failures + " disagreement(s) between the compiler and the "
					+ "closed forms");
			System.exit(1);
		}
	}
}
