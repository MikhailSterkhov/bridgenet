package me.moonways.endpoint.socials.api.data;

import com.google.gson.Gson;
import lombok.experimental.UtilityClass;
import me.moonways.bridgenet.assembly.util.StreamToStringUtils;
import me.moonways.bridgenet.model.socials.Social;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@UtilityClass
public class SocialNetworkDataParser {

    private static final Gson GSON = new Gson();

    private static final String CREDENTIALS_RESOURCE_DIR = "/credentials/";
    private static final String CHAT_RESOURCE_DIR = "/chat/";

    public SocialNetworkDataDescriptor parseDescriptor(Social social) {
        String resourceNamePrefix = social.name().toLowerCase();
        return SocialNetworkDataDescriptor.builder()
                .credentials(readCredentialsResource(resourceNamePrefix + ".json"))
                .chat(readChatPropertiesResource(resourceNamePrefix + ".properties"))
                .build();
    }

    private SocialNetworkChat readChatPropertiesResource(String resource) {
        Properties properties = new Properties();
        try {
            properties.load(readResource(CHAT_RESOURCE_DIR + resource));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return SocialNetworkChat.builder()
                .unknownCommandMessage(properties.getProperty("bot_unknown_command"))
                .offlineModeMessage(properties.getProperty("bot_mode_offline"))
                .onlineModeMessage(properties.getProperty("bot_mode_online"))
                .linkageAlreadyAcceptedMessage(properties.getProperty("bot_callback_link_already_accepted"))
                .linkageAcceptMessage(properties.getProperty("bot_callback_link_accept"))
                .linkageCancelMessage(properties.getProperty("bot_callback_link_cancel"))
                .linkageAcceptButtonText(properties.getProperty("bot_callback_link_accept_button"))
                .linkageCancelButtonText(properties.getProperty("bot_callback_link_cancel_button"))
                .linkageMessage(properties.getProperty("bot_callback_link_text"))
                .build();
    }

    private SocialNetworkCredentials readCredentialsResource(String resource) {
        return GSON.fromJson(StreamToStringUtils.toStringFull(readResource(CREDENTIALS_RESOURCE_DIR + resource)), SocialNetworkCredentials.class);
    }

    private InputStream readResource(String resource) {
        return SocialNetworkDataParser.class.getResourceAsStream(resource);
    }
}
