package zk.multisharp;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CircuitBuilder {

	private final BigInteger[] statementValues;

	private final List<BigInteger> wireValues = new ArrayList<>();

	private final List<Form> range = new ArrayList<>();

	private final List<Form[]> mul = new ArrayList<>();

	private final List<Form> linear = new ArrayList<>();

	private final Set<Integer> mulOutputWires = new HashSet<>();

	public CircuitBuilder(BigInteger[] statementValues) {
		this.statementValues = statementValues.clone();
	}

	public int S() {
		return statementValues.length;
	}

	public int U() {
		return wireValues.size();
	}

	public Form statement(int s) {
		if (s < 0 || s >= statementValues.length) {
			throw new IndexOutOfBoundsException("statement value " + s);
		}
		return Form.var(s);
	}

	public Form[] statements() {
		Form[] f = new Form[statementValues.length];
		for (int s = 0; s < f.length; s++) {
			f[s] = Form.var(s);
		}
		return f;
	}

	public BigInteger eval(Form f) {
		return f.eval(currentValues());
	}

	private BigInteger[] currentValues() {
		BigInteger[] v = new BigInteger[statementValues.length + wireValues.size()];
		System.arraycopy(statementValues, 0, v, 0, statementValues.length);
		for (int u = 0; u < wireValues.size(); u++) {
			v[statementValues.length + u] = wireValues.get(u);
		}
		return v;
	}

	public Form wire(BigInteger value) {
		wireValues.add(value);
		return Form.var(statementValues.length + wireValues.size() - 1);
	}

	public void assertNonNegative(Form phi) {
		range.add(phi);
	}

	public void assertAtLeast(Form phi, BigInteger k) {
		range.add(phi.plusConstant(k.negate()));
	}

	public void assertPositive(Form phi) {
		range.add(phi.plusConstant(-1));
	}

	public void assertZero(Form phi) {
		linear.add(phi);
	}

	public void assertEqual(Form a, Form b) {
		linear.add(a.minus(b));
	}

	public void assertProduct(Form phi1, Form phi2, Form phi3) {
		mul.add(new Form[] { phi1, phi2, phi3 });
		int t = soleUnitVariable(phi3);
		if (t >= 0) {
			mulOutputWires.add(t);
		}
	}

	public void assertProductEquals(Form phi1, Form phi2, BigInteger k) {
		assertProduct(phi1, phi2, Form.constant(k));
	}

	public void assertProductZero(Form phi1, Form phi2) {
		assertProduct(phi1, phi2, Form.ZERO);
	}

	private static int soleUnitVariable(Form phi) {
		if (phi.terms() != 1 || phi.constantTerm().signum() != 0) {
			return -1;
		}
		return phi.coefficientAt(0).equals(BigInteger.ONE) ? phi.indexAt(0) : -1;
	}

	public Form multiply(Form phi1, Form phi2) {
		BigInteger[] v = currentValues();
		Form w = wire(phi1.eval(v).multiply(phi2.eval(v)));
		assertProduct(phi1, phi2, w);
		return w;
	}

	public Form square(Form phi) {
		return multiply(phi, phi);
	}

	public void assertNonZero(Form phi) {
		BigInteger v = eval(phi);
		if (v.signum() == 0) {
			throw new ArithmeticException("assertNonZero on a form that evaluates to 0");
		}
		Form sigma = wire(BigInteger.valueOf(v.signum()));
		assertPositive(multiply(phi, sigma));
	}

	public Form divideExact(Form phi1, Form phi2) {
		BigInteger[] v = currentValues();
		BigInteger a = phi1.eval(v);
		BigInteger b = phi2.eval(v);
		if (b.signum() == 0) {
			throw new ArithmeticException("division by a form that evaluates to 0");
		}
		BigInteger[] qr = a.divideAndRemainder(b);
		if (qr[1].signum() != 0) {
			throw new ArithmeticException("divideExact on a non-integer quotient " + a + "/" + b);
		}
		Form w = wire(qr[0]);
		assertProduct(w, phi2, phi1);
		return w;
	}

	public Form divideFloor(Form phi1, Form phi2) {
		BigInteger[] v = currentValues();
		BigInteger a = phi1.eval(v);
		BigInteger b = phi2.eval(v);
		if (b.signum() <= 0) {
			throw new ArithmeticException("divideFloor requires a positive divisor, got " + b);
		}
		BigInteger q = a.subtract(a.mod(b)).divide(b);

		Form w = wire(q);
		Form w1 = wire(b.multiply(q));
		Form w2 = wire(b.multiply(q.add(BigInteger.ONE)));
		assertProduct(phi2, w, w1);
		assertProduct(phi2, w.plusConstant(1), w2);
		assertNonNegative(phi1.minus(w1));
		assertNonNegative(w2.plusConstant(-1).minus(phi1));
		return w;
	}

	public Form sqrtExact(Form phi) {
		BigInteger a = eval(phi);
		if (a.signum() < 0) {
			throw new ArithmeticException("square root of a negative value");
		}
		BigInteger r = a.sqrt();
		if (!r.multiply(r).equals(a)) {
			throw new ArithmeticException("sqrtExact on a non-square " + a);
		}
		Form w = wire(r);
		assertProduct(w, w, phi);
		assertNonNegative(w);
		return w;
	}

	public Form sqrtFloor(Form phi) {
		BigInteger a = eval(phi);
		if (a.signum() < 0) {
			throw new ArithmeticException("square root of a negative value");
		}
		BigInteger r = a.sqrt();
		Form w = wire(r);
		Form w1 = wire(r.multiply(r));
		Form w2 = wire(r.add(BigInteger.ONE).multiply(r.add(BigInteger.ONE)));
		assertProduct(w, w, w1);
		assertProduct(w.plusConstant(1), w.plusConstant(1), w2);
		assertNonNegative(phi.minus(w1));
		assertNonNegative(w2.plusConstant(-1).minus(phi));
		return w;
	}

	public Form max(Form phi1, Form phi2) {
		BigInteger[] v = currentValues();
		BigInteger a = phi1.eval(v);
		BigInteger b = phi2.eval(v);
		Form w = wire(a.max(b));
		Form d1 = w.minus(phi1);
		Form d2 = w.minus(phi2);
		assertProductZero(d1, d2);
		assertNonNegative(d1);
		assertNonNegative(d2);
		return w;
	}

	public Form min(Form phi1, Form phi2) {
		BigInteger[] v = currentValues();
		BigInteger a = phi1.eval(v);
		BigInteger b = phi2.eval(v);
		Form w = wire(a.min(b));
		assertProductZero(w.minus(phi1), w.minus(phi2));
		assertNonNegative(phi1.minus(w));
		assertNonNegative(phi2.minus(w));
		return w;
	}

	public Form abs(Form phi) {
		return max(phi, phi.negate());
	}

	public Form bit(boolean value) {
		Form b = wire(value ? BigInteger.ONE : BigInteger.ZERO);
		assertBit(b);
		return b;
	}

	public void assertBit(Form b) {
		assertProductZero(b, b.negate().plusConstant(1));
	}

	public Form signBit(Form phi) {
		BigInteger v = eval(phi);
		if (v.signum() == 0) {
			throw new ArithmeticException("signBit on a form that evaluates to 0");
		}
		Form b = bit(v.signum() > 0);
		Form signed = multiply(b.scale(2).plusConstant(-1), phi);
		assertNonNegative(signed);
		return b;
	}

	public void assertOdd(Form phi) {
		BigInteger v = eval(phi);
		if (!v.testBit(0)) {
			throw new ArithmeticException("assertOdd on the even value " + v);
		}
		Form m = wire(v.subtract(BigInteger.ONE).shiftRight(1));
		assertNonNegative(m);
		assertZero(phi.minus(m.scale(2)).plusConstant(-1));
	}

	public CircuitSpec spec() {
		return new CircuitSpec(statementValues.length, wireValues.size(),
				range.toArray(new Form[0]), mul.toArray(new Form[0][]),
				linear.toArray(new Form[0]));
	}

	public BigInteger[] witness() {
		return currentValues();
	}

	public int widestProductCount() {
		Set<Integer> boundedAlone = new HashSet<>();
		List<Form> covered = new ArrayList<>(range);
		for (Form[] node : mul) {
			covered.add(node[0]);
			covered.add(node[1]);
		}
		for (Form f : covered) {
			int t = soleUnitVariable(f);
			if (t >= 0) {
				boundedAlone.add(t);
			}
		}

		List<Form> all = new ArrayList<>(covered);
		for (Form[] node : mul) {
			all.add(node[2]);
		}
		all.addAll(linear);

		BigInteger worst = BigInteger.ZERO;
		for (Form f : all) {
			BigInteger acc = BigInteger.ZERO;
			for (int k = 0; k < f.terms(); k++) {
				int t = f.indexAt(k);
				if (mulOutputWires.contains(t) && !boundedAlone.contains(t)) {
					acc = acc.add(f.coefficientAt(k).abs());
				}
			}
			if (acc.compareTo(worst) > 0) {
				worst = acc;
			}
		}
		int t = worst.intValueExact();
		return mul.isEmpty() ? t : Math.max(t, 1);
	}

	public BigInteger requiredB() {
		BigInteger[] v = currentValues();
		BigInteger worst = BigInteger.ZERO;
		for (Form f : range) {
			worst = worst.max(f.eval(v).abs());
		}
		for (Form[] node : mul) {
			worst = worst.max(node[0].eval(v).abs());
			worst = worst.max(node[1].eval(v).abs());
		}
		return worst;
	}
}
