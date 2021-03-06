package com.ascargon.rocketshow.dmx;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;

import com.ascargon.rocketshow.Manager;

import ola.OlaClient;
import ola.proto.Ola.UniverseInfoReply;

public class DmxManager {

	final static Logger logger = Logger.getLogger(DmxManager.class);

	final String OLA_URL = "http://localhost:9090/";

	private Manager manager;

	// Cache the channel values and send them each time
	private HashMap<Integer, Integer> channelValues;

	private OlaClient olaClient;

	// Delay sending of the universe because of 2 reasons:
	// - Performance: Sending the whole universe each midi event is not fast
	// enough
	// - Glitches: If we send each event separately, you can see the transitions
	// even if they're not meant to be (e.g. activate two channels at the same
	// time, but sentseparately)
	private Timer sendUniverseTimer;

	private List<String> standardDeviceNames = new ArrayList<String>();

	public DmxManager(Manager manager) {
		this.manager = manager;

		try {
			olaClient = new OlaClient();
		} catch (Exception e) {
			logger.error("Could not initialize OLA DMX client", e);
		}

		reset();

		standardDeviceNames.add("Dummy Device");
		standardDeviceNames.add("ArtNet");
		standardDeviceNames.add("ShowNet");
		standardDeviceNames.add("ESP Net");
		standardDeviceNames.add("SandNet");
		standardDeviceNames.add("Pathport");
		standardDeviceNames.add("E1.31 (DMX over ACN)");
		standardDeviceNames.add("OSC Device");

		initializeUniverse();
	}

	public void reset() {
		// Initialize the universe
		channelValues = new HashMap<Integer, Integer>();

		for (int i = 0; i < 512; i++) {
			channelValues.put(i, 0);
		}

		try {
			sendUniverse();
		} catch (IOException e) {
			logger.error("Could not initialize the DMX universe", e);
		}
	}

	private void sendUniverse() throws IOException {
		if (olaClient == null) {
			return;
		}

		short[] universe = new short[512];

		for (int i = 0; i < channelValues.size(); i++) {
			universe[i] = (short) channelValues.get(i).intValue();
		}

		olaClient.streamDmx(1, universe);
	}

	public void send(int channel, int value) throws IOException {
		logger.trace("Setting DMX channel " + channel + " to value " + value);

		channelValues.put(channel, value);

		// Schedule the specified count of executions in the specified delay
		if (sendUniverseTimer != null) {
			sendUniverseTimer.cancel();
			sendUniverseTimer = null;
		}

		TimerTask timerTask = new TimerTask() {
			@Override
			public void run() {
				try {
					// Send the universe
					sendUniverse();
				} catch (IOException e) {
					logger.error("Could not send the DMX universe", e);
				}

				if (sendUniverseTimer != null) {
					sendUniverseTimer.cancel();
				}

				sendUniverseTimer = null;
			}
		};

		sendUniverseTimer = new Timer();
		sendUniverseTimer.schedule(timerTask, manager.getSettings().getDmxSendDelayMillis());
	}

	private boolean isStandardDevice(String name) {
		for (String standardDeviceName : standardDeviceNames) {
			if (name.startsWith(standardDeviceName)) {
				return true;
			}
		}

		return false;
	}

	private String getConnectedPort() throws ClientProtocolException, IOException {
		// Query the OLA JSON API for all ports
		HttpClient httpClient;
		RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(5000).build();
		httpClient = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build();
		HttpGet httpGet = new HttpGet(OLA_URL + "json/get_ports");
		HttpResponse response = httpClient.execute(httpGet);

		// Parse the resulting JSON ports
		ObjectMapper mapper = new ObjectMapper();
		OlaPort[] olaPortList = mapper.readValue(response.getEntity().getContent(), OlaPort[].class);

		// Search for any non-default ports (e.g. a connected DMX USB device)
		for (OlaPort olaPort : olaPortList) {
			if (olaPort.isOutput() && !isStandardDevice(olaPort.getDevice())) {
				return olaPort.getId();
			}
		}

		return null;
	}

	private void createUniverse(int universeId, String name, String portId)
			throws ClientProtocolException, IOException {

		logger.debug("Adding new universe with port '" + portId + "'...");

		HttpClient httpClient;

		RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(5000).build();
		httpClient = HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build();

		HttpPost httpPost = new HttpPost(OLA_URL + "new_universe");

		List<NameValuePair> data = new ArrayList<NameValuePair>(3);

		data.add(new BasicNameValuePair("id", String.valueOf(universeId)));
		data.add(new BasicNameValuePair("name", name));
		data.add(new BasicNameValuePair("add_ports", portId));

		httpPost.setEntity(new UrlEncodedFormEntity(data, "UTF-8"));
		httpPost.addHeader("Content-Type", "application/x-www-form-urlencoded");

		HttpResponse response = httpClient.execute(httpPost);

		// Read the response. The POST connection will not be released otherwise
		BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));

		String line = "";

		while ((line = bufferedReader.readLine()) != null) {
			logger.debug("Response from OLA POST: " + line);
		}
	}

	private void initializeUniverse() {
		if (olaClient == null) {
			// OLA client is not connected
			return;
		}

		UniverseInfoReply universeInfoReply = olaClient.getUniverseList();

		if (universeInfoReply != null) {
			if (universeInfoReply.getUniverseCount() > 0) {
				// At least one universe is already initialized
				return;
			}
		}

		logger.debug("Initializing DMX universe on OLA...");

		String portId = null;

		try {
			portId = getConnectedPort();
		} catch (Exception e) {
			logger.error("Could not get a output port", e);
		}

		if (portId == null || portId.length() == 0) {
			// No connected DMX device-port found
			logger.trace("No connected DMX output device found");
			return;
		}

		// Create a new universe with the found device
		try {
			// Create the port with the device-id, "O" for output and the port
			// ID
			createUniverse(1, "Standard", portId);
		} catch (Exception e) {
			logger.error("Could not create a new universe on OLA", e);
		}

		logger.debug("DMX universe on OLA initialized");
	}

	public void close() {
		if (sendUniverseTimer != null) {
			sendUniverseTimer.cancel();
			sendUniverseTimer = null;
		}
	}

}
