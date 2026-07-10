package me.moonways.bridgenet.mtp.message;

import lombok.extern.log4j.Log4j2;
import me.moonways.bridgenet.api.inject.Autobind;
import me.moonways.bridgenet.api.inject.processor.ScanningResult;
import me.moonways.bridgenet.api.inject.processor.persistence.AwaitAnnotationsScanning;
import me.moonways.bridgenet.api.inject.processor.persistence.GetAnnotationsScanningResult;
import me.moonways.bridgenet.mtp.channel.ChannelDirection;
import me.moonways.bridgenet.mtp.message.persistence.ClientMessage;
import me.moonways.bridgenet.mtp.message.persistence.ServerMessage;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Log4j2
@Autobind
@AwaitAnnotationsScanning({ClientMessage.class, ServerMessage.class})
public class NetworkMessagesService {

    private final Map<Integer, WrappedNetworkMessage> wrappersByIdMap = new ConcurrentHashMap<>();
    private final Map<Class<?>, WrappedNetworkMessage> wrappersByClassMap = new ConcurrentHashMap<>();

    @GetAnnotationsScanningResult
    private ScanningResult<Object> messagesResult;

    private WrappedNetworkMessage toWrapper(ChannelDirection direction, Class<?> messageClass) {
        int messageID = wrappersByIdMap.size();
        return new WrappedNetworkMessage(messageID, messageClass, direction);
    }

    private WrappedNetworkMessage toWrapper(Class<? extends Annotation> annotation, Class<?> messageClass) {
        ChannelDirection direction = ChannelDirection.fromAnnotationMarker(annotation);
        return toWrapper(direction, messageClass);
    }

    public void bindMessages(boolean reverse) {
        // Инвариант: id сообщений присваиваются по порядку в отсортированном по FQN
        // списке классов, поэтому id message-класса Handshake зависит от того, сколько
        // классов сортируются раньше него. Добавление нового message-класса с FQN,
        // сортирующимся раньше me.moonways.bridgenet.model.message.Handshake, сдвинет
        // id Handshake и сломает fail-fast проверку версии протокола при смешанных
        // версиях ядра/клиента. Новые message-классы называть так, чтобы FQN
        // сортировался после Handshake, либо бампать ProtocolVersion.CURRENT и
        // деплоить ядро и плагины строго вместе.
        List<Object> messagesList = messagesResult.toList();
        messagesList.sort(Comparator.comparing(o -> o.getClass().getName()));

        for (Object message : messagesList) {
            register(reverse, message.getClass());
        }
    }

    private Class<? extends Annotation> findMessageAnnotation(Class<?> messageClass) {
        Class<ClientMessage> clientAnnotation = ClientMessage.class;
        Class<ServerMessage> serverAnnotation = ServerMessage.class;

        if (messageClass.isAnnotationPresent(clientAnnotation)) {
            return clientAnnotation;
        } else if (messageClass.isAnnotationPresent(serverAnnotation)) {
            return serverAnnotation;
        }

        return null;
    }

    public void register(boolean reverse, Class<?> messageClass) {
        Class<ClientMessage> clientAnnotation = ClientMessage.class;
        Class<ServerMessage> serverAnnotation = ServerMessage.class;

        if (messageClass.isAnnotationPresent(clientAnnotation)) {
            registerAnnotated(reverse ? serverAnnotation : clientAnnotation, messageClass);
        }
        if (messageClass.isAnnotationPresent(serverAnnotation)) {
            registerAnnotated(reverse ? clientAnnotation : serverAnnotation, messageClass);
        }
    }

    private void registerAnnotated(Class<? extends Annotation> annotation, Class<?> messageType) {
        WrappedNetworkMessage wrapper = toWrapper(annotation, messageType);
        wrappersByIdMap.put(wrapper.getId(), wrapper);
        wrappersByClassMap.put(wrapper.getMessageType(), wrapper);

        log.debug("Protocol was registered message: §3{} §7(id: {}, direction: {})", messageType.getName(),
                wrapper.getId(),
                wrapper.getDirection());
    }

    public WrappedNetworkMessage lookupWrapperByID(int id) {
        return wrappersByIdMap.get(id);
    }

    public WrappedNetworkMessage lookupWrapperByClass(@NotNull Class<?> messageClass) {
        return wrappersByClassMap.get(messageClass);
    }

    public ExportedMessage export(@NotNull Object message) {
        WrappedNetworkMessage wrapper = lookupWrapperByClass(message.getClass());
        return new ExportedMessage(wrapper, message);
    }
}
