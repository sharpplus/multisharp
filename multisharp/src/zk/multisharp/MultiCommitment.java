package zk.multisharp;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

import com.github.aelstad.keccakj.fips202.Shake256;

import ec.ECPoint;
import zk.bulletproofs.BulletProofs;
import zk.bulletproofs.PedersenCommitment;
import zk.bulletproofs.Utils;

public class MultiCommitment {

	private final ECPoint blindingGen;

	private final ECPoint[] gens;

	public MultiCommitment(String label, int size) {
		this.gens = new ECPoint[size];

		Shake256 digest = new Shake256();
		digest.getAbsorbStream().write("MultiSharpGenerators".getBytes(StandardCharsets.UTF_8));
		digest.getAbsorbStream().write(label.getBytes(StandardCharsets.UTF_8));

		byte[] raw = new byte[32 * (size + 1)];
		digest.getSqueezeStream().read(raw);
		digest.reset();

		byte[] chunk = new byte[32];
		System.arraycopy(raw, 0, chunk, 0, 32);
		this.blindingGen = BulletProofs.getFactory().fromUniformBytes(chunk);

		for (int i = 0; i < size; i++) {
			byte[] c = new byte[32];
			System.arraycopy(raw, 32 * (i + 1), c, 0, 32);
			this.gens[i] = BulletProofs.getFactory().fromUniformBytes(c);
		}
	}

	public int size() {
		return gens.length;
	}

	public ECPoint getBlindingGen() {
		return blindingGen;
	}

	public ECPoint getGen(int i) {
		return gens[i];
	}

	public ECPoint commit(BigInteger[] values, BigInteger blinding) {
		if (values.length != gens.length) {
			throw new IllegalArgumentException(
					"expected " + gens.length + " coordinates, got " + values.length);
		}

		BigInteger[] s = new BigInteger[values.length + 1];
		ECPoint[] p = new ECPoint[values.length + 1];
		int n = 0;

		BigInteger b = blinding.mod(PedersenCommitment.GROUP_ORDER);
		if (b.signum() != 0) {
			s[n] = b;
			p[n] = blindingGen;
			n++;
		}
		for (int i = 0; i < values.length; i++) {
			if (values[i] == null) {
				continue;
			}
			BigInteger v = values[i].mod(PedersenCommitment.GROUP_ORDER);
			if (v.signum() == 0) {
				continue;
			}
			s[n] = v;
			p[n] = gens[i];
			n++;
		}

		return multiScalarMul(s, p, n).compress();
	}

	private static final int SCALAR_BITS = PedersenCommitment.GROUP_ORDER.bitLength();

	private static int windowSize(int n) {
		int best = 1;
		double bestCost = Double.MAX_VALUE;
		for (int w = 1; w <= 16; w++) {
			double windows = Math.ceil(SCALAR_BITS / (double) w);
			double cost = windows * (n + 2.0 * ((1L << w) - 1));
			if (cost < bestCost) {
				bestCost = cost;
				best = w;
			}
		}
		return best;
	}

	private static int windowAt(byte[] le, int pos, int w) {
		int digit = 0;
		for (int t = 0; t < w; t++) {
			int bit = pos + t;
			int idx = bit >>> 3;
			if (idx >= le.length) {
				break;
			}
			if (((le[idx] >>> (bit & 7)) & 1) != 0) {
				digit |= 1 << t;
			}
		}
		return digit;
	}

	public static boolean naiveMsm =
			Boolean.getBoolean("multisharp.naiveMsm");

	static ECPoint multiScalarMul(BigInteger[] s, ECPoint[] p, int n) {
		if (ec.GroupOps.ENABLED) {
			ec.GroupOps.beginMsm(n);
			ECPoint counted = multiScalarMulUncounted(s, p, n);
			ec.GroupOps.endMsm();
			return counted;
		}
		return multiScalarMulUncounted(s, p, n);
	}

	private static ECPoint multiScalarMulUncounted(BigInteger[] s, ECPoint[] p, int n) {
		ECPoint identity = BulletProofs.getFactory().identity();
		if (n == 0) {
			return identity;
		}
		if (n == 1) {
			return p[0].multiply(Utils.scalar(s[0]));
		}
		if (naiveMsm) {
			ECPoint acc = p[0].multiply(Utils.scalar(s[0]));
			for (int i = 1; i < n; i++) {
				acc = acc.add(p[i].multiply(Utils.scalar(s[i])));
			}
			return acc;
		}

		int nbytes = (SCALAR_BITS + 7) / 8;
		byte[][] le = new byte[n][nbytes];
		for (int i = 0; i < n; i++) {
			byte[] be = s[i].toByteArray();
			for (int j = 0; j < be.length; j++) {
				int dst = be.length - 1 - j;
				if (dst < nbytes) {
					le[i][dst] = be[j];
				}
			}
		}

		int w = windowSize(n);
		int nwin = (SCALAR_BITS + w - 1) / w;
		int nbuckets = 1 << w;
		ECPoint[] buckets = new ECPoint[nbuckets];

		ECPoint acc = identity;
		boolean accEmpty = true;

		for (int win = nwin - 1; win >= 0; win--) {
			if (!accEmpty) {
				for (int t = 0; t < w; t++) {
					acc = acc.dbl();
				}
			}

			java.util.Arrays.fill(buckets, identity);
			boolean any = false;
			for (int i = 0; i < n; i++) {
				int digit = windowAt(le[i], win * w, w);
				if (digit != 0) {
					buckets[digit] = buckets[digit].add(p[i]);
					any = true;
				}
			}
			if (!any) {
				continue;
			}

			ECPoint running = identity;
			ECPoint total = identity;
			for (int d = nbuckets - 1; d >= 1; d--) {
				running = running.add(buckets[d]);
				total = total.add(running);
			}
			acc = accEmpty ? total : acc.add(total);
			accEmpty = false;
		}

		return acc;
	}

	public BigInteger[] zeroVector() {
		BigInteger[] v = new BigInteger[gens.length];
		for (int i = 0; i < v.length; i++) {
			v[i] = BigInteger.ZERO;
		}
		return v;
	}
}
