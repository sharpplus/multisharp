package decomp;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class SquareDecomp {

	static final String PRECOMP_FILE_DIR = System.getProperty("user.dir") + "/precomp/";

	public interface PrecompSource {
		InputStream open(String name) throws IOException;
	}

	private static PrecompSource source = new PrecompSource() {
		public InputStream open(String name) throws IOException {
			return new FileInputStream(PRECOMP_FILE_DIR + name);
		}
	};

	public static void setPrecompSource(PrecompSource s) {
		source = s;
		cachedDecomp2s = null;
		cachedPrimes = null;
	}

	private static BufferedReader open(String name) throws IOException {
		return new BufferedReader(new InputStreamReader(source.open(name), StandardCharsets.UTF_8));
	}

	private static Map<Integer, int[]> cachedDecomp2s;
	private static int cachedMaxDecomp2 = -1;
	private static Set<Integer> cachedPrimes;
	private static int cachedMaxPrime = -1;
	static final String DECOMP2_FILE_NAME = "decomps2.txt";
	static final String PRIME_FILE_NAME = "primes.txt";
	static final String PRIME_DECOMP_FILE_NAME = "prime_decomps.txt";

	static String tempName(String fileName) {
		return fileName.substring(0, fileName.length() - 4) + "_temp.txt";
	}

	static final int DEFAULT_N = 1000000;

	static final int PROGRESS_EVERY = 100000;

	Set<Integer> primes;
	int maxPrime = -1;

	Map<Integer, int[]> decomp2s;
	int maxDecomp2 = -1;

	boolean isPrecompPrime(int inputNumber) {
		return primes.contains(inputNumber);
	}

	boolean isPrime(int inputNumber) {
		if(maxPrime >= 0 && inputNumber < maxPrime) return isPrecompPrime(inputNumber);
		if(inputNumber <= 1) return false;
		else {
			for (int i = 2; i<= inputNumber/2; i++) {
				if ((inputNumber % i) == 0) return false;
			}
			return true;
		}
	}

	public void printAllDecomps(int x) {
		System.out.println("x: "+x);
//		int v = (int) Math.sqrt(t) + 1;

		for(int i=0;i<x;i++) {
			for(int j=0;j<=i;j++) {
				int k = x - i * i - j * j;
				if(k <= i) {
					int l = (int) Math.sqrt(k);
					if(l * l == k) System.out.println(i+" "+j+" "+l);
				}
			}
		}
	}

	public void printAllDecomps(int x, int B) {
		int t = 4 * x * (B - x) + 1;

		System.out.println("x: "+x+", B: "+B+", t: "+t);
//		int v = (int) Math.sqrt(t) + 1;

		for(int i=0;i<B;i++) {
			for(int j=0;j<=i;j++) {
				int k = t - i * i - j * j;
				if(k <= i) {
					int l = (int) Math.sqrt(k);
					if(l * l == k) System.out.println(i+" "+j+" "+l);
				}
			}
		}
	}

	public int[] decomp3(int x) {
		if(x < 0) return new int[] {-1, -1, -1};
		return decomp3base(4 * x + 1);
	}

	public int[] decomp3(int x, int B) {
		if(x < 0 || x > B) return new int[] {-1, -1, -1};
		return decomp3base(4 * x * (B - x) + 1);
	}

	public int[] decomp3base(int t) {
		for(int i : decomp2s.keySet()) {
			int k = (int) Math.sqrt(t - i);
			if(k * k == t - i) {
				int[] vals = decomp2s.get(i);
				return new int[] {k, vals[0], vals[1]};
			}

		}

		return new int[] {-1, -1, -1};
	}

	public void precomputePrimes(int n) {
	    PrintWriter printWriter;
		try {
			printWriter = new PrintWriter(new FileWriter(PRECOMP_FILE_DIR + tempName(PRIME_FILE_NAME)));
			printWriter.println(n);

		    for(int i=0;i<n+1;i++) {
		    	if(isPrime(i) && i % 4 == 1) printWriter.println(i);
		    }

		    printWriter.close();


		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void precomputePrimeDecomps(int n) {
	    PrintWriter printWriter;
		try {
			printWriter = new PrintWriter(new FileWriter(PRECOMP_FILE_DIR + tempName(PRIME_DECOMP_FILE_NAME)));
			printWriter.println(n);

		    for(int i : primes) {
		    	for(int j = (int) Math.ceil(Math.sqrt(i));j>=0;j--) {
		    		int k = (int) Math.ceil(Math.sqrt(i - j*j));
		    		if(i == j * j + k * k) {
		    			printWriter.println(i+","+j+","+k);
		    			break;
		    		}
		    	}
		    }

		    printWriter.close();


		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void loadPrecompPrimes() {
		if (cachedPrimes == null) {
			Set<Integer> loaded = new TreeSet<Integer>();
			try (BufferedReader br = open(PRIME_FILE_NAME)) {
			    String line;
			    line = br.readLine();
			    int i = Integer.parseInt(line);
			    cachedMaxPrime = i;

			    while ((line = br.readLine()) != null) {
			    	i = Integer.parseInt(line);
			    	loaded.add(i);
			    }
			    cachedPrimes = loaded;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		primes = cachedPrimes;
		maxPrime = cachedMaxPrime;
	}

	public void checkPrimeDecomps(int n) {
		SquareDecomp decomp = new SquareDecomp();
		decomp.loadPrecompPrimes();

		for(int i=0;i<n+1;i++) {
			int j = 4*i+1;
			boolean success = false;
//			String s = "";
			for(int y=(int) Math.ceil(Math.sqrt(j));y>=0;y--) {
//				if(y % 2 == 1) y--;
				int z = j - y*y;
				if(z >= 0) {
	//				System.out.println(z+" + " + (y*y));
//					s += "check "+z+" + "+(y*y)+"\n";
					if((decomp.primes.contains(z)) || z == 1 || z == 0) success = true;
				}
			}
			if(!success) {
				System.out.println(j+": "+success);
//				System.out.println(s);
			}
		}
	}

	public void precomputeDecomp2s(int n) {
	    PrintWriter printWriter;
		try {
			printWriter = new PrintWriter(new FileWriter(PRECOMP_FILE_DIR + tempName(DECOMP2_FILE_NAME)));

		    printWriter.println(n);

		    int written = 0;
		    for(int i=0;i<n+1;i++) {
		    	if (i > 0 && i % PROGRESS_EVERY == 0) {
		    		System.out.println("  " + i + " of " + n + " (" + written
		    				+ " decompositions so far)");
		    	}
		    	boolean found = false;
		    	int z = (int) Math.sqrt(i);
		    	for(int j=z;j>=0 && !found;j--) {
		    		for(int k=0;k<=j && !found;k++) {
		    			if(i == j * j + k * k) {
		    				printWriter.println(i+","+j+","+k);
		    				found = true;
		    				written++;
		    			}
		    		}
		    	}
		    }
		    System.out.println("  " + written + " decompositions of the "
		    		+ (n + 1) + " values up to " + n);

		    printWriter.close();


		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void loadPrecompDecomp2s() {
		if (cachedDecomp2s == null) {
			Map<Integer, int[]> loaded = new TreeMap<Integer, int[]>();
			try (BufferedReader br = open(DECOMP2_FILE_NAME)) {
			    String line;
			    line = br.readLine();
			    int ii = Integer.parseInt(line);
			    cachedMaxDecomp2 = ii;

			    while ((line = br.readLine()) != null) {
			    	String[] data = line.split(",");
			    	int i = Integer.parseInt(data[0]);
			    	int j = Integer.parseInt(data[1]);
			    	int k = Integer.parseInt(data[2]);

			    	loaded.put(i, new int[] {j, k});
			    }
			    cachedDecomp2s = loaded;

			} catch (Exception e) {
				throw new IllegalStateException("cannot read the precomputed two-square"
						+ " table " + DECOMP2_FILE_NAME + "; on the JVM it is read"
						+ " from " + PRECOMP_FILE_DIR + ", and on Android from whatever"
						+ " setPrecompSource was given", e);
			}
		}
		decomp2s = cachedDecomp2s;
		maxDecomp2 = cachedMaxDecomp2;
	}

	public static void main(String[] a) {
		String what = a.length > 0 ? a[0] : "decomps2";
		int n = a.length > 1 ? Integer.parseInt(a[1]) : DEFAULT_N;

		SquareDecomp decomp = new SquareDecomp();
		String written;
		if ("decomps2".equals(what)) {
			System.out.println("regenerating the two-square table up to " + n + "...");
			decomp.precomputeDecomp2s(n);
			written = tempName(DECOMP2_FILE_NAME);
		} else if ("primes".equals(what)) {
			System.out.println("regenerating the primes up to " + n + "...");
			decomp.precomputePrimes(n);
			written = tempName(PRIME_FILE_NAME);
		} else if ("primedecomps".equals(what)) {
			System.out.println("regenerating the prime decompositions up to " + n + "...");
			decomp.loadPrecompPrimes();
			decomp.precomputePrimeDecomps(n);
			written = tempName(PRIME_DECOMP_FILE_NAME);
		} else if ("check".equals(what)) {
			System.out.println("checking 4i+1 up to " + n
					+ "; any line below is a failure...");
			decomp.checkPrimeDecomps(n);
			System.out.println("done");
			return;
		} else {
			System.out.println("usage: SquareDecomp"
					+ " [decomps2|primes|primedecomps|check] [n]");
			return;
		}
		System.out.println("wrote " + PRECOMP_FILE_DIR + written);
		System.out.println("check it, then put it in service with");
		System.out.println("    mv precomp/" + written + " precomp/"
				+ written.replace("_temp.txt", ".txt"));
	}

}
