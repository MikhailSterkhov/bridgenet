package me.moonways.bridgenet.model.players.social;

import lombok.*;

@Getter
@Setter
@ToString
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
public class SocialContainer {

    private String
            emailAddress,
            telegramId,
            discordId,
            vkontakteId;
}