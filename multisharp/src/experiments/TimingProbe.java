package experiments;

import java.security.NoSuchAlgorithmException;

public class TimingProbe {

	public static void main(String[] args) throws NoSuchAlgorithmException {
		String mode = args.length > 0 ? args[0] : "solo";
		int N = args.length > 1 ? Integer.parseInt(args[1]) : 1024;
		int R = args.length > 2 ? Integer.parseInt(args[2]) : 256;
		int reps = args.length > 3 ? Integer.parseInt(args[3]) : 10;
		int blocks = args.length > 4 ? Integer.parseInt(args[4]) : 3;

		System.out.println("TimingProbe mode=" + mode + " N=M=" + N + " R=" + R + " reps=" + reps
				+ " blocks=" + blocks);

		MultiSharpExperiments ms = new MultiSharpExperiments();
		for (int b = 0; b < blocks; b++) {
			MultiSharpExperiments.Result res = ms.measure(N, N, R, reps);
			System.out.printf("  block %d: gen=%7.1f ms (+-%.1f)  vrf=%7.1f ms (+-%.1f)%n",
					b + 1, res.genMean, res.genCi, res.vrfMean, res.vrfCi);
		}
	}
}
