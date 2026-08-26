package zk.multisharp;

import java.io.Serializable;

public final class CircuitSpec implements Serializable {

	static final long serialVersionUID = 1L;

	public final int S;

	public final int U;

	private final Form[] range;

	private final Form[][] mul;

	private final Form[] linear;

	CircuitSpec(int S, int U, Form[] range, Form[][] mul, Form[] linear) {
		this.S = S;
		this.U = U;
		this.range = range;
		this.mul = mul;
		this.linear = linear;
		int limit = S + U;
		for (Form f : range) {
			checkBounds(f, limit);
		}
		for (Form[] node : mul) {
			for (Form f : node) {
				checkBounds(f, limit);
			}
		}
		for (Form f : linear) {
			checkBounds(f, limit);
		}
	}

	private static void checkBounds(Form f, int limit) {
		if (f.maxIndex() >= limit) {
			throw new IllegalArgumentException(
					"form refers to value index " + f.maxIndex() + " but S+U = " + limit);
		}
	}

	public int N() {
		return range.length;
	}

	public int M() {
		return mul.length;
	}

	public int E() {
		return linear.length;
	}

	public int values() {
		return S + U;
	}

	public Form rangeForm(int i) {
		return range[i];
	}

	public Form mulForm(int i, int j) {
		return mul[i][j];
	}

	public Form linearForm(int e) {
		return linear[e];
	}

	void absorb(MultiSharpTranscript t) {
		t.absorb(S);
		t.absorb(U);
		t.absorb(range.length);
		for (Form f : range) {
			f.absorb(t);
		}
		t.absorb(mul.length);
		for (Form[] node : mul) {
			for (Form f : node) {
				f.absorb(t);
			}
		}
		t.absorb(linear.length);
		for (Form f : linear) {
			f.absorb(t);
		}
	}

	@Override
	public String toString() {
		return "CircuitSpec[S=" + S + ", U=" + U + ", N=" + N() + ", M=" + M() + ", E=" + E() + "]";
	}
}
