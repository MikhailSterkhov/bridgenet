package me.moonways.endpoint.socials.api.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class UsernameTagUtil {

    private static final String NAMETAG_PREFIX = "@";
    private static final String URL_RESOURCE_DELIMITER = "/";

    public boolean verify(String socialSiteAddress, String input) {
        return input.startsWith(NAMETAG_PREFIX) || input.contains(socialSiteAddress + URL_RESOURCE_DELIMITER);
    }

    public String simplify(String socialSiteAddress, String input) {
        if (input.contains(NAMETAG_PREFIX)) {
            return input.substring(input.indexOf(NAMETAG_PREFIX) + 1);
        }

        socialSiteAddress = socialSiteAddress + URL_RESOURCE_DELIMITER;

        if (input.contains(socialSiteAddress)) {
            return input.substring(input.indexOf(socialSiteAddress) + socialSiteAddress.length()).replace(URL_RESOURCE_DELIMITER, "");
        }

        throw new UnsupportedOperationException("UsernameTagUtil.simplify(" + input + ")");
    }
}
