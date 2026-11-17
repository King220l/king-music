import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class ReleaseSigner {
    private static final Path DIRECTORY = Path.of(System.getProperty("user.home"), ".kingmusic");
    private static final Path PRIVATE_KEY = DIRECTORY.resolve("release-signing.pk8");
    private static final Path PUBLIC_KEY = DIRECTORY.resolve("release-signing.pub");

    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("generate or sign <file> <signature>");
        if ("generate".equals(args[0])) {
            if (Files.exists(PRIVATE_KEY)) throw new IllegalStateException("Signing key already exists");
            Files.createDirectories(DIRECTORY);
            var pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            Files.write(PRIVATE_KEY, pair.getPrivate().getEncoded());
            Files.writeString(PUBLIC_KEY, Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
            System.out.println(Files.readString(PUBLIC_KEY));
            return;
        }
        if ("sign".equals(args[0]) && args.length == 3) {
            var key = KeyFactory.getInstance("Ed25519").generatePrivate(
                new PKCS8EncodedKeySpec(Files.readAllBytes(PRIVATE_KEY)));
            var signature = Signature.getInstance("Ed25519");
            signature.initSign(key);
            signature.update(Files.readAllBytes(Path.of(args[1])));
            Files.writeString(Path.of(args[2]), Base64.getEncoder().encodeToString(signature.sign()));
            return;
        }
        if ("verify".equals(args[0]) && args.length == 3) {
            var key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(
                Base64.getDecoder().decode(Files.readString(PUBLIC_KEY).strip())));
            var signature = Signature.getInstance("Ed25519");
            signature.initVerify(key);
            signature.update(Files.readAllBytes(Path.of(args[1])));
            if (!signature.verify(Base64.getDecoder().decode(Files.readString(Path.of(args[2])).strip()))) {
                throw new SecurityException("Invalid release signature");
            }
            System.out.println("Signature verified");
            return;
        }
        throw new IllegalArgumentException("generate, sign <file> <signature>, or verify <file> <signature>");
    }
}
