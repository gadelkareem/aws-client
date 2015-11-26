package com.gadelkareem.awsclient.application;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/application.fxml"));
        primaryStage.setTitle("AWS Client");
        primaryStage.setScene(new Scene(root, 1200, 800));
        primaryStage.show();
    }
}
