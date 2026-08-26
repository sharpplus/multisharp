package zk.sharp;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigInteger;

import ec.ECPoint;

public class SharpProof implements Serializable {
	
	static final long serialVersionUID = 1L;

	ECPoint C_y;
	ECPoint D_x;
	ECPoint D_y;
	ECPoint C_star;
	ECPoint D_star;
	BigInteger t_x;
	BigInteger t_y;
	BigInteger t_star;
	BigInteger[] zeta;
	BigInteger[] z_x;
	BigInteger[][] z_y;
	BigInteger[] tau;
	BigInteger[] d;
	
	public SharpProof(ECPoint C_y, ECPoint D_x, ECPoint D_y, ECPoint C_star, ECPoint D_star, 
			BigInteger t_x, BigInteger t_y, BigInteger t_star, BigInteger[] zeta, BigInteger[] z_x, BigInteger[][] z_y, BigInteger[] tau, BigInteger[] d) {
		this.C_y = C_y;
		this.D_x = D_x;
		this.D_y = D_y;
		this.C_star = C_star;
		this.D_star = D_star;
		this.t_x = t_x;
		this.t_y = t_y;
		this.t_star = t_star;
		this.zeta = zeta;
		this.z_x = z_x;
		this.z_y = z_y;
		this.tau = tau;
		this.d = d;
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
