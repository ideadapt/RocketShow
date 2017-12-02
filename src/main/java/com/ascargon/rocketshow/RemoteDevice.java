package com.ascargon.rocketshow;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;

/**
 * Defines a remote RocketShow device to be triggered by this one.
 *
 * @author Moritz A. Vieli
 */
@XmlRootElement
public class RemoteDevice {

	final static Logger logger = Logger.getLogger(RemoteDevice.class);

	private HttpClient httpClient;

	// The id of the remote device
	private int id;

	// The name of the remote device
	private String name;

	// The host address (IP or hostname) of the remote device
	private String host;
	
	// Synchronize song plays/stops with the local device
	private boolean synchronize;

	public RemoteDevice() {
		RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(5000).build();
		httpClient = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build();
	}

	public void doPost(String apiUrl) throws ClientProtocolException, IOException {
		// Build the url for the post request
		String url = "http://" + host + "/api/" + apiUrl;

		HttpPost httpPost = new HttpPost(url);
		HttpResponse response = httpClient.execute(httpPost);

		// Read the response. The POST connection will not be released otherwise
		BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));

		String line = "";

		while ((line = rd.readLine()) != null) {
			logger.debug("Response from remote device POST: " + line);
		}

		if (response.getStatusLine().getStatusCode() != 200) {
			logger.error("Could not execute action on remote device with url '" + url + "'. Reason: '"
					+ response.getStatusLine().getReasonPhrase() + "'. Body: "
					+ EntityUtils.toString(response.getEntity()));
		}
	}
	
	public void play() throws ClientProtocolException, IOException {
		doPost("transport/play");
	}

	public void pause() throws ClientProtocolException, IOException {
		doPost("transport/pause");
	}

	public void stop() throws ClientProtocolException, IOException {
		doPost("transport/stop");
	}
	
	public void togglePlay() throws ClientProtocolException, IOException {
		doPost("transport/toggle-play");
	}
	
	public void resume() throws ClientProtocolException, IOException {
		doPost("transport/resume");
	}
	
	public void setNextSong() throws ClientProtocolException, IOException {
		doPost("transport/next-song");
	}
	
	public void setPreviousSong() throws ClientProtocolException, IOException {
		doPost("transport/next-song");
	}
	
	public void setSongIndex(int songIndex) throws ClientProtocolException, IOException {
		doPost("transport/set-song-index?index=" + songIndex);
	}
	
	@XmlElement
	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	@XmlElement
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@XmlElement
	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	@XmlElement
	public boolean isSynchronize() {
		return synchronize;
	}

	public void setSynchronize(boolean synchronize) {
		this.synchronize = synchronize;
	}

}
