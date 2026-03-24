package com.example.my_bot.enumeration.user;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

@Getter
public enum NameCase{
    @SerializedName("Nom")
    NOMINATIVE("Nom"),
    @SerializedName("Gen")
    GENITIVE("Gen"),
    @SerializedName("Dat")
    DATIVE("Dat"),
    @SerializedName("Acc")
    ACCUSATIVE("Acc"),
    @SerializedName("Ins")
    INSTRUMENTAL("Ins"),
    @SerializedName("Abl")
    PREPOSITIONAL("Abl");

    private final String value;

    private NameCase(String value) {
        this.value = value;
    }

    public String toString() {
        return this.value.toLowerCase();
    }
}