package ec;

import com.weavechain.curve25519.RistrettoElement;

public class RistrettoPoint implements ECPoint {

    public static final RistrettoPoint BASEPOINT = new RistrettoPoint(RistrettoElement.BASEPOINT);

    public static final RistrettoPoint IDENTITY = new RistrettoPoint(RistrettoElement.IDENTITY);

    private final RistrettoElement point;
    
    public RistrettoPoint(RistrettoElement point) {
    	this.point = point;
    }
    
    public RistrettoElement getPoint() {
    	return point;
    }

    public byte[] toByteArray() {
        if (GroupOps.ENABLED) {
            GroupOps.rawCompress();
        }
        return point.compress().toByteArray();
    }

    public ECPoint compress() {
        if (GroupOps.ENABLED) {
            GroupOps.rawCompress();
        }
        return new CompressedRistrettoPoint(point.compress());
    }

    public ECPoint decompress() {
        return this;
    }

    public ECPoint add(ECPoint other) {
        if (GroupOps.ENABLED) {
            GroupOps.rawAdd();
        }
        return new RistrettoPoint(point.add(((RistrettoPoint)other).getPoint()));
    }

    public ECPoint subtract(ECPoint other) {
        if (GroupOps.ENABLED) {
            GroupOps.rawSub();
        }
        return new RistrettoPoint(point.subtract(((RistrettoPoint)other).getPoint()));
    }

    public ECPoint multiply(Scalar scalar) {
        if (GroupOps.ENABLED) {
            GroupOps.rawMul();
        }
        return new RistrettoPoint(point.multiply(((RScalar)scalar).getScalar()));
    }

    public ECPoint negate() {
        if (GroupOps.ENABLED) {
            GroupOps.rawNeg();
        }
        return new RistrettoPoint(point.negate());
    }

    public ECPoint dbl() {
        if (GroupOps.ENABLED) {
            GroupOps.rawDbl();
        }
        return new RistrettoPoint(point.dbl());
    }

    public String toString() {
        return point.toString();
    }
    
    @Override
    public boolean equals(ECPoint other) {
    	if(other instanceof RistrettoPoint) {
    		RistrettoPoint other2 = (RistrettoPoint) other;
    		return point.equals(other2.point);
    	}
    	return false;
    }
}
