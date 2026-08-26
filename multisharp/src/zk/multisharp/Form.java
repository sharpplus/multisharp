package zk.multisharp;

import java.io.Serializable;
import java.math.BigInteger;

public final class Form implements Serializable {

	static final long serialVersionUID = 1L;

	private static final BigInteger P = MultiSharpParams.P;

	private final BigInteger c0;

	private final int[] idx;

	private final BigInteger[] coef;

	private Form(BigInteger c0, int[] idx, BigInteger[] coef) {
		this.c0 = c0;
		this.idx = idx;
		this.coef = coef;
	}

	public static final Form ZERO = new Form(BigInteger.ZERO, new int[0], new BigInteger[0]);

	public static Form constant(BigInteger k) {
		if (k.signum() == 0) {
			return ZERO;
		}
		return new Form(k, new int[0], new BigInteger[0]);
	}

	public static Form constant(long k) {
		return constant(BigInteger.valueOf(k));
	}

	public static Form var(int t) {
		if (t < 0) {
			throw new IllegalArgumentException("negative value index: " + t);
		}
		return new Form(BigInteger.ZERO, new int[] { t }, new BigInteger[] { BigInteger.ONE });
	}

	public boolean isConstant() {
		return idx.length == 0;
	}

	public BigInteger constantTerm() {
		return c0;
	}

	public int terms() {
		return idx.length;
	}

	public int indexAt(int k) {
		return idx[k];
	}

	public BigInteger coefficientAt(int k) {
		return coef[k];
	}

	public int maxIndex() {
		return idx.length == 0 ? -1 : idx[idx.length - 1];
	}

	public Form plus(Form other) {
		return combine(this, BigInteger.ONE, other, BigInteger.ONE);
	}

	public Form minus(Form other) {
		return combine(this, BigInteger.ONE, other, BigInteger.ONE.negate());
	}

	public Form scale(BigInteger k) {
		if (k.signum() == 0) {
			return ZERO;
		}
		BigInteger[] c = new BigInteger[coef.length];
		for (int i = 0; i < coef.length; i++) {
			c[i] = coef[i].multiply(k);
		}
		return new Form(c0.multiply(k), idx.clone(), c);
	}

	public Form scale(long k) {
		return scale(BigInteger.valueOf(k));
	}

	public Form negate() {
		return scale(BigInteger.ONE.negate());
	}

	public Form plusConstant(BigInteger k) {
		if (k.signum() == 0) {
			return this;
		}
		return new Form(c0.add(k), idx.clone(), coef.clone());
	}

	public Form plusConstant(long k) {
		return plusConstant(BigInteger.valueOf(k));
	}

	private static Form combine(Form a, BigInteger ka, Form b, BigInteger kb) {
		int[] ri = new int[a.idx.length + b.idx.length];
		BigInteger[] rc = new BigInteger[ri.length];
		int n = 0;
		int i = 0;
		int j = 0;
		while (i < a.idx.length || j < b.idx.length) {
			int ti = i < a.idx.length ? a.idx[i] : Integer.MAX_VALUE;
			int tj = j < b.idx.length ? b.idx[j] : Integer.MAX_VALUE;
			int t;
			BigInteger v;
			if (ti < tj) {
				t = ti;
				v = a.coef[i].multiply(ka);
				i++;
			} else if (tj < ti) {
				t = tj;
				v = b.coef[j].multiply(kb);
				j++;
			} else {
				t = ti;
				v = a.coef[i].multiply(ka).add(b.coef[j].multiply(kb));
				i++;
				j++;
			}
			if (v.signum() != 0) {
				ri[n] = t;
				rc[n] = v;
				n++;
			}
		}
		int[] fi = new int[n];
		BigInteger[] fc = new BigInteger[n];
		System.arraycopy(ri, 0, fi, 0, n);
		System.arraycopy(rc, 0, fc, 0, n);
		return new Form(a.c0.multiply(ka).add(b.c0.multiply(kb)), fi, fc);
	}

	public BigInteger eval(BigInteger[] V) {
		BigInteger acc = c0;
		for (int i = 0; i < idx.length; i++) {
			acc = acc.add(coef[i].multiply(V[idx[i]]));
		}
		return acc;
	}

	public BigInteger evalMask(BigInteger[] Vt) {
		BigInteger acc = BigInteger.ZERO;
		for (int i = 0; i < idx.length; i++) {
			acc = acc.add(coef[i].multiply(Vt[idx[i]]));
		}
		return MultiSharpParams.mod(acc);
	}

	public BigInteger response(BigInteger[] z, BigInteger gamma) {
		BigInteger acc = c0.multiply(gamma);
		for (int i = 0; i < idx.length; i++) {
			acc = acc.add(coef[i].multiply(z[idx[i]]));
		}
		return MultiSharpParams.mod(acc);
	}

	void absorb(MultiSharpTranscript t) {
		t.absorb(c0);
		t.absorb(idx.length);
		for (int i = 0; i < idx.length; i++) {
			t.absorb(idx[i]);
			t.absorb(coef[i]);
		}
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		if (c0.signum() != 0 || idx.length == 0) {
			sb.append(c0);
		}
		for (int i = 0; i < idx.length; i++) {
			BigInteger c = coef[i].mod(P);
			if (c.compareTo(P.shiftRight(1)) > 0) {
				c = c.subtract(P);
			}
			if (sb.length() > 0) {
				sb.append(c.signum() < 0 ? " - " : " + ");
			} else if (c.signum() < 0) {
				sb.append("-");
			}
			BigInteger a = c.abs();
			if (!a.equals(BigInteger.ONE)) {
				sb.append(a).append("*");
			}
			sb.append("V").append(idx[i]);
		}
		return sb.toString();
	}
}
