package com.example.my_bot.service;

import lombok.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class CryptoService {

    private final SecretKey secretKey;
    private final static String ENCRYPTION_ALGORITHM = "AES";

    public CryptoService(@Value("${security.encryption-key}") String key){
        this.secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ENCRYPTION_ALGORITHM);
    }

    public String encrypt(@NonNull String value){
        try{
            Cipher cipher= Cipher.getInstance(ENCRYPTION_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);

            byte[] encrypted = cipher.doFinal(
                    value.getBytes(StandardCharsets.UTF_8)
            );
            return Base64.getEncoder().encodeToString(encrypted);
        }
        catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    public String decrypt(@NonNull String value){
        try{
            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);

            cipher.init(Cipher.DECRYPT_MODE, secretKey);

            byte[] decrypted= cipher.doFinal(Base64.getDecoder().decode(value));

            return new String(decrypted, StandardCharsets.UTF_8);

        }
        catch (Exception e){
            throw new RuntimeException(e);
        }
    }
}