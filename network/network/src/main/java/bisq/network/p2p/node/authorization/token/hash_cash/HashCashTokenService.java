package bisq.network.p2p.node.authorization.token.hash_cash;

import bisq.common.application.DevMode;
import bisq.common.encoding.Hex;
import bisq.common.util.ByteArrayUtils;
import bisq.common.util.MathUtils;
import bisq.network.p2p.message.EnvelopePayloadMessage;
import bisq.network.p2p.node.authorization.AuthorizationToken;
import bisq.network.p2p.node.authorization.AuthorizationTokenService;
import bisq.network.p2p.node.network_load.NetworkLoad;
import bisq.security.DigestUtil;
import bisq.security.pow.ProofOfWork;
import bisq.security.pow.hashcash.HashCashProofOfWorkService;
import com.google.common.base.Charsets;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class HashCashTokenService extends AuthorizationTokenService<HashCashToken> {
    public final static int MIN_DIFFICULTY = 128;  // Math.pow(2, 7) = 128; 3 ms on old CPU, 1 ms on high-end CPU
    public final static int MAX_DIFFICULTY = 65536;  // Math.pow(2, 16) = 262144; 1000 ms on old CPU, 60 ms on high-end CPU
    public final static int DIFFICULTY_TOLERANCE = 50_000;

    private final HashCashProofOfWorkService proofOfWorkService;
    // Keep track of message counter per connection to avoid reuse of pow
    private final Map<String, Set<Integer>> receivedMessageCountersByConnectionId = new ConcurrentHashMap<>();

    public HashCashTokenService(HashCashProofOfWorkService proofOfWorkService) {
        this.proofOfWorkService = proofOfWorkService;
    }

    @Override
    public HashCashToken createToken(EnvelopePayloadMessage message,
                                     NetworkLoad networkLoad,
                                     String peerAddress,
                                     int messageCounter) {
        long ts = System.currentTimeMillis();
        double difficulty = calculateDifficulty(message, networkLoad);
        byte[] challenge = getChallenge(peerAddress, messageCounter);
        byte[] payload = getPayload(message);
        ProofOfWork proofOfWork = proofOfWorkService.mint(payload, challenge, difficulty);
        HashCashToken token = new HashCashToken(proofOfWork, messageCounter);
        log.info("Create HashCashToken for {} took {} ms\n" +
                        "CostFactor={}; Load={}; Difficulty=2^{}={}",
                message.getClass().getSimpleName(), System.currentTimeMillis() - ts,
                message.getCostFactor(), networkLoad.getValue(),
                MathUtils.roundDouble(Math.log(difficulty) / MathUtils.LOG2, 2), difficulty);
        return token;
    }

    @Override
    public boolean isAuthorized(EnvelopePayloadMessage message,
                                AuthorizationToken authorizationToken,
                                NetworkLoad currentNetworkLoad,
                                Optional<NetworkLoad> previousNetworkLoad,
                                String connectionId,
                                String myAddress) {

        HashCashToken hashCashToken = (HashCashToken) authorizationToken;
        ProofOfWork proofOfWork = hashCashToken.getProofOfWork();
        int messageCounter = hashCashToken.getMessageCounter();

        // Verify that pow is not reused
        Set<Integer> receivedMessageCounters;
        if (receivedMessageCountersByConnectionId.containsKey(connectionId)) {
            receivedMessageCounters = receivedMessageCountersByConnectionId.get(connectionId);
            if (receivedMessageCounters.contains(messageCounter)) {
                log.warn("Invalid receivedMessageCounters. We received the proofOfWork for that message already.");
                return false;
            }
        } else {
            receivedMessageCounters = new HashSet<>();
            receivedMessageCountersByConnectionId.put(connectionId, receivedMessageCounters);
        }
        receivedMessageCounters.add(messageCounter);

        // Verify payload
        byte[] payload = getPayload(message);
        if (!Arrays.equals(payload, proofOfWork.getPayload())) {
            log.warn("Message payload not matching proof of work payload. " +
                            "getPayload(message)={}; proofOfWork.getPayload()={}; " +
                            "getPayload(message).length={}; proofOfWork.getPayload().length={}",
                    Hex.encode(payload), Hex.encode(proofOfWork.getPayload()),
                    payload.length, proofOfWork.getPayload().length);
            return false;
        }

        // Verify challenge
        if (!Arrays.equals(getChallenge(myAddress, messageCounter), proofOfWork.getChallenge())) {
            log.warn("Invalid challenge");
            return false;
        }

        // Verify difficulty
        if (isDifficultyInvalid(message, proofOfWork.getDifficulty(), currentNetworkLoad, previousNetworkLoad)) {
            return false;
        }
        return proofOfWorkService.verify(proofOfWork);
    }

    // We check the difficulty used for the proof of work if it matches the current network load or if available the
    // previous network load. If the difference is inside a tolerance range we consider it still valid, but it should
    // be investigated why that happens, thus we log those cases.
    private boolean isDifficultyInvalid(EnvelopePayloadMessage message,
                                        double proofOfWorkDifficulty,
                                        NetworkLoad currentNetworkLoad,
                                        Optional<NetworkLoad> previousNetworkLoad) {
        log.debug("isDifficultyInvalid/currentNetworkLoad: message.getCostFactor()={}, networkLoad.getValue()={}",
                message.getCostFactor(), currentNetworkLoad.getValue());
        double expectedDifficulty = calculateDifficulty(message, currentNetworkLoad);
        if (proofOfWorkDifficulty >= expectedDifficulty) {
            // We don't want to call calculateDifficulty with the previousNetworkLoad if we are not in dev mode.
            if (DevMode.isDevMode() && proofOfWorkDifficulty > expectedDifficulty && previousNetworkLoad.isPresent()) {
                // Might be that the difficulty was using the previous network load
                double expectedPreviousDifficulty = calculateDifficulty(message, previousNetworkLoad.get());
                if (proofOfWorkDifficulty != expectedPreviousDifficulty) {
                    log.warn("Unexpected high difficulty provided. This might be a bug (but valid as provided difficulty is larger as expected): " +
                                    "expectedDifficulty={}; expectedPreviousDifficulty={}; proofOfWorkDifficulty={}",
                            expectedDifficulty, expectedPreviousDifficulty, proofOfWorkDifficulty);
                }
            }
            return false;
        }

        double missing = expectedDifficulty - proofOfWorkDifficulty;
        double deviationToTolerance = MathUtils.roundDouble(missing / DIFFICULTY_TOLERANCE * 100, 2);
        double deviationToExpectedDifficulty = MathUtils.roundDouble(missing / expectedDifficulty * 100, 2);
        if (previousNetworkLoad.isEmpty()) {
            log.debug("No previous network load available");
            if (missing <= DIFFICULTY_TOLERANCE) {
                log.info("Difficulty of current network load deviates from the proofOfWork difficulty but is inside the tolerated range.\n" +
                                "deviationToTolerance={}%; deviationToExpectedDifficulty={}%; expectedDifficulty={}; proofOfWorkDifficulty={}; DIFFICULTY_TOLERANCE={}",
                        deviationToTolerance, deviationToExpectedDifficulty, expectedDifficulty, proofOfWorkDifficulty, DIFFICULTY_TOLERANCE);
                return false;
            }

            log.warn("Difficulty of current network load deviates from the proofOfWork difficulty and is outside the tolerated range.\n" +
                            "deviationToTolerance={}%; deviationToExpectedDifficulty={}%; expectedDifficulty={}; proofOfWorkDifficulty={}; DIFFICULTY_TOLERANCE={}",
                    deviationToTolerance, deviationToExpectedDifficulty, expectedDifficulty, proofOfWorkDifficulty, DIFFICULTY_TOLERANCE);
            return true;
        }

        log.debug("isDifficultyInvalid/previousNetworkLoad: message.getCostFactor()={}, networkLoad.getValue()={}",
                message.getCostFactor(), previousNetworkLoad.get().getValue());
        double expectedPreviousDifficulty = calculateDifficulty(message, previousNetworkLoad.get());
        if (proofOfWorkDifficulty >= expectedPreviousDifficulty) {
            log.debug("Difficulty of previous network load is correct");
            if (proofOfWorkDifficulty > expectedPreviousDifficulty) {
                log.warn("Unexpected high difficulty provided. This might be a bug (but valid as provided difficulty is larger as expected): " +
                                "expectedPreviousDifficulty={}; proofOfWorkDifficulty={}",
                        expectedPreviousDifficulty, proofOfWorkDifficulty);
            }
            return false;
        }

        if (missing <= DIFFICULTY_TOLERANCE) {
            log.info("Difficulty of current network load deviates from the proofOfWork difficulty but is inside the tolerated range.\n" +
                            "deviationToTolerance={}%; deviationToExpectedDifficulty={}%; expectedDifficulty={}; proofOfWorkDifficulty={}; DIFFICULTY_TOLERANCE={}",
                    deviationToTolerance, deviationToExpectedDifficulty, expectedDifficulty, proofOfWorkDifficulty, DIFFICULTY_TOLERANCE);
            return false;
        }

        double missingUsingPrevious = expectedPreviousDifficulty - proofOfWorkDifficulty;
        if (missingUsingPrevious <= DIFFICULTY_TOLERANCE) {
            deviationToTolerance = MathUtils.roundDouble(missingUsingPrevious / DIFFICULTY_TOLERANCE * 100, 2);
            deviationToExpectedDifficulty = MathUtils.roundDouble(missingUsingPrevious / expectedPreviousDifficulty * 100, 2);
            log.info("Difficulty of previous network load deviates from the proofOfWork difficulty but is inside the tolerated range.\n" +
                            "deviationToTolerance={}%; deviationToExpectedDifficulty={}%; expectedDifficulty={}; proofOfWorkDifficulty={}; DIFFICULTY_TOLERANCE={}",
                    deviationToTolerance, deviationToExpectedDifficulty, expectedPreviousDifficulty, proofOfWorkDifficulty, DIFFICULTY_TOLERANCE);
            return false;
        }

        log.warn("Difficulties of current and previous network load deviate from the proofOfWork difficulty and are outside the tolerated range.\n" +
                        "deviationToTolerance={}%; deviationToExpectedDifficulty={}%; expectedDifficulty={}; proofOfWorkDifficulty={}; DIFFICULTY_TOLERANCE={}",
                deviationToTolerance, deviationToExpectedDifficulty, expectedDifficulty, proofOfWorkDifficulty, DIFFICULTY_TOLERANCE);
        return true;
    }

    private byte[] getPayload(EnvelopePayloadMessage message) {
        return message.toProto().toByteArray();
    }

    private byte[] getChallenge(String peerAddress, int messageCounter) {
        return DigestUtil.sha256(ByteArrayUtils.concat(peerAddress.getBytes(Charsets.UTF_8),
                BigInteger.valueOf(messageCounter).toByteArray()));
    }

    private double calculateDifficulty(EnvelopePayloadMessage message, NetworkLoad networkLoad) {
        double messageCostFactor = MathUtils.bounded(0.01, 1, message.getCostFactor());
        double loadValue = MathUtils.bounded(0.01, 1, networkLoad.getValue());
        double difficulty = MAX_DIFFICULTY * messageCostFactor * loadValue;
        return MathUtils.bounded(MIN_DIFFICULTY, MAX_DIFFICULTY, difficulty);
    }
}
