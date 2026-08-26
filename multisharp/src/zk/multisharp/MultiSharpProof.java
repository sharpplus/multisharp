package zk.multisharp;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigInteger;

import ec.ECPoint;

public class MultiSharpProof implements Serializable {

	static final long serialVersionUID = 3L;

	ECPoint C_y;
	ECPoint C_w;
	BigInteger[] zeta;

	ECPoint[] D_x;
	ECPoint D_w;
	ECPoint D_y;
	ECPoint C_star;
	ECPoint D_star;
	BigInteger[] d;
	BigInteger[] eta;

	BigInteger[] z_x;
	BigInteger[] t_x;
	BigInteger[] z_w;
	BigInteger[][] z_y;
	BigInteger t_y;
	BigInteger t_w;
	BigInteger t_star;
	BigInteger[] tau;

	public MultiSharpProof(ECPoint C_y, ECPoint C_w, BigInteger[] zeta, ECPoint[] D_x, ECPoint D_w,
			ECPoint D_y, ECPoint C_star, ECPoint D_star, BigInteger[] d, BigInteger[] eta,
			BigInteger[] z_x, BigInteger[] t_x, BigInteger[] z_w, BigInteger[][] z_y,
			BigInteger t_y, BigInteger t_w, BigInteger t_star, BigInteger[] tau) {
		this.C_y = C_y;
		this.C_w = C_w;
		this.zeta = zeta;
		this.D_x = D_x;
		this.D_w = D_w;
		this.D_y = D_y;
		this.C_star = C_star;
		this.D_star = D_star;
		this.d = d;
		this.eta = eta;
		this.z_x = z_x;
		this.t_x = t_x;
		this.z_w = z_w;
		this.z_y = z_y;
		this.t_y = t_y;
		this.t_w = t_w;
		this.t_star = t_star;
		this.tau = tau;
	}

	public BigInteger[] getZeta() {
		return zeta;
	}

	public BigInteger[] getZx() {
		return z_x;
	}

	public BigInteger[] getZw() {
		return z_w;
	}

	public BigInteger[] getEta() {
		return eta;
	}

	public int commitmentCount() {
		return 6 + D_x.length;
	}

	public int scalarCount() {
		int S = z_x.length;
		int U = z_w.length;
		int N = z_y.length;
		int E = eta.length;
		int R = tau.length;
		return 3 + 3 * R + 3 * N + 2 * S + U + E;
	}

	public int size() {
		return this.serialize().length;
	}

	public byte[] serialize() {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try (ObjectOutputStream out = new ObjectOutputStream(bos)) {
			out.writeObject(this);
			out.flush();
			return bos.toByteArray();
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}
}
