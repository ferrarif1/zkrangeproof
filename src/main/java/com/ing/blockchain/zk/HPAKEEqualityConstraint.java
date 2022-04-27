/*
 * Copyright 2017 ING Bank N.V.
 * This file is part of the go-ethereum library.
 *
 * The go-ethereum library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The go-ethereum library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the go-ethereum library. If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.ing.blockchain.zk;

import com.ing.blockchain.zk.dto.ECProof;
import com.ing.blockchain.zk.exception.ZeroKnowledgeException;
import com.ing.blockchain.zk.util.DigestUtil;
import org.bouncycastle.util.BigIntegers;

import java.math.BigInteger;
import java.security.SecureRandom;

import static java.math.BigInteger.ONE;

/**
 * Implementation of 'Proof that Two Commitments Hide the Same Secret'
 * 证明承诺隐藏的值相等  c = g^x*h^r mod N
 * This protocol is described in section 2.2 in the following paper:
 * Fabrice Boudot, Efficient Proofs that a Committed Number Lies in an Interval
 */
public class HPAKEEqualityConstraint {

    // Security parameters
    public static final int t = 128;  // Parameter for soundness (bitlength of hash divided by 2)
    public static final int l = 40;   // Parameter for zero-knowledge property
    public static final int s1 = TTPGenerator.s; // s from commitment 1
    public static final int s2 = TTPGenerator.s; // s from commitment 2
    public static final BigInteger TWO = BigInteger.valueOf(2);
    public static final BigInteger b = TWO.pow(256); // max uint in Ethereum
    /*
    * W1，W2 hide the same value x
    *
    * */
    public static ECProof calculateZeroKnowledgeProof(
            BigInteger N,   // large composite number whose factorization is unknown by Alice and Bob
            BigInteger g1,  // element of large order in Zn*
            BigInteger g2,  //  |
            BigInteger h1,  //  |> elements of the group generated by g1 such that discrete logarithms are unknown
            BigInteger h2,  //  |
            BigInteger x,   // the secretly committed number
            BigInteger r1,  // random number used in commitment 1 (secret)
            BigInteger r2,  // random number used in commitment 2 (secret)
            SecureRandom random) {

        BigInteger w = BigIntegers.createRandomInRange(ONE, TWO.pow(l + t).multiply(b).subtract(ONE), random);
        BigInteger n1 = BigIntegers.createRandomInRange(ONE, TWO.pow(l + t + s1).multiply(N).subtract(ONE), random);
        BigInteger n2 = BigIntegers.createRandomInRange(ONE, TWO.pow(l + t + s2).multiply(N).subtract(ONE), random);

        BigInteger W1 = g1.modPow(w, N).multiply(h1.modPow(n1, N)).mod(N); // g1^w h1^n1
        BigInteger W2 = g2.modPow(w, N).multiply(h2.modPow(n2, N)).mod(N); // g2^w h2^n2

        BigInteger c = DigestUtil.calculateHash(W1, W2);

        BigInteger D = w.add(c.multiply(x));    // w + cx
        BigInteger D1 = n1.add(c.multiply(r1)); // n1 + c*r1
        BigInteger D2 = n2.add(c.multiply(r2)); // n2 + c*r2

        return new ECProof(c, D, D1, D2);
    }
    /*
    * 根据proof计算出W1，W2，验证是不是等于c
    * W1 = g1^D h1^D1 E^-c = g1^(w + cx) h1^(n1 + c*r1) g1^(-x*c) h1^(-r1*c)
    *                      = g1^(w + cx - xc)  h1^(n1 + c*r1-r1*c)
    *                      = g1^w h1^n1
    * W2 = g2^D h2^D1 E^-c = g2^(w + cx) h2^(n2 + c*r2) g1^(-x*c) h1^(-r2*c)
    *                      = g2^(w + cx - xc)  h2^(n2 + c*r2-r2*c)
    *                      = g2^w h2^n2
    * c' = Hash(W1,W2) ?= ecProof.c
    * 由于验证时g1与g2都pow了相同的D，E与F都pow了相同的-c,
    * 如果x不相等或证明中的r1，r2与E，F中不同，
    * 就无法约掉cx与cr
    * */
    public static void validateZeroKnowledgeProof(BigInteger N, BigInteger g1, BigInteger g2, BigInteger h1, BigInteger h2,
                                                  BigInteger E, BigInteger F, ECProof ecProof) {

        if (E.equals(BigInteger.ZERO) || F.equals(BigInteger.ZERO)) {
            // To prevent failure at 0 ^ -c
            throw new ZeroKnowledgeException("Zero-knowledge proof validation failed");
        }

        BigInteger c = ecProof.getC();
        BigInteger D = ecProof.getD();
        BigInteger D1 = ecProof.getD1();
        BigInteger D2 = ecProof.getD2();

        BigInteger W1 = g1.modPow(D, N).multiply(h1.modPow(D1, N)).multiply(E.modPow(c.negate(), N)).mod(N); // g1^D h1^D1 E^-c
        BigInteger W2 = g2.modPow(D, N).multiply(h2.modPow(D2, N)).multiply(F.modPow(c.negate(), N)).mod(N); // g2^D h2^D2 F^-c

        if (!c.equals(DigestUtil.calculateHash(W1, W2))) {
            throw new ZeroKnowledgeException("Zero-knowledge proof validation failed");
        }
    }
}