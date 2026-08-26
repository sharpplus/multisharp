package zk.multisharp;

import java.math.BigInteger;
import java.util.Random;

import decomp.SquareDecomp;
import ec.ECPoint;

public class MultiSharpProver {

	private static final BigInteger P = MultiSharpParams.P;
	private static final BigInteger FOUR = BigInteger.valueOf(4);
	private static final BigInteger TWO = BigInteger.TWO;

	private final MultiSharpParams pp;

	private final Random generator;

	private final SquareDecomp decomp;

	private ECPoint[] C_x;

	private int lastAttempts;

	public int getLastAttempts() {
		return lastAttempts;
	}

	private boolean witnessChecks = true;

	public void setWitnessChecks(boolean enabled) {
		this.witnessChecks = enabled;
	}

	public MultiSharpProver(MultiSharpParams pp) {
		this(pp, new Random());
	}

	public MultiSharpProver(MultiSharpParams pp, Random generator) {
		this.pp = pp;
		this.generator = generator;
		this.decomp = new SquareDecomp();
		this.decomp.loadPrecompDecomp2s();
	}

	public ECPoint[] getCx() {
		return C_x;
	}

	private BigInteger rnd() {
		return new BigInteger(P.bitLength() + 64, generator).mod(P);
	}

	private BigInteger rndMu() {
		int bits = pp.L.bitLength() + 1;
		while (true) {
			BigInteger v = new BigInteger(bits, generator);
			if (v.compareTo(pp.L) <= 0) {
				return v;
			}
		}
	}

	public MultiSharpProof prove(CircuitSpec spec, BigInteger[] V, BigInteger[] r_x) {

		final int S = pp.S;
		final int U = pp.U;
		final int N = pp.N;
		final int M = pp.M;
		final int E = pp.E;
		final int R = pp.R;

		if (!pp.matches(spec)) {
			throw new IllegalArgumentException("specification does not match the parameters");
		}
		if (V.length != S + U || r_x.length != S) {
			throw new IllegalArgumentException("witness has the wrong shape");
		}

		C_x = new ECPoint[S];
		for (int s = 0; s < S; s++) {
			C_x[s] = pp.pc.commit(MultiSharpParams.mod(V[s]), r_x[s]);
		}

		BigInteger[] rangeVal = new BigInteger[N];
		for (int i = 0; i < N; i++) {
			rangeVal[i] = spec.rangeForm(i).eval(V);
		}
		BigInteger[][] mulVal = new BigInteger[M][3];
		for (int i = 0; i < M; i++) {
			for (int j = 0; j < 3; j++) {
				mulVal[i][j] = spec.mulForm(i, j).eval(V);
			}
		}

		BigInteger[][] y = new BigInteger[N][3];
		for (int i = 0; i < N; i++) {
			BigInteger v = rangeVal[i];
			if (witnessChecks) {
				if (v.signum() < 0) {
					throw new IllegalArgumentException(
							"range form " + i + " evaluates to the negative value " + v);
				}
				if (v.compareTo(pp.B) > 0) {
					throw new IllegalArgumentException(
							"range form " + i + " evaluates to " + v + ", which exceeds B");
				}
			}
			int[] dv = decomp.decomp3(v.intValueExact());
			long check = 0;
			for (int j = 0; j < 3; j++) {
				y[i][j] = BigInteger.valueOf(dv[j]);
				check += (long) dv[j] * dv[j];
			}
			if (check != 4L * v.longValueExact() + 1L) {
				throw new IllegalStateException("bad three-square decomposition for i=" + i);
			}
		}

		if (witnessChecks) {
			for (int i = 0; i < M; i++) {
				for (int j = 0; j < 2; j++) {
					if (mulVal[i][j].abs().compareTo(pp.B) > 0) {
						throw new IllegalArgumentException("multiplication input " + i + ","
								+ (j + 1) + " evaluates to " + mulVal[i][j]
								+ ", whose magnitude exceeds B");
					}
				}
				if (!mulVal[i][0].multiply(mulVal[i][1]).equals(mulVal[i][2])) {
					throw new IllegalArgumentException(
							"multiplication node " + i + " does not hold");
				}
			}
			for (int e = 0; e < E; e++) {
				if (spec.linearForm(e).eval(V).signum() != 0) {
					throw new IllegalArgumentException("linear assertion " + e + " does not hold");
				}
			}
		}

		final BigInteger zetaFloor = pp.zetaFloor();
		final BigInteger zetaCeil = pp.zetaBound();
		if (witnessChecks && !pp.checkAcceptanceWindow()) {
			throw new IllegalArgumentException(
					"empty acceptance window: 4(N+M)*B*gamma = " + zetaFloor
							+ " is not below L = " + zetaCeil
							+ "; no honest proof can be produced at these parameters");
		}
		BigInteger r_y = null;
		BigInteger r_w = null;
		BigInteger[] mu = null;
		ECPoint C_y = null;
		ECPoint C_w = null;
		byte[] h1 = null;
		boolean[][][] gamma = null;
		boolean[][][] gammaP = null;
		BigInteger[] zeta = null;
		final int maxAttempts = witnessChecks ? 64 : 1;
		int attempts = 0;
		boolean accepted = false;
		while (!accepted && attempts < maxAttempts) {
			attempts++;
			accepted = true;

		r_y = rnd();
		r_w = rnd();
		mu = new BigInteger[R];
		for (int k = 0; k < R; k++) {
			mu[k] = rndMu();
		}

		BigInteger[] cyVec = pp.ckY.zeroVector();
		for (int i = 0; i < N; i++) {
			for (int j = 1; j <= 3; j++) {
				cyVec[pp.slotY(i, j)] = y[i][j - 1];
			}
		}
		for (int k = 0; k < R; k++) {
			cyVec[pp.slotMu(k)] = mu[k];
		}
		C_y = pp.ckY.commit(cyVec, r_y);

		BigInteger[] cwVec = new BigInteger[U];
		for (int u = 0; u < U; u++) {
			cwVec[u] = MultiSharpParams.mod(V[S + u]);
		}
		C_w = pp.ckW.commit(cwVec, r_w);

		MultiSharpTranscript t1 = new MultiSharpTranscript("h1");
		MultiSharpVerifier.absorbPrefix1(t1, pp, C_x, spec, C_y, C_w);
		h1 = t1.seed();
		gamma = MultiSharpTranscript.expandBits(h1, N, R);
		gammaP = MultiSharpTranscript.expandBitsPrime(h1, M, R);

		zeta = new BigInteger[R];
		for (int k = 0; k < R; k++) {
			BigInteger acc = BigInteger.ZERO;
			for (int i = 0; i < N; i++) {
				if (gamma[i][0][k]) {
					acc = acc.add(rangeVal[i]);
				}
				for (int j = 1; j <= 3; j++) {
					if (gamma[i][j][k]) {
						acc = acc.add(y[i][j - 1]);
					}
				}
			}
			for (int i = 0; i < M; i++) {
				for (int j = 0; j < 2; j++) {
					if (gammaP[i][j][k]) {
						acc = acc.add(mulVal[i][j]).add(pp.B);
					}
				}
			}
			zeta[k] = acc.add(mu[k]);
		}

		for (int k = 0; k < R; k++) {
			if (zeta[k].compareTo(zetaFloor) < 0 || zeta[k].compareTo(zetaCeil) > 0) {
				accepted = false;
				break;
			}
		}
		}
		if (witnessChecks && !accepted) {
			throw new IllegalArgumentException("no zeta_k inside the acceptance window after "
					+ attempts + " attempts; the witness is not short enough for these"
					+ " parameters");
		}
		this.lastAttempts = attempts;

		BigInteger tilde_r_y = rnd();
		BigInteger tilde_r_w = rnd();
		BigInteger[] Vt = new BigInteger[S + U];
		BigInteger[] tilde_r_x = new BigInteger[S];
		for (int s = 0; s < S; s++) {
			Vt[s] = rnd();
			tilde_r_x[s] = rnd();
		}
		for (int u = 0; u < U; u++) {
			Vt[S + u] = rnd();
		}
		BigInteger[][] tilde_y = new BigInteger[N][3];
		for (int i = 0; i < N; i++) {
			for (int j = 0; j < 3; j++) {
				tilde_y[i][j] = rnd();
			}
		}
		BigInteger r_star = rnd();
		BigInteger tilde_r_star = rnd();
		BigInteger[] tilde_mu = new BigInteger[R];
		for (int k = 0; k < R; k++) {
			tilde_mu[k] = rnd();
		}

		BigInteger[] rangeMask = new BigInteger[N];
		for (int i = 0; i < N; i++) {
			rangeMask[i] = spec.rangeForm(i).evalMask(Vt);
		}
		BigInteger[][] mulMask = new BigInteger[M][3];
		for (int i = 0; i < M; i++) {
			for (int j = 0; j < 3; j++) {
				mulMask[i][j] = spec.mulForm(i, j).evalMask(Vt);
			}
		}

		BigInteger[] d = new BigInteger[R];
		for (int k = 0; k < R; k++) {
			BigInteger acc = BigInteger.ZERO;
			for (int i = 0; i < N; i++) {
				if (gamma[i][0][k]) {
					acc = acc.add(rangeMask[i]);
				}
				for (int j = 1; j <= 3; j++) {
					if (gamma[i][j][k]) {
						acc = acc.add(tilde_y[i][j - 1]);
					}
				}
			}
			for (int i = 0; i < M; i++) {
				for (int j = 0; j < 2; j++) {
					if (gammaP[i][j][k]) {
						acc = acc.add(mulMask[i][j]);
					}
				}
			}
			d[k] = MultiSharpParams.mod(acc.add(tilde_mu[k]));
		}

		ECPoint[] D_x = new ECPoint[S];
		for (int s = 0; s < S; s++) {
			D_x[s] = pp.pc.commit(Vt[s], tilde_r_x[s]);
		}

		BigInteger[] dwVec = new BigInteger[U];
		for (int u = 0; u < U; u++) {
			dwVec[u] = Vt[S + u];
		}
		ECPoint D_w = pp.ckW.commit(dwVec, tilde_r_w);

		BigInteger[] dyVec = pp.ckY.zeroVector();
		for (int i = 0; i < N; i++) {
			for (int j = 1; j <= 3; j++) {
				dyVec[pp.slotY(i, j)] = tilde_y[i][j - 1];
			}
		}
		for (int k = 0; k < R; k++) {
			dyVec[pp.slotMu(k)] = tilde_mu[k];
		}
		ECPoint D_y = pp.ckY.commit(dyVec, tilde_r_y);

		BigInteger[] eta = new BigInteger[E];
		for (int e = 0; e < E; e++) {
			eta[e] = spec.linearForm(e).evalMask(Vt);
		}

		BigInteger[] alpha1 = new BigInteger[N];
		BigInteger[] alpha0 = new BigInteger[N];
		for (int i = 0; i < N; i++) {
			BigInteger cross = BigInteger.ZERO;
			BigInteger sq = BigInteger.ZERO;
			for (int j = 0; j < 3; j++) {
				cross = cross.add(y[i][j].multiply(tilde_y[i][j]));
				sq = sq.add(tilde_y[i][j].multiply(tilde_y[i][j]));
			}
			alpha1[i] = MultiSharpParams
					.mod(FOUR.multiply(rangeMask[i]).subtract(TWO.multiply(cross)));
			alpha0[i] = MultiSharpParams.mod(sq.negate());
		}
		BigInteger[] beta1 = new BigInteger[M];
		BigInteger[] beta0 = new BigInteger[M];
		for (int i = 0; i < M; i++) {
			beta1[i] = MultiSharpParams.mod(
					mulVal[i][0].multiply(mulMask[i][1])
							.add(mulMask[i][0].multiply(mulVal[i][1]))
							.subtract(mulMask[i][2]));
			beta0[i] = MultiSharpParams.mod(mulMask[i][0].multiply(mulMask[i][1]));
		}

		BigInteger[] csVec = new BigInteger[N + M];
		BigInteger[] dsVec = new BigInteger[N + M];
		for (int i = 0; i < N; i++) {
			csVec[i] = alpha1[i];
			dsVec[i] = alpha0[i];
		}
		for (int i = 0; i < M; i++) {
			csVec[N + i] = beta1[i];
			dsVec[N + i] = beta0[i];
		}
		ECPoint C_star = pp.ckStar.commit(csVec, r_star);
		ECPoint D_star = pp.ckStar.commit(dsVec, tilde_r_star);

		MultiSharpTranscript t2 = new MultiSharpTranscript("h2");
		t2.absorbBytes(h1);
		MultiSharpVerifier.absorbPrefix2(t2, zeta, D_x, D_w, D_y, C_star, D_star, d, eta);
		BigInteger g = MultiSharpTranscript.expandScalar(t2.seed());

		BigInteger[] z_x = new BigInteger[S];
		BigInteger[] t_x = new BigInteger[S];
		for (int s = 0; s < S; s++) {
			z_x[s] = MultiSharpParams.mod(g.multiply(V[s]).add(Vt[s]));
			t_x[s] = MultiSharpParams.mod(g.multiply(r_x[s]).add(tilde_r_x[s]));
		}
		BigInteger[] z_w = new BigInteger[U];
		for (int u = 0; u < U; u++) {
			z_w[u] = MultiSharpParams.mod(g.multiply(V[S + u]).add(Vt[S + u]));
		}
		BigInteger[][] z_y = new BigInteger[N][3];
		for (int i = 0; i < N; i++) {
			for (int j = 0; j < 3; j++) {
				z_y[i][j] = MultiSharpParams.mod(g.multiply(y[i][j]).add(tilde_y[i][j]));
			}
		}
		BigInteger t_y = MultiSharpParams.mod(g.multiply(r_y).add(tilde_r_y));
		BigInteger t_w = MultiSharpParams.mod(g.multiply(r_w).add(tilde_r_w));
		BigInteger t_star = MultiSharpParams.mod(g.multiply(r_star).add(tilde_r_star));
		BigInteger[] tau = new BigInteger[R];
		for (int k = 0; k < R; k++) {
			tau[k] = MultiSharpParams.mod(g.multiply(mu[k]).add(tilde_mu[k]));
		}

		return new MultiSharpProof(C_y, C_w, zeta, D_x, D_w, D_y, C_star, D_star, d, eta,
				z_x, t_x, z_w, z_y, t_y, t_w, t_star, tau);
	}
}
