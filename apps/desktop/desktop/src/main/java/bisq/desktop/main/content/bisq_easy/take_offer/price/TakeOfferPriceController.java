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

package bisq.desktop.main.content.bisq_easy.take_offer.price;

import bisq.bonded_roles.market_price.MarketPriceService;
import bisq.common.currency.Market;
import bisq.common.monetary.PriceQuote;
import bisq.desktop.ServiceProvider;
import bisq.desktop.common.view.Controller;
import bisq.desktop.components.overlay.Popup;
import bisq.desktop.main.content.bisq_easy.components.PriceInput;
import bisq.i18n.Res;
import bisq.offer.bisq_easy.BisqEasyOffer;
import bisq.offer.price.PriceUtil;
import bisq.offer.price.spec.FixPriceSpec;
import bisq.offer.price.spec.FloatPriceSpec;
import bisq.offer.price.spec.PriceSpec;
import bisq.offer.price.spec.PriceSpecUtil;
import bisq.presentation.formatters.PriceFormatter;
import bisq.settings.CookieKey;
import bisq.settings.SettingsService;
import javafx.beans.property.ReadOnlyObjectProperty;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import java.util.Optional;

import static bisq.presentation.formatters.PercentageFormatter.formatToPercentWithSymbol;
import static bisq.presentation.parser.PercentageParser.parse;

@Slf4j
public class TakeOfferPriceController implements Controller {
    private final TakeOfferPriceModel model;
    @Getter
    private final TakeOfferPriceView view;
    private final PriceInput priceInput;
    private final MarketPriceService marketPriceService;
    private final SettingsService settingsService;
    private Subscription priceInputPin;

    public TakeOfferPriceController(ServiceProvider serviceProvider) {
        marketPriceService = serviceProvider.getBondedRolesService().getMarketPriceService();
        settingsService = serviceProvider.getSettingsService();
        priceInput = new PriceInput(serviceProvider.getBondedRolesService().getMarketPriceService());
        model = new TakeOfferPriceModel();
        view = new TakeOfferPriceView(model, this, priceInput);
    }

    public void init(BisqEasyOffer bisqEasyOffer) {
        Market market = bisqEasyOffer.getMarket();
        priceInput.setMarket(market);
        model.setMarket(market);
    }

    public ReadOnlyObjectProperty<PriceSpec> getPriceSpec() {
        return model.getPriceSpec();
    }

    public void reset() {
        model.reset();
    }

    @Override
    public void onActivate() {
        settingsService.getCookie().asBoolean(CookieKey.CREATE_OFFER_USE_FIX_PRICE, getCookieSubKey())
                .ifPresent(useFixPrice -> model.getUseFixPrice().set(useFixPrice));

        priceInputPin = EasyBind.subscribe(priceInput.getQuote(), this::onQuoteInput);

        String marketCodes = model.getMarket().getMarketCodes();
        priceInput.setDescription(Res.get("bisqEasy.price.tradePrice", marketCodes));

        applyPriceSpec();
    }

    @Override
    public void onDeactivate() {
        priceInputPin.unsubscribe();
    }

    void onPercentageFocussed(boolean focussed) {
        if (!focussed) {
            try {
                double percentage = parse(model.getPercentageAsString().get());
                // Need to change the value first otherwise it does not trigger an update
                model.getPercentageAsString().set("");
                model.getPercentageAsString().set(formatToPercentWithSymbol(percentage));
                findMarketPriceQuote().ifPresentOrElse(marketPriceQuote -> priceInput.setQuote(PriceUtil.fromMarketPriceMarkup(marketPriceQuote, percentage)),
                        () -> log.warn("marketPriceQuote not present"));
                applyPriceSpec();
            } catch (NumberFormatException t) {
                new Popup().warning(Res.get("bisqEasy.price.warn.invalidPrice")).show();
                onQuoteInput(priceInput.getQuote().get());
            }
        }
    }

    void onToggleUseFixPrice() {
        boolean useFixPrice = !model.getUseFixPrice().get();
        model.getUseFixPrice().set(useFixPrice);
        settingsService.setCookie(CookieKey.CREATE_OFFER_USE_FIX_PRICE, getCookieSubKey(), useFixPrice);
        applyPriceSpec();
    }

    private void applyPriceSpec() {
        model.getPriceSpec().set(model.getUseFixPrice().get() ?
                new FixPriceSpec(priceInput.getQuote().get()) :
                new FloatPriceSpec(model.getPercentage().get()));
    }

    private void onQuoteInput(PriceQuote priceQuote) {
        if (priceQuote == null) {
            model.getPercentage().set(0);
            model.getPercentageAsString().set("");
            return;
        }
        if (isQuoteValid(priceQuote)) {
            model.getPriceAsString().set(PriceFormatter.format(priceQuote, true));
            applyPercentageFromQuote(priceQuote);
            applyPriceSpec();
        } else {
            new Popup().warning(Res.get("bisqEasy.price.warn.invalidPrice")).show();
            findMarketPriceQuote().ifPresentOrElse(marketPrice -> {
                        priceInput.setQuote(marketPrice);
                        applyPercentageFromQuote(marketPrice);
                    },
                    () -> log.warn("marketPrice not present"));
            applyPriceSpec();
        }
    }

    private void applyPercentageFromQuote(PriceQuote priceQuote) {
        Optional<Double> optionalPercentage = getPercentage(priceQuote);
        if (optionalPercentage.isPresent()) {
            double percentage = optionalPercentage.get();
            model.getPercentage().set(percentage);
            model.getPercentageAsString().set(formatToPercentWithSymbol(percentage));
        } else {
            log.warn("optionalPercentage not present");
        }
    }


    //todo (refactor, low prio) add validator and give feedback
    private boolean isQuoteValid(PriceQuote priceQuote) {
        Optional<Double> optionalPercentage = getPercentage(priceQuote);
        if (optionalPercentage.isPresent()) {
            double percentage = optionalPercentage.get();
            if (percentage >= -0.1 && percentage <= 0.5) {
                return true;
            }
        } else {
            log.warn("optionalPercentage not present");
            return true;
        }
        return false;
    }

    private Optional<Double> getPercentage(PriceQuote priceQuote) {
        return PriceSpecUtil.createFloatPriceSpec(marketPriceService, priceQuote).map(FloatPriceSpec::getPercentage);
    }

    private Optional<PriceQuote> findMarketPriceQuote() {
        return marketPriceService.findMarketPriceQuote(model.getMarket());
    }

    private String getCookieSubKey() {
        return model.getMarket().getMarketCodes();
    }
}
