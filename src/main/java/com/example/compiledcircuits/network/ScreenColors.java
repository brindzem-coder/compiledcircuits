package com.example.compiledcircuits.network;

import java.math.BigInteger;

/** Least-significant bits first: RRR GGG BB. */
public final class ScreenColors {
    public static String channel(String base,int bit){
        if(bit<0||bit>7)throw new IllegalArgumentException("Invalid screen bit");
        return WireIds.normalize(new BigInteger(WireIds.normalize(base)).add(BigInteger.valueOf(bit)).toString());
    }
    public static String base(String id){id=WireIds.normalize(id);channel(id,7);return id;}
    public static int rgb(int value){return ((value&7)*255/7<<16)|((value>>3&7)*255/7<<8)|(value>>6&3)*85;}
}
