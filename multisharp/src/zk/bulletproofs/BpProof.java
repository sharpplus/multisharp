package zk.bulletproofs;

import ec.ECPoint;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.List;

public class BpProof implements Serializable {
	
	static final long serialVersionUID = 1L;

    private final R1CSProof proof;

    private final List<ECPoint> commitments;
    
    public BpProof(R1CSProof proof, List<ECPoint> commitments) {
    	this.proof = proof;
    	this.commitments = commitments;
    }
    
    public R1CSProof getProof() {
    	return proof;
    }
    
    public List<ECPoint> getCommitments() {
    	return commitments;
    }

    public ECPoint getCommitment(int i) {
        return commitments.get(i);
    }
	
	public int size() {
		return this.serialize().length;
	}
    
	public byte[] serialize() {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();

	    try (ObjectOutputStream out = new ObjectOutputStream(bos)) {
	        out.writeObject(this);
	        out.flush();
	        return bos.toByteArray();
	    } catch (Exception ex) {
	        throw new RuntimeException(ex);
	    }	
	}


//    public static Proof deserialize(byte[] data) throws IOException {
//        MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(data);
//        int len = unpacker.unpackInt();
//
//        List<ECPoint> commitments = new ArrayList<>();
//        for (int i = 0; i < len; i++) {
//            commitments.add(BulletProofs.getFactory().fromCompressed(unpacker.readPayload(32)));
//        }
//        R1CSProof proof = R1CSProof.unpack(unpacker);
//        unpacker.close();
//
//        return new Proof(proof, commitments);
//    }
//
//    public byte[] serialize() throws IOException {
//        MessageBufferPacker packer = MessagePack.newDefaultBufferPacker();
//        packer.packInt(commitments.size());
//
//        for (ECPoint p : commitments) {
//            packer.writePayload(p.toByteArray());
//        }
//        proof.pack(packer);
//        packer.close();
//
//        return packer.toMessageBuffer().toByteArray();
//    }

}
