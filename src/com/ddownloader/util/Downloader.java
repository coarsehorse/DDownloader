package com.ddownloader.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;

import com.ddownloader.view.MainLayoutController;

import javafx.application.Platform;

public class Downloader {
	
	private static MainLayoutController mlController;
	private static HashMap<String, String> mimeMap= new HashMap<String, String>();
	private static boolean mimeMapCreated = false;
	
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
			
			boolean gettedException = false;
			
			try {
				in = urlconn.getInputStream();
			}
			catch (IOException e) {
				gettedException = true;
				if (e.getMessage().split(" for URL: ")[0]
						.equals("Server returned HTTP response code: 400")) {
					url = e.getMessage().split(" for URL: ")[1].replaceAll(" ", "%20"); // Replace spaces
					connection = new URL(url);
					urlconn = (HttpURLConnection) connection.openConnection();
					urlconn.setRequestMethod("GET");
					size = urlconn.getContentLengthLong();
					urlconn.connect();
				}
			}
			
			if (gettedException) { // Try again with url fix	
				try {
					in = urlconn.getInputStream();
				}
				catch (IOException e) {
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
			}
			
			/* Find file full path */
			String headerVal = urlconn.getHeaderField("Content-Disposition");
			String fileName = null;
			String fullPath = null;
			
			if (headerVal != null) {
				fileName = headerVal.split("\"")[1];
			}
			else {
				String contentType = urlconn.getContentType();
				
				if (contentType != null) {
					if (!mimeMapCreated) createMimeMap();
					
					String extension = mimeMap.get(contentType);
					String[] arr = url.split("/");
					
					fileName = arr[arr.length - 1] + "." + extension;
				}
				else {
					String[] arr = url.split("/");
					
					fileName = arr[arr.length - 1];
				}
			}
			
			fullPath = savePath + fileName;
			
			/* Set Labels */
			String copyOffileName = fileName;
			Platform.runLater(() -> mlController.downloadingLabel.setText(copyOffileName));
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
					Double speed = new BigDecimal((getted_b / Math.pow(10, 6)))
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
			e.printStackTrace();
		}
	}
	
	/**
	 * Pull actual MIME list from Apache svn and save data HashMap
	 */
	public static void createMimeMap() {
		try {
			String url = "https://svn.apache.org/repos/asf/httpd/httpd/trunk/docs/conf/mime.types";
			URL connection = new URL(url);
			HttpURLConnection urlconn;
			
			urlconn = (HttpURLConnection) connection.openConnection();
			urlconn.setRequestMethod("GET");
			urlconn.connect();
			
			InputStream in = urlconn.getInputStream();
			OutputStream writer = new FileOutputStream("MIME.txt");
			int i = 0;
			byte[] buffer = new byte[10000];
			
			while ((i = in.read(buffer)) > 0) 
				writer.write(buffer, 0, i);
			
			writer.flush();
			writer.close();
			in.close();
			urlconn.disconnect();
			
			File f = new File("MIME.txt");
			BufferedReader br = new BufferedReader(new FileReader(f));
			String temp;
			String[] parts;
			
			while ((temp = br.readLine()) != null) {
				parts = temp.split("	+");
				if (!parts[0].contains("#")) mimeMap.put(parts[0], parts[1].split(" ")[0]);
			}
			
			br.close();
			
			mimeMapCreated = true;
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
}
