package util;

import java.util.Random;

public final class Compat {

	public static long nextLong(Random r, long bound) {
		if (bound <= 0) {
			throw new IllegalArgumentException("bound must be positive");
		}
		long m = bound - 1;
		long v = r.nextLong();
		if ((bound & m) == 0L) {
			return v & m;
		}
		for (long u = v >>> 1; u + m - (v = u % bound) < 0L; u = r.nextLong() >>> 1) {
		}
		return v;
	}

	private Compat() {
	}
}
