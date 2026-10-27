package com.clippocket.app;
public class VersionOrderTest {
    static void expect(int expected,String a,String b){int got=Integer.signum(VersionOrder.compare(a,b));if(got!=expected)throw new AssertionError(a+" / "+b);}
    static void bad(String s){try{VersionOrder.compare(s,"1.8");throw new AssertionError("Accepted "+s);}catch(IllegalArgumentException expected){}}
    public static void main(String[] args){expect(1,"v1.8","1.7");expect(0,"1.8.0","v1.8");expect(1,"v1.10","v1.9");expect(-1,"1.7","1.8");expect(1,"v2.0","1.99");bad("nightly");bad("v1.8-beta");bad("");bad(null);System.out.println("Passed version comparison and invalid-tag checks.");}
}
