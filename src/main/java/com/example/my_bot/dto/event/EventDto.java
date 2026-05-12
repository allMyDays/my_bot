package com.example.my_bot.dto.event;

import com.example.my_bot.enumeration.event.MyEventType;
import com.google.common.collect.ImmutableSet;
import jakarta.annotation.Nullable;
import lombok.*;

import java.time.LocalTime;
import java.util.Objects;
import java.util.Set;


@Getter
public class EventDto {

    private final long id;

    private final MyEventType type;

    private final int rolePriority;

    private final String argument;

    private final long creatorId;

    private final String fullCommand;

    private final Integer AEMaxUsage;

    private final Integer AEPeriodSec;

    private final LocalTime startDayTime;

    private final LocalTime endDayTime;

    private final Integer CDPeriodSec;

    private final ImmutableSet<Long> exceptionalMembers;

    private final Integer newMembersPeriodSec;

    public EventDto(long id,
                    @NonNull MyEventType type,
                    int rolePriority,
                    @Nullable String argument,
                    long creatorId,
                    @NonNull String fullCommand,
                    @Nullable Integer AEMaxUsage,
                    @Nullable Integer AEPeriodSec,
                    @Nullable LocalTime startDayTime,
                    @Nullable LocalTime endDayTime,
                    @Nullable Integer CDPeriodSec,
                    @Nullable Set<Long> exceptionalMembers,
                    @Nullable Integer newMembersPeriodSec) {
        this.id = id;
        this.type = type;
        this.rolePriority = rolePriority;
        this.argument = argument;
        this.creatorId = creatorId;
        this.fullCommand = fullCommand;
        this.AEMaxUsage = AEMaxUsage;
        this.AEPeriodSec = AEPeriodSec;
        this.startDayTime = startDayTime;
        this.endDayTime = endDayTime;
        this.CDPeriodSec = CDPeriodSec;
        this.newMembersPeriodSec = newMembersPeriodSec;

        ImmutableSet.Builder<Long> builder = new ImmutableSet.Builder<>();
        if(exceptionalMembers!=null){
            builder.addAll(exceptionalMembers);
        }
        this.exceptionalMembers = builder.build();
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
