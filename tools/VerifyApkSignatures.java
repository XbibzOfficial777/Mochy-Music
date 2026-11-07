import com.android.apksig.ApkVerifier;
import java.io.File;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipFile;

/** Run with JDK 21 and Android build-tools/lib/apksigner.jar on the classpath. */
class VerifyApkSignatures {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("Expected: APK, APK.idsig, expected certificate SHA-256");
        }
        File idsig = new File(args[1]);
        if (!idsig.isFile() || idsig.length() == 0) {
            throw new IllegalArgumentException("Missing v4 signature sidecar");
        }
        try (var archive = new ZipFile(args[0])) {
            boolean hasV1Files = archive.stream().anyMatch(entry ->
                    entry.getName().matches("(?i)^META-INF/[^/]+\\.(SF|RSA|DSA|EC)$"));
            if (hasV1Files) throw new IllegalStateException("v1 signature files are forbidden");
        }
        var result = new ApkVerifier.Builder(new File(args[0]))
                .setV4SignatureFile(idsig)
                .build().verify();
        System.out.println("Verified: " + result.isVerified());
        System.out.println("v1: " + result.isVerifiedUsingV1Scheme());
        System.out.println("v2: " + result.isVerifiedUsingV2Scheme());
        System.out.println("v3: " + result.isVerifiedUsingV3Scheme());
        System.out.println("v4: " + result.isVerifiedUsingV4Scheme());
        System.out.println("Errors: " + result.getAllErrors());
        System.out.println("Warnings: " + result.getWarnings());
        if (!result.isVerified() || !result.isVerifiedUsingV2Scheme()
                || result.isVerifiedUsingV1Scheme() || result.isVerifiedUsingV3Scheme()
                || result.isVerifiedUsingV31Scheme() || !result.isVerifiedUsingV4Scheme()
                || !result.getAllErrors().isEmpty() || !result.getWarnings().isEmpty()) {
            throw new IllegalStateException("APK signature verification failed");
        }
        if (result.getSignerCertificates().size() != 1) {
            throw new IllegalStateException("Expected exactly one signer");
        }
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(result.getSignerCertificates().getFirst().getEncoded()));
        System.out.println("Certificate SHA-256: " + actual);
        if (!actual.equalsIgnoreCase(args[2].replace(":", ""))) {
            throw new IllegalStateException("Signing certificate does not match repository release key");
        }
    }
}
