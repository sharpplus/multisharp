package ec;

public final class GroupOps {

	private GroupOps() {
	}

	public static final boolean ENABLED = false;

	public static final int LAMBDA = 253;

	private static final double STRAUS_PER_TERM = LAMBDA / 6.0 + 7.0;

	public static final class Counts implements Cloneable {

		public long msmCalls;
		public long terms;
		public long scalarMul;
		public long add;
		public long sub;
		public long neg;
		public long compress;
		public long decompress;
		public long maxTerms;
		public double msmAdds;
		public double msmDbls;
		public double naiveAdds;
		public double naiveDbls;
		public long paramCompress;
		public long bucketAdd;
		public long bucketDbl;
		public long innerScalarMul;

		public Counts copy() {
			try {
				return (Counts) super.clone();
			} catch (CloneNotSupportedException e) {
				throw new AssertionError(e);
			}
		}

		public Counts minus(Counts o) {
			Counts d = new Counts();
			d.msmCalls = msmCalls - o.msmCalls;
			d.terms = terms - o.terms;
			d.scalarMul = scalarMul - o.scalarMul;
			d.add = add - o.add;
			d.sub = sub - o.sub;
			d.neg = neg - o.neg;
			d.compress = compress - o.compress;
			d.decompress = decompress - o.decompress;
			d.maxTerms = Math.max(maxTerms, o.maxTerms);
			d.msmAdds = msmAdds - o.msmAdds;
			d.msmDbls = msmDbls - o.msmDbls;
			d.naiveAdds = naiveAdds - o.naiveAdds;
			d.naiveDbls = naiveDbls - o.naiveDbls;
			d.bucketAdd = bucketAdd - o.bucketAdd;
			d.bucketDbl = bucketDbl - o.bucketDbl;
			d.innerScalarMul = innerScalarMul - o.innerScalarMul;
			d.paramCompress = paramCompress - o.paramCompress;
			return d;
		}

		public double additions() {
			return msmAdds + add + sub;
		}

		public double doublings() {
			return msmDbls;
		}
	}

	private static final Counts C = new Counts();

	private static int inMsm;

	private static int inParams;

	public static void reset() {
		Counts z = new Counts();
		C.msmCalls = z.msmCalls;
		C.terms = z.terms;
		C.scalarMul = z.scalarMul;
		C.add = z.add;
		C.sub = z.sub;
		C.neg = z.neg;
		C.compress = z.compress;
		C.decompress = z.decompress;
		C.maxTerms = z.maxTerms;
		C.msmAdds = z.msmAdds;
		C.msmDbls = z.msmDbls;
		C.naiveAdds = z.naiveAdds;
		C.naiveDbls = z.naiveDbls;
		C.bucketAdd = z.bucketAdd;
		C.bucketDbl = z.bucketDbl;
		C.innerScalarMul = z.innerScalarMul;
		C.paramCompress = z.paramCompress;
		inMsm = 0;
		inParams = 0;
	}

	public static void beginParams() {
		inParams++;
	}

	public static void endParams() {
		inParams--;
	}

	public static void beginMsm(int n) {
		if (inMsm == 0 && n > 0) {
			record(n);
		}
		inMsm++;
	}

	public static void endMsm() {
		inMsm--;
	}

	private static void record(int n) {
		C.msmCalls++;
		C.terms += n;
		C.msmAdds += additions(n);
		C.msmDbls += doublings(n);
		C.naiveAdds += naiveAdditions(n);
		C.naiveDbls += naiveDoublings(n);
		if (n > C.maxTerms) {
			C.maxTerms = n;
		}
		if (n == 1) {
			C.scalarMul++;
		}
	}

	static void rawAdd() {
		if (inMsm > 0) {
			C.bucketAdd++;
		} else {
			C.add++;
		}
	}

	static void rawSub() {
		if (inMsm > 0) {
			C.bucketAdd++;
		} else {
			C.sub++;
		}
	}

	static void rawDbl() {
		C.bucketDbl++;
	}

	static void rawMul() {
		if (inMsm > 0) {
			C.innerScalarMul++;
		} else {
			record(1);
		}
	}

	static void rawNeg() {
		if (inMsm == 0) {
			C.neg++;
		}
	}

	static void rawCompress() {
		if (inParams > 0) {
			C.paramCompress++;
		} else {
			C.compress++;
		}
	}

	static void rawDecompress() {
		C.decompress++;
	}

	public static double additions(long n) {
		if (n <= 0) {
			return 0;
		}
		double best = n * STRAUS_PER_TERM;
		for (int w = 2; w <= 16; w++) {
			double windows = Math.ceil(LAMBDA / (double) w);
			double cost = windows * (n + (1L << (w - 1)));
			if (cost < best) {
				best = cost;
			}
		}
		return best;
	}

	public static double doublings(long n) {
		return n <= 0 ? 0 : LAMBDA;
	}

	public static double naiveAdditions(long n) {
		return n <= 0 ? 0 : n * STRAUS_PER_TERM + (n - 1);
	}

	public static double naiveDoublings(long n) {
		return n <= 0 ? 0 : n * (double) LAMBDA;
	}
}
