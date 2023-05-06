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

package bisq.chat.channel;

import bisq.chat.message.CommonPublicChatMessage;
import bisq.i18n.Res;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public final class CommonPublicChatChannel extends PublicChatChannel<CommonPublicChatMessage> {
    private final String displayName;
    private final String description;
    private final String channelAdminId;
    private final List<String> channelModeratorIds;

    public CommonPublicChatChannel(ChannelDomain channelDomain, String channelName) {
        this(channelDomain,
                channelName,
                Res.get(channelDomain.name().toLowerCase() + "." + channelName + ".name"),
                Res.get(channelDomain.name().toLowerCase() + "." + channelName + ".description"),
                "",
                new ArrayList<>(),
                ChannelNotificationType.GLOBAL_DEFAULT);
    }

    private CommonPublicChatChannel(ChannelDomain channelDomain,
                                    String channelName,
                                    String displayName,
                                    String description,
                                    String channelAdminId,
                                    List<String> channelModeratorIds,
                                    ChannelNotificationType channelNotificationType) {
        super(channelDomain, channelName, channelNotificationType);

        this.displayName = displayName;
        this.description = description;
        this.channelAdminId = channelAdminId;
        this.channelModeratorIds = channelModeratorIds;
        // We need to sort deterministically as the data is used in the proof of work check
        this.channelModeratorIds.sort(Comparator.comparing((String e) -> e));
    }

    public bisq.chat.protobuf.ChatChannel toProto() {
        return getChannelBuilder()
                .setCommonPublicChatChannel(bisq.chat.protobuf.CommonPublicChatChannel.newBuilder()
                        .setChannelName(displayName)
                        .setDescription(description)
                        .setChannelAdminId(channelAdminId)
                        .addAllChannelModeratorIds(channelModeratorIds))
                .build();
    }

    public static CommonPublicChatChannel fromProto(bisq.chat.protobuf.ChatChannel baseProto,
                                                    bisq.chat.protobuf.CommonPublicChatChannel proto) {
        CommonPublicChatChannel commonPublicChatChannel = new CommonPublicChatChannel(ChannelDomain.fromProto(baseProto.getChannelDomain()),
                baseProto.getChannelName(),
                proto.getChannelName(),
                proto.getDescription(),
                proto.getChannelAdminId(),
                new ArrayList<>(proto.getChannelModeratorIdsList()),
                ChannelNotificationType.fromProto(baseProto.getChannelNotificationType()));
        commonPublicChatChannel.getSeenChatMessageIds().addAll(new HashSet<>(baseProto.getSeenChatMessageIdsList()));
        return commonPublicChatChannel;
    }

    @Override
    public String getDisplayString() {
        return displayName;
    }
}