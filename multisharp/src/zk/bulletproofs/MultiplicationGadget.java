package zk.bulletproofs;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import ec.ECPoint;
import ec.Scalar;

public class MultiplicationGadget {

	public static Scalar fieldScalar(BigInteger v) {
		return Utils.scalar(v.mod(PedersenCommitment.GROUP_ORDER));
	}

	public static LRO constrainProductInternal(ConstraintSystem cs, Scalar x1, Scalar x2) {
		return cs.allocateMultiplier(x1, x2);
	}

	public static void constrainProduct(ConstraintSystem cs, Variable v1, Variable v2,
			Variable vy, Scalar x1, Scalar x2) {
		LRO gate = cs.allocateMultiplier(x1, x2);

		cs.constrain(LinearCombination.from(gate.getLeft()).sub(LinearCombination.from(v1)));
		cs.constrain(LinearCombination.from(gate.getRight()).sub(LinearCombination.from(v2)));
		cs.constrain(LinearCombination.from(gate.getOutput()).sub(LinearCombination.from(vy)));
	}

	public static BpProof generateMulProof(BigInteger x1, BigInteger x2, Scalar r1, Scalar r2,
			Scalar ry, PedersenCommitment pedersenCommitment, BulletProofGenerators generators) {
		BigInteger y = x1.multiply(x2);

		Scalar s1 = fieldScalar(x1);
		Scalar s2 = fieldScalar(x2);
		Scalar sy = fieldScalar(y);

		Transcript transcript = new Transcript();
		BpProver prover = new BpProver(transcript, pedersenCommitment);

		List<ECPoint> commitments = new ArrayList<>();

		Commitment c1 = prover.commit(s1, r1 != null ? r1 : Utils.randomScalar());
		commitments.add(c1.getCommitment());

		Commitment c2 = prover.commit(s2, r2 != null ? r2 : Utils.randomScalar());
		commitments.add(c2.getCommitment());

		Commitment cy = prover.commit(sy, ry != null ? ry : Utils.randomScalar());
		commitments.add(cy.getCommitment());

		constrainProduct(prover, c1.getVariable(), c2.getVariable(), cy.getVariable(), s1, s2);

		return new BpProof(prover.prove(generators), commitments);
	}

	public static boolean verifyMulProof(BpProof proof, PedersenCommitment pedersenCommitment,
			BulletProofGenerators generators) {
		Transcript transcript = new Transcript();
		BpVerifier verifier = new BpVerifier(transcript);

		Variable v1 = verifier.commit(proof.getCommitment(0));
		Variable v2 = verifier.commit(proof.getCommitment(1));
		Variable vy = verifier.commit(proof.getCommitment(2));

		constrainProduct(verifier, v1, v2, vy, null, null);

		return verifier.verify(proof, pedersenCommitment, generators);
	}
}
