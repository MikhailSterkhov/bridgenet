package me.moonways.endpoint.socials.api.type.vkontakte;

import me.moonways.bridgenet.model.socials.Social;
import me.moonways.endpoint.socials.api.AbstractSocialNetworkLinkageApi;
import me.moonways.endpoint.socials.api.SocialNetworkAccount;
import me.moonways.endpoint.socials.api.SocialNetworkBot;
import me.moonways.endpoint.socials.api.util.UsernameTagUtil;
import me.moonways.endpoint.socials.api.data.SocialNetworkDataDescriptor;

import java.util.UUID;

public class VkontakteLinkageApi extends AbstractSocialNetworkLinkageApi {

    private static final String VK_SITE_ADDRESS = "vk.com";

    public VkontakteLinkageApi() {
        super(Social.VKONTAKTE);
    }

    @Override
    public boolean verify(String input) {
        return UsernameTagUtil.verify(VK_SITE_ADDRESS, input);
    }

    @Override
    protected String simplify(String playerInput) {
        return UsernameTagUtil.simplify(VK_SITE_ADDRESS, playerInput);
    }

    @Override
    protected SocialNetworkBot start(SocialNetworkDataDescriptor descriptor) {
        return null;
    }

    @Override
    protected SocialNetworkAccount findAccount(UUID playerId, String username) {
        return null;
    }
}
