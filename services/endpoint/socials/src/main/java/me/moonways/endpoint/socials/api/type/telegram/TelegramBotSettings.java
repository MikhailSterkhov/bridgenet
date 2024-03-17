package me.moonways.endpoint.socials.api.type.telegram;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@RequiredArgsConstructor
public class TelegramBotSettings {

    @SerializedName("bot_username")
    private final String username;
    @SerializedName("bot_access_token")
    private final String accessToken;
}
