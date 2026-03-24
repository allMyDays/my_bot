package com.example.my_bot.dto.user;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserFullNameInEachCase {
        private String nominative;     // именительный
        private String genitive;       // родительный
        private String dative;         // дательный
        private String accusative;     // винительный
        private String instrumental;   // творительный
        private String prepositional;  // предложный

        @Override
        public String toString() {
            return "UserFullNameInEachCase{" +
                    "nominative='" + nominative + '\'' +
                    ", genitive='" + genitive + '\'' +
                    ", dative='" + dative + '\'' +
                    ", accusative='" + accusative + '\'' +
                    ", instrumental='" + instrumental + '\'' +
                    ", prepositional='" + prepositional + '\'' +
                    '}';
        }
    }