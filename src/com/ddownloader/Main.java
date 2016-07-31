package com.ddownloader;

import com.ddownloader.view.MainLayoutController;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;

public class Main extends Application {
	
	public static ArrayList<Thread> onlineThreads = new ArrayList<Thread>();
	public static ArrayList<OutputStream> notClosedWriters = new ArrayList<OutputStream>();
	
	private Stage primaryStage;
	
	@Override
	public void start(Stage primaryStage) {
		this.primaryStage = primaryStage;
		this.primaryStage.setTitle("DDownloader");
		this.primaryStage.getIcons().add(new Image("file:resources/img/main-icon.png"));
		this.primaryStage.setResizable(false);
		
		loadMainLayout();
	}
	
	@Override
	public void stop() {
    	if (!onlineThreads.isEmpty())
    		for (Thread t : onlineThreads)
    			t.stop();	
    	onlineThreads.clear();
    	
    	if (!notClosedWriters.isEmpty()) {
    		for (OutputStream w : notClosedWriters) {
    			try {
					w.flush();
					w.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
    		}
    	}
    	notClosedWriters.clear();
	}
	
	public static void main(String[] args) {
		launch(args);
	}
	
	public void loadMainLayout() {
		try {
			FXMLLoader loader = new FXMLLoader();
			
			loader.setLocation(Main.class.getResource("view/MainLayout.fxml"));
			
			AnchorPane anchorPane = (AnchorPane) loader.load();
			
			Scene scene = new Scene(anchorPane);
			
			primaryStage.setScene(scene);
			
			MainLayoutController controller = loader.getController();
			
			controller.setMainLink(this);
			
			primaryStage.show();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public Stage getPrimaryStage() {
		return primaryStage;
	}
}
