package zk.multisharp;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

import com.github.aelstad.keccakj.fips202.Shake256;

import ec.ECPoint;

public class MultiSharpTranscript {

	private final Shake256 shake = new Shake256();

	private boolean squeezed = false;

	public MultiSharpTranscript(String domain) {
		absorbBytes("MultiSharp/".concat(domain).getBytes(StandardCharsets.UTF_8));
	}

	public void absorbBytes(byte[] b) {
		if (squeezed) {
			throw new IllegalStateException("cannot absorb after squeezing");
		}
		shake.getAbsorbStream().write(b, 0, b.length);
	}

	public void absorb(int v) {
		byte[] b = new byte[4];
		for (int i = 0; i < 4; i++) {
			b[i] = (byte) ((v >>> (8 * i)) & 0xFF);
		}
		absorbBytes(b);
	}

	public void absorb(BigInteger v) {
		byte[] raw = v.mod(MultiSharpParams.P).toByteArray();
		absorb(raw.length);
		absorbBytes(raw);
	}

	public void absorb(BigInteger[] v) {
		absorb(v.length);
		for (BigInteger x : v) {
			absorb(x);
		}
	}

	public void absorb(BigInteger[][] v) {
		absorb(v.length);
		for (BigInteger[] row : v) {
			absorb(row);
		}
	}

	public void absorb(ECPoint p) {
		byte[] raw = p.compress().toByteArray();
		absorb(raw.length);
		absorbBytes(raw);
	}

	public void absorb(ECPoint[] p) {
		absorb(p.length);
		for (ECPoint q : p) {
			absorb(q);
		}
	}

	public byte[] squeeze(int nbytes) {
		squeezed = true;
		byte[] out = new byte[nbytes];
		shake.getSqueezeStream().read(out);
		return out;
	}

	public byte[] seed() {
		return squeeze(32);
	}

	public static boolean[][][] expandBits(byte[] seed, int N, int R) {
		return expandBitsBlock(seed, "MultiSharp/gamma1", N, 4, R);
	}

	public static boolean[][][] expandBitsPrime(byte[] seed, int M, int R) {
		return expandBitsBlock(seed, "MultiSharp/gamma1p", M, 2, R);
	}

	private static boolean[][][] expandBitsBlock(byte[] seed, String domain, int n, int width,
			int R) {
		boolean[][][] out = new boolean[n][width][R];
		if (n == 0) {
			return out;
		}
		int total = n * width * R;
		Shake256 sh = new Shake256();
		sh.getAbsorbStream().write(domain.getBytes(StandardCharsets.UTF_8));
		sh.getAbsorbStream().write(seed, 0, seed.length);
		byte[] raw = new byte[(total + 7) / 8];
		sh.getSqueezeStream().read(raw);
		sh.reset();

		int idx = 0;
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < width; j++) {
				for (int k = 0; k < R; k++) {
					out[i][j][k] = ((raw[idx >>> 3] >>> (idx & 7)) & 1) == 1;
					idx++;
				}
			}
		}
		return out;
	}

	public static BigInteger expandScalar(byte[] seed) {
		Shake256 sh = new Shake256();
		sh.getAbsorbStream().write("MultiSharp/gamma2".getBytes(StandardCharsets.UTF_8));
		sh.getAbsorbStream().write(seed, 0, seed.length);
		byte[] raw = new byte[64];
		sh.getSqueezeStream().read(raw);
		sh.reset();
		return new BigInteger(1, raw).mod(MultiSharpParams.P);
	}
}
