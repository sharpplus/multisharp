package zk.sharp;

import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

import decomp.SquareDecomp;
import ec.ECPoint;
import util.SharpUtils;
import zk.bulletproofs.PedersenCommitment;

public class SharpAttacker1 {

	Random generator;
	PedersenCommitment pc;
	ECPoint C_x;

	public SharpAttacker1() throws NoSuchAlgorithmException {
		pc = PedersenCommitment.getDefault();
		generator = new Random();
	}

	public ECPoint getCx() {
		return C_x;
	}

	public SharpProof generateRangeProof(BigInteger[] x_base, BigInteger r_x, BigInteger B, int R, BigInteger L) {
		int N = x_base.length;

		BigInteger[] x = new BigInteger[N];
		for(int i=0;i<N;i++) {
			x[i] = x_base[i];
		}

		x[0] = x[0].add(BigInteger.valueOf(10));
		x[1] = x[1].add(BigInteger.valueOf(-10));

		BigInteger sum_x = BigInteger.ZERO;
		for(int i=0;i<N;i++) {
			sum_x = sum_x.add(x[i]);
		}

		C_x = pc.commit(sum_x, r_x);

		SquareDecomp decomp = new SquareDecomp();
		decomp.loadPrecompDecomp2s();

		BigInteger[][] y = new BigInteger[N][3];

		for(int i=0;i<N;i++) {
			int[] decompVals = decomp.decomp3(x[i].intValueExact(), B.intValueExact());
			for(int j=0;j<3;j++) {
				y[i][j] = BigInteger.valueOf(decompVals[j]);
			}
			if(decompVals[0]*decompVals[0] + decompVals[1]*decompVals[1] + decompVals[2]*decompVals[2] != 4 * x[i].intValueExact() * (B.intValueExact() - x[i].intValueExact()) + 1) {
				System.out.println("Error: bad decomposition!");
			}
		}

		BigInteger r_y = SharpUtils.randomBigInt(generator);
		BigInteger[] mu = new BigInteger[R];
		for(int k=0;k<R;k++) {
			mu[k] = SharpUtils.randomBigInt(generator).mod(L);
		}

		BigInteger sum_y_mu = BigInteger.ZERO;
		for(int i=0;i<N;i++) {
			for(int j=0;j<3;j++) {
				sum_y_mu = sum_y_mu.add(y[i][j]);
			}
		}
		for(int k=0;k<R;k++) {
			sum_y_mu = sum_y_mu.add(mu[k]);
		}

		ECPoint C_y = pc.commit(sum_y_mu, r_y);

		byte[] challenge1 = SharpUtils.fiatShamir(C_x, C_y, B, N, R);
		boolean[] gammas1 = SharpUtils.drawBooleansFromSeed(challenge1, N * 4 * R);

		boolean[][][] gamma = new boolean[N][4][R];

		for(int i=0;i<N;i++) {
			for(int j=0;j<4;j++) {
				for(int k=0;k<R;k++) {
					gamma[i][j][k] = gammas1[4 * R * i + R * j + k];
				}
			}
		}

		BigInteger[] zeta = new BigInteger[R];
		for(int k=0;k<R;k++) {
			zeta[k] = BigInteger.ZERO;
			for(int i=0;i<N;i++) {
				for(int j=0;j<3;j++) {
					if(gamma[i][j][k]) {
						zeta[k] = zeta[k].add(y[i][j]);
					}
				}
				if(gamma[i][3][k]) {
					zeta[k] = zeta[k].add(x[i]);
				}
			}

			zeta[k] = SharpUtils.mask(zeta[k], mu[k]);

			if(zeta[k].compareTo(BigInteger.ZERO) < 0) {

			}
		}

		BigInteger tilde_rx = SharpUtils.randomBigInt(generator);
		BigInteger tilde_ry = SharpUtils.randomBigInt(generator);

		BigInteger[] tilde_x = new BigInteger[N];
		BigInteger[][] tilde_y = new BigInteger[N][3];

		for(int i=0;i<N;i++) {
			tilde_x[i] = SharpUtils.randomBigInt(generator);
			for(int j=0;j<3;j++) {
				tilde_y[i][j] = SharpUtils.randomBigInt(generator);
			}
		}

		BigInteger[] tilde_mu = new BigInteger[R];

		for(int k=0;k<R;k++) {
			tilde_mu[k] = SharpUtils.randomBigInt(generator);
		}

		BigInteger[] d = new BigInteger[R];

		for(int k=0;k<R;k++) {
			d[k] = tilde_mu[k];
			for(int i=0;i<N;i++) {
				for(int j=0;j<3;j++) {
					if(gamma[i][j][k]) {
						d[k] = d[k].add(tilde_y[i][j]);
					}
				}
				if(gamma[i][3][k]) {
					d[k] = d[k].add(tilde_x[i]);
				}
			}
		}

		BigInteger sum_tx = BigInteger.ZERO;
		for(int i=0;i<N;i++) {
			sum_tx = sum_tx.add(tilde_x[i]);
		}

		ECPoint D_x = pc.commit(sum_tx, tilde_rx);

		BigInteger sum_ty_tmu = BigInteger.ZERO;

		for(int i=0;i<N;i++) {
			for(int j=0;j<3;j++) {
				sum_ty_tmu = sum_ty_tmu.add(tilde_y[i][j]);
			}
		}
		for(int k=0;k<R;k++) {
			sum_ty_tmu = sum_ty_tmu.add(tilde_mu[k]);
		}

		ECPoint D_y = pc.commit(sum_ty_tmu, tilde_ry);

		BigInteger r_star = SharpUtils.randomBigInt(generator);
		BigInteger tilde_r_star = SharpUtils.randomBigInt(generator);

		BigInteger[] alpha_1_pos = new BigInteger[N];
		BigInteger[] alpha_1_neg = new BigInteger[N];
		for(int i=0;i<N;i++) {
			alpha_1_pos[i] = x[i].multiply(tilde_x[i]).multiply(BigInteger.valueOf(8));
			BigInteger sum_y_ty = BigInteger.ZERO;
			for(int j=0;j<3;j++) {
				sum_y_ty = sum_y_ty.add(y[i][j].multiply(tilde_y[i][j]));
			}
			alpha_1_pos[i] = alpha_1_pos[i].add(sum_y_ty.multiply(BigInteger.TWO));
			alpha_1_neg[i] = tilde_x[i].multiply(B).multiply(BigInteger.valueOf(4));
		}

		BigInteger[] alpha_0 = new BigInteger[N];
		for(int i=0;i<N;i++) {
			alpha_0[i] = tilde_x[i].multiply(tilde_x[i]).multiply(BigInteger.valueOf(4));
			BigInteger sum_ty_ty = BigInteger.ZERO;
			for(int j=0;j<3;j++) {
				sum_ty_ty = sum_ty_ty.add(tilde_y[i][j].multiply(tilde_y[i][j]));
			}
			alpha_0[i] = alpha_0[i].add(sum_ty_ty);
		}

		BigInteger sum_alpha_1_pos = BigInteger.ZERO;
		BigInteger sum_alpha_1_neg = BigInteger.ZERO;
		for(int i=0;i<N;i++) {
			sum_alpha_1_pos = sum_alpha_1_pos.add(alpha_1_pos[i]);
			sum_alpha_1_neg = sum_alpha_1_neg.add(alpha_1_neg[i]);
		}

		ECPoint ec_sum_alpha_1_pos = pc.commit(sum_alpha_1_pos, r_star.multiply(BigInteger.TWO)).decompress();
		ECPoint ec_sum_alpha_1_neg = pc.commit(sum_alpha_1_neg, r_star).decompress();
		ECPoint C_star = ec_sum_alpha_1_pos.subtract(ec_sum_alpha_1_neg).compress();

		BigInteger sum_alpha_0 = BigInteger.ZERO;
		for(int i=0;i<N;i++) {
			sum_alpha_0 = sum_alpha_0.add(alpha_0[i]);
		}
		ECPoint D_star = pc.commit(sum_alpha_0, tilde_r_star);

		byte[] challenge2 = SharpUtils.fiatShamir(C_x, C_y, B, N, R);
		BigInteger gamma_star = SharpUtils.drawNumberFromSeed(challenge2);


		BigInteger[] z_x = new BigInteger[N];
		BigInteger[][] z_y = new BigInteger[N][3];

		for(int i=0;i<N;i++) {
			z_x[i] = SharpUtils.mask(gamma_star.multiply(x[i]), tilde_x[i]);
			for(int j=0;j<3;j++) {
				z_y[i][j] = SharpUtils.mask(gamma_star.multiply(y[i][j]), tilde_y[i][j]);
			}
		}


		BigInteger t_x = SharpUtils.mask(gamma_star.multiply(r_x), tilde_rx);
		BigInteger t_y = SharpUtils.mask(gamma_star.multiply(r_y), tilde_ry);

		BigInteger t_star = SharpUtils.mask(gamma_star.multiply(r_star), tilde_r_star);

		BigInteger[] tau = new BigInteger[R];
		for(int k=0;k<R;k++) {
			tau[k] = SharpUtils.mask(gamma_star.multiply(mu[k]), tilde_mu[k]);
		}


		return new SharpProof(C_y, D_x, D_y, C_star, D_star, t_x, t_y, t_star, zeta, z_x, z_y, tau, d);
	}
}
