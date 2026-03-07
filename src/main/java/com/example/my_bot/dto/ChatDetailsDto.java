package com.example.my_bot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import lombok.*;

import java.time.Instant;


@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ChatDetailsDto {

    private Character prefix;


    private Instant lastSyncTime;

}
