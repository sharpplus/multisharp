package experiments;

import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

import ec.ECPoint;
import zk.multisharp.CircuitBuilder;
import zk.multisharp.CircuitSpec;
import zk.multisharp.Form;
import zk.multisharp.MultiSharpParams;
import zk.multisharp.MultiSharpProof;
import zk.multisharp.MultiSharpProver;
import zk.multisharp.MultiSharpVerifier;

public class MultiSharpTests {

	private Random generator = new Random(20260805);

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

	static BigInteger[] randomBlindings(int n, Random rnd) {
		BigInteger[] r = new BigInteger[n];
		for (int i = 0; i < n; i++) {
			r[i] = new BigInteger(128, rnd);
		}
		return r;
	}

	static CircuitBuilder benchmarkCircuit(int S, int N, int M, Random rnd) {
		BigInteger[] xs = new BigInteger[S];
		for (int s = 0; s < S; s++) {
			xs[s] = BigInteger.valueOf(rnd.nextInt(17) - 8);
		}
		CircuitBuilder cb = new CircuitBuilder(xs);

		for (int i = 0; i < M; i++) {
			Form a = cb.wire(BigInteger.valueOf(rnd.nextInt(17) - 8));
			Form b = cb.wire(BigInteger.valueOf(rnd.nextInt(17) - 8));
			Form in1 = a.plus(S == 0 ? Form.ZERO : cb.statement(2 * i % Math.max(S, 1)));
			Form in2 = b.plus(S == 0 ? Form.ZERO : cb.statement((2 * i + 1) % Math.max(S, 1)));
			cb.multiply(in1, in2);
		}
		for (int i = 0; i < N; i++) {
			cb.assertNonNegative(cb.wire(BigInteger.valueOf(rnd.nextInt(1024))));
		}
		return cb;
	}

	public void run() throws NoSuchAlgorithmException {
		int S = 4;
		int N = 8;
		int M = 4;
		int R = 16;
		BigInteger B = BigInteger.valueOf(1024);

		CircuitBuilder cb = benchmarkCircuit(S, N, M, generator);
		CircuitSpec spec = cb.spec();
		BigInteger[] V = cb.witness();
		int T = cb.widestProductCount();
		BigInteger L = MultiSharpParams.maxL(N, M, B, 1, T);

		MultiSharpParams pp = MultiSharpParams.forCircuit(spec, R, B, L, T);

		System.out.println("MultiSharp tests (" + spec + ", R=" + R + ", T=" + T + ")");
		System.out.println("  L                       = " + L);
		System.out.println("  zeta window             = [" + pp.zetaFloor() + ", "
				+ pp.zetaBound() + "]");
		check("parameter constraint 4(T+1)*W3^2 < p", pp.checkParameterConstraint());
		check("acceptance window is non-empty", pp.checkAcceptanceWindow());

		{
			MultiSharpParams degenerate = MultiSharpParams.forCircuit(
					spec, R, B, pp.zetaFloor(), T);
			boolean rejected = !degenerate.checkAcceptanceWindow();
			try {
				new MultiSharpProver(degenerate, generator).prove(spec, V, randomBlindings(spec.S, generator));
				rejected = false;
			} catch (IllegalArgumentException expected) {
				rejected = rejected && expected.getMessage().contains("empty acceptance window");
			}
			check("empty acceptance window is reported", rejected);
		}

		BigInteger[] rx = randomBlindings(spec.S, generator);

		MultiSharpProver prover = new MultiSharpProver(pp, generator);
		MultiSharpVerifier verifier = new MultiSharpVerifier(pp);

		MultiSharpProof proof = prover.prove(spec, V, rx);
		ECPoint[] Cx = prover.getCx();

		check("honest proof verifies", verifier.verify(proof, Cx, spec));

		check("commitment count = 6+S", proof.commitmentCount() == 6 + spec.S);
		check("scalar count = 3+3R+3N+2S+U+E",
				proof.scalarCount() == 3 + 3 * R + 3 * N + 2 * spec.S + spec.U + spec.E());
		check("commitment count does not grow with the circuit",
				proof.commitmentCount() == 6 + S);

		check("first-phase rejection did not fire for an honest witness",
				prover.getLastAttempts() == 1);
		{
			boolean inWindow = true;
			for (BigInteger z : proof.getZeta()) {
				if (z.compareTo(pp.zetaFloor()) < 0 || z.compareTo(pp.zetaBound()) > 0) {
					inWindow = false;
				}
			}
			check("every zeta_k lies in the acceptance window", inWindow);
		}
		{
			MultiSharpProof low = prover.prove(spec, V, rx);
			low.getZeta()[0] = pp.zetaFloor().subtract(BigInteger.ONE);
			check("proof rejected when zeta_0 falls below the window",
					!verifier.verify(low, prover.getCx(), spec));
		}

		if (S >= 1) {
			ECPoint[] CxBad = Cx.clone();
			CxBad[0] = pp.pc.commit(V[0].add(BigInteger.ONE).mod(MultiSharpParams.P), rx[0]);
			check("proof rejected when C_{x,0} commits to a different value",
					!verifier.verify(proof, CxBad, spec));
		}

		if (S >= 2) {
			ECPoint[] CxShift = Cx.clone();
			BigInteger delta = BigInteger.valueOf(3);
			CxShift[0] = pp.pc.commit(V[0].add(delta).mod(MultiSharpParams.P), rx[0]);
			CxShift[1] = pp.pc.commit(V[1].subtract(delta).mod(MultiSharpParams.P), rx[1]);
			check("proof rejected when value is moved between C_{x,0} and C_{x,1}",
					!verifier.verify(proof, CxShift, spec));
		}

		MultiSharpProof tw = prover.prove(spec, V, rx);
		tw.getZw()[0] = tw.getZw()[0].add(BigInteger.ONE);
		check("proof rejected when a wire response is altered",
				!verifier.verify(tw, Cx, spec));

		MultiSharpProof t1 = prover.prove(spec, V, rx);
		t1.getZeta()[0] = t1.getZeta()[0].add(BigInteger.ONE);
		check("proof rejected when zeta_0 is altered", !verifier.verify(t1, Cx, spec));

		MultiSharpProof t2 = prover.prove(spec, V, rx);
		t2.getZeta()[0] = pp.zetaBound().add(BigInteger.ONE);
		check("proof rejected when zeta_0 leaves the window", !verifier.verify(t2, Cx, spec));

		if (M >= 1) {
			BigInteger[] Vbad = V.clone();
			Vbad[spec.S + 2] = Vbad[spec.S + 2].add(BigInteger.ONE);
			prover.setWitnessChecks(false);
			MultiSharpProof t3 = prover.prove(spec, Vbad, rx);
			prover.setWitnessChecks(true);
			check("proof rejected when a product wire is off by one",
					!verifier.verify(t3, prover.getCx(), spec));
		}

		if (M >= 1) {
			BigInteger[] Vwrap = V.clone();
			BigInteger a = MultiSharpParams.P.subtract(BigInteger.valueOf(7));
			BigInteger b = V[spec.S + 1];
			Vwrap[spec.S] = a;
			Vwrap[spec.S + 2] = MultiSharpParams.mod(
					a.add(V[0]).multiply(b.add(V[1 % Math.max(spec.S, 1)])));
			prover.setWitnessChecks(false);
			MultiSharpProof t5 = prover.prove(spec, Vwrap, rx);
			prover.setWitnessChecks(true);
			check("proof rejected when a multiplication input is a large residue",
					!verifier.verify(t5, prover.getCx(), spec));
		}

		MultiSharpProof t4 = prover.prove(spec, V, rx);
		ECPoint[] CxOther = Cx.clone();
		CxOther[spec.S - 1] = pp.pc.commit(MultiSharpParams.mod(V[spec.S - 1]),
				rx[spec.S - 1].add(BigInteger.ONE));
		check("proof rejected under a different statement (challenge is bound to it)",
				!verifier.verify(t4, CxOther, spec));

		check("a different circuit of the same shape is rejected",
				!verifier.verify(proof, Cx, alteredSpec(cb)));

		runLinearAssertionTests(R, B);

		System.out.println();
		System.out.println("passed: " + passed + ", failed: " + failed);
		if (failed > 0) {
			throw new AssertionError(failed + " test(s) failed");
		}
	}

	private static CircuitSpec alteredSpec(CircuitBuilder cb) {
		CircuitSpec base = cb.spec();
		CircuitBuilder alt = new CircuitBuilder(new BigInteger[base.S]);
		for (int u = 0; u < base.U; u++) {
			alt.wire(BigInteger.ZERO);
		}
		for (int i = 0; i < base.N(); i++) {
			alt.assertNonNegative(i == 0 ? base.rangeForm(i).plusConstant(1) : base.rangeForm(i));
		}
		for (int i = 0; i < base.M(); i++) {
			alt.assertProduct(base.mulForm(i, 0), base.mulForm(i, 1), base.mulForm(i, 2));
		}
		for (int e = 0; e < base.E(); e++) {
			alt.assertZero(base.linearForm(e));
		}
		return alt.spec();
	}

	private void runLinearAssertionTests(int R, BigInteger B) throws NoSuchAlgorithmException {
		BigInteger[] xs = { BigInteger.valueOf(11), BigInteger.valueOf(20) };
		CircuitBuilder cb = new CircuitBuilder(xs);
		Form a = cb.wire(BigInteger.valueOf(7));
		Form b = cb.wire(BigInteger.valueOf(4));
		cb.assertZero(cb.statement(0).plus(cb.statement(1)).minus(a).minus(b).plusConstant(-20));
		cb.assertNonNegative(a.minus(b));

		CircuitSpec spec = cb.spec();
		int T = cb.widestProductCount();
		BigInteger L = MultiSharpParams.maxL(spec.N(), spec.M(), B, 1, T);
		MultiSharpParams pp = MultiSharpParams.forCircuit(spec, R, B, L, T);
		MultiSharpProver prover = new MultiSharpProver(pp, generator);
		MultiSharpVerifier verifier = new MultiSharpVerifier(pp);

		BigInteger[] rx = randomBlindings(spec.S, generator);
		MultiSharpProof proof = prover.prove(spec, cb.witness(), rx);
		check("linear assertion: honest proof verifies",
				verifier.verify(proof, prover.getCx(), spec));
		check("linear assertion costs one scalar and no commitment",
				proof.commitmentCount() == 6 + spec.S && spec.E() == 1);

		proof.getEta()[0] = proof.getEta()[0].add(BigInteger.ONE);
		check("linear assertion: proof rejected when eta_0 is altered",
				!verifier.verify(proof, prover.getCx(), spec));

		BigInteger[] bad = cb.witness();
		bad[spec.S] = bad[spec.S].add(BigInteger.ONE);
		prover.setWitnessChecks(false);
		MultiSharpProof t = prover.prove(spec, bad, rx);
		prover.setWitnessChecks(true);
		check("linear assertion: proof rejected when the relation does not hold",
				!verifier.verify(t, prover.getCx(), spec));
	}

	public static void main(String[] args) throws NoSuchAlgorithmException {
		new MultiSharpTests().run();
	}
}
