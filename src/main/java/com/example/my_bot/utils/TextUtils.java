package com.example.my_bot.utils;

import lombok.NonNull;

import java.util.Arrays;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.lang.UProperty;
import java.text.BreakIterator;

public class TextUtils {

    public final static char DEFAULT_CHAT_PREFIX = '!';


     public static String createMention(long memberId){

        if(memberId<0) {
            return "@club"+(memberId*-1);
        } return "@id"+memberId;
    }

    public static String createMemberLink(long memberId){

        if(memberId<0) {
            return "vk.com/club"+(memberId*-1);
        } return "vk.com/id"+memberId;
    }


    public static boolean isValidInteger(String str) {
        if (str == null || !(str=str.trim()).matches("-?\\d+")) {
            return false;
        }
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean isValidLong(String str) {
        if (str == null || !(str=str.trim()).matches("-?\\d+")) {
            return false;
        }
        try {
            Long.parseLong(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean isNumber(@NonNull String str) {
        return str.trim().matches("-?\\d+");
    }


    public static String collectArgumentsSinceIndex(@NonNull String[] args, int index){
           if(index<0||index>= args.length){
               throw new ArrayIndexOutOfBoundsException();
           } return String.join(" ", Arrays.copyOfRange(args, index, args.length));

    }

    public static String cutDefaultPrefix(@NonNull String command){
        command = command.trim();
        if(!command.isEmpty()){
            if(command.charAt(0)==DEFAULT_CHAT_PREFIX){
                return command.substring(1);
            }
        }
        return command;
    }

    public static boolean isMostlyCaps(@NonNull String text){
        text = text.trim();
        if (text.isBlank()) return false;

        int letters=0;
        int upper=0;

        for(int i=0; i < text.length(); ) {
            int cp = text.codePointAt(i);

            if (Character.isLetter(cp)) {
                letters++;
                if (Character.isUpperCase(cp)) upper++;
            }
            i+=Character.charCount(cp);
        }
        if(letters < 4) return false;
        return (double) upper / letters >= 0.8;
    }

    public static boolean isZalgo(@NonNull String text){
        if(text.isEmpty()) return false;

        int total=0;
        int marks=0;
        int maxSequence=0;
        int currentSequence=0;

        int[] codePoints=text.codePoints().toArray();

        for(int cp: codePoints){
            total++;
            int type= Character.getType(cp);
            boolean isCombiningMark=type==Character.NON_SPACING_MARK      // Mn
                    || type==Character.COMBINING_SPACING_MARK // Mc
                    || type==Character.ENCLOSING_MARK;

            if(isCombiningMark){
                marks++;
                currentSequence++;
                maxSequence= Math.max(maxSequence, currentSequence);
            } else{
                currentSequence = 0;
            }
        }
        double density=(double) marks / total;

        if (maxSequence>= 3) return true;
        if (marks>= 6) return true;
        if (density> 0.15) return true;
        return false;
    }

    public static int countEmojis(@NonNull String text){
         text = text.trim();
         if (text.isEmpty()) return 0;

        BreakIterator it = BreakIterator.getCharacterInstance();
        it.setText(text);

        int count = 0;
        int start = it.first();

        for (int end = it.next(); end != BreakIterator.DONE; start = end, end = it.next()) {
            int[] cps = text.substring(start, end).codePoints().toArray();

            if (isEmojiCluster(cps)) {
                count++;
            }
        }

        return count;
    }

    private static boolean isEmojiCluster(int[] cps) {
        for (int cp : cps) {
            if (UCharacter.hasBinaryProperty(cp, UProperty.EMOJI)
                    || UCharacter.hasBinaryProperty(cp, UProperty.EMOJI_PRESENTATION)
                    || UCharacter.hasBinaryProperty(cp, UProperty.EXTENDED_PICTOGRAPHIC)) {
                return true;
            }
        }
        return false;
    }




}
