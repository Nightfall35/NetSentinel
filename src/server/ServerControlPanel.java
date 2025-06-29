package server;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;

public class ServerControlPanel extends Application {

    private TextArea logArea;
    private ListView<String> clientList;
    private TextField broadcastField;
    private static ServerControlPanel instance;

    @Override
    public void start(Stage primaryStage) {
        showSplash(primaryStage);  
    }

    private void showSplash(Stage stage) {
        TextArea splashLog = new TextArea();
        splashLog.setEditable(false);
        splashLog.setStyle("-fx-control-inner-background: black; -fx-font-family: 'Consolas'; -fx-text-fill: #00FF90; -fx-font-size: 14;");
        splashLog.setPrefSize(600, 400);

        VBox splashLayout = new VBox(splashLog);
        splashLayout.setPadding(new Insets(15));
        Scene splashScene = new Scene(splashLayout);
        stage.setScene(splashScene);
        stage.setTitle("🛠 Booting Umbra Node...");
        stage.show();

        List<String> bootLines = List.of(
            "[*] Initializing JVM modules...",
            "[*] Loading JavaFX core components...",
            "[*] Binding socket interfaces...",
            "[*] Validating command channel...",
            "[✓] Secure uplink established.",
            "[*] Launching Server Control Panel..."
        );

        final int[] lineIndex = {0};
        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(800), event -> {
            if (lineIndex[0] < bootLines.size()) {
                splashLog.appendText(bootLines.get(lineIndex[0]++) + "\n");
            } else {
                stage.hide();
                launchMainUI(new Stage());
            }
        }));
        timeline.setCycleCount(bootLines.size() + 1);
        timeline.play();
    }

    private void launchMainUI(Stage primaryStage) {
        primaryStage.setTitle("🛡 UMBRA NODE: Command Interface");

        Text header = new Text("UMBRA OS - NODE CONSOLE");
        header.setFill(Color.web("#00FFC6"));
        header.setFont(Font.font("Consolas", FontWeight.BOLD, 24));

        Label statusLabel = new Label("🧠 Command Feed Online...");
        statusLabel.setTextFill(Color.LIMEGREEN);
        statusLabel.setFont(Font.font("JetBrains Mono", FontWeight.NORMAL, 14));

        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setPrefHeight(280);
        logArea.setStyle("-fx-control-inner-background: #1e1e1e; -fx-text-fill: #00FF90; -fx-font-family: 'Consolas';");

        clientList = new ListView<>();
        clientList.setPrefHeight(180);
        clientList.setStyle("-fx-control-inner-background: #101010; -fx-font-family: 'Courier New'; -fx-text-fill: #00faff;");

        broadcastField = new TextField();
        broadcastField.setPromptText("Enter message to broadcast...");
        broadcastField.setStyle("-fx-background-color: #2e2e2e; -fx-text-fill: #00ffcc;");

        Button sendBtn = new Button("📢 Broadcast");
        sendBtn.setStyle("-fx-background-color: #00FF90; -fx-text-fill: black;");
        sendBtn.setOnAction(e -> {
            String msg = broadcastField.getText();
            if (!msg.isEmpty()) {
                ServerMain.broadcastMessage(msg);
                appendLog("📢 Broadcasted: " + msg);
                broadcastField.clear();
            }
        });

        Button kickBtn = new Button("⚠ Kick Selected");
        kickBtn.setStyle("-fx-background-color: #ff4444; -fx-text-fill: white;");
        kickBtn.setOnAction(e -> {
            String selected = clientList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                ServerMain.disconnectClient(selected);
                appendLog("❌ Kicked: " + selected);
            }
        });

        VBox layout = new VBox(10);
        layout.setPadding(new Insets(12));
        layout.getChildren().addAll(
            header,
            statusLabel,
            new Label("📡 Live Server Logs:"),
            logArea,
            new Label("💀 Connected Clients:"),
            clientList,
            broadcastField,
            new HBox(10, sendBtn, kickBtn)
        );

        ((HBox) layout.getChildren().get(layout.getChildren().size() - 1)).setAlignment(Pos.CENTER);

        Scene scene = new Scene(layout, 620, 640);
        scene.setFill(Color.BLACK);
        primaryStage.setScene(scene);
        primaryStage.show();

        startClientPolling();
    }

    private void startClientPolling() {
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                Platform.runLater(() -> updateClientList());
            }
        }, 0, 2000);
    }

    private void updateClientList() {
        List<String> clients = ServerMain.getActiveClientUserNames();
        clientList.getItems().setAll(clients);
    }

    public void appendLog(String msg) {
        logArea.appendText("[" + java.time.LocalTime.now().withNano(0) + "] " + msg + "\n");
    }

    public static void logExternally(String msg) {
        if (instance != null) {
            instance.appendLog(msg);
        }
    }

    public ServerControlPanel() {
        instance = this;
    }

    public static void main(String[] args) {
        launch(args);
    }
}

