package me.moonways.bridgenet.api.command.api.label.regex;

import me.moonways.bridgenet.api.container.MapContainerImpl;
import me.moonways.bridgenet.api.inject.Autobind;

@Autobind
public class CommandRegexRegistry extends MapContainerImpl<String, CommandRegex> {
}