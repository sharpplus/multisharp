package zk.multisharp;

import java.math.BigInteger;

import ec.ECPoint;
import zk.bulletproofs.Utils;

public class MultiSharpVerifier {

	private static final BigInteger FOUR = BigInteger.valueOf(4);

	private final MultiSharpParams pp;

	public MultiSharpVerifier(MultiSharpParams pp) {
		this.pp = pp;
	}

	public boolean verify(MultiSharpProof p, ECPoint[] C_x, CircuitSpec spec) {

		final int S = pp.S;
		final int U = pp.U;
		final int N = pp.N;
		final int M = pp.M;
		final int E = pp.E;
		final int R = pp.R;

		if (!pp.matches(spec) || C_x.length != S || p.D_x.length != S || p.z_x.length != S
				|| p.z_w.length != U || p.z_y.length != N || p.eta.length != E
				|| p.tau.length != R || p.zeta.length != R || p.d.length != R) {
			return false;
		}

		BigInteger floor = pp.zetaFloor();
		BigInteger bound = pp.zetaBound();
		for (int k = 0; k < R; k++) {
			if (p.zeta[k].compareTo(floor) < 0 || p.zeta[k].compareTo(bound) > 0) {
				return false;
			}
		}

		MultiSharpTranscript t1 = new MultiSharpTranscript("h1");
		absorbPrefix1(t1, pp, C_x, spec, p.C_y, p.C_w);
		byte[] h1 = t1.seed();
		boolean[][][] gamma = MultiSharpTranscript.expandBits(h1, N, R);
		boolean[][][] gammaP = MultiSharpTranscript.expandBitsPrime(h1, M, R);

		MultiSharpTranscript t2 = new MultiSharpTranscript("h2");
		t2.absorbBytes(h1);
		absorbPrefix2(t2, p.zeta, p.D_x, p.D_w, p.D_y, p.C_star, p.D_star, p.d, p.eta);
		BigInteger g = MultiSharpTranscript.expandScalar(t2.seed());
		BigInteger g2 = MultiSharpParams.mod(g.multiply(g));

		BigInteger[] z = new BigInteger[S + U];
		System.arraycopy(p.z_x, 0, z, 0, S);
		System.arraycopy(p.z_w, 0, z, S, U);

		BigInteger[] rangeResp = new BigInteger[N];
		for (int i = 0; i < N; i++) {
			rangeResp[i] = spec.rangeForm(i).response(z, g);
		}
		BigInteger[][] mulResp = new BigInteger[M][3];
		for (int i = 0; i < M; i++) {
			for (int j = 0; j < 3; j++) {
				mulResp[i][j] = spec.mulForm(i, j).response(z, g);
			}
		}

		for (int s = 0; s < S; s++) {
			ECPoint lhs = pp.pc.commit(p.z_x[s], p.t_x[s]).decompress()
					.subtract(C_x[s].decompress().multiply(Utils.scalar(g)));
			if (!lhs.equals(p.D_x[s].decompress())) {
				return false;
			}
		}

		ECPoint F_w = pp.ckW.commit(p.z_w, p.t_w).decompress()
				.subtract(p.C_w.decompress().multiply(Utils.scalar(g)));
		if (!F_w.equals(p.D_w.decompress())) {
			return false;
		}

		BigInteger[] fyVec = pp.ckY.zeroVector();
		for (int i = 0; i < N; i++) {
			for (int j = 1; j <= 3; j++) {
				fyVec[pp.slotY(i, j)] = p.z_y[i][j - 1];
			}
		}
		for (int k = 0; k < R; k++) {
			fyVec[pp.slotMu(k)] = p.tau[k];
		}
		ECPoint F_y = pp.ckY.commit(fyVec, p.t_y).decompress()
				.subtract(p.C_y.decompress().multiply(Utils.scalar(g)));
		if (!F_y.equals(p.D_y.decompress())) {
			return false;
		}

		for (int k = 0; k < R; k++) {
			BigInteger acc = BigInteger.ZERO;
			for (int i = 0; i < N; i++) {
				if (gamma[i][0][k]) {
					acc = acc.add(rangeResp[i]);
				}
				for (int j = 1; j <= 3; j++) {
					if (gamma[i][j][k]) {
						acc = acc.add(p.z_y[i][j - 1]);
					}
				}
			}
			BigInteger shift = BigInteger.ZERO;
			for (int i = 0; i < M; i++) {
				for (int j = 0; j < 2; j++) {
					if (gammaP[i][j][k]) {
						acc = acc.add(mulResp[i][j]);
						shift = shift.add(BigInteger.ONE);
					}
				}
			}
			acc = acc.add(g.multiply(pp.B).multiply(shift));
			BigInteger f = MultiSharpParams
					.mod(acc.add(p.tau[k]).subtract(g.multiply(p.zeta[k])));
			if (!f.equals(MultiSharpParams.mod(p.d[k]))) {
				return false;
			}
		}

		BigInteger[] fs = new BigInteger[N + M];
		for (int i = 0; i < N; i++) {
			BigInteger sq = BigInteger.ZERO;
			for (int j = 0; j < 3; j++) {
				sq = sq.add(p.z_y[i][j].multiply(p.z_y[i][j]));
			}
			fs[i] = MultiSharpParams.mod(
					FOUR.multiply(g).multiply(rangeResp[i]).add(g2).subtract(sq));
		}
		for (int i = 0; i < M; i++) {
			fs[N + i] = MultiSharpParams.mod(
					mulResp[i][0].multiply(mulResp[i][1]).subtract(g.multiply(mulResp[i][2])));
		}

		ECPoint F_star = pp.ckStar.commit(fs, p.t_star).decompress()
				.subtract(p.C_star.decompress().multiply(Utils.scalar(g)));
		if (!F_star.equals(p.D_star.decompress())) {
			return false;
		}

		for (int e = 0; e < E; e++) {
			if (!spec.linearForm(e).response(z, g).equals(MultiSharpParams.mod(p.eta[e]))) {
				return false;
			}
		}

		return true;
	}

	static void absorbPrefix1(MultiSharpTranscript t, MultiSharpParams pp, ECPoint[] C_x,
			CircuitSpec spec, ECPoint C_y, ECPoint C_w) {
		t.absorb(pp.S);
		t.absorb(pp.U);
		t.absorb(pp.N);
		t.absorb(pp.M);
		t.absorb(pp.E);
		t.absorb(pp.R);
		t.absorb(pp.gamma);
		t.absorb(pp.B);
		t.absorb(pp.L);
		t.absorbBytes(pp.commitmentKeyDigest());
		t.absorb(C_x);
		spec.absorb(t);
		t.absorb(C_y);
		t.absorb(C_w);
	}

	static void absorbPrefix2(MultiSharpTranscript t, BigInteger[] zeta, ECPoint[] D_x,
			ECPoint D_w, ECPoint D_y, ECPoint C_star, ECPoint D_star, BigInteger[] d,
			BigInteger[] eta) {
		t.absorb(zeta);
		t.absorb(D_x);
		t.absorb(D_w);
		t.absorb(D_y);
		t.absorb(C_star);
		t.absorb(D_star);
		t.absorb(d);
		t.absorb(eta);
	}
}
