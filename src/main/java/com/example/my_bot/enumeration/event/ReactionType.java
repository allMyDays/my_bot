package com.example.my_bot.enumeration.event;

import com.example.my_bot.utils.TextUtils;
import com.vdurmont.emoji.EmojiManager;
import lombok.Getter;
import lombok.NonNull;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
public enum ReactionType {

    HEART("❤",1),
    POOP("💩",5),
    COOL_FACE_WITH_SUNGLASSES("😎",24),
    THUMB_UP("👍",4),
    EXPLODING_HEAD("🤯",37),
    LAUGHING_FACE_WITH_JOY_TEARS("😂",3),
    FIRE("🔥",2),
    QUESTION_MARK("❓",6),
    CLOWN_FACE("🤡",17),
    CRYING_FACE("😢",27),
    LOUDLY_CRYING_FACE("😭",7),
    ANGRY_FACE("😡",8),
    THUMB_DOWN("👎",9),
    OK_HAND("👌",10),
    GRINNING_FACE_WITH_SMILING_EYES("😄",11),
    THINKING_FACE("🤔",12),
    PRAYING_PALMS("🙏",13),
    FACE_BLOWING_A_KISS("😘",14),
    LOVESTRUCK_FACE_WITH_HEART_EYES("😍",15),
    PARTY_POPPER("🎉",16),
    HANDSHAKE("🤝",18),
    FACE_SCREAMING_IN_FEAR("😱",30),
    ANGRY_CUSSING_FACE("🤬",31),
    POKER_FACE("😐",20),
    MOAI("🗿",21),
    PURPLE_DEVIL("😈",39),
    SALUTING_EMOJI("🫡",40),
    FACE_WITH_ROLLING_EYES("🙄",22),
    BROKEN_HEART("💔",23),
    LIGHTING("⚡",64),
    CLAPPING_HANDS("👏",26),
    VOMITING_FACE("🤮",42),
    ASTONISHED_FACE("😲",19),
    EYES("👀",32),
    MOON_FACE("🌚",34),
    HUNDRED_POINTS("💯",35),
    PAINTING_NAILS("💅",36),
    SLEEPING_FACE("😴",38),
    GREEN_CHECKMARK("✅",28),
    TROPHY("🏆",29);


    private final String emoji;
    private final int reactionId;


    ReactionType(@NonNull String emoji, int reactionId){
        this.emoji = emoji;
        this.reactionId = reactionId;
    }

    private static final Map<String, ReactionType> emojiMAP =
            Arrays.stream(values())
                    .collect(Collectors.toMap(
                            z -> z.emoji,
                            Function.identity()
                    ));

    private static final Map<Integer, ReactionType> reactionIdMAP =
            Arrays.stream(values())
                    .collect(Collectors.toMap(
                            z ->z.reactionId,
                            Function.identity()
                    ));


    public static Optional<ReactionType> findByEmoji(@NonNull String emoji){
        return Optional.ofNullable(emojiMAP.get(emoji.trim()));
    }

    public static Optional<ReactionType> findByReactionId(int reactionId){
        return Optional.ofNullable(reactionIdMAP.get(reactionId));
    }
    public static Optional<ReactionType> findByEmojiOrReactionId(@NonNull String arg){
        arg = arg.trim();
        if(EmojiManager.isEmoji(arg)){
            return findByEmoji(arg);
        }
        if(TextUtils.isValidInteger(arg)){
            return findByReactionId(Integer.parseInt(arg));
        }
        return Optional.empty();
    }
    
    
    
    
    
    
    
    
    
    
}
