package com.ddownloader.view;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import com.ddownloader.Main;
import com.ddownloader.util.Downloader;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.FileChooser;
import javafx.stage.DirectoryChooser;

public class MainLayoutController {
	@FXML
	public Label downloadedLabel;
	@FXML
	public Label downloadingLabel;
	@FXML
	public Label sizeLabel;
	@FXML
	public Label speedLabel;
	
	@FXML
	private TextField downloadPath;
	@FXML
	private TextField savePath;
	
	@FXML
	public ProgressBar downloadingPB;
	@FXML
	public ProgressBar downloadedPB;

	public int quantity = 0;
	public int doneQuantity = 0;
	
	private Main main;
	public boolean downloadPaused = true;
	
	public MainLayoutController() {
		// Stub
	}
	
	public void setMainLink(Main main) {
		this.main = main;
	}
	
	@FXML
	private void pauseHandler() {
		downloadPaused = true;
	}
	
	@FXML
	private void resumeHandler() {
		downloadPaused = false;
	}
	
	@FXML
	private void stopHandler() {
		main.stop();
		
		downloadingLabel.setText("");
		downloadedLabel.setText("0/0");
		sizeLabel.setText("-");
		speedLabel.setText("-");
		downloadedPB.setProgress(0.0);
		updateDownloadingPB(100, 0); // Else downloading method will re-update it
	}
	
	@FXML
	private void fileListBrowseHandler() {
		FileChooser fileChooser = new FileChooser();
		FileChooser.ExtensionFilter
			extFilter0 = new FileChooser.ExtensionFilter("Text file with links", "*.txt"),
			extFilter1 = new FileChooser.ExtensionFilter("Text file with links", "*.*");
		
		fileChooser.getExtensionFilters().addAll(extFilter0, extFilter1);
		
		File fileList = fileChooser.showOpenDialog(main.getPrimaryStage());
		
		if (fileList != null)
			downloadPath.setText(fileList.getAbsolutePath());
	}
	
	@FXML
	private void savePathBrowseHandler() {
		DirectoryChooser dirChooser = new DirectoryChooser();
		File saveDir = dirChooser.showDialog(main.getPrimaryStage());
		
		if (saveDir != null)
			savePath.setText(saveDir.getAbsolutePath());
	}
	
	@FXML
	private void downloadHandler() {
		/* Stop possible download */
		main.stop();
		
		/* Check readiness */
		String errorMessage = "";
		
		if (downloadPath.getText().equals(""))
			errorMessage += "\"File list or URL\" field is empty!\n";
		if (savePath.getText().equals(""))
			errorMessage += "\"Save Path\" field is empty!\n";
		if (!errorMessage.equals("")) {
			throwAlert(errorMessage);
			return;
		}
		
		/* Get saveDir */
		String temp = savePath.getText().replaceAll("\\\\", "/"); // If user/FileMan forgot '/'
		String saveDir = temp += temp.toCharArray()[temp.length() - 1] == '/' ? "" : "/";
		
		doneQuantity = 0;
		
		/* DO simple download */
		if (downloadPath.getText().split("/")[0].equals("http:") ||
			downloadPath.getText().split("/")[0].equals("https:"))
		{	
			String url = downloadPath.getText();

			quantity = 1;
			startDownload(url, saveDir);
		}
		/* DO download from file list */
		else {
			/* Pull data from textEdits */
			String fileList = downloadPath.getText().replaceAll("\\\\", "/");
			
			/* Get URLs */
			ArrayList<String> URLs = new ArrayList<>();
			
			temp = null;
			
			try {
				BufferedReader reader = new BufferedReader(new FileReader(fileList));
				
				while((temp = reader.readLine()) != null && !temp.isEmpty())
					URLs.add(temp);

				reader.close();
			}
			catch (IOException e) {
				throwAlert(e.getMessage());
			}
			
			quantity = URLs.size();
			
			if (quantity < 1) {
				throwAlert("Unsuitable file!\nExpected file format: one line - one URL");
				return;
			}
			
			/* Send each of URLs to download method */
			Task<Void> downloadAll = new Task<Void>() {
				@Override
				protected Void call() throws Exception {
					int check = -1;
					
					for (String str : URLs) {
						check = doneQuantity;
						startDownload(str, saveDir);
						while (check == doneQuantity) {
							Thread.sleep(1000);
						}
					}		
					Main.onlineThreads.remove(Thread.currentThread());
					
					return null;
				}
			};
			
			Thread tempThread = new Thread(downloadAll);
			Main.onlineThreads.add(tempThread);
			tempThread.start();
		}
	}
	
	public void throwAlert(String alertText) {
		Alert alert = new Alert(AlertType.WARNING);
		
    	alert.initOwner(main.getPrimaryStage());
    	alert.setTitle("Warning");
    	alert.setContentText(alertText);
    	alert.showAndWait();
	}
	
	public void updateDownloadingPB(long size, long i_sum) {
		Platform.runLater(() -> downloadingPB.setProgress((double)i_sum / size));
	}
	
	public void updateDownloadedPB(double progress) {
		Platform.runLater(() -> downloadedPB.setProgress(progress));
	}

	private void startDownload(String URL, String PATH) {
		final int buffer = 10000; // On my hardware no more makes sense
		
		Downloader.setController(this);
		
		Task<Void> downloadTask = new Task<Void>() {
			
			@Override
			protected Void call() throws Exception {
				Downloader.downloadFile(URL, PATH, buffer);
				Main.onlineThreads.remove(Thread.currentThread());
				
				return null;
			}
			
		};
		
		downloadPaused = false;
		
		Thread downlThread = new Thread(downloadTask);
		
		Main.onlineThreads.add(downlThread);
		downlThread.start();
	}
}