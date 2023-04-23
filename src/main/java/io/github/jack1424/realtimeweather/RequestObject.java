package io.github.jack1424.realtimeweather;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.net.URL;
import java.util.Scanner;

public class RequestObject {
	private final int responseCode;
	private boolean rain = false, thunder = false;

	public RequestObject(String apiKey, String lat, String lon) throws IOException, ParseException {
		URL url = new URL(String.format("https://api.openweathermap.org/data/2.5/weather?lat=%s&lon=%s&appid=%s", lat, lon, apiKey));

		HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
		con.setRequestMethod("GET");
		con.connect();
		responseCode = con.getResponseCode();
		if (responseCode > 399)
			return;

		Scanner scanner = new Scanner(url.openStream());
		StringBuilder data = new StringBuilder();
		while (scanner.hasNext()) {
			data.append(scanner.nextLine());
		}
		scanner.close();

		JSONArray conditions = (JSONArray) ((JSONObject) new JSONParser().parse(data.toString())).get("weather");

		for (Object rawCondition : conditions) {
			int id = Integer.parseInt(String.valueOf(((JSONObject) rawCondition).get("id")));

			while (id >= 10)
				id /= 10;

			if (!rain)
				rain = id == 2 || id == 3 || id == 5 || id == 6;
			if (!thunder)
				thunder = id == 2;
		}
	}

	public int getResponseCode() {
		return responseCode;
	}

	public boolean isRaining() {
		return rain;
	}

	public boolean isThundering() {
		return thunder;
	}
}
