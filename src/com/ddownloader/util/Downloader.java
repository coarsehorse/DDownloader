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
import java.util.ArrayList;
import java.util.HashMap;

import com.ddownloader.Main;
import com.ddownloader.view.MainLayoutController;

import javafx.application.Platform;

abstract public class Downloader {
	
	private static MainLayoutController mlController;
	
	private static HashMap<String, String> mimeMap = new HashMap<String, String>();
	private static ArrayList<String> mimeTypes = new ArrayList<String>();
	private static boolean mimeMapCreated = false;
	
	/**
	 * Controller sets link on itself to give access to the UI
	 * @param c
	 * link on itself
	 */
	public static void setController(MainLayoutController c) {
		mlController = c;
	}
	
	/**
	 * Downloading the file with UI updating during the downloading.
	 * Method supports pausing and resuming.
	 * @param url
	 * URL of file to download
	 * @param savePath
	 * the directory in which the file will be downloaded(with last symbol "/")
	 * @param buffSize
	 * the maximum number of bytes that can be taken at a time 
	 */
	public static void downloadFile(String url, String savePath, int buffSize) {
		try {
			/* Get connection */
			URL connection = new URL(url);
			HttpURLConnection urlconn;
			long fileSize, downloadedBytes = 0;
			
			urlconn = (HttpURLConnection) connection.openConnection();
			urlconn.setRequestMethod("GET");
			fileSize = urlconn.getContentLengthLong();
			urlconn.connect();
			
			/* Set input stream */
			InputStream in = null;
			
			boolean gettedException = false;
			
			try {
				in = urlconn.getInputStream();
			}
			catch (IOException e) { // Fix url(replace spaces with %20)
				gettedException = true;
				if (e.getMessage().split(" for URL: ")[0]
						.equals("Server returned HTTP response code: 400")) {
					url = e.getMessage().split(" for URL: ")[1].replaceAll(" ", "%20"); // Replace spaces
					connection = new URL(url);
					urlconn.disconnect();
					urlconn = (HttpURLConnection) connection.openConnection();
					urlconn.setRequestMethod("GET");
					fileSize = urlconn.getContentLengthLong();
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
			
			/* Find file name and create full path */
			String fileName = null;
			String fullPath = null;
			
			String contentDispos = urlconn.getHeaderField("Content-Disposition"); // 1. Try to extract done name
			
			if (contentDispos != null) { // If name is in header
				fileName = contentDispos.split("\"")[1];
				// TODO improve Regex (filename=\"The.Walking.Dead.S06E01.1080p.HDTV.FOX HD.ts\")
			}
			else { // 2. Try to construct name from url
				if (!mimeMapCreated) {
					Platform.runLater(() ->
						mlController.downloadingLabel.setText("Pull MIME types from Apache svn..."));
					createMimeMap();
				}
				
				String[] tArr = url.split("/"); // Separate name + extension(if is) from url
				String possibleName = tArr[tArr.length - 1];
				
				tArr = possibleName.split("[.]"); // Separate extension
				
				if (mimeTypes.contains(tArr[tArr.length - 1])) { // Check extension
					fileName = possibleName;
				}
				else { // Try to find extension by content-type
					String contentType = urlconn.getContentType();
					
					tArr = url.split("/");
					
					if (contentType != null) { // If content-type is in header
						String extension = mimeMap.get(contentType);
						
						if (extension != null) // And extension registered in Apache MIME types
							fileName = tArr[tArr.length - 1] + "." + extension;
						else
							fileName = tArr[tArr.length - 1] + "." + "noformat";
					}
					else // Huh, I did everything I could(
						fileName = tArr[tArr.length - 1] + "." + "noformat";
				}	
			}

			fullPath = savePath + fileName;
			
			/* Set Labels */
			String copyOfFileName = fileName;
			Platform.runLater(() -> mlController.downloadingLabel.setText(copyOfFileName));
			Platform.runLater(() -> mlController.downloadingPB.setProgress(0.0));
			
			Platform.runLater(() ->
				mlController.downloadedLabel.setText(mlController.doneQuantity + "/" + mlController.quantity)
			);
			if (mlController.doneQuantity == 0) Platform.runLater(() -> mlController.downloadedPB.setProgress(0.0));
			
			Double dTemp = new BigDecimal(fileSize / Math.pow(10, 6))
					.setScale(3, BigDecimal.ROUND_HALF_UP)
					.doubleValue();
			Platform.runLater(() -> mlController.sizeLabel.setText(dTemp + " Mb"));
			
			/* Set write stream */
			OutputStream writer = new FileOutputStream(fullPath);
			
			Main.notClosedWriters.add(writer);
			
			byte buffer[] = new byte[buffSize]; // Max bytes per one reception
			
			/* Download */
			int i = 0;
			long delta_t = System.nanoTime();
			double second_waiter = 0.0;
			
			while ((i = in.read(buffer)) > 0) {
				writer.write(buffer, 0, i);
				second_waiter += i;
				downloadedBytes += i;
				
				mlController.updateDownloadingPB(fileSize, downloadedBytes); // With method because lambda needs final values
				
				if ((System.nanoTime() - delta_t) >= 1E9) { // If the second was over
					Double speed = new BigDecimal((second_waiter / Math.pow(10, 6)))
							.setScale(3, BigDecimal.ROUND_HALF_UP)
							.doubleValue();

					Platform.runLater(() -> mlController.speedLabel.setText(speed + " Mb/s"));
					
					delta_t = System.nanoTime(); // Set to zero
					second_waiter = 0.0;
				}
				if (downloadedBytes == fileSize) { // If download is complete
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
				while (mlController.downloadPaused) { // If download has paused
					try {
						Thread.sleep(500);
					} catch (InterruptedException e) {
						Platform.runLater(() -> mlController.throwAlert(e.getMessage()));
					}
				}
			}
			
			/* Cleaning */
			Main.notClosedWriters.remove(writer);
			writer.flush();
			writer.close();
			in.close();
			urlconn.disconnect();
		} catch (IOException e) {
			Platform.runLater(() -> mlController.throwAlert(e.getMessage()));
			mlController.doneQuantity++;
		}
	}
	
	/**
	 * Pull actual MIME list from Apache svn and save in HashMap
	 * @author 
	 * heroys6
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
			
			Main.notClosedWriters.add(writer);
			
			int i = 0;
			byte[] buffer = new byte[10000];
			
			while ((i = in.read(buffer)) > 0) 
				writer.write(buffer, 0, i);
			
			Main.notClosedWriters.remove(writer);
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
				if (!parts[0].contains("#")) { // Add content-type if not commented
					mimeMap.put(parts[0], parts[1].split(" ")[0]);
					for (String s : parts[1].split(" ")) // Consider all possible extensions
						mimeTypes.add(s);
				}
			}
			
			mimeMapCreated = true;
			
			br.close();
			f.delete();
		}
		catch (Exception e) {
			Platform.runLater(() -> mlController.throwAlert(e.getMessage()));
		}
	}
}
