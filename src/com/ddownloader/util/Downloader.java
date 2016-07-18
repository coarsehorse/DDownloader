package com.ddownloader.util;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;

import com.ddownloader.view.MainLayoutController;

import javafx.application.Platform;

public class Downloader {
	private static MainLayoutController mlController;
	
	public static void setController(MainLayoutController c) {
		mlController = c;
	}
	
	public static void downloadFile(String url, String savePath, int buffSize) {
		try {
			/* Get connection */
			URL connection = new URL(url);
			HttpURLConnection urlconn;
			long size;
			
			urlconn = (HttpURLConnection) connection.openConnection();
			urlconn.setRequestMethod("GET");
			size = urlconn.getContentLengthLong();
			urlconn.connect();
			
			/* Set input stream */
			InputStream in = null;
			
			try {
				in = urlconn.getInputStream();
			}
			catch (IOException e) { // If something goes wrong
				Platform.runLater(() -> mlController.throwAlert(e.getMessage()));
				
				mlController.doneQuantity++;
				Platform.runLater(() ->
					mlController.downloadedLabel.setText(mlController.doneQuantity + "/" + mlController.quantity)
				);
				Platform.runLater(() ->
					mlController.updateDownloadedPB((1.0 / mlController.quantity) * mlController.doneQuantity)
				);
				
				Platform.runLater(() -> mlController.downloadingLabel.setText("Done"));
				
				Platform.runLater(() -> mlController.sizeLabel.setText("-"));
				Platform.runLater(() -> mlController.speedLabel.setText("-"));
				
				return;
			}
			
			/* Find file full path */
			String[] tempArr = url.split("/");
			
			String fullPath = savePath + tempArr[tempArr.length - 1];
			
			/* Set Labels */
			Platform.runLater(() -> mlController.downloadingLabel.setText(tempArr[tempArr.length - 1]));
			Platform.runLater(() -> mlController.downloadingPB.setProgress(0.0));
			
			Platform.runLater(() ->
				mlController.downloadedLabel.setText(mlController.doneQuantity + "/" + mlController.quantity)
			);
			if (mlController.doneQuantity == 0) Platform.runLater(() -> mlController.downloadedPB.setProgress(0.0));
			
			Double dTemp = new BigDecimal(size / Math.pow(10, 6))
					.setScale(3, BigDecimal.ROUND_HALF_UP)
					.doubleValue();
			Platform.runLater(() -> mlController.sizeLabel.setText(dTemp + " Mb"));
			
			/* Set write stream */
			OutputStream writer = new FileOutputStream(fullPath);
			byte buffer[] = new byte[buffSize]; // Max bytes per one reception
			
			/* Download */
			int i = 0;
			long i_sum = 0;
			long delta_t = System.nanoTime();
			double getted_b = 0.0;
			
			while ((i = in.read(buffer)) > 0) {
				writer.write(buffer, 0, i);
				getted_b += i;
				i_sum += i;
				
				mlController.updateDownloadingPB(size, i_sum); // with method because lambda needs final values
				
				if ((System.nanoTime() - delta_t) >= 1E9) { // If the second was over
					Double speed = new BigDecimal(getted_b / Math.pow(10, 6))
							.setScale(3, BigDecimal.ROUND_HALF_UP)
							.doubleValue();

					Platform.runLater(() -> mlController.speedLabel.setText(speed + " Mb/s"));
					
					delta_t = System.nanoTime(); // Set to zero
					getted_b = 0.0;
				}
				if (size == i_sum) { // ==> Download is complete
					mlController.doneQuantity++;
					
					Platform.runLater(() ->
						mlController.downloadedLabel.setText(mlController.doneQuantity + "/" + mlController.quantity)
					);
					Platform.runLater(() ->
						mlController.updateDownloadedPB((1.0 / mlController.quantity) * mlController.doneQuantity)
					);
					
					Platform.runLater(() -> mlController.downloadingLabel.setText("Done"));
					Platform.runLater(() -> mlController.sizeLabel.setText("-"));
					Platform.runLater(() -> mlController.speedLabel.setText("-"));
				}
			}
			
			/* Cleaning */
			writer.flush();
			writer.close();
			in.close();
		} catch (IOException e) {
			System.out.println(e);
		}
	}
}
