package com.gadelkareem.awsclient.application;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Screen;
import javafx.stage.Stage;

public class Main extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            Rectangle2D primaryScreenBounds = Screen.getPrimary().getVisualBounds();
            Parent root = FXMLLoader.load(getClass().getResource("/application.fxml"));
            primaryStage.setTitle("AWS Client v1.0.0");
            primaryStage.setScene(new Scene(root, primaryScreenBounds.getWidth() / 1.5, primaryScreenBounds.getHeight() / 1.5));
            primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/AwsClient.png")));
            primaryStage.show();
        } catch (Exception e) {
            System.err.println("Error loading AwsClient!");
            e.printStackTrace();
        }
    }
}
