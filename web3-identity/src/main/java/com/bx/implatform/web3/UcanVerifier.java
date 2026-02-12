package com.bx.implatform.web3;

import com.bx.implatform.config.props.Web3Properties;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Component
@RequiredArgsConstructor
public class UcanVerifier {

    private static final Gson GSON = new Gson();
    private static final String BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

    private final Web3Properties web3Properties;

    public boolean isUcanToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) return false;
            JsonObject header = GSON.fromJson(new String(base64UrlDecode(parts[0]), StandardCharsets.UTF_8), JsonObject.class);
            String typ = getString(header, "typ");
            String alg = getString(header, "alg");
            return "UCAN".equals(typ) || "EdDSA".equals(alg);
        } catch (Exception e) {
            return false;
        }
    }

    public String verifyInvocation(String token) {
        VerifiedUcan verified = verifyUcanJws(token);
        JsonObject payload = verified.payload;
        String aud = getString(payload, "aud");
        String expectedAud = resolveAudience();
        if (expectedAud != null && !expectedAud.isEmpty() && !expectedAud.equals(aud)) {
            throw new IllegalArgumentException(String.format("UCAN audience mismatch expected=%s got=%s", expectedAud, aud));
        }
        List<UcanCapability> cap = parseCapabilities(payload.get("cap"));
        List<UcanCapability> required = new ArrayList<>();
        required.add(new UcanCapability(resolveResource(), resolveAction()));
        if (!capsAllow(cap, required)) {
            throw new IllegalArgumentException("UCAN capability denied");
        }
        JsonArray proofs = payload.has("prf") && payload.get("prf").isJsonArray() ? payload.getAsJsonArray("prf") : new JsonArray();
        String iss = verifyProofChain(getString(payload, "iss"), cap, verified.exp, proofs);
        return iss.replace("did:pkh:eth:", "");
    }

    private String resolveAudience() {
        String aud = web3Properties.getUcanAudience();
        if (aud != null && !aud.isBlank()) {
            return aud;
        }
        String domain = web3Properties.getExpectedDomain();
        if (domain != null && !domain.isBlank()) {
            return "did:web:" + domain;
        }
        return null;
    }

    private String resolveResource() {
        String resource = web3Properties.getUcanResource();
        return resource == null || resource.isBlank() ? "profile" : resource;
    }

    private String resolveAction() {
        String action = web3Properties.getUcanAction();
        return action == null || action.isBlank() ? "read" : action;
    }

    private static List<UcanCapability> parseCapabilities(JsonElement element) {
        List<UcanCapability> list = new ArrayList<>();
        if (element == null || !element.isJsonArray()) {
            return list;
        }
        for (JsonElement capEl : element.getAsJsonArray()) {
            if (!capEl.isJsonObject()) continue;
            JsonObject obj = capEl.getAsJsonObject();
            String resource = getString(obj, "resource");
            String action = getString(obj, "action");
            if (resource != null && action != null) {
                list.add(new UcanCapability(resource, action));
            }
        }
        return list;
    }

    private static boolean capsAllow(List<UcanCapability> available, List<UcanCapability> required) {
        if (available == null || available.isEmpty()) return false;
        for (UcanCapability req : required) {
            boolean matched = false;
            for (UcanCapability cap : available) {
                if (matchPattern(cap.resource, req.resource) && matchPattern(cap.action, req.action)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) return false;
        }
        return true;
    }

    private static boolean matchPattern(String pattern, String value) {
        if ("*".equals(pattern)) return true;
        if (pattern != null && pattern.endsWith("*")) {
            return value != null && value.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        return pattern != null && pattern.equals(value);
    }

    private static UcanStatement extractUcanStatement(String message) {
        if (message == null) return null;
        String[] lines = message.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.toUpperCase().startsWith("UCAN-AUTH")) {
                String payload = trimmed.substring("UCAN-AUTH".length()).replaceFirst("^\\s*:?\\s*", "");
                JsonObject json = GSON.fromJson(payload, JsonObject.class);
                if (json == null) return null;
                String aud = getString(json, "aud");
                List<UcanCapability> cap = parseCapabilities(json.get("cap"));
                Long exp = getLong(json, "exp");
                Long nbf = getLong(json, "nbf");
                if (aud == null || cap.isEmpty() || exp == null) return null;
                return new UcanStatement(aud, cap, normalizeEpochMillis(exp), normalizeEpochMillis(nbf));
            }
        }
        return null;
    }

    private static UcanRootResult verifyRootProof(JsonObject root) {
        if (root == null || !"siwe".equals(getString(root, "type"))) {
            throw new IllegalArgumentException("Invalid root proof");
        }
        JsonObject siwe = root.has("siwe") && root.get("siwe").isJsonObject() ? root.getAsJsonObject("siwe") : null;
        String message = siwe == null ? null : getString(siwe, "message");
        String signature = siwe == null ? null : getString(siwe, "signature");
        if (message == null || signature == null) {
            throw new IllegalArgumentException("Missing SIWE message");
        }

        String recovered = recoverAddress(message, signature);
        String iss = "did:pkh:eth:" + recovered;
        String rootIss = getString(root, "iss");
        if (rootIss != null && !rootIss.equals(iss)) {
            throw new IllegalArgumentException("Root issuer mismatch");
        }

        UcanStatement statement = extractUcanStatement(message);
        if (statement == null) {
            throw new IllegalArgumentException("Missing UCAN statement");
        }

        String rootAud = getString(root, "aud");
        if (rootAud != null && !rootAud.equals(statement.aud)) {
            throw new IllegalArgumentException("Root audience mismatch");
        }
        Long rootExp = getLong(root, "exp");
        if (rootExp != null && normalizeEpochMillis(rootExp) != statement.exp) {
            throw new IllegalArgumentException("Root expiry mismatch");
        }

        long now = nowMillis();
        if (statement.nbf != null && now < statement.nbf) {
            throw new IllegalArgumentException("Root not active");
        }
        if (now > statement.exp) {
            throw new IllegalArgumentException("Root expired");
        }

        return new UcanRootResult(iss, statement);
    }

    private static DecodedUcan decodeUcanToken(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid UCAN token");
        }
        JsonObject header = GSON.fromJson(new String(base64UrlDecode(parts[0]), StandardCharsets.UTF_8), JsonObject.class);
        JsonObject payload = GSON.fromJson(new String(base64UrlDecode(parts[1]), StandardCharsets.UTF_8), JsonObject.class);
        byte[] signature = base64UrlDecode(parts[2]);
        return new DecodedUcan(header, payload, signature, parts[0] + "." + parts[1]);
    }

    private static VerifiedUcan verifyUcanJws(String token) {
        DecodedUcan decoded = decodeUcanToken(token);
        String alg = getString(decoded.header, "alg");
        if (alg != null && !"EdDSA".equals(alg)) {
            throw new IllegalArgumentException("Unsupported UCAN alg");
        }
        String iss = getString(decoded.payload, "iss");
        byte[] rawKey = didKeyToPublicKey(iss);
        Ed25519Signer signer = new Ed25519Signer();
        signer.init(false, new Ed25519PublicKeyParameters(rawKey, 0));
        byte[] signingBytes = decoded.signingInput.getBytes(StandardCharsets.UTF_8);
        signer.update(signingBytes, 0, signingBytes.length);
        if (!signer.verifySignature(decoded.signature)) {
            throw new IllegalArgumentException("Invalid UCAN signature");
        }

        long exp = normalizeEpochMillis(getLong(decoded.payload, "exp"));
        Long nbfValue = getLong(decoded.payload, "nbf");
        long nbf = normalizeEpochMillis(nbfValue);
        long now = nowMillis();
        if (nbf != 0 && now < nbf) {
            throw new IllegalArgumentException("UCAN not active");
        }
        if (exp != 0 && now > exp) {
            throw new IllegalArgumentException("UCAN expired");
        }

        return new VerifiedUcan(decoded.payload, exp);
    }

    private static String verifyProofChain(String currentDid, List<UcanCapability> requiredCap, long requiredExp, JsonArray proofs) {
        if (proofs == null || proofs.size() == 0) {
            throw new IllegalArgumentException("Missing UCAN proof chain");
        }
        JsonElement first = proofs.get(0);
        if (first.isJsonPrimitive() && first.getAsJsonPrimitive().isString()) {
            VerifiedUcan verified = verifyUcanJws(first.getAsString());
            JsonObject payload = verified.payload;
            String aud = getString(payload, "aud");
            if (!currentDid.equals(aud)) {
                throw new IllegalArgumentException(String.format("UCAN audience mismatch expected=%s got=%s", currentDid, aud));
            }
            List<UcanCapability> cap = parseCapabilities(payload.get("cap"));
            if (!capsAllow(cap, requiredCap)) {
                throw new IllegalArgumentException("UCAN capability denied");
            }
            if (verified.exp != 0 && requiredExp != 0 && verified.exp < requiredExp) {
                throw new IllegalArgumentException("UCAN proof expired");
            }
            JsonArray nextProofs = payload.has("prf") && payload.get("prf").isJsonArray()
                ? payload.getAsJsonArray("prf")
                : null;
            if ((nextProofs == null || nextProofs.size() == 0) && proofs.size() > 1) {
                JsonArray rest = new JsonArray();
                for (int i = 1; i < proofs.size(); i++) {
                    rest.add(proofs.get(i));
                }
                nextProofs = rest;
            }
            return verifyProofChain(getString(payload, "iss"), cap, verified.exp, nextProofs);
        }

        if (!first.isJsonObject()) {
            throw new IllegalArgumentException("Invalid UCAN proof");
        }
        UcanRootResult root = verifyRootProof(first.getAsJsonObject());
        if (!currentDid.equals(root.statement.aud)) {
            throw new IllegalArgumentException("Root audience mismatch");
        }
        if (!capsAllow(root.statement.cap, requiredCap)) {
            throw new IllegalArgumentException("Root capability denied");
        }
        if (requiredExp != 0 && root.statement.exp < requiredExp) {
            throw new IllegalArgumentException("Root expired");
        }
        return root.iss;
    }

    private static long normalizeEpochMillis(Long value) {
        if (value == null) return 0;
        if (value < 10_000_000_000L) {
            return value * 1000L;
        }
        return value;
    }

    private static long nowMillis() {
        return System.currentTimeMillis();
    }

    private static byte[] base64UrlDecode(String input) {
        return Base64.getUrlDecoder().decode(input);
    }

    private static byte[] didKeyToPublicKey(String didKey) {
        if (didKey == null || !didKey.startsWith("did:key:")) {
            throw new IllegalArgumentException("Invalid did:key");
        }
        String value = didKey.substring("did:key:".length());
        if (value.startsWith("z")) {
            value = value.substring(1);
        }
        byte[] decoded = base58Decode(value);
        if (decoded.length < 2) {
            throw new IllegalArgumentException("Invalid did:key data");
        }
        byte[] raw = new byte[decoded.length - 2];
        System.arraycopy(decoded, 2, raw, 0, raw.length);
        return raw;
    }

    private static byte[] base58Decode(String input) {
        if (input == null) return new byte[0];
        byte[] bytes = new byte[input.length()];
        for (int i = 0; i < input.length(); i++) {
            int index = BASE58_ALPHABET.indexOf(input.charAt(i));
            if (index < 0) {
                throw new IllegalArgumentException("Invalid Base58 character");
            }
            bytes[i] = (byte) index;
        }
        byte[] decoded = new byte[input.length()];
        int length = 0;
        for (byte b : bytes) {
            int carry = b & 0xFF;
            int j = 0;
            for (int k = input.length() - 1; (carry != 0 || j < length) && k >= 0; k--, j++) {
                carry += 58 * (decoded[k] & 0xFF);
                decoded[k] = (byte) (carry & 0xFF);
                carry >>= 8;
            }
            length = j;
        }
        int leadingZeroes = 0;
        for (int i = 0; i < input.length() && input.charAt(i) == '1'; i++) {
            leadingZeroes++;
        }
        int start = input.length() - length;
        byte[] output = new byte[leadingZeroes + (input.length() - start)];
        System.arraycopy(decoded, start, output, leadingZeroes, output.length - leadingZeroes);
        return output;
    }

    private static String recoverAddress(String message, String signature) {
        Sign.SignatureData signatureData = signatureData(signature);
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        try {
            BigInteger publicKey = Sign.signedPrefixedMessageToKey(messageBytes, signatureData);
            return "0x" + Keys.getAddress(publicKey);
        } catch (java.security.SignatureException e) {
            throw new IllegalArgumentException("Invalid signature", e);
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
        byte[] r = java.util.Arrays.copyOfRange(signatureBytes, 0, 32);
        byte[] s = java.util.Arrays.copyOfRange(signatureBytes, 32, 64);
        return new Sign.SignatureData((byte) v, r, s);
    }

    private static Long getLong(JsonObject body, String key) {
        if (body == null) return null;
        JsonElement element = body.get(key);
        if (element == null || element.isJsonNull()) return null;
        try {
            return element.getAsLong();
        } catch (Exception e) {
            return null;
        }
    }

    private static String getString(JsonObject body, String key) {
        if (body == null) return null;
        JsonElement element = body.get(key);
        if (element == null || element.isJsonNull()) return null;
        try {
            return element.getAsString();
        } catch (Exception e) {
            return null;
        }
    }

    private static class UcanCapability {
        final String resource;
        final String action;

        UcanCapability(String resource, String action) {
            this.resource = resource;
            this.action = action;
        }
    }

    private static class UcanStatement {
        final String aud;
        final List<UcanCapability> cap;
        final long exp;
        final Long nbf;

        UcanStatement(String aud, List<UcanCapability> cap, long exp, Long nbf) {
            this.aud = aud;
            this.cap = cap;
            this.exp = exp;
            this.nbf = nbf;
        }
    }

    private static class UcanRootResult {
        final String iss;
        final UcanStatement statement;

        UcanRootResult(String iss, UcanStatement statement) {
            this.iss = iss;
            this.statement = statement;
        }
    }

    private static class DecodedUcan {
        final JsonObject header;
        final JsonObject payload;
        final byte[] signature;
        final String signingInput;

        DecodedUcan(JsonObject header, JsonObject payload, byte[] signature, String signingInput) {
            this.header = header;
            this.payload = payload;
            this.signature = signature;
            this.signingInput = signingInput;
        }
    }

    private static class VerifiedUcan {
        final JsonObject payload;
        final long exp;

        VerifiedUcan(JsonObject payload, long exp) {
            this.payload = payload;
            this.exp = exp;
        }
    }

    public static String randomHex(int bytes) {
        SecureRandom random = new SecureRandom();
        byte[] buffer = new byte[bytes];
        random.nextBytes(buffer);
        return Numeric.toHexStringNoPrefix(buffer);
    }
}
