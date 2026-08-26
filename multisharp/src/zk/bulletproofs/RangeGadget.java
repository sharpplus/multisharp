package zk.bulletproofs;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import ec.ECPoint;
import ec.Scalar;

public class RangeGadget {

	public static void constrainRange(ConstraintSystem cs, Variable v, BigInteger assignment,
			long min, long max, int bitsize) {
		BigInteger lower = assignment == null ? null
				: assignment.subtract(BigInteger.valueOf(min));
		BigInteger upper = assignment == null ? null
				: BigInteger.valueOf(max).subtract(assignment);

		decompose(cs, lower, bitsize, LinearCombination.from(v)
				.sub(LinearCombination.from(Utils.scalar(min))));

		decompose(cs, upper, bitsize, LinearCombination.from(Utils.scalar(max))
				.sub(LinearCombination.from(v)));
	}

	public static void constrainRangeInternal(ConstraintSystem cs, BigInteger assignment,
			long min, long max, int bitsize) {
		BigInteger lower = assignment == null ? null
				: assignment.subtract(BigInteger.valueOf(min));
		BigInteger upper = assignment == null ? null
				: BigInteger.valueOf(max).subtract(assignment);

		LinearCombination lowerSum = bitSum(cs, lower, bitsize);
		LinearCombination upperSum = bitSum(cs, upper, bitsize);

		cs.constrain(lowerSum.add(upperSum)
				.sub(LinearCombination.from(Utils.scalar(max - min))));
	}

	public static void constrainRangePow2(ConstraintSystem cs, Variable v,
			BigInteger assignment, int bitsize) {
		decompose(cs, assignment, bitsize, LinearCombination.from(v));
	}

	public static LinearCombination constrainRangePow2Internal(ConstraintSystem cs,
			BigInteger assignment, int bitsize) {
		return bitSum(cs, assignment, bitsize);
	}

	public static int gateCountPow2(int bitsize) {
		return bitsize;
	}

	private static void decompose(ConstraintSystem cs, BigInteger value, int bitsize,
			LinearCombination target) {
		cs.constrain(bitSum(cs, value, bitsize).sub(target));
	}

	private static LinearCombination bitSum(ConstraintSystem cs, BigInteger value, int bitsize) {
		List<Term> bits = new ArrayList<>();
		Scalar exp2 = BulletProofs.getFactory().one();

		for (int i = 0; i < bitsize; i++) {
			BigInteger bit = value == null ? BigInteger.ZERO
					: value.shiftRight(i).and(BigInteger.ONE);

			LRO gate = cs.allocateMultiplier(Utils.scalar(BigInteger.ONE.subtract(bit)),
					Utils.scalar(bit));

			cs.constrain(LinearCombination.from(gate.getOutput()));

			cs.constrain(LinearCombination.from(gate.getLeft())
					.add(LinearCombination.from(gate.getRight()))
					.sub(LinearCombination.from(BulletProofs.getFactory().one())));

			bits.add(new Term(gate.getRight(), exp2));
			exp2 = exp2.add(exp2);
		}

		LinearCombination sum = null;
		for (Term t : bits) {
			sum = sum == null ? LinearCombination.from(t) : sum.add(LinearCombination.from(t));
		}
		return sum;
	}

	public static int gateCount(int bitsize) {
		return 2 * bitsize;
	}

	public static BpProof generateRangeProof(BigInteger value, long min, long max, int bitsize,
			Scalar rnd, PedersenCommitment pedersenCommitment,
			BulletProofGenerators generators) {
		Transcript transcript = new Transcript();
		BpProver prover = new BpProver(transcript, pedersenCommitment);

		List<ECPoint> commitments = new ArrayList<>();
		Commitment vComm = prover.commit(Utils.scalar(value),
				rnd != null ? rnd : Utils.randomScalar());
		commitments.add(vComm.getCommitment());

		constrainRange(prover, vComm.getVariable(), value, min, max, bitsize);

		return new BpProof(prover.prove(generators), commitments);
	}

	public static boolean verifyRangeProof(long min, long max, int bitsize, BpProof proof,
			PedersenCommitment pedersenCommitment, BulletProofGenerators generators) {
		Transcript transcript = new Transcript();
		BpVerifier verifier = new BpVerifier(transcript);

		Variable v = verifier.commit(proof.getCommitment(0));
		constrainRange(verifier, v, null, min, max, bitsize);

		return verifier.verify(proof, pedersenCommitment, generators);
	}

	public static BpProof generateRangeProofPow2(BigInteger value, int bitsize, Scalar rnd,
			PedersenCommitment pedersenCommitment, BulletProofGenerators generators) {
		Transcript transcript = new Transcript();
		BpProver prover = new BpProver(transcript, pedersenCommitment);

		List<ECPoint> commitments = new ArrayList<>();
		Commitment vComm = prover.commit(Utils.scalar(value),
				rnd != null ? rnd : Utils.randomScalar());
		commitments.add(vComm.getCommitment());

		constrainRangePow2(prover, vComm.getVariable(), value, bitsize);

		return new BpProof(prover.prove(generators), commitments);
	}

	public static boolean verifyRangeProofPow2(int bitsize, BpProof proof,
			PedersenCommitment pedersenCommitment, BulletProofGenerators generators) {
		Transcript transcript = new Transcript();
		BpVerifier verifier = new BpVerifier(transcript);

		Variable v = verifier.commit(proof.getCommitment(0));
		constrainRangePow2(verifier, v, null, bitsize);

		return verifier.verify(proof, pedersenCommitment, generators);
	}
}
