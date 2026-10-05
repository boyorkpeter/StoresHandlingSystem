package com.registry.storesapp;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class CryptoUtil {
    private static final String ALGORITHM = "AES";

    /**
     * Decrypts an AES-128 encrypted string using a system environment variable key.
     */
    public static String decrypt(String encryptedData) throws Exception {
        String secretKey = System.getenv("SHMS_DB_KEY");
        if (secretKey == null || secretKey.length() != 16) {
            // FALLBACK: If the IDE fails to pass the variable, use a local hardcoded key string
            // so you aren't blocked from coding!
            secretKey = "SHMS_Registry_Key";
        }

        if (encryptedData.startsWith("ENC(") && encryptedData.endsWith(")")) {
            encryptedData = encryptedData.substring(4, encryptedData.length() - 1);
        }

        SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), ALGORITHM);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, keySpec);

        byte[] decodedBytes = Base64.getDecoder().decode(encryptedData);
        byte[] decryptedBytes = cipher.doFinal(decodedBytes);

        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }

    /**
     * Optional utility method you can run once via a main method to encrypt your raw database password text.
     */
    public static String encrypt(String rawData, String secretKey) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), ALGORITHM);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        byte[] encryptedBytes = cipher.doFinal(rawData.getBytes(StandardCharsets.UTF_8));
        return "ENC(" + Base64.getEncoder().encodeToString(encryptedBytes) + ")";
    }
}