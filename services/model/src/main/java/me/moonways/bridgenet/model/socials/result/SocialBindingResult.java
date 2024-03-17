package me.moonways.bridgenet.model.socials.result;

public enum SocialBindingResult {

    SUCCESS,
    FAILURE_NO_CONNECTION,
    FAILURE_SOCIAL_NOT_EXISTS,
    FAILURE_PROFILE_NOT_FOUND,
    FAILURE_NOT_LINKED,
    FAILURE_NOT_BELONG,
    FAILURE_ALREADY_LINKED,
    FAILURE_UNCORRECTED_INPUT,
    FAILURE_NEEDS_DATA, // необходимо написать боту первым.
}
