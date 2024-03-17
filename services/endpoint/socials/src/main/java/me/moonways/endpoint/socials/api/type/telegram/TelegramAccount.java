package me.moonways.endpoint.socials.api.type.telegram;

import lombok.*;
import me.moonways.bridgenet.jdbc.dao.entity.EntityAccessible;
import me.moonways.bridgenet.jdbc.dao.entity.EntityElement;
import me.moonways.bridgenet.jdbc.dao.entity.EntityUnique;
import me.moonways.endpoint.socials.api.SocialNetworkAccount;

@Getter
@Builder
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@EntityAccessible(name = "TelegramAccounts")
public class TelegramAccount implements SocialNetworkAccount {

    @EntityElement(id = "chat_id", order = 1)
    @EntityUnique
    @EqualsAndHashCode.Include
    private final long chatID;

    @EntityElement(order = 2)
    private final String username;

    @EntityElement(id = "first_name", order = 2)
    private final String firstName;

    @EntityElement(id = "lang", order = 3)
    private final String languageCode;
}
