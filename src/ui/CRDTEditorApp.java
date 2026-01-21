package ui;

import crdt.Operation;
import crdt.RGAReplica;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import network.CRDTNetworkNode;
import network.CRDTServer;
import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;


public class CRDTEditorApp extends Application {
    RGAReplica replica = new RGAReplica();
    CRDTNetworkNode networkNode;
    Runnable uiRefresh;
    CRDTServer server;

    @Override
    public void start(Stage stage) {
        // UI Components
        Button serverBtn = new Button("Start Server");
        Button clientBtn = new Button("Join Server");
        Button disconnectBtn = new Button("❌ Disconnect");
        Label statusLabel = new Label("Not Connected");
        TextArea textArea = new TextArea();

        // Menu Bar
        MenuBar menuBar = new MenuBar();
        Menu fileMenu = new Menu("File");
        MenuItem saveTextItem = new MenuItem("Save as Text");
        menuBar.setStyle("-fx-font-size: 15px; -fx-padding: 10; -fx-background-color: #B6AE9F;");

        fileMenu.getItems().addAll(saveTextItem);
        menuBar.getMenus().add(fileMenu);

        // Setup
        uiRefresh = () -> textArea.setText(replica.getText());
        Runnable conRefresh = () -> {
            serverBtn.setVisible(true);
            clientBtn.setVisible(true);
            disconnectBtn.setVisible(false);
            statusLabel.setText("Not Connected");
        };
        networkNode = new CRDTNetworkNode(replica, uiRefresh, conRefresh);

        // Initial button state
        disconnectBtn.setVisible(false);

        // Button Actions
        serverBtn.setOnAction(e -> {
            startServer();
            serverBtn.setVisible(false);
            clientBtn.setVisible(false);
            disconnectBtn.setVisible(true);

            try {
                String hosting = "🟢 Server Running\n IP: " + InetAddress.getLocalHost().getHostAddress();
                statusLabel.setText(hosting);
            } catch (UnknownHostException ex) {
                statusLabel.setText("🟢 Server Running At IP: \n IP not found");
            }

        });

        clientBtn.setOnAction(e -> {
            startClient();
            serverBtn.setVisible(false);
            clientBtn.setVisible(false);
            disconnectBtn.setVisible(true);
            statusLabel.setText("🟢 Connected to Server");

        });

        disconnectBtn.setOnAction(e -> {
            networkNode.disconnect();
            if (server != null) {
                server.stop();
                server = null;
            }
            serverBtn.setVisible(true);
            clientBtn.setVisible(true);
            disconnectBtn.setVisible(false);
            statusLabel.setText("Not Connected");

         });

        // Save Actions
        saveTextItem.setOnAction(e -> saveAsText(stage));


        // Layout
        HBox buttonBox = new HBox(10, serverBtn, clientBtn, disconnectBtn, statusLabel);
        buttonBox.setStyle("-fx-padding: 10; -fx-background-color: #C5C7BC;");
        statusLabel.setStyle("-fx-text-fill: #333333; -fx-font-size: 14px;");

        VBox root = new VBox(menuBar, buttonBox, textArea);
        VBox.setVgrow(textArea, Priority.ALWAYS);

        // Styling
        root.setStyle("-fx-background-color: #C5C7BC;");
        textArea.setStyle("""
            -fx-font-size: 18px;
            -fx-font-family: 'Consolas', 'Monaco', monospace;
            -fx-background-color: white;
            -fx-text-fill: black;
            -fx-control-inner-background: white;
        """);

        Scene scene = new Scene(root, 700, 500);
        stage.setTitle("CRDT Collaborative Editor");
        stage.setScene(scene);
        // Handle window close
        stage.setOnCloseRequest(e -> {
            networkNode.disconnect();
            if (server != null) {
                server.stop();
            }
            Platform.exit();
        });

        stage.show();

        // KEY EVENT HANDLING - FIXED for special characters
        textArea.addEventFilter(KeyEvent.KEY_TYPED, event -> {
            String text = event.getCharacter();

            // Handle all printable characters including special ones
            if (text != null && text.length() == 1) {
                char c = text.charAt(0);

                // Only process printable characters (not control characters)
                if (c >= 32 && c != 127) {
                    int caret = textArea.getCaretPosition();
                    Operation op = replica.localInsert(caret, c);
                    String newText = replica.getText();
                    int newCaret = caret + 1;
                    networkNode.send(op);

                    Platform.runLater(() -> {
                        textArea.setText(newText);
                        textArea.positionCaret(newCaret);
                    });

                    event.consume();
                }
            }
        });

        textArea.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            int caret = textArea.getCaretPosition();

            //paste
            if (event.isControlDown() && event.getCode() == KeyCode.V) {
                String pastedText = Clipboard.getSystemClipboard().getString();
                List<Operation> list = replica.localInsert(caret, pastedText);
                for(Operation op : list){
                    networkNode.send(op);
                }
            }

            // BACKSPACE
            if (event.getCode() == KeyCode.BACK_SPACE) {
                if (caret > 0) {
                    try {
                        Operation op = replica.localDelete(caret);
                        String newText = replica.getText();
                        int newCaret = caret - 1;
                        networkNode.send(op);

                        Platform.runLater(() -> {
                            textArea.setText(newText);
                            textArea.positionCaret(newCaret);
                        });
                    } catch (Exception e) {
                        System.err.println("Delete failed: " + e.getMessage());
                    }
                }
                event.consume();
                return;
            }

            // ENTER
            if (event.getCode() == KeyCode.ENTER) {
                Operation op = replica.localInsert(caret, '\n');
                String newText = replica.getText();
                int newCaret = caret + 1;
                networkNode.send(op);

                Platform.runLater(() -> {
                    textArea.setText(newText);
                    textArea.positionCaret(newCaret);
                });

                event.consume();
            }
        });
    }

    private void startClient() {
        TextInputDialog dialog = new TextInputDialog("localhost");
        dialog.setContentText("Server IP:");
        dialog.showAndWait().ifPresent(ip -> networkNode.connect(ip));
//        networkNode.connect();
    }

    private void startServer() {
         server = new CRDTServer(replica);
         server.go();
         networkNode.connect("");
    }

    private void saveAsText(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Text File");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Text Files", "*.txt")
        );
        File file = fileChooser.showSaveDialog(stage);

        if (file != null) {
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(replica.getText());
                System.out.println("Saved text to: " + file.getAbsolutePath());
            } catch (IOException e) {
                System.out.println("file didn't saved properly");
            }
        }
    }


    public static void main(String[] args) {
        launch(args);
    }
}