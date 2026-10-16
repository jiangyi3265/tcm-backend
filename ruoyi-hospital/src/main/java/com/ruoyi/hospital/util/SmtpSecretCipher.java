package com.ruoyi.hospital.util;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.ruoyi.common.exception.ServiceException;

/** The encryption key stays outside the application, upload directory and database. */
@Component
public class SmtpSecretCipher
{
    @Value("${tcm.smtp.key-file:${user.home}/.otcm/smtp-settings.key}")
    private String keyFile;
    private static final SecureRandom RANDOM = new SecureRandom();

    public String encrypt(String secret)
    {
        try
        {
            byte[] nonce = new byte[12];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key(true), "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD("otcm:smtp:v1".getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8));
            byte[] packed = Arrays.copyOf(nonce, nonce.length + encrypted.length);
            System.arraycopy(encrypted, 0, packed, nonce.length, encrypted.length);
            return "v1:" + Base64.getEncoder().encodeToString(packed);
        }
        catch (Exception e) { throw new ServiceException("SMTP_SECRET_STORAGE_UNAVAILABLE"); }
    }

    public String decrypt(String value)
    {
        try
        {
            if (value == null || !value.startsWith("v1:")) throw new IllegalArgumentException();
            byte[] packed = Base64.getDecoder().decode(value.substring(3));
            if (packed.length < 29) throw new IllegalArgumentException();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key(false), "AES"), new GCMParameterSpec(128, packed, 0, 12));
            cipher.updateAAD("otcm:smtp:v1".getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(packed, 12, packed.length - 12), StandardCharsets.UTF_8);
        }
        catch (Exception e) { throw new ServiceException("SMTP_SECRET_UNAVAILABLE_REENTER_KEY"); }
    }

    private synchronized byte[] key(boolean create) throws Exception
    {
        Path path = Paths.get(keyFile).toAbsolutePath().normalize();
        if (Files.isSymbolicLink(path)) throw new IllegalStateException();
        if (create && !Files.exists(path))
        {
            Path parent = path.getParent();
            boolean posix = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
            if (posix) Files.createDirectories(parent, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            else Files.createDirectories(parent);
            byte[] generated = new byte[32];
            RANDOM.nextBytes(generated);
            Path temporary = posix
                    ? Files.createTempFile(parent, ".smtp-key-", ".tmp", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
                    : Files.createTempFile(parent, ".smtp-key-", ".tmp");
            try
            {
                Files.write(temporary, generated);
                // No replacement: an existing encryption key must never be rotated implicitly.
                Files.move(temporary, path);
            }
            catch (FileAlreadyExistsException alreadyCreated) { /* Read the winning key below. */ }
            finally { Files.deleteIfExists(temporary); }
        }
        if (Files.isSymbolicLink(path)) throw new IllegalStateException();
        byte[] bytes = Files.readAllBytes(path);
        if (bytes.length != 32) throw new IllegalStateException();
        return bytes;
    }
}
