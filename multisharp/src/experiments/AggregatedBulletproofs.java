package experiments;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ec.ECPoint;
import ec.Scalar;
import zk.bulletproofs.BpProof;
import zk.bulletproofs.BpProver;
import zk.bulletproofs.BpVerifier;
import zk.bulletproofs.BulletProofGenerators;
import zk.bulletproofs.BulletProofs;
import zk.bulletproofs.Commitment;
import zk.bulletproofs.LRO;
import zk.bulletproofs.LinearCombination;
import zk.bulletproofs.MultiplicationGadget;
import zk.bulletproofs.PedersenCommitment;
import zk.bulletproofs.RangeGadget;
import zk.bulletproofs.Transcript;
import zk.bulletproofs.Utils;
import zk.bulletproofs.Variable;

public class AggregatedBulletproofs {

	public static int proofOnlySize(BpProof proof) {
		return new BpProof(proof.getProof(), Collections.<ECPoint>emptyList()).size();
	}

	public static int gateCount(int N, int M, int S, int bitsize) {
		return bitsize * N + M + Math.max(0, S - M);
	}

	public static int requiredCapacity(int N, int M, int S, int bitsize) {
		return Math.max(1, Utils.nextPowerOf2(gateCount(N, M, S, bitsize)));
	}

	public static BpProof generate(int N, int M, int S, BigInteger[] values, long min,
			long max, int bitsize, BigInteger[] xp1, BigInteger[] xp2,
			PedersenCommitment pedersenCommitment, BulletProofGenerators generators) {
		Transcript transcript = new Transcript();
		BpProver prover = new BpProver(transcript, pedersenCommitment);

		List<ECPoint> commitments = new ArrayList<>();
		Variable[] committed = new Variable[S];

		for (int s = 0; s < S; s++) {
			Commitment c = prover.commit(MultiplicationGadget.fieldScalar(committedValue(s, M,
					values, xp1)), Utils.randomScalar());
			commitments.add(c.getCommitment());
			committed[s] = c.getVariable();
		}

		for (int i = 0; i < M; i++) {
			Scalar s1 = MultiplicationGadget.fieldScalar(xp1[i]);
			Scalar s2 = MultiplicationGadget.fieldScalar(xp2[i]);
			LRO gate = MultiplicationGadget.constrainProductInternal(prover, s1, s2);
			if (i < S) {
				prover.constrain(LinearCombination.from(gate.getLeft())
						.sub(LinearCombination.from(committed[i])));
			}
		}

		for (int s = M; s < S; s++) {
			LRO gate = MultiplicationGadget.constrainProductInternal(prover,
					MultiplicationGadget.fieldScalar(committedValue(s, M, values, xp1)),
					BulletProofs.getFactory().one());
			prover.constrain(LinearCombination.from(gate.getLeft())
					.sub(LinearCombination.from(committed[s])));
		}

		for (int i = 0; i < N; i++) {
			RangeGadget.constrainRangePow2Internal(prover, values[i], bitsize);
		}

		return new BpProof(prover.prove(generators), commitments);
	}

	private static BigInteger committedValue(int s, int M, BigInteger[] values,
			BigInteger[] xp1) {
		if (s < M) {
			return xp1[s];
		}
		return values.length > 0 ? values[(s - M) % values.length] : BigInteger.ONE;
	}

	public static boolean verify(int N, int M, int S, long min, long max, int bitsize,
			BpProof proof, PedersenCommitment pedersenCommitment,
			BulletProofGenerators generators) {
		Transcript transcript = new Transcript();
		BpVerifier verifier = new BpVerifier(transcript);

		Variable[] committed = new Variable[S];
		for (int s = 0; s < S; s++) {
			committed[s] = verifier.commit(proof.getCommitment(s));
		}

		for (int i = 0; i < M; i++) {
			LRO gate = MultiplicationGadget.constrainProductInternal(verifier, null, null);
			if (i < S) {
				verifier.constrain(LinearCombination.from(gate.getLeft())
						.sub(LinearCombination.from(committed[i])));
			}
		}

		for (int s = M; s < S; s++) {
			LRO gate = MultiplicationGadget.constrainProductInternal(verifier, null, null);
			verifier.constrain(LinearCombination.from(gate.getLeft())
					.sub(LinearCombination.from(committed[s])));
		}

		for (int i = 0; i < N; i++) {
			RangeGadget.constrainRangePow2Internal(verifier, null, bitsize);
		}

		return verifier.verify(proof, pedersenCommitment, generators);
	}

}
