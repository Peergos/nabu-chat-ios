package org.peergos.views;

import com.gluonhq.charm.glisten.control.AppBar;
import com.gluonhq.charm.glisten.mvc.View;
import io.ipfs.multiaddr.MultiAddress;
import io.ipfs.multihash.Multihash;
import io.libp2p.core.PeerId;
import io.libp2p.core.crypto.PrivKey;
import io.libp2p.core.multiformats.Multiaddr;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;
import org.peergos.*;
import org.peergos.ChatContext;
import org.peergos.blockstore.Blockstore;
import org.peergos.blockstore.RamBlockstore;
import org.peergos.config.Config;
import org.peergos.config.IdentitySection;
import org.peergos.net.ConnectionException;
import org.peergos.protocol.dht.RamRecordStore;
import org.peergos.protocol.dht.RecordStore;
import org.peergos.protocol.http.HttpProtocol;

import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

public class PrimaryView extends View {

    private AppBar appBarRef;
    private VBox content = new VBox(5);
    private boolean init;
    private Multihash otherNodeId;
    private PeerId peerId;

    private PeerId otherPeerId;
    private EmbeddedIpfs embeddedIpfs;
    public PrimaryView() {
        getStylesheets().add(PrimaryView.class.getResource("primary.css").toExternalForm());
        Label InitLabel = new Label("Initializing. Please wait...");
        VBox controls = new VBox(30.0, InitLabel);
        controls.setAlignment(Pos.CENTER);
        setCenter(controls);
        new Thread() {
            public void run() {
                try {
                    RecordStore recordStore = new RamRecordStore();
                    Blockstore blockStore = new RamBlockstore();
                    int portNumber = 10000 + new Random().nextInt(50000);
                    List<MultiAddress> swarmAddresses = new ArrayList<>();
                    swarmAddresses.add(new MultiAddress("/ip6/::/tcp/" + portNumber));
                    List<MultiAddress> bootstrapNodes = new ArrayList<>(Config.defaultBootstrapNodes);

                    HostBuilder builder = new HostBuilder().generateIdentity();
                    PrivKey privKey = builder.getPrivateKey();
                    peerId = builder.getPeerId();
                    System.out.println("Our PeerId:" + peerId.toBase58());
                    IdentitySection identitySection = new IdentitySection(privKey.bytes(), peerId);
                    BlockRequestAuthoriser authoriser = (c, b, a) -> CompletableFuture.completedFuture(true);

                    HttpProtocol.HttpRequestProcessor proxyHandler = (s, req, h) -> {
                        ByteBuf content = req.content();
                        String output = content.getCharSequence(0, content.readableBytes(), Charset.defaultCharset()).toString();
                        System.out.println("received msg:" + output);
                        Platform.runLater(new Runnable() {
                            public void run() {
                                addMessage(false, output);
                            }
                        });
                        FullHttpResponse replyOk = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.buffer(0));
                        replyOk.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
                        h.accept(replyOk.retain());
                    };

                    embeddedIpfs = EmbeddedIpfs.build(recordStore, blockStore, false,
                            swarmAddresses,
                            bootstrapNodes,
                            identitySection,
                            authoriser, Optional.of(proxyHandler));
                    embeddedIpfs.start();
                    Platform.runLater(new Runnable() {
                        public void run() {
                            if (appBarRef != null) {
                                ChatContext.PeerIdList.add(peerId.toBase58());
                            }
                            getAppManager().switchView(Chat.SECONDARY_VIEW);
                            init = true;
                        }
                    });
                } catch (Exception ie) {
                    ie.printStackTrace();
                }
            }
        }.start();
    }
    private void setPrimaryScene() {
        ScrollPane scroller = new ScrollPane(content);
        scroller.setFitToWidth(true);
        Button addButton = new Button("Add");
        TextField field = new TextField();
        addButton.setOnAction(e -> {
            String text = field.getCharacters().toString().trim();
            try {
                proxyMessage(text, EmbeddedIpfs.getAddresses(embeddedIpfs.node, embeddedIpfs.dht, otherNodeId));
            } catch (Exception ex) {
                System.out.println("Exception ex: " + ex.getMessage());
            }
            addMessage(true, text);
            field.setText("");
        });
        HBox hbox = new HBox();
        HBox.setHgrow(field, Priority.ALWAYS);
        hbox.getChildren().addAll(new Label("Message:"), field, addButton);
        BorderPane scene = new BorderPane(scroller, null, null, hbox, null);
        setCenter(scene);
    }
    private synchronized void addMessage(boolean me, String text) {
        LocalDateTime now = LocalDateTime.now();
        String prefix = me ? "You: " : "";
        Label label = new Label(prefix + text + " | " + now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        AnchorPane.setLeftAnchor(label, 5.0);
        AnchorPane.setTopAnchor(label, 5.0);
        AnchorPane anchorPane = new AnchorPane();
        anchorPane.getChildren().addAll(label);
        content.getChildren().add(anchorPane);
    }

    private void proxyMessage(String message, Multiaddr[] addressesToDial) {
        byte[] msg = message.getBytes();
        FullHttpRequest httpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/", Unpooled.copiedBuffer(msg));
        httpRequest.headers().set(HttpHeaderNames.CONTENT_LENGTH, msg.length);
        HttpProtocol.HttpController proxier = embeddedIpfs.p2pHttp.get().dial(embeddedIpfs.node, otherPeerId, addressesToDial).getController().join();
        proxier.send(httpRequest.retain()).join().release();
    }
    @Override
    protected void updateAppBar(AppBar appBar) {
        appBarRef = appBar;
        if (init) {
            appBarRef.setTitleText("PeerId: " + ChatContext.PeerIdList.get(0));
            otherNodeId = Multihash.fromBase58(ChatContext.PeerIdList.get(1));
            otherPeerId = PeerId.fromBase58(otherNodeId.toBase58());
            setPrimaryScene();
        }
    }
}
