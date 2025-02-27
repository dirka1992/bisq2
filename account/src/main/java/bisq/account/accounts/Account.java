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

package bisq.account.accounts;

import bisq.account.payment_method.PaymentMethod;
import bisq.common.currency.TradeCurrency;
import bisq.common.proto.PersistableProto;
import bisq.common.proto.UnresolvableProtobufMessageException;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Account is only stored locally and never shared with the peer. It can contain sensitive data.
 */
@Getter
@Slf4j
@ToString
@EqualsAndHashCode
public abstract class Account<P extends AccountPayload, M extends PaymentMethod<?>> implements PersistableProto {
    protected final long creationDate;
    protected final String accountName;
    protected final P accountPayload;
    protected final M paymentMethod;

    public Account(String accountName,
                   M paymentMethod,
                   P accountPayload) {
        this(new Date().getTime(), accountName, paymentMethod, accountPayload);
    }

    public Account(long creationDate,
                   String accountName,
                   M paymentMethod,
                   P accountPayload) {
        this.creationDate = creationDate;
        this.accountName = accountName;
        this.accountPayload = accountPayload;
        this.paymentMethod = paymentMethod;
    }

    public abstract bisq.account.protobuf.Account toProto();

    protected bisq.account.protobuf.Account.Builder getAccountBuilder() {
        return bisq.account.protobuf.Account.newBuilder()
                .setCreationDate(creationDate)
                .setAccountName(accountName)
                .setPaymentMethod(paymentMethod.toProto())
                .setAccountPayload(accountPayload.toProto());
    }

    public static Account<?, ?> fromProto(bisq.account.protobuf.Account proto) {
        switch (proto.getMessageCase()) {
            case ZELLEACCOUNT: {
                return ZelleAccount.fromProto(proto);
            }
            case USERDEFINEDFIATACCOUNT: {
                return UserDefinedFiatAccount.fromProto(proto);
            }
            case REVOLUTACCOUNT: {
                return RevolutAccount.fromProto(proto);
            }
            case COUNTRYBASEDACCOUNT: {
                return CountryBasedAccount.fromProto(proto);
            }
            case FASTERPAYMENTSACCOUNT: {
                return FasterPaymentsAccount.fromProto(proto);
            }
            case PAYIDACCOUNT: {
                return PayIDAccount.fromProto(proto);
            }
            case CASHBYMAILACCOUNT: {
                return CashByMailAccount.fromProto(proto);
            }
            case INTERACETRANSFERACCOUNT: {
                return InteracETransferAccount.fromProto(proto);
            }
            case MESSAGE_NOT_SET: {
                throw new UnresolvableProtobufMessageException(proto);
            }
        }
        throw new UnresolvableProtobufMessageException(proto);
    }


    public Set<String> getTradeCurrencyCodes() {
        return paymentMethod.getTradeCurrencies().stream().map(TradeCurrency::getCode).collect(Collectors.toSet());
    }
}