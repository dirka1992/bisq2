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

package bisq.desktop.main.content.bisq_easy.offerbook;

import bisq.chat.bisqeasy.offerbook.BisqEasyOfferbookChannel;
import bisq.chat.bisqeasy.offerbook.BisqEasyOfferbookMessage;
import bisq.common.currency.Market;
import bisq.desktop.common.threading.UIThread;
import bisq.desktop.main.content.components.MarketImageComposition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.CacheHint;
import javafx.scene.Node;
import javafx.scene.effect.ColorAdjust;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.lang.ref.WeakReference;

@EqualsAndHashCode
@Getter
public class MarketChannelItem {
    private final BisqEasyOfferbookChannel channel;
    private final Market market;
    private final Node marketLogo;
    private final IntegerProperty numOffers = new SimpleIntegerProperty(0);
    private final BooleanProperty selected = new SimpleBooleanProperty(false);
    private final ChangeListener<Boolean> selectedChangeListener;
    private final ColorAdjust defaultColorAdjust = new ColorAdjust();
    private final ColorAdjust selectedColorAdjust = new ColorAdjust();

    public MarketChannelItem(BisqEasyOfferbookChannel channel) {
        this.channel = channel;
        market = channel.getMarket();
        marketLogo = MarketImageComposition.createMarketLogo(market.getQuoteCurrencyCode());
        marketLogo.setCache(true);
        marketLogo.setCacheHint(CacheHint.SPEED);

        setUpColorAdjustments();
        selectedChangeListener = (observable, oldValue, newValue) ->
                marketLogo.setEffect(newValue ? selectedColorAdjust : defaultColorAdjust);
        selected.addListener(selectedChangeListener);
        marketLogo.setEffect(defaultColorAdjust);

        channel.getChatMessages().addObserver(new WeakReference<Runnable>(this::updateNumOffers).get());
        updateNumOffers();
    }

    private void setUpColorAdjustments() {
        defaultColorAdjust.setBrightness(-0.6);
        defaultColorAdjust.setSaturation(-0.33);
        defaultColorAdjust.setContrast(-0.1);

        selectedColorAdjust.setBrightness(-0.1);
    }

    private void updateNumOffers() {
        UIThread.run(() -> {
            int numOffers = (int) channel.getChatMessages().stream()
                    .filter(BisqEasyOfferbookMessage::hasBisqEasyOffer)
                    .count();
            this.getNumOffers().set(numOffers);
        });
    }

    public String getMarketString() {
        return market.toString();
    }

    @Override
    public String toString() {
        return market.toString();
    }

    public void cleanUp() {
        selected.removeListener(selectedChangeListener);
    }
}
