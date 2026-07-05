package com.example.my_bot.command.commands.member;

import com.example.my_bot.annotation.Command;
import com.example.my_bot.client.VkChatClient;
import com.example.my_bot.command.ChatCommand;
import com.example.my_bot.config.CommandCooldown;
import com.example.my_bot.dto.command.CommandMessageDto;
import com.example.my_bot.entity.MemberEntity;
import com.example.my_bot.enumeration.command.CommandExecutionStatus;
import com.example.my_bot.enumeration.user.NameCase;
import com.example.my_bot.mapper.MessageMapper;
import com.example.my_bot.service.GlobalUserService;
import com.example.my_bot.service.MemberService;
import com.example.my_bot.service.RoleService;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;

import java.util.*;

import static com.example.my_bot.enumeration.command.CommandExecutionStatus.SUCCESS;
import static com.example.my_bot.enumeration.DefaultRole.MEMBER;
import static com.example.my_bot.enumeration.chat.AdminChatCommandExecutionMode.ONLY_SINGLE_BOUND_CHAT_AT_ONCE;
import static com.example.my_bot.enumeration.member.MemberPresenceType.IN_CHAT;
import static com.example.my_bot.utils.TextUtils.createMention;

@Command(mainCommandName = "управляющие", alternativeCommandNames = {"staff", "админы"}, defaultRole = MEMBER, eventable = true, adminChatCommandExecutionMode = ONLY_SINGLE_BOUND_CHAT_AT_ONCE)
public class StaffShowCommand implements ChatCommand {

    @Getter
    private final CommandCooldown cooldown = new CommandCooldown(7,60*2);

    private final MemberService memberService;
    private final RoleService roleService;
    private VkChatClient vkChatClient;
    private final long theMainBotId;
    private final MessageMapper messageMapper;
    private final GlobalUserService globalUserService;

    public StaffShowCommand(MemberService memberService, RoleService roleService, @Value("${vk.main-bot.id}") long theMainBotId, MessageMapper messageMapper, GlobalUserService globalUserService) {
        this.memberService = memberService;
        this.roleService = roleService;
        this.theMainBotId = theMainBotId;
        this.messageMapper = messageMapper;
        this.globalUserService = globalUserService;
    }

    @Autowired
    @Lazy
    public void setVkChatService(VkChatClient vkChatClient) {
        this.vkChatClient = vkChatClient;
    }

    @Override
    public CommandExecutionStatus execute(CommandMessageDto commandMessage) throws ClientException, ApiException {

        long chatId = commandMessage.getCommandRoutingData().getDataBaseChatId();
        GroupActor executorBot = commandMessage.getCommandRoutingData().getExecutorBot();

        StringBuilder sb = new StringBuilder();

        long exitedStaffMembers = 0;
        Set<Long> allStaffMembers = new HashSet<>();

        TreeMap<Integer, List<MemberEntity>> staffMap =  new TreeMap<>(Comparator.reverseOrder());
        Collection<MemberEntity> membersWithPositiveRole = memberService.getMembersWithPositiveRole(chatId);

        for(MemberEntity memberEntity: membersWithPositiveRole){

            if(memberEntity.getUserId()==-theMainBotId||memberEntity.getUserId().equals(-executorBot.getGroupId())){
                // отсеиваю самого бота
                continue;
            }
            staffMap.computeIfAbsent(memberEntity.getRolePriority(), k -> new ArrayList<>()).add(memberEntity);

            if(!memberEntity.getPresenceType().equals(IN_CHAT)){
                exitedStaffMembers++;
            }
            allStaffMembers.add(memberEntity.getUserId());
        }

        Map<Long, String> memberNamesMap = globalUserService.getUserFullNamesInRequiredCase(allStaffMembers, NameCase.NOMINATIVE);

        sb.append("В чате %d управляющих (из них %d сейчас отсутствует).\n\n".formatted(allStaffMembers.size(), exitedStaffMembers));

        Map<Integer, String> roleMap = roleService.getAllRolesWithNoSorting(chatId);


        for(Map.Entry<Integer, List<MemberEntity>> entry: staffMap.entrySet()){
            sb.append(roleMap.get(entry.getKey()))
                    .append(" ")
                    .append("(%d):\n".formatted(entry.getKey()));
            for(MemberEntity member:entry.getValue()){
            if(member.isChatAdmin()){
                sb.append("\uD83D\uDCA0 ");
            }
            sb.append("%s(%s)".formatted(createMention(member.getUserId()),memberNamesMap.get(member.getUserId())));

            if(!member.getPresenceType().equals(IN_CHAT)){
                sb.append(" \uD83D\uDEAA");
            }sb.append("\n");

            }sb.append("\n");
        }

        vkChatClient.sendText(messageMapper.toSendMessageDto(sb.toString(),commandMessage));
        return SUCCESS;

    }

}
