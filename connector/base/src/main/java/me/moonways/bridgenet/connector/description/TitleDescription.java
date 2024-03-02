package me.moonways.bridgenet.connector.description;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
@EqualsAndHashCode
public class TitleDescription {

    private String title;
    private String subtitle;

    private int fadeIn, stay, fadeOut;
}