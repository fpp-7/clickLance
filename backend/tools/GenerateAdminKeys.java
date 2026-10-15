import java.nio.file.*;
import java.security.KeyPairGenerator;
import java.util.Base64;

/** Executar com JDK 21: java tools/GenerateAdminKeys.java .local-secrets */
public class GenerateAdminKeys {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Informe o diretorio local de segredos.");
        Path directory = Path.of(args[0]);
        Files.createDirectories(directory);
        Path privatePath = directory.resolve("admin-private.pem");
        Path publicPath = directory.resolve("admin-public.pem");
        if (Files.exists(privatePath) || Files.exists(publicPath)) {
            throw new IllegalStateException("Os arquivos ja existem; nenhuma chave sera sobrescrita.");
        }
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        var pair = generator.generateKeyPair();
        write(privatePath, "PRIVATE KEY", pair.getPrivate().getEncoded());
        write(publicPath, "PUBLIC KEY", pair.getPublic().getEncoded());
        System.out.println("Par RSA criado. Restrinja o acesso ao diretorio de segredos ao operador do backend.");
    }
    private static void write(Path path, String type, byte[] bytes) throws Exception {
        String data = Base64.getMimeEncoder(64, new byte[]{10}).encodeToString(bytes);
        Files.writeString(path, "-----BEGIN " + type + "-----\n" + data + "\n-----END " + type + "-----\n", StandardOpenOption.CREATE_NEW);
    }
}
