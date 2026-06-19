package com.example.my_bot.mapper;

import com.example.my_bot.dto.submanager.SubmanagerDto;
import com.example.my_bot.entity.SubmanagerEntity;
import com.example.my_bot.service.CryptoService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SubmanagerMapper {
    private final CryptoService cryptoService;


    public SubmanagerDto toSubmanagerDto(@NonNull SubmanagerEntity entity){
        return new SubmanagerDto(
                entity.getGroupId(),
                cryptoService.decrypt(entity.getEncryptedToken()),
                entity.getServerId(),
                entity.getSecretKey()
        );
    }
}
