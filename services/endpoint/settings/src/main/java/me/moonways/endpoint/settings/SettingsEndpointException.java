package me.moonways.endpoint.settings;

import lombok.experimental.StandardException;
import me.moonways.bridgenet.rmi.service.RemoteServiceException;

@StandardException
public class SettingsEndpointException extends RemoteServiceException {

    private static final long serialVersionUID = 104732541587845806L;
}
