package com.example.my_bot.command.commands.event;


import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.dto.event.EventDto;
import com.example.my_bot.enumeration.event.EventArgumentType;
import com.example.my_bot.enumeration.event.MyEventType;
import com.example.my_bot.service.RoleService;
import com.example.my_bot.service.chat.ChatService;
import com.example.my_bot.service.event.EventService;
import com.example.my_bot.utils.TimeUtils;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.util.List;

import static com.example.my_bot.enumeration.DefaultRole.MODERATOR;
import static com.example.my_bot.utils.TextUtils.*;
import static com.example.my_bot.utils.TimeUtils.formatDurationFromSeconds;

@Slf4j
@RequiredArgsConstructor
@Command(mainCommandName = "события", alternativeCommandNames = {"ивенты","events"}, defaultRole = MODERATOR, eventable = true)
public class AllEventsShowCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(4,60*2);

    private final EventService eventService;

    private final ChatService chatService;

    private VkChatClient vkChatClient;

    private RoleService roleService;



    @Autowired
    @Lazy
    public void setVkChatClient(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }



    @Override
    public void execute(CommandMessageDto messageDto) throws ClientException, ApiException {

        String[] args = messageDto.getFirstRowArguments();
        StringBuilder sb = new StringBuilder();

        if(args.length>=1&&args[0].equalsIgnoreCase("доступные")){
            sb.append("Вам доступно %d событий для создания:\n\n".formatted(MyEventType.values().length));
            int counter = 1;
            for(MyEventType myEventType: MyEventType.values()){
                sb.append("%d. (%s) — %s. %s.\n".formatted(
                        counter++, myEventType.getCyrillicType(), myEventType.getDescription(),myEventType.getArgumentType()==EventArgumentType.NONE?"Аргумент не требуется":"Требуется аргумент")
                );
            }
            vkChatClient.sendText(sb.toString(), messageDto.getPeerId(), true);
            return;
        }

        long chatId = messageDto.getChatId();
        String chatTimeZone = chatService.getChatTimeZone(chatId).getStringType();

        List<EventDto> events = eventService.getEventsSortedByIdInIncreasingOrder(chatId);
        sb.append("В чате установлено (%d/%d) событий:\n\n"
                .formatted(events.size(), eventService.getMaxEvents())
        );

        int counter=1;
        for(EventDto eventDto: events){
            MyEventType type = eventDto.getType();
            sb.append("%s(%d). ".formatted(createMention(eventDto.getCreatorId()), counter++));
            String roleName = roleService.getRoleName(chatId, eventDto.getRolePriority())
                    .map("«%s»"::formatted)
                    .orElse("с приоритетом "+eventDto.getRolePriority());

            String desc = eventDto.getType().getDescription();
            String eventView = eventDto.getAEMaxUsage()==null
                    ? "событии «%s»".formatted(desc)
                    : "достижении лимита действия «%s» в %d за %s".formatted(desc,eventDto.getAEMaxUsage(),formatDurationFromSeconds(eventDto.getAEPeriodInSeconds(), true));

            sb.append("Выполнение команды «%s» при %s для роли %s и ниже."
                    .formatted(eventDto.getFullCommand(),eventView, roleName));

            if(eventDto.getArgument()!=null){
                String argView = eventDto.getArgument();

                switch (type){
                    case WITH_SUBSCRIPTION, WITHOUT_SUBSCRIPTION->{
                        argView = createMention(Long.parseLong(argView));
                    }
                    case SHORT_MESSAGE -> {
                        argView = argView+" (символов)";
                    }
                    case SHORT_VOICE_MESSAGE, LONG_VOICE_MESSAGE -> {
                        argView = argView+" (длительность в сек.)";
                    }
                }
                sb.append(" Аргумент: ").append(argView);
            } sb.append("\n");
            if(eventDto.getStartDayTime()!=null){
                sb.append("\uD83D\uDD57Работает ежедневно с %s до %s %s.\n"
                        .formatted(eventDto.getStartDayTime(), eventDto.getEndDayTime(), chatTimeZone)
                );
            }
            Integer cd = eventDto.getCDPeriodInSeconds();
            if(cd!=null){
                sb.append("⏳Кулдаун: ").append(cd == 0
                        ? "отключён"
                        : "реагирует на участника максимум 1 раз в %s".formatted(formatDurationFromSeconds(cd, true))
                ).append("\n");
            }
        }


        vkChatClient.sendText(sb.toString(), messageDto.getPeerId(), true);

    }

    @Autowired
    public void setRoleService(RoleService roleService) {
        this.roleService = roleService;
    }
}
