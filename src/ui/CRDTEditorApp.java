package ui;

import crdt.RGAReplica;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import network.CRDTNetworkNode;
import network.CRDTServer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class CRDTEditorApp extends Application {

    private static final Duration BATCH_FLUSH_DELAY = Duration.millis(400);
    private final RGAReplica replica = new RGAReplica();
    private CRDTNetworkNode networkNode;
    private CRDTServer server;
    private TypingBatcher batcher;
    private PauseTransition flushTimer;
    private Stage stage;

    private TextArea textArea;
    private Button serverBtn, clientBtn, disconnectBtn, undoBtn, redoBtn;
    private Label statusLabel;
    private HBox topBar, buttonBox;

    private static final String BUTTON_STYLE = """
    -fx-font-size: 14px;
    -fx-padding: 8 18;
    -fx-background-color: #D4AF37;
    -fx-background-insets: 0;
    -fx-text-fill: #1B2A4A;
    -fx-background-radius: 4;
    -fx-cursor: hand;
    """;

    private static final String BUTTON_DISABLED_STYLE = """
    -fx-font-size: 14px;
    -fx-padding: 8 18;
    -fx-background-color: #B8A56D;
    -fx-background-insets: 0;
    -fx-text-fill: #8C7D52;
    -fx-background-radius: 4;
    -fx-cursor: default;
    """;

    private static final String DISCONNECT_STYLE = """
    -fx-font-size: 14px;
    -fx-padding: 8 18;
    -fx-background-color: #B0524A;
    -fx-background-insets: 0;
    -fx-text-fill: white;
    -fx-background-radius: 4;
    -fx-cursor: hand;
    """;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        batcher = new TypingBatcher(replica);

        buildComponents();
        wireNetworking();
        wireUndoRedo();
        wireConnectionButtons();
        wireTypingHandlers();

        Scene scene = new Scene(layout(), 700, 500);
        stage.setTitle("CRDT Collaborative Editor");
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> {
            batcher.flush();
            networkNode.disconnect();
            networkNode = null;
            if (server != null) {
                server.stop();
                server = null;
            }
            Platform.exit();
        });
        stage.show();
    }

    private void buildComponents() {
        serverBtn = new Button("Start Server");
        clientBtn = new Button("Join Server");
        disconnectBtn = new Button("❌ Disconnect");
        statusLabel = new Label("Not Connected");
        textArea = new TextArea();

        MenuBar menuBar = new MenuBar();
        Menu fileMenu = new Menu("File");
        MenuItem saveTextItem = new MenuItem("Save as Text");

        fileMenu.getItems().addAll(saveTextItem);
        menuBar.getMenus().add(fileMenu);

        undoBtn = new Button("Undo");
        redoBtn = new Button("Redo");

        serverBtn.setStyle(BUTTON_STYLE);
        clientBtn.setStyle(BUTTON_STYLE);
        undoBtn.setStyle(BUTTON_DISABLED_STYLE);   // starts disabled
        redoBtn.setStyle(BUTTON_DISABLED_STYLE);   // starts disabled
        disconnectBtn.setStyle(DISCONNECT_STYLE);

        undoBtn.setDisable(true);
        redoBtn.setDisable(true);
        disconnectBtn.setVisible(false);

        topBar = new HBox(10, menuBar, undoBtn, redoBtn);
        buttonBox = new HBox(10, serverBtn, clientBtn, disconnectBtn, statusLabel);

        topBar.setAlignment(Pos.CENTER_LEFT);
        buttonBox.setAlignment(Pos.CENTER_LEFT);

        Label fileLabel = new Label("File");
        fileLabel.setStyle("-fx-text-fill: white;");
        fileMenu.setGraphic(fileLabel);
        fileMenu.setText("");

        menuBar.setStyle("""
        -fx-font-size: 14px;
        -fx-padding: 6 10;
        -fx-background-color: #1B2A4A;
        """);
        topBar.setStyle("-fx-padding: 8; -fx-background-color: #1B2A4A;");
        buttonBox.setStyle("-fx-padding: 10; -fx-background-color: #1B2A4A;");

        statusLabel.setStyle("""
        -fx-text-fill: white;
        -fx-font-size: 14px;
        -fx-font-weight: 500;
        -fx-padding: 8 0;
        """);

        textArea.setStyle("""
        -fx-font-size: 18px;
        -fx-font-family: 'Consolas', 'Monaco', monospace;
        -fx-background-color: white;
        -fx-text-fill: #222222;
        -fx-control-inner-background: white;
        """);

        textArea.setWrapText(true);


        saveTextItem.setOnAction(e -> {
            batcher.flush();
            saveAsText();
        });

        flushTimer = new PauseTransition(BATCH_FLUSH_DELAY);
        flushTimer.setOnFinished(e -> {
            batcher.flush();
            updateUndoRedoButtons();
        });
    }

    private VBox layout() {
        VBox root = new VBox(topBar, buttonBox, textArea);
        VBox.setVgrow(textArea, Priority.ALWAYS);

        root.setStyle("-fx-background-color: white;");

        return root;
    }
    // ---- networking ----

    private void wireNetworking() {
        Runnable uiRefresh = () -> {
            batcher.flush();
            flushTimer.stop();
            textArea.setText(replica.getText());
            updateUndoRedoButtons();
        };
        Runnable conRefresh = () -> setConnectedUiState(false, "Not Connected");

        networkNode = new CRDTNetworkNode(replica, uiRefresh, conRefresh);
        replica.networkNode = networkNode;
    }

    private void wireConnectionButtons() {
        serverBtn.setOnAction(e -> {
            server = new CRDTServer(replica);
            try {
                server.go();
            } catch (Exception ex) {
                System.err.println("Failed to start server: " + ex.getMessage());
                return;
            }
            networkNode.setServerOn();
            networkNode.connect("");
            String status;
            try {
                status = "🟢 Server Running\n IP: " + InetAddress.getLocalHost().getHostAddress();
            } catch (UnknownHostException ex) {
                status = "🟢 Server Running At IP: \n IP not found";
            }
            setConnectedUiState(true, status);
        });

        clientBtn.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog("localhost");
            dialog.setContentText("Server IP:");
            dialog.showAndWait().ifPresent(ip -> {
                networkNode.connect(ip);
                setConnectedUiState(true, "🟢 Connected to Server");
            });
        });

        disconnectBtn.setOnAction(e -> {
            batcher.flush();
            networkNode.disconnect();
            if (server != null) {
                server.stop();
                server = null;
                networkNode.setServerOff();
            }
            setConnectedUiState(false, "Not Connected");
        });
    }

    private void setConnectedUiState(boolean connected, String status) {
        serverBtn.setVisible(!connected);
        clientBtn.setVisible(!connected);
        disconnectBtn.setVisible(connected);
        statusLabel.setText(status);
    }

    // ---- undo/redo ----

    private void wireUndoRedo() {
        undoBtn.setOnAction(e -> handleUndo());
        redoBtn.setOnAction(e -> handleRedo());
    }

    private void updateUndoRedoButtons() {
        boolean canUndo = !replica.undoEmpty() || batcher.isPending();
        boolean canRedo = !replica.redoEmpty();
        undoBtn.setDisable(!canUndo);
        redoBtn.setDisable(!canRedo);
        undoBtn.setStyle(canUndo ? BUTTON_STYLE: BUTTON_DISABLED_STYLE );
        redoBtn.setStyle(canRedo ? BUTTON_STYLE: BUTTON_DISABLED_STYLE);
    }

    // ---- typing ----

    private void wireTypingHandlers() {
        textArea.addEventFilter(KeyEvent.KEY_TYPED, event -> {
            String text = event.getCharacter();
            if (text != null && text.length() == 1) {
                char c = text.charAt(0);
                if (c >= 32 && c != 127) {
                    insertLocalChar(c);
                    event.consume();
                }
            }
        });

        textArea.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.V) {
                handlePaste();
                event.consume();
            } else if (event.isControlDown() && event.getCode() == KeyCode.X) {
                handleCut(); event.consume();
            } else if (event.isControlDown() && event.getCode() == KeyCode.BACK_SPACE) {
                handleWordBackspace(); event.consume();
            } else if (event.isControlDown() && event.getCode() == KeyCode.Y){
                handleRedo(); event.consume();
            } else if (event.isControlDown() && event.getCode() == KeyCode.Z){
                handleUndo(); event.consume();
            }
            else if (event.getCode() == KeyCode.BACK_SPACE) {
                handleBackspace(); event.consume();
            } else if (event.getCode() == KeyCode.DELETE) {
                handleForwardDelete(); event.consume();
            } else if (event.getCode() == KeyCode.ENTER) {
                insertLocalChar('\n');
                event.consume();
            } else if (event.getCode() == KeyCode.TAB) {
                insertLocalChar('\t');
                event.consume();
            }

        });

        ContextMenu cleanMenu = new ContextMenu();

        MenuItem cutItem = new MenuItem("Cut");
        cutItem.setOnAction(e -> handleCut());

        MenuItem copyItem = new MenuItem("Copy");
        copyItem.setOnAction(e -> textArea.copy());

        MenuItem pasteItem = new MenuItem("Paste");
        pasteItem.setOnAction(e -> handlePaste());

        cleanMenu.getItems().addAll(cutItem, copyItem, pasteItem);
        textArea.setContextMenu(cleanMenu);
    }

    private void insertLocalChar(char c) {
        IndexRange sel = textArea.getSelection();
        if (sel.getLength() > 0) deleteRange(sel.getStart(), sel.getEnd());
        int caret = textArea.getCaretPosition();
        textArea.positionCaret(caret + 1);
        batcher.type(caret, c);
        textArea.insertText(caret, String.valueOf(c));
        flushTimer.playFromStart();
        updateUndoRedoButtons();
    }


    private void deleteRange(int start, int end) {
        batcher.flush();
        replica.localDelete(start, end);
        textArea.deleteText(start, end);
        textArea.positionCaret(start);
        updateUndoRedoButtons();
    }
    private void handleBackspace() {
        IndexRange sel = textArea.getSelection();
        if (sel.getLength() > 0) { deleteRange(sel.getStart(), sel.getEnd()); return; }
        int caret = textArea.getCaretPosition();
        if (caret == 0) return;
        boolean absorbed = batcher.backspace(caret);
        textArea.deleteText(caret - 1, caret);
        textArea.positionCaret(caret - 1);
        if (!absorbed) replica.localDelete(caret);
        updateUndoRedoButtons();
    }

    private void handleForwardDelete() {
        IndexRange sel = textArea.getSelection();
        if (sel.getLength() > 0) { deleteRange(sel.getStart(), sel.getEnd()); return; }
        int caret = textArea.getCaretPosition();
        if (caret >= textArea.getLength()) return;
        replica.localDelete(caret + 1);
        textArea.deleteText(caret, caret + 1);
        updateUndoRedoButtons();
    }

    private void handleCut() {
        IndexRange sel = textArea.getSelection();
        if (sel.getLength() == 0) return;
        ClipboardContent content = new ClipboardContent();
        content.putString(textArea.getText(sel.getStart(), sel.getEnd()));
        Clipboard.getSystemClipboard().setContent(content);
        deleteRange(sel.getStart(), sel.getEnd());
    }

    private void handleWordBackspace() {
        int caret = textArea.getCaretPosition();
        if (caret == 0) return;

        int windowSize = 100;
        int windowStart = Math.max(0, caret - windowSize);
        int start;
        while (true) {
            String window = textArea.getText(windowStart, caret);
            int i = window.length();
            while (i > 0 && Character.isWhitespace(window.charAt(i - 1))) i--;
            while (i > 0 && !Character.isWhitespace(window.charAt(i - 1))) i--;
            if (i > 0 || windowStart == 0) {
                start = windowStart + i;
                break;
            }
            windowStart = Math.max(0, windowStart - windowSize); // word is longer than window, widen and retry
        }
        deleteRange(start, caret);

    }

    private void handlePaste() {
        IndexRange sel = textArea.getSelection();
        if (sel.getLength() > 0) { deleteRange(sel.getStart(), sel.getEnd()); }
        batcher.flush();
        String rawClipboard = Clipboard.getSystemClipboard().getString();
        if (rawClipboard == null || rawClipboard.isEmpty()) return;
        int caret = textArea.getCaretPosition();
        textArea.insertText(caret, rawClipboard);
        int insertedLength = textArea.getCaretPosition() - caret;
        // Extract the clean-sanitized text directly from the TextArea model
        String cleanPastedText = textArea.getText(caret, caret + insertedLength);
        replica.localInsert(caret, cleanPastedText);
        updateUndoRedoButtons();
    }


    private void handleUndo(){
        batcher.flush();
        if (!replica.undoEmpty()) {
            replica.undo();
            textArea.setText(replica.getText());
            updateUndoRedoButtons();
        }
    }
    private void handleRedo(){
        batcher.flush();
        if (!replica.redoEmpty()) {
            replica.redo();
            textArea.setText(replica.getText());
            updateUndoRedoButtons();
        }
    }
    // ---- file ----

    private void saveAsText() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Text File");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Text Files", "*.txt"));
        File file = fileChooser.showSaveDialog(stage);
        if (file == null) return;
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(replica.getText());
        } catch (IOException e) {
            System.err.println("file didn't saved properly" + e.getMessage());
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
