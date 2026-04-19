package com.example.my_bot.dto.event;

import com.example.my_bot.enumeration.event.MyEventType;
import jakarta.annotation.Nullable;
import jakarta.persistence.Column;
import lombok.*;
import org.checkerframework.checker.units.qual.N;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;


@Getter
public class EventDto {

    private final long id;

    private final MyEventType type;

    private final int rolePriority;

    private final String argument;

    private final long creatorId;

    private final String fullCommand;

    public EventDto(long id, @NonNull MyEventType type, int rolePriority, @Nullable String argument, long creatorId, @NonNull String fullCommand) {
        this.id = id;
        this.type = type;
        this.rolePriority = rolePriority;
        this.argument = argument;
        this.creatorId = creatorId;
        this.fullCommand = fullCommand;
    }

    @Override
    public boolean equals(Object object) {
        if (object == null || getClass() != object.getClass()) return false;
        EventDto eventDto = (EventDto) object;
        return id == eventDto.id;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
