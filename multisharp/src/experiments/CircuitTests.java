package experiments;

import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

import zk.multisharp.CircuitBuilder;
import zk.multisharp.CircuitSpec;
import zk.multisharp.Form;
import zk.multisharp.MultiSharpParams;
import zk.multisharp.MultiSharpProof;
import zk.multisharp.MultiSharpProver;
import zk.multisharp.MultiSharpVerifier;

public class CircuitTests {

	private static final BigInteger B = BigInteger.valueOf(1 << 16);

	private static final int R = 16;

	private final Random generator = new Random(20260812);

	private int passed = 0;
	private int failed = 0;

	private void check(String name, boolean condition) {
		if (condition) {
			passed++;
			System.out.println("  [ ok ] " + name);
		} else {
			failed++;
			System.out.println("  [FAIL] " + name);
		}
	}

	private MultiSharpProof roundTrip(String name, CircuitBuilder cb)
			throws NoSuchAlgorithmException {
		CircuitSpec spec = cb.spec();
		int T = cb.widestProductCount();
		BigInteger L = MultiSharpParams.maxL(spec.N(), spec.M(), B, 1, T);
		MultiSharpParams pp = MultiSharpParams.forCircuit(spec, R, B, L, T);
		if (!pp.checkParameterConstraint()) {
			throw new IllegalStateException("parameter constraint violated for " + name);
		}

		MultiSharpProver prover = new MultiSharpProver(pp, generator);
		MultiSharpVerifier verifier = new MultiSharpVerifier(pp);
		BigInteger[] rx = MultiSharpTests.randomBlindings(spec.S, generator);

		MultiSharpProof proof = prover.prove(spec, cb.witness(), rx);
		boolean ok = verifier.verify(proof, prover.getCx(), spec);
		check(name + " " + spec + " T=" + T, ok);
		return proof;
	}

	private static BigInteger bi(long v) {
		return BigInteger.valueOf(v);
	}

	private void runGadgetTests() throws NoSuchAlgorithmException {
		System.out.println("Gadgets");

		{
			CircuitBuilder cb = new CircuitBuilder(new BigInteger[] { bi(12), bi(5) });
			Form p = cb.multiply(cb.statement(0).plusConstant(3), cb.statement(1).negate());
			check("  multiply evaluates correctly", cb.eval(p).equals(bi(-75)));
			cb.assertNonNegative(p.negate());
			roundTrip("  multiplication", cb);
		}

		{
			CircuitBuilder cb = new CircuitBuilder(new BigInteger[] { bi(7) });
			int before = cb.U();
			cb.assertProductZero(cb.statement(0).minus(Form.constant(7)),
					cb.statement(0).plusConstant(1));
			check("  constant-product node allocates no wire", cb.U() == before);
			roundTrip("  constant product", cb);
		}

		{
			CircuitBuilder cb = new CircuitBuilder(new BigInteger[0]);
			Form b0 = cb.bit(false);
			Form b1 = cb.bit(true);
			cb.assertZero(b0.plus(b1).plusConstant(-1));
			roundTrip("  bit decomposition", cb);
		}

		{
			CircuitBuilder cb = new CircuitBuilder(new BigInteger[] { bi(6), bi(7) });
			cb.assertProductEquals(cb.statement(0), cb.statement(1), bi(42));
			roundTrip("  product equal to a constant", cb);
		}

		{
			CircuitBuilder cb = new CircuitBuilder(new BigInteger[0]);
			cb.bit(true);
			CircuitSpec spec = cb.spec();
			BigInteger L = MultiSharpParams.maxL(spec.N(), spec.M(), B, 1, 1);
			MultiSharpParams pp = MultiSharpParams.forCircuit(spec, R, B, L, 1);
			MultiSharpProver prover = new MultiSharpProver(pp, generator);
			MultiSharpVerifier verifier = new MultiSharpVerifier(pp);
			BigInteger[] bad = cb.witness();
			bad[0] = bi(2);
			prover.setWitnessChecks(false);
			MultiSharpProof proof = prover.prove(spec, bad, new BigInteger[0]);
			prover.setWitnessChecks(true);
			check("  a wire that is not a bit is rejected",
					!verifier.verify(proof, prover.getCx(), spec));
		}

		{
			CircuitBuilder cb = new CircuitBuilder(new BigInteger[] { bi(84), bi(7) });
			Form q = cb.divideExact(cb.statement(0), cb.statement(1));
			check("  divideExact evaluates correctly", cb.eval(q).equals(bi(12)));
			roundTrip("  exact division", cb);
		}

		{
			CircuitBuilder cb = new CircuitBuilder(new BigInteger[] { bi(84), bi(7) });
			int wires = cb.U();
			cb.assertNonZero(cb.statement(1));
			CircuitSpec spec = cb.spec();
			check("  assertNonZero costs two wires, one product and one range proof",
					cb.U() - wires == 2 && spec.M() == 1 && spec.N() == 1);
			roundTrip("  non-zero divisor, positive", cb);
		}
		{
			CircuitBuilder cb = new CircuitBuilder(new BigInteger[] { bi(84), bi(-7) });
			Form q = cb.divideExact(cb.statement(0), cb.statement(1));
			cb.assertNonZero(cb.statement(1));
			check("  divideExact with a negative divisor", cb.eval(q).equals(bi(-12)));
			roundTrip("  non-zero divisor, negative", cb);
		}
		{
			CircuitBuilder cb = new CircuitBuilder(new BigInteger[] { bi(84), bi(7) });
			int wires = cb.U();
			cb.assertPositive(cb.statement(1));
			cb.divideExact(cb.statement(0), cb.statement(1));
			CircuitSpec spec = cb.spec();
			check("  a positive divisor costs one range proof and no wire",
					cb.U() - wires == 1 && spec.M() == 1 && spec.N() == 1);
			roundTrip("  exact division, divisor proved positive", cb);
		}
		{
			CircuitBuilder cb = new CircuitBuilder(new BigInteger[] { bi(84), bi(5) });
			cb.assertNonZero(cb.statement(1));
			CircuitSpec spec = cb.spec();
			BigInteger L = MultiSharpParams.maxL(spec.N(), spec.M(), B, 1, 1);
			MultiSharpParams pp = MultiSharpParams.forCircuit(spec, R, B, L, 1);
			MultiSharpProver prover = new MultiSharpProver(pp, generator);
			MultiSharpVerifier verifier = new MultiSharpVerifier(pp);
			BigInteger[] bad = cb.witness();
			bad[1] = BigInteger.ZERO;
			prover.setWitnessChecks(false);
			MultiSharpProof proof = prover.prove(spec, bad, MultiSharpTests.randomBlindings(spec.S, generator));
			prover.setWitnessChecks(true);
			check("  a zero form cannot be certified non-zero",
					!verifier.verify(proof, prover.getCx(), spec));
		}
		{
			CircuitBuilder cb = new CircuitBuilder(new BigInteger[] { bi(87), bi(7) });
			Form q = cb.divideFloor(cb.statement(0), cb.statement(1));
			check("  divideFloor evaluates correctly", cb.eval(q).equals(bi(12)));
			roundTrip("  floor division", cb);
		}
		{
			CircuitBuilder cb = new CircuitBuilder(new BigInteger[] { bi(-87), bi(7) });
			Form q = cb.divideFloor(cb.statement(0), cb.statement(1));
			check("  divideFloor rounds towards minus infinity", cb.eval(q).equals(bi(-13)));
			roundTrip("  floor division, negative dividend", cb);
		}

		{
			CircuitBuilder cb = new CircuitBuilder(new BigInteger[] { bi(1764) });
			Form r = cb.sqrtExact(cb.statement(0));
			check("  sqrtExact evaluates correctly", cb.eval(r).equals(bi(42)));
			roundTrip("  exact square root", cb);
		}
		{
			CircuitBuilder cb = new CircuitBuilder(new BigInteger[] { bi(1800) });
			Form r = cb.sqrtFloor(cb.statement(0));
			check("  sqrtFloor evaluates correctly", cb.eval(r).equals(bi(42)));
			roundTrip("  floor square root", cb);
		}
		{
			CircuitBuilder cb = new CircuitBuilder(new BigInteger[] { bi(1764) });
			Form r = cb.sqrtFloor(cb.statement(0));
			check("  sqrtFloor is exact on a perfect square", cb.eval(r).equals(bi(42)));
			roundTrip("  floor square root of a square", cb);
		}

		{
			CircuitBuilder cb = new CircuitBuilder(new BigInteger[] { bi(-40), bi(17) });
			Form mn = cb.min(cb.statement(0), cb.statement(1));
			Form mx = cb.max(cb.statement(0), cb.statement(1));
			Form ab = cb.abs(cb.statement(0));
			check("  min evaluates correctly", cb.eval(mn).equals(bi(-40)));
			check("  max evaluates correctly", cb.eval(mx).equals(bi(17)));
			check("  abs evaluates correctly", cb.eval(ab).equals(bi(40)));
			roundTrip("  min/max/abs", cb);
		}

		{
			CircuitBuilder cb = new CircuitBuilder(new BigInteger[] { bi(-9), bi(4), bi(11) });
			Form s0 = cb.signBit(cb.statement(0));
			Form s1 = cb.signBit(cb.statement(1));
			Form s2 = cb.signBit(cb.statement(2));
			check("  signBit reads the signs", cb.eval(s0).signum() == 0
					&& cb.eval(s1).equals(BigInteger.ONE) && cb.eval(s2).equals(BigInteger.ONE));
			cb.assertZero(s0.plus(s1).plus(s2).plusConstant(-2));
			roundTrip("  sign bits", cb);
		}
		{
			CircuitBuilder cb = new CircuitBuilder(new BigInteger[] { bi(3), bi(4) });
			cb.assertOdd(cb.statement(0).plus(cb.statement(1)));
			roundTrip("  parity assertion", cb);
		}

		{
			CircuitBuilder cb = new CircuitBuilder(new BigInteger[] { bi(6), bi(5) });
			Form a = cb.multiply(cb.statement(0), cb.statement(1));
			Form b = cb.multiply(a.plusConstant(2), cb.statement(0));
			Form c = cb.min(b, a.scale(7));
			Form d = cb.sqrtFloor(c.plus(cb.statement(1)));
			check("  chained nodes evaluate correctly",
					cb.eval(a).equals(bi(30)) && cb.eval(b).equals(bi(192))
							&& cb.eval(c).equals(bi(192)) && cb.eval(d).equals(bi(14)));
			cb.assertNonNegative(d.plusConstant(-10));
			roundTrip("  composition of depth four", cb);
		}

		{
			int n = 4;
			BigInteger[] xs = new BigInteger[n];
			for (int i = 0; i < n; i++) {
				xs[i] = bi(3);
			}
			CircuitBuilder cb = new CircuitBuilder(xs);
			Form sum = Form.ZERO;
			for (int i = 0; i < n; i++) {
				sum = sum.plus(cb.square(cb.statement(i)));
			}
			Form r = cb.sqrtExact(sum);
			check("  sqrtExact over a sum of products evaluates correctly",
					cb.eval(sum).equals(bi(36)) && cb.eval(r).equals(bi(6)));
			check("  a wide output form is counted in T", cb.widestProductCount() == n);
			roundTrip("  exact square root of a sum of products", cb);
		}
		{
			CircuitBuilder cb = new CircuitBuilder(new BigInteger[] { bi(4), bi(6), bi(2) });
			Form sum = cb.square(cb.statement(0)).plus(cb.square(cb.statement(1)));
			Form q = cb.divideExact(sum, cb.statement(2));
			cb.assertPositive(cb.statement(2));
			check("  a wide dividend is counted in T",
					cb.eval(q).equals(bi(26)) && cb.widestProductCount() == 2);
			roundTrip("  exact division by a wide dividend", cb);
		}
	}

	static CircuitBuilder polygonCircuit(long[][] poly, long latStar, long lonStar) {
		int n = poly.length;
		CircuitBuilder cb = new CircuitBuilder(new BigInteger[] { bi(latStar), bi(lonStar) });
		Form lat = cb.statement(0);
		Form lon = cb.statement(1);

		Form count = Form.ZERO;
		for (int i = 0; i < n; i++) {
			long[] prev = poly[(i + n - 1) % n];
			long[] cur = poly[i];
			long latA = prev[0];
			long lonA = prev[1];
			long latB = cur[0];
			long lonB = cur[1];

			Form d1 = cb.multiply(lat.negate().plusConstant(latA), lat.plusConstant(-latB));

			Form d2 = lat.plusConstant(-latB).scale(lonA - lonB)
					.plus(lon.negate().plusConstant(lonB).scale(latA - latB));

			long sigma = latA > latB ? 1 : -1;
			Form dmin = cb.min(d1, d2.scale(sigma));

			count = count.plus(cb.signBit(dmin));
		}

		cb.assertOdd(count);
		return cb;
	}

	private static boolean rayCast(long[][] poly, long latStar, long lonStar) {
		int n = poly.length;
		int crossings = 0;
		for (int i = 0; i < n; i++) {
			long[] prev = poly[(i + n - 1) % n];
			long[] cur = poly[i];
			long d1 = (prev[0] - latStar) * (latStar - cur[0]);
			long d2 = (prev[1] - cur[1]) * (latStar - cur[0])
					+ (cur[1] - lonStar) * (prev[0] - cur[0]);
			long sigma = prev[0] > cur[0] ? 1 : -1;
			if (Math.min(d1, sigma * d2) > 0) {
				crossings++;
			}
		}
		return crossings % 2 == 1;
	}

	private void runLocationTests() throws NoSuchAlgorithmException {
		System.out.println("Use case I: verifiable locations");

		long[][] poly = { { 10, 10 }, { 10, 90 }, { 50, 90 }, { 50, 50 }, { 90, 50 },
				{ 90, 10 } };

		check("  reference: (30,40) is inside", rayCast(poly, 30, 40));
		check("  reference: (70,70) is outside", !rayCast(poly, 70, 70));
		check("  reference: (70,30) is inside", rayCast(poly, 70, 30));

		CircuitBuilder cb = polygonCircuit(poly, 30, 40);
		CircuitSpec spec = cb.spec();
		int n = poly.length;
		check("  S = 2 regardless of the number of edges", spec.S == 2);
		check("  U = 4n+1, N = 3n+1, M = 4n, E = 1",
				spec.U == 4 * n + 1 && spec.N() == 3 * n + 1 && spec.M() == 4 * n
						&& spec.E() == 1);
		check("  T = 1", cb.widestProductCount() == 1);
		MultiSharpProof proof = roundTrip("  point inside the polygon", cb);
		check("  transcript carries 8 group elements for a 6-edge polygon",
				proof.commitmentCount() == 8);

		roundTrip("  a second interior point", polygonCircuit(poly, 70, 30));

		boolean threw = false;
		try {
			polygonCircuit(poly, 70, 70);
		} catch (ArithmeticException ex) {
			threw = true;
		}
		check("  an exterior point has no parity witness", threw);

		long[][] big = new long[24][2];
		for (int i = 0; i < big.length; i++) {
			double a = 2 * Math.PI * i / big.length;
			big[i][0] = 100 + Math.round(80 * Math.cos(a));
			big[i][1] = 100 + Math.round(80 * Math.sin(a));
		}
		check("  reference: centre is inside the 24-gon", rayCast(big, 101, 103));
		CircuitBuilder cbBig = polygonCircuit(big, 101, 103);
		MultiSharpProof bigProof = roundTrip("  24-edge polygon", cbBig);
		check("  24-edge polygon still carries 8 group elements",
				bigProof.commitmentCount() == 8);
		check("  scalars grow, commitments do not",
				bigProof.scalarCount() > proof.scalarCount()
						&& bigProof.commitmentCount() == proof.commitmentCount());
	}

	static CircuitBuilder biometricCircuit(long[] base, long[] fresh, String metric,
			long theta) {
		int n = base.length;
		BigInteger[] xs = new BigInteger[2 * n];
		for (int i = 0; i < n; i++) {
			xs[i] = bi(base[i]);
			xs[n + i] = bi(fresh[i]);
		}
		CircuitBuilder cb = new CircuitBuilder(xs);

		Form[] d = new Form[n];
		for (int i = 0; i < n; i++) {
			d[i] = cb.statement(i).minus(cb.statement(n + i));
		}

		Form distance;
		switch (metric) {
		case "manhattan": {
			Form sum = Form.ZERO;
			for (int i = 0; i < n; i++) {
				sum = sum.plus(cb.abs(d[i]));
			}
			distance = sum;
			break;
		}
		case "euclidean": {
			Form sum = Form.ZERO;
			for (int i = 0; i < n; i++) {
				sum = sum.plus(cb.square(d[i]));
			}
			distance = cb.sqrtFloor(sum);
			break;
		}
		case "chebyshev": {
			Form m = cb.abs(d[0]);
			for (int i = 1; i < n; i++) {
				m = cb.max(m, cb.abs(d[i]));
			}
			distance = m;
			break;
		}
		default:
			throw new IllegalArgumentException(metric);
		}

		cb.assertNonNegative(Form.constant(theta).minus(distance));
		return cb;
	}

	private void runBiometricTests() throws NoSuchAlgorithmException {
		System.out.println("Use case II: verifiable biometrics");

		int n = 8;
		long[] base = new long[n];
		long[] fresh = new long[n];
		long manhattan = 0;
		long sumsq = 0;
		long chebyshev = 0;
		for (int i = 0; i < n; i++) {
			base[i] = 40 + 17 * i;
			fresh[i] = base[i] + (i % 2 == 0 ? 3 : -5);
			long diff = Math.abs(base[i] - fresh[i]);
			manhattan += diff;
			sumsq += diff * diff;
			chebyshev = Math.max(chebyshev, diff);
		}
		long euclidean = (long) Math.sqrt(sumsq);

		CircuitBuilder man = biometricCircuit(base, fresh, "manhattan", manhattan);
		check("  S = 2n for every metric", man.spec().S == 2 * n);
		roundTrip("  Manhattan distance at the threshold", man);
		roundTrip("  Manhattan distance below the threshold",
				biometricCircuit(base, fresh, "manhattan", manhattan + 10));

		CircuitBuilder euc = biometricCircuit(base, fresh, "euclidean", euclidean);
		roundTrip("  Euclidean distance at the threshold", euc);

		CircuitBuilder che = biometricCircuit(base, fresh, "chebyshev", chebyshev);
		roundTrip("  Chebyshev distance at the threshold", che);

		boolean threw = false;
		try {
			CircuitBuilder over = biometricCircuit(base, fresh, "manhattan", manhattan - 1);
			roundTrip("  (should not get here)", over);
		} catch (IllegalArgumentException ex) {
			threw = true;
		}
		check("  a distance above the threshold has no witness", threw);

		check("  Manhattan has T = 1", man.widestProductCount() == 1);
		check("  Chebyshev has T = 1", che.widestProductCount() == 1);
		check("  Euclidean has T = n+1", euc.widestProductCount() == n + 1);

		BigInteger l1 = MultiSharpParams.maxL(euc.spec().N(), euc.spec().M(), B, 1, 1);
		BigInteger lT = MultiSharpParams.maxL(euc.spec().N(), euc.spec().M(), B, 1, n + 1);
		check("  a larger T costs bits of L", lT.compareTo(l1) < 0
				&& l1.bitLength() - lT.bitLength() <= 4);
	}

	public void run() throws NoSuchAlgorithmException {
		runGadgetTests();
		System.out.println();
		runLocationTests();
		System.out.println();
		runBiometricTests();

		System.out.println();
		System.out.println("passed: " + passed + ", failed: " + failed);
		if (failed > 0) {
			throw new AssertionError(failed + " test(s) failed");
		}
	}

	public static void main(String[] args) throws NoSuchAlgorithmException {
		new CircuitTests().run();
	}
}
