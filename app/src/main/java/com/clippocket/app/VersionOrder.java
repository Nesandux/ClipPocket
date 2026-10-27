package com.clippocket.app;
import java.math.BigInteger;

/** Compare simple dotted numeric release tags, e.g. v1.10 and 1.9. */
final class VersionOrder {
    static int compare(String a,String b) {
        String[] left=parts(a),right=parts(b);int count=Math.max(left.length,right.length);
        for(int i=0;i<count;i++) {
            BigInteger x=i<left.length?new BigInteger(left[i]):BigInteger.ZERO;
            BigInteger y=i<right.length?new BigInteger(right[i]):BigInteger.ZERO;
            int result=x.compareTo(y);if(result!=0)return result;
        }
        return 0;
    }
    private static String[] parts(String value) {
        if(value==null||!value.matches("[vV]?[0-9]+(\\.[0-9]+){0,4}")||value.length()>80)
            throw new IllegalArgumentException("Use a numeric tag such as v1.8");
        return value.replaceFirst("^[vV]", "").split("\\.");
    }
}
