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

package bisq.desktop.main.content.user.user_profile.create.step2;

import bisq.desktop.common.view.View;
import bisq.desktop.components.controls.MaterialTextArea;
import bisq.desktop.components.controls.MaterialTextField;
import bisq.desktop.overlay.OverlayModel;
import bisq.i18n.Res;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CreateNewProfileStep2View extends View<VBox, CreateNewProfileStep2Model, CreateNewProfileStep2Controller> {
    private final ImageView catIconView;
    private final MaterialTextField statement;
    private final MaterialTextArea terms;
    private final Button saveButton, cancelButton;
    private final Label nickName, nym;
    protected final Label headlineLabel;

    public CreateNewProfileStep2View(CreateNewProfileStep2Model model, CreateNewProfileStep2Controller controller) {
        super(new VBox(25), model, controller);

        root.setAlignment(Pos.TOP_CENTER);
        root.setPadding(new Insets(-30, 0, 10, 0));
        root.setPrefWidth(OverlayModel.WIDTH);
        root.setPrefHeight(OverlayModel.HEIGHT);

        headlineLabel = new Label(Res.get("user.userProfile.new.step2.headline"));
        headlineLabel.getStyleClass().add("bisq-text-headline-2");

        Label subtitleLabel = new Label(Res.get("user.userProfile.new.step2.subTitle"));
        subtitleLabel.setTextAlignment(TextAlignment.CENTER);
        subtitleLabel.setMaxWidth(400);
        subtitleLabel.setMinHeight(40); // does not wrap without that...
        subtitleLabel.getStyleClass().addAll("bisq-text-3", "wrap-text");

        nickName = new Label();
        nickName.getStyleClass().addAll("bisq-text-9", "font-semi-bold");
        nickName.setAlignment(Pos.TOP_CENTER);

        catIconView = new ImageView();
        catIconView.setFitWidth(128);
        catIconView.setFitHeight(128);

        nym = new Label();
        nym.getStyleClass().addAll("bisq-text-7");
        nym.setAlignment(Pos.TOP_CENTER);

        int width = 250;
        VBox catVBox = new VBox(8, nickName, catIconView, nym);
        catVBox.setAlignment(Pos.TOP_CENTER);
        catVBox.setPrefWidth(width);
        catVBox.setPrefHeight(200);

        statement = new MaterialTextField(Res.get("user.userProfile.new.statement"), Res.get("user.userProfile.new.statement.prompt"));
        statement.setPrefWidth(width);

        terms = new MaterialTextArea(Res.get("user.userProfile.new.terms"), Res.get("user.userProfile.new.terms.prompt"));
        terms.setPrefWidth(width);
        terms.setFixedHeight(100);

        VBox fieldsAndButtonsVBox = new VBox(20, statement, terms);
        fieldsAndButtonsVBox.setPadding(new Insets(50, 0, 0, 0));
        fieldsAndButtonsVBox.setPrefWidth(width);
        fieldsAndButtonsVBox.setPrefHeight(200);
        fieldsAndButtonsVBox.setAlignment(Pos.CENTER);

        HBox.setMargin(fieldsAndButtonsVBox, new Insets(-55, 0, 0, 0));
        HBox centerHBox = new HBox(10, catVBox, fieldsAndButtonsVBox);
        centerHBox.setAlignment(Pos.TOP_CENTER);

        cancelButton = new Button(Res.get("action.cancel"));
        saveButton = new Button(Res.get("action.save"));
        saveButton.setDefaultButton(true);

        HBox buttons = new HBox(20, cancelButton, saveButton);
        buttons.setAlignment(Pos.CENTER);

        VBox.setMargin(headlineLabel, new Insets(40, 0, 0, 0));
        VBox.setMargin(buttons, new Insets(40, 0, 0, 0));
        root.getChildren().addAll(
                headlineLabel,
                subtitleLabel,
                centerHBox,
                buttons
        );
    }

    @Override
    protected void onViewAttached() {
        catIconView.imageProperty().bind(model.getCatHashImage());
        nickName.textProperty().bind(model.getNickName());
        nym.textProperty().bind(model.getNym());
        terms.textProperty().bindBidirectional(model.getTerms());
        statement.textProperty().bindBidirectional(model.getStatement());
        saveButton.setOnAction((event) -> controller.onSave());
        cancelButton.setOnAction((event) -> controller.onCancel());
    }

    @Override
    protected void onViewDetached() {
        catIconView.imageProperty().unbind();
        nickName.textProperty().unbind();
        nym.textProperty().unbind();
        terms.textProperty().unbindBidirectional(model.getTerms());
        statement.textProperty().unbindBidirectional(model.getStatement());
    }
}
