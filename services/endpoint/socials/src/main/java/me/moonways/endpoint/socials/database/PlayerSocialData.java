package me.moonways.endpoint.socials.database;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import me.moonways.bridgenet.jdbc.dao.entity.EntityAccessible;
import me.moonways.bridgenet.jdbc.dao.entity.EntityElement;

import java.util.UUID;

@Getter
@ToString
@EqualsAndHashCode
@RequiredArgsConstructor
@EntityAccessible(name = "Socials")
public class PlayerSocialData {

    @EntityElement(id = "id")
    private final UUID playerId;

    @EntityElement(id = "social_name")
    private final String socialName;

    @EntityElement(id = "social_id")
    private final String socialId;

    @EntityElement(id = "social_link")
    private final String socialLink;
}
