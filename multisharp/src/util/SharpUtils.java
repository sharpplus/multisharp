package util;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.util.Random;

import ec.ECPoint;
import zk.bulletproofs.Commitment;
import zk.bulletproofs.BpProver;
import zk.bulletproofs.Utils;

public class SharpUtils {

	public static BigInteger mask(BigInteger x, BigInteger mu) {
		return x.add(mu);
	}

	public static BigInteger randomBigInt(Random generator) {
		return new BigInteger(128, generator);
	}

	public static Commitment commit(BpProver prover, BigInteger v, BigInteger r) {
		return prover.commit(Utils.scalar(v), Utils.scalar(r));
	}

	public static byte[] fiatShamir(ECPoint C_x, ECPoint C_y, BigInteger B, int N, int R) {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();

	    try (ObjectOutputStream out = new ObjectOutputStream(bos)) {
	        out.writeObject(C_x);
	        out.writeObject(C_y);
	        out.writeObject(B);
	        out.writeObject(N);
	        out.writeObject(R);
	        out.flush();
	        return bos.toByteArray();
	    } catch (Exception ex) {
	        throw new RuntimeException(ex);
	    }
	}

	public static byte[] fiatShamir(ECPoint[] C_x, ECPoint C_y, ECPoint C_y_prime, int N, int R) {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();

	    try (ObjectOutputStream out = new ObjectOutputStream(bos)) {
	    	for(int i=0;i<C_x.length;i++) {
	    		out.writeObject(C_x[i]);
	    	}
	        out.writeObject(C_y);
	        out.writeObject(C_y_prime);
	        out.writeObject(N);
	        out.writeObject(R);
	        out.flush();
	        return bos.toByteArray();
	    } catch (Exception ex) {
	        throw new RuntimeException(ex);
	    }
	}

	public static Random[] initGeneratorsFromSeed(byte[] seed) {
		int K = seed.length / 8 + 1;
		long[] seeds = new long[K];

		for(int k=0;k<K;k++) {
			seeds[k] = 0;
			for (int i = 0; i < Math.min(8, K - 8 * k); i++)
			{
			   seeds[k] += ((long) seed[i + 8 *k] & 0xffL) << (8 * i);
			}
		}

		Random[] generators = new Random[K];
		for(int k = 0;k<K;k++) {
			generators[k] = new Random(seeds[k]);
		}

		return generators;
	}

	public static BigInteger drawNumberFromSeed(byte[] seed) {
		Random[] generators = initGeneratorsFromSeed(seed);


		return BigInteger.valueOf(Compat.nextLong(generators[0], Long.MAX_VALUE));
	}

	public static boolean[] drawBooleansFromSeed(byte[] seed, int n) {
		Random[] generators = {new Random(0)};
		boolean[] result = new boolean[n];

		for(int i=0;i<n;i++) {
			result[i] = generators[0].nextBoolean();
		}

		return result;
	}

	public static BigInteger[] generateX(Random generator, int N, int maxVal) {
		BigInteger[] x = new BigInteger[N];
		for(int i=0;i<N;i++) {
			x[i] = BigInteger.valueOf(generator.nextInt(maxVal));
		}
		return x;
	}

	public static byte[] fiatShamir(ECPoint C_x, ECPoint C_y, int N, int R) {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();

	    try (ObjectOutputStream out = new ObjectOutputStream(bos)) {
	        out.writeObject(C_x.compress());
	        out.writeObject(C_y.compress());
	        out.writeObject(N);
	        out.writeObject(R);
	        out.flush();
	        return bos.toByteArray();
	    } catch (Exception ex) {
	        throw new RuntimeException(ex);
	    }
	}

	public static byte[] fiatShamir(ECPoint C_x, ECPoint C_y, int N) {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();

	    try (ObjectOutputStream out = new ObjectOutputStream(bos)) {
	        out.writeObject(C_x.compress());
	        out.writeObject(C_y.compress());
	        out.writeObject(N);
	        out.flush();
	        return bos.toByteArray();
	    } catch (Exception ex) {
	        throw new RuntimeException(ex);
	    }
	}
}
