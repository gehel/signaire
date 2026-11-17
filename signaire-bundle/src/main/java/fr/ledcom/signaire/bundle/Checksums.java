package fr.ledcom.signaire.bundle;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 checksums, as stored in bundle manifests. */
final class Checksums {

    private Checksums() {
    }

    static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    static String toHex(MessageDigest digest) {
        return HexFormat.of().formatHex(digest.digest());
    }

    static String sha256(byte[] content) {
        MessageDigest digest = newDigest();
        digest.update(content);
        return toHex(digest);
    }

    static String sha256(InputStream in) throws IOException {
        MessageDigest digest = newDigest();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
        }
        return toHex(digest);
    }
}
