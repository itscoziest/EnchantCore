// src/main/java/com/strikesenchantcore/util/StrikesLicenseManager.java
package com.strikesenchantcore.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class StrikesLicenseManager { // Renamed from LicenseGate
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String DEFAULT_SERVER = "https://api.licensegate.io";

    private String userId;

    private String publicRsaKey;

    private String validationServer = "https://api.licensegate.io";

    private boolean useChallenges = false;

    private boolean debug = false;

    public StrikesLicenseManager(String userId) { // Constructor updated
        this.userId = userId;
    }

    public StrikesLicenseManager(String userId, String publicRsaKey) { // Constructor updated
        this.userId = userId;
        this.publicRsaKey = publicRsaKey;
        this.useChallenges = true;
    }

    public StrikesLicenseManager setPublicRsaKey(String publicRsaKey) { // Return type updated
        this.publicRsaKey = publicRsaKey;
        return this;
    }

    public StrikesLicenseManager setValidationServer(String validationServer) { // Return type updated
        this.validationServer = validationServer;
        return this;
    }

    public StrikesLicenseManager useChallenges() { // Return type updated
        this.useChallenges = true;
        return this;
    }

    public StrikesLicenseManager debug() { // Return type updated
        this.debug = true;
        return this;
    }

    public ValidationType verify(String licenseKey) {
        return verify(licenseKey, null, null);
    }

    public ValidationType verify(String licenseKey, String scope) {
        return verify(licenseKey, scope, null);
    }

    public ValidationType verify(String licenseKey, String scope, String metadata) {
        try {
            String challenge = this.useChallenges ? String.valueOf(System.currentTimeMillis()) : null;
            ObjectNode response = requestServer(buildUrl(licenseKey, scope, metadata, challenge));
            if (response.has("error") || !response.has("result")) {
                if (this.debug)
                    System.out.println("StrikesLicenseManager Debug: Error from server response - " + response.get("error").asText());
                return ValidationType.SERVER_ERROR;
            }
            if (response.has("valid") && !response.get("valid").asBoolean()) {
                ValidationType result = ValidationType.valueOf(response.get("result").asText());
                if (result == ValidationType.VALID) // This case indicates an inconsistency
                    return ValidationType.SERVER_ERROR;
                return result;
            }
            if (this.useChallenges) {
                if (this.publicRsaKey == null || this.publicRsaKey.trim().isEmpty()) {
                    if (this.debug)
                        System.out.println("StrikesLicenseManager Debug: Public RSA Key is not set. Skipping challenge verification.");
                    // Depending on strictness, you might return FAILED_CHALLENGE or proceed if challenge isn't mandatory without key
                } else {
                    if (!response.has("signedChallenge")) {
                        if (this.debug)
                            System.out.println("StrikesLicenseManager Debug: Error - No signedChallenge in response.");
                        return ValidationType.FAILED_CHALLENGE;
                    }
                    if (!verifyChallenge(challenge, response.get("signedChallenge").asText())) {
                        if (this.debug)
                            System.out.println("StrikesLicenseManager Debug: Error - Challenge verification failed.");
                        return ValidationType.FAILED_CHALLENGE;
                    }
                }
            }
            return ValidationType.valueOf(response.get("result").asText());
        } catch (IOException e) {
            if (this.debug) {
                System.out.println("StrikesLicenseManager Debug: IOException during verification.");
                e.printStackTrace();
            }
            return ValidationType.CONNECTION_ERROR;
        } catch (IllegalArgumentException e) {
            // This can happen if ValidationType.valueOf() gets an unknown string
            if (this.debug) {
                System.out.println("StrikesLicenseManager Debug: IllegalArgumentException - unknown validation result from server.");
                e.printStackTrace();
            }
            return ValidationType.SERVER_ERROR;
        }
    }

    public boolean verifySimple(String licenseKey) {
        return (verify(licenseKey) == ValidationType.VALID);
    }

    public boolean verifySimple(String licenseKey, String scope) {
        return (verify(licenseKey, scope) == ValidationType.VALID);
    }

    public boolean verifySimple(String licenseKey, String scope, String metadata) {
        return (verify(licenseKey, scope, metadata) == ValidationType.VALID);
    }

    private String buildUrl(String licenseKey, String scope, String metadata, String challenge) throws UnsupportedEncodingException {
        StringBuilder queryString = new StringBuilder();
        if (metadata != null) {
            queryString.append("?metadata=").append(URLEncoder.encode(metadata, StandardCharsets.UTF_8.name()));
        }
        if (scope != null) {
            queryString.append(queryString.length() == 0 ? "?" : "&").append("scope=").append(URLEncoder.encode(scope, StandardCharsets.UTF_8.name()));
        }
        if (this.useChallenges && challenge != null) {
            queryString.append(queryString.length() == 0 ? "?" : "&").append("challenge=").append(URLEncoder.encode(challenge, StandardCharsets.UTF_8.name()));
        }
        return this.validationServer + "/license/" + this.userId + "/" + licenseKey + "/verify" + queryString.toString();
    }

    private ObjectNode requestServer(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection con = (HttpURLConnection)url.openConnection();
        con.setRequestMethod("GET");
        // It's good practice to set connect and read timeouts
        con.setConnectTimeout(5000); // 5 seconds
        con.setReadTimeout(5000);    // 5 seconds
        con.setRequestProperty("User-Agent", "StrikesEnchantCore-LicenseClient/1.0"); // Custom User-Agent
        // con.setDoOutput(true); // Not needed for GET requests

        int responseCode = con.getResponseCode();
        if (this.debug) {
            System.out.println("\nStrikesLicenseManager Debug: Sending request to URL : " + url);
            System.out.println("StrikesLicenseManager Debug: Response Code : " + responseCode);
        }

        // Handle non-200 responses more gracefully
        InputStreamReader streamReader;
        if (responseCode >= 200 && responseCode < 300) {
            streamReader = new InputStreamReader(con.getInputStream());
        } else {
            streamReader = new InputStreamReader(con.getErrorStream()); // Read error stream for non-OK responses
        }

        try (BufferedReader in = new BufferedReader(streamReader)) {
            StringBuilder response = new StringBuilder();
            String inputLine;
            while ((inputLine = in.readLine()) != null)
                response.append(inputLine);
            String jsonStr = response.toString();
            if (this.debug)
                System.out.println("StrikesLicenseManager Debug: Response JSON: " + jsonStr);

            if (jsonStr.isEmpty()) { // Handle empty response
                if (this.debug) System.out.println("StrikesLicenseManager Debug: Empty response from server.");
                // Create a minimal error JSON if needed, or handle as SERVER_ERROR
                return (ObjectNode) OBJECT_MAPPER.createObjectNode().put("error", "Empty server response");
            }
            return (ObjectNode)OBJECT_MAPPER.readValue(jsonStr, ObjectNode.class);
        }
    }

    private boolean verifyChallenge(String challenge, String signedChallengeBase64) {
        // Guard clause for missing public RSA key
        if (this.publicRsaKey == null || this.publicRsaKey.trim().isEmpty()) {
            if (this.debug)
                System.out.println("StrikesLicenseManager Debug: Public RSA Key is not set. Cannot verify challenge.");
            return false;
        }
        try {
            String pemHeader = "-----BEGIN PUBLIC KEY-----";
            String pemFooter = "-----END PUBLIC KEY-----";
            String base64PublicKey = this.publicRsaKey.replace(pemHeader, "").replace(pemFooter, "").replaceAll("\\s+", "");
            byte[] publicKeyBytes = Base64.getDecoder().decode(base64PublicKey);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey publicKey = keyFactory.generatePublic(keySpec);
            byte[] signatureBytes = Base64.getDecoder().decode(signedChallengeBase64);
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(challenge.getBytes(StandardCharsets.UTF_8)); // Specify charset
            return signature.verify(signatureBytes);
        } catch (Exception e) {
            if (this.debug) {
                System.out.println("StrikesLicenseManager Debug: Exception during challenge verification.");
                e.printStackTrace();
            }
            return false;
        }
    }

    // Inner enum for ValidationType
    public enum ValidationType {
        VALID(true),
        NOT_FOUND(false), // Added default false
        NOT_ACTIVE(false),
        EXPIRED(false),
        LICENSE_SCOPE_FAILED(false),
        IP_LIMIT_EXCEEDED(false),
        RATE_LIMIT_EXCEEDED(false),
        FAILED_CHALLENGE(false),
        SERVER_ERROR(false),
        CONNECTION_ERROR(false); // Added default false

        private boolean validState; // Renamed from 'valid' to avoid confusion

        ValidationType(boolean validState) {
            this.validState = validState;
        }

        // Renamed method to avoid conflict if 'valid' is used as enum constant name
        public boolean isValidState() {
            return this.validState;
        }

        // Optional: A static method to safely get enum from string, defaulting to SERVER_ERROR
        public static ValidationType fromString(String text) {
            try {
                return ValidationType.valueOf(text.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Log this occurrence if debugging is enabled in the outer class instance
                // This requires passing the debug flag or logger instance if you want to log here
                return SERVER_ERROR;
            }
        }
    }
}