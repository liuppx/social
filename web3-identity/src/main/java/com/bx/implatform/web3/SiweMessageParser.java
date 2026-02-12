package com.bx.implatform.web3;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SiweMessageParser {

    private static final Pattern NONCE_PATTERN = Pattern.compile("(?m)^Nonce:\\s*(.+)$");
    private static final Pattern CHAIN_ID_PATTERN = Pattern.compile("(?m)^Chain ID:\\s*(.+)$");
    private static final Pattern URI_PATTERN = Pattern.compile("(?m)^URI:\\s*(.+)$");
    private static final Pattern ISSUED_AT_PATTERN = Pattern.compile("(?m)^Issued At:\\s*(.+)$");

    private SiweMessageParser() {
    }

    public static SiweMessage parse(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String[] lines = message.split("\\r?\\n");
        if (lines.length < 2) {
            return null;
        }
        SiweMessage siwe = new SiweMessage();
        String domainLine = lines[0].trim();
        String addressLine = lines[1].trim();
        siwe.setAddress(addressLine);
        String domain = domainLine;
        String suffix = " wants you to sign in with your Ethereum account:";
        if (domainLine.endsWith(suffix)) {
            domain = domainLine.substring(0, domainLine.length() - suffix.length());
        }
        siwe.setDomain(domain);
        siwe.setNonce(findFirst(message, NONCE_PATTERN));
        siwe.setChainId(findFirst(message, CHAIN_ID_PATTERN));
        siwe.setUri(findFirst(message, URI_PATTERN));
        siwe.setIssuedAt(findFirst(message, ISSUED_AT_PATTERN));
        return siwe;
    }

    private static String findFirst(String message, Pattern pattern) {
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }
}
