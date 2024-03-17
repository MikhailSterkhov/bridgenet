package me.moonways.endpoint.socials.database;

import me.moonways.bridgenet.api.inject.Inject;
import me.moonways.bridgenet.api.inject.PostConstruct;
import me.moonways.bridgenet.jdbc.core.DatabaseConnection;
import me.moonways.bridgenet.jdbc.dao.EntityAccessCondition;
import me.moonways.bridgenet.jdbc.dao.EntityDao;
import me.moonways.bridgenet.jdbc.provider.DatabaseProvider;
import me.moonways.bridgenet.model.socials.Social;
import me.moonways.bridgenet.model.socials.SocialProfile;

import java.rmi.RemoteException;
import java.util.Optional;
import java.util.UUID;

public final class SocialDbRepository {

    @Inject
    private DatabaseProvider provider;
    @Inject
    private DatabaseConnection connection;

    private EntityDao<PlayerSocialData> dao;

    @PostConstruct
    private void initDao() {
        dao = provider.createDao(PlayerSocialData.class, connection);
    }

    public void insertSocialLinkage(SocialProfile profile) throws RemoteException {
        dao.insertMono(new PlayerSocialData(
                profile.getPlayerId(),
                profile.getSocialType().name(),
                profile.getSocialId(),
                profile.getSocialLink()));
    }

    public void deleteSocialLinkage(SocialProfile profile) throws RemoteException {
        dao.delete(EntityAccessCondition.createMono("id", profile.getPlayerId())
                .withMono("social_name", profile.getSocialType().name()));
    }

    public Optional<PlayerSocialData> findLinkedSocial(UUID playerId, Social social) {
        return dao.findMono(EntityAccessCondition.createMono("id", playerId)
                .withMono("social_name", social.name()));
    }

    public Optional<PlayerSocialData> findBySocialId(String socialId) {
        return dao.findMono(EntityAccessCondition.createMono("social_id", socialId));
    }
}
