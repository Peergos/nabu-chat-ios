package org.peergos.views;

import com.gluonhq.charm.glisten.animation.BounceInRightTransition;
import com.gluonhq.charm.glisten.control.AppBar;
import com.gluonhq.charm.glisten.mvc.View;
import io.ipfs.multihash.Multihash;
import io.libp2p.core.PeerId;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.peergos.Chat;
import org.peergos.ChatContext;

public class SecondaryView extends View {
    public SecondaryView() {
        getStylesheets().add(SecondaryView.class.getResource("secondary.css").toExternalForm());

        TextField nodeId = new TextField("Enter Other Node's Peer Id");
        Button button = new Button("Submit");
        button.setOnAction(e -> {
            String otherNodeIdStr = nodeId.getCharacters().toString().trim();
            try {
                Multihash otherNodeId = Multihash.fromBase58(otherNodeIdStr);
                PeerId.fromBase58(otherNodeId.toBase58());
                nodeId.setText("");
                ChatContext.PeerIdList.add(otherNodeIdStr);
                getAppManager().switchView(Chat.PRIMARY_VIEW);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
        VBox controls = new VBox(nodeId, button);
        controls.setAlignment(Pos.CENTER);
        setCenter(controls);
        setShowTransitionFactory(BounceInRightTransition::new);
    }

    @Override
    protected void updateAppBar(AppBar appBar) {
        appBar.setTitleText("PeerId: " + ChatContext.PeerIdList.get(0));
    }
}
