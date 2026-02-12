package com.bx.implatform.web3;

import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class Web3SignatureVerifier {

    private Web3SignatureVerifier() {
    }

    public static boolean verifyPersonalSign(String message, String signature, String address) {
        if (message == null || signature == null || address == null) {
            return false;
        }
        try {
            Sign.SignatureData signatureData = signatureData(signature);
            byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
            BigInteger publicKey = Sign.signedPrefixedMessageToKey(messageBytes, signatureData);
            String recoveredAddress = "0x" + Keys.getAddress(publicKey);
            return recoveredAddress.equalsIgnoreCase(normalize(address));
        } catch (Exception e) {
            return false;
        }
    }

    private static Sign.SignatureData signatureData(String signature) {
        String hexSignature = signature.startsWith("0x") ? signature.substring(2) : signature;
        byte[] signatureBytes = Numeric.hexStringToByteArray(hexSignature);
        int v = signatureBytes[64];
        if (v < 27) {
            v += 27;
        } else if (v >= 35) {
            v = (v - 35) % 2 + 27;
        }
        byte[] r = Arrays.copyOfRange(signatureBytes, 0, 32);
        byte[] s = Arrays.copyOfRange(signatureBytes, 32, 64);
        return new Sign.SignatureData((byte) v, r, s);
    }

    private static String normalize(String address) {
        return address == null ? null : address.trim().toLowerCase();
    }
}
