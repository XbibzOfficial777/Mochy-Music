import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.HexFormat;

class VerifyKeystore {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Expected keystore path and certificate SHA-256");
        char[] storePassword = System.getenv("STORE_PASSWORD").toCharArray();
        char[] keyPassword = System.getenv("KEY_PASSWORD").toCharArray();
        try {
            KeyStore store = KeyStore.getInstance(System.getenv().getOrDefault("RELEASE_STORE_TYPE", "PKCS12"));
            try (var input = new FileInputStream(args[0])) { store.load(input, storePassword); }
            String alias = System.getenv("KEY_ALIAS");
            if (!(store.getKey(alias, keyPassword) instanceof PrivateKey)) {
                throw new IllegalStateException("Alias does not contain the expected private signing key");
            }
            String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(store.getCertificate(alias).getEncoded()));
            if (!actual.equalsIgnoreCase(args[1].trim())) throw new IllegalStateException("Signing certificate mismatch");
            System.out.println("Signing key and certificate validated: " + actual);
        } finally {
            Arrays.fill(storePassword, '\0');
            Arrays.fill(keyPassword, '\0');
        }
    }
}
