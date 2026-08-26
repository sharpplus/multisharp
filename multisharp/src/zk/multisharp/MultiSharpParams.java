package zk.multisharp;

import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;

import zk.bulletproofs.PedersenCommitment;

public class MultiSharpParams {

	public static final BigInteger P = PedersenCommitment.GROUP_ORDER;

	public final int S;

	public final int U;

	public final int N;

	public final int M;

	public final int E;

	public final int R;

	public final BigInteger B;

	public final BigInteger L;

	public final int gamma = 1;

	public final int T;

	public final PedersenCommitment pc;

	public final MultiCommitment ckW;

	public final MultiCommitment ckY;

	public final MultiCommitment ckStar;

	public MultiSharpParams(int S, int U, int N, int M, int E, int R, BigInteger B, BigInteger L)
			throws NoSuchAlgorithmException {
		this(S, U, N, M, E, R, B, L, 1);
	}

	public MultiSharpParams(int S, int U, int N, int M, int E, int R, BigInteger B, BigInteger L,
			int T) throws NoSuchAlgorithmException {
		this.S = S;
		this.U = U;
		this.N = N;
		this.M = M;
		this.E = E;
		this.R = R;
		this.B = B;
		this.L = L;
		this.T = T;
		this.pc = PedersenCommitment.getDefault();
		this.ckW = new MultiCommitment("w|" + U, U);
		this.ckY = new MultiCommitment("y|" + N + "|" + R, 3 * N + R);
		this.ckStar = new MultiCommitment("star|" + N + "|" + M, N + M);
	}

	public static MultiSharpParams forCircuit(CircuitSpec spec, int R, BigInteger B, BigInteger L,
			int T) throws NoSuchAlgorithmException {
		return new MultiSharpParams(spec.S, spec.U, spec.N(), spec.M(), spec.E(), R, B, L, T);
	}

	public boolean matches(CircuitSpec spec) {
		return spec != null && spec.S == S && spec.U == U && spec.N() == N && spec.M() == M
				&& spec.E() == E;
	}

	public int slotY(int i, int j) {
		return 3 * i + (j - 1);
	}

	public int slotMu(int k) {
		return 3 * N + k;
	}

	public BigInteger zetaBound() {
		return L;
	}

	public BigInteger zetaFloor() {
		return BigInteger.valueOf(4L * (N + M) * gamma).multiply(B);
	}

	public boolean checkParameterConstraint() {
		BigInteger w3 = zetaBound();
		return BigInteger.valueOf(4L * (T + 1)).multiply(w3).multiply(w3).compareTo(P) < 0;
	}

	public boolean checkAcceptanceWindow() {
		return zetaFloor().compareTo(zetaBound()) < 0;
	}

	public static BigInteger maxL(int N, int M, BigInteger B, int gamma) {
		return maxL(N, M, B, gamma, 1);
	}

	private static final BigInteger MARGIN = BigInteger.ONE.shiftLeft(32);

	public static BigInteger maxL(int N, int M, BigInteger B, int gamma, int T) {
		BigInteger a = BigInteger.valueOf(4L * (T + 1));
		BigInteger disc = MARGIN.multiply(MARGIN).add(a.shiftLeft(2).multiply(P));
		BigInteger w3 = disc.sqrt().subtract(MARGIN).divide(a.shiftLeft(1));
		while (w3.signum() > 0 && exceeds(a, w3)) {
			w3 = w3.subtract(BigInteger.ONE);
		}
		while (!exceeds(a, w3.add(BigInteger.ONE))) {
			w3 = w3.add(BigInteger.ONE);
		}
		return w3;
	}

	private static boolean exceeds(BigInteger a, BigInteger w) {
		return a.multiply(w).multiply(w).add(MARGIN.multiply(w)).compareTo(P) >= 0;
	}

	private byte[] ckDigest;

	public byte[] commitmentKeyDigest() {
		byte[] d = ckDigest;
		if (d == null) {
			d = ec.GroupOps.ENABLED ? countedCommitmentKeyDigest() : computeCommitmentKeyDigest();
			ckDigest = d;
		}
		return d.clone();
	}

	private byte[] countedCommitmentKeyDigest() {
		ec.GroupOps.beginParams();
		try {
			return computeCommitmentKeyDigest();
		} finally {
			ec.GroupOps.endParams();
		}
	}

	private byte[] computeCommitmentKeyDigest() {
		MultiSharpTranscript t = new MultiSharpTranscript("ck");
		t.absorb(pc.getB());
		t.absorb(pc.getBlinding());
		absorbKey(t, ckW);
		absorbKey(t, ckY);
		absorbKey(t, ckStar);
		return t.seed();
	}

	private static void absorbKey(MultiSharpTranscript t, MultiCommitment ck) {
		t.absorb(ck.getBlindingGen());
		t.absorb(ck.size());
		for (int i = 0; i < ck.size(); i++) {
			t.absorb(ck.getGen(i));
		}
	}

	public static BigInteger mod(BigInteger a) {
		return a.mod(P);
	}
}
