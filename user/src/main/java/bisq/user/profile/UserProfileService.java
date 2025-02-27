/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.user.profile;

import bisq.common.application.Service;
import bisq.common.observable.Observable;
import bisq.common.observable.collection.ObservableSet;
import bisq.network.NetworkService;
import bisq.network.p2p.services.data.DataService;
import bisq.network.p2p.services.data.storage.DistributedData;
import bisq.network.p2p.services.data.storage.auth.AuthenticatedData;
import bisq.persistence.DbSubDirectory;
import bisq.persistence.Persistence;
import bisq.persistence.PersistenceClient;
import bisq.persistence.PersistenceService;
import bisq.security.SecurityService;
import bisq.security.pow.hashcash.HashCashProofOfWorkService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class UserProfileService implements PersistenceClient<UserProfileStore>, DataService.Listener, Service {
    private static final String SEPARATOR_START = " [";
    private static final String SEPARATOR_END = "]";

    @Getter
    private final UserProfileStore persistableStore = new UserProfileStore();
    @Getter
    private final Persistence<UserProfileStore> persistence;
    private final NetworkService networkService;
    @Getter
    private final Observable<Integer> numUserProfiles = new Observable<>();
    private final HashCashProofOfWorkService hashCashProofOfWorkService;

    public UserProfileService(PersistenceService persistenceService,
                              SecurityService securityService,
                              NetworkService networkService) {
        persistence = persistenceService.getOrCreatePersistence(this, DbSubDirectory.SETTINGS, persistableStore);
        hashCashProofOfWorkService = securityService.getHashCashProofOfWorkService();
        this.networkService = networkService;
        UserNameLookup.setUserProfileService(this);
    }

    public CompletableFuture<Boolean> initialize() {
        log.info("initialize");
        networkService.addDataServiceListener(this);
        networkService.getDataService().ifPresent(ds -> ds.getAuthenticatedData().forEach(this::onAuthenticatedDataAdded));
        return CompletableFuture.completedFuture(true);
    }

    public CompletableFuture<Boolean> shutdown() {
        log.info("shutdown");
        networkService.removeDataServiceListener(this);
        return CompletableFuture.completedFuture(true);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////
    // DataService.Listener
    ///////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAuthenticatedDataAdded(AuthenticatedData authenticatedData) {
        DistributedData distributedData = authenticatedData.getDistributedData();
        if (distributedData instanceof UserProfile) {
            processUserProfileAdded((UserProfile) distributedData);
        }
    }


    @Override
    public void onAuthenticatedDataRemoved(AuthenticatedData authenticatedData) {
        processUserProfileRemoved(authenticatedData);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////////////

    public Optional<UserProfile> findUserProfile(String id) {
        return Optional.ofNullable(getUserProfileById().get(id));
    }

    public List<UserProfile> getUserProfiles() {
        return new ArrayList<>(getUserProfileById().values());
    }

    public boolean isChatUserIgnored(String profileId) {
        return findUserProfile(profileId)
                .map(this::isChatUserIgnored)
                .orElse(false);
    }

    public boolean isChatUserIgnored(UserProfile userProfile) {
        return getIgnoredUserProfileIds().contains(userProfile.getId());
    }

    public void ignoreUserProfile(UserProfile userProfile) {
        getIgnoredUserProfileIds().add(userProfile.getId());
        persist();
    }

    public void undoIgnoreUserProfile(UserProfile userProfile) {
        getIgnoredUserProfileIds().remove(userProfile.getId());
        persist();
    }

    public ObservableSet<String> getIgnoredUserProfileIds() {
        return persistableStore.getIgnoredUserProfileIds();
    }

    public String getUserName(String nym, String nickName) {
        Map<String, Set<String>> nymsByNickName = getNymsByNickName();
        if (!nymsByNickName.containsKey(nickName)) {
            nymsByNickName.put(nickName, new HashSet<>());
        }

        Set<String> nyms = nymsByNickName.get(nickName);
        nyms.add(nym);
        persist();
        if (nyms.size() == 1) {
            return nickName;
        } else {
            return nickName + SEPARATOR_START + nym + SEPARATOR_END;
        }
    }


    private void processUserProfileAdded(UserProfile userProfile) {
        Optional<UserProfile> optionalChatUser = findUserProfile(userProfile.getId());
        if (optionalChatUser.isEmpty() || !optionalChatUser.get().equals(userProfile)) {
            if (verifyUserProfile(userProfile)) {
                synchronized (persistableStore) {
                    getUserProfileById().put(userProfile.getId(), userProfile);
                }
                numUserProfiles.set(getUserProfileById().values().size());
                persist();
            }
        }
    }

    private boolean verifyUserProfile(UserProfile userProfile) {
        if (!Arrays.equals(userProfile.getProofOfWork().getPayload(), userProfile.getPubKeyHash())) {
            log.warn("Payload of proof of work not matching pubKeyHash of user profile {}", userProfile);
            return false;
        }

        if (!hashCashProofOfWorkService.verify(userProfile.getProofOfWork())) {
            log.warn("Proof of work verification of user profile {} failed", userProfile);
            return false;
        }

        return true;
    }

    private void processUserProfileRemoved(AuthenticatedData authenticatedData) {
        DistributedData distributedData = authenticatedData.getDistributedData();
        if (distributedData instanceof UserProfile) {
            UserProfile userProfile = (UserProfile) distributedData;
            synchronized (persistableStore) {
                getUserProfileById().remove(userProfile.getId());
            }
            numUserProfiles.set(getUserProfileById().values().size());
            persist();
        }
    }

    private Map<String, Set<String>> getNymsByNickName() {
        return persistableStore.getNymsByNickName();
    }

    public Map<String, UserProfile> getUserProfileById() {
        return persistableStore.getUserProfileById();
    }
}
