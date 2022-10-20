package io.github.jack1424.realtimeweather;

import com.github.prominence.openweathermap.api.OpenWeatherMapClient;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import javax.net.ssl.HttpsURLConnection;
import java.net.URL;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.Calendar;
import java.util.Objects;
import java.util.TimeZone;
import java.util.logging.Logger;

public final class RealTimeWeather extends JavaPlugin {
	/*
	TODO:
	- Check configuration error handling
	- Check debug outputs
	- Make logs look better (colors and stuff)
	- Override default minecraft time/weather commands (when applicable)
	 */

	private Logger logger;
	private String zipCode, countryCode;
	private ZoneId timezone;
	private boolean timeEnabled, weatherEnabled, debug;

	@Override
	public void onEnable() {
		logger = getLogger();
		logger.info("Starting...");

		saveDefaultConfig();

		debug = getConfig().getBoolean("Debug");

		timeEnabled = getConfig().getBoolean("SyncTime");
		if (timeEnabled)
			setupTime();

		weatherEnabled = getConfig().getBoolean("SyncWeather");
		if (weatherEnabled)
			setupWeather();

		logger.info("Started!");
	}

	@Override
	public void onDisable() {
		for (World world : getServer().getWorlds())
			if (world.getEnvironment().equals(World.Environment.NORMAL)) {
				debug("Re-enabling normal daylight and weather cycles...");

				world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
				world.setGameRule(GameRule.DO_WEATHER_CYCLE, true);
			}

		logger.info("Stopping...");
	}

	private void setupTime() {
		try {
			timezone = ZoneId.of(Objects.requireNonNull(getConfig().getString("Timezone")));
		} catch (NullPointerException|ZoneRulesException e) {
			logger.severe("Error loading timezone. Check that the values in your configuration file are valid.");
			debug(e.getMessage());
			logger.severe("Disabling time sync...");

			timeEnabled = false;
			return;
		}

		debug("Enabling time zone sync (every second)");
		debug("Syncing time with " + timezone.toString());

		for (World world : getServer().getWorlds())
			if (world.getEnvironment().equals(World.Environment.NORMAL))
				world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);

		getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
			if (timeEnabled) {
				Calendar cal = Calendar.getInstance(TimeZone.getTimeZone(timezone));
				for (World world : getServer().getWorlds())
					if (world.getEnvironment().equals(World.Environment.NORMAL))
						world.setTime((1000 * cal.get(Calendar.HOUR_OF_DAY)) + (16 * cal.get(Calendar.MINUTE)) - 6000); // TODO: Time is one minute behind
			}
		}, 0L, 20L); // TODO: Does this really need to update every second?
	}

	private void setupWeather() {
		String apiKey = getConfig().getString("APIKey");
		zipCode = getConfig().getString("ZipCode");
		countryCode = getConfig().getString("CountryCode");

		try {
			HttpsURLConnection con = (HttpsURLConnection) new URL(String.format("https://api.openweathermap.org/geo/1.0/zip?zip=%s,%s&appid=%s", zipCode, countryCode, apiKey)).openConnection();
			con.setRequestMethod("GET");
			con.connect();

			int response = con.getResponseCode();
			if (response > 499) {
				logger.severe("There was a server error when requesting weather information. Please try again later");
				throw new Exception("Server error");
			}
			else if (response > 399) {
				String message = "Error when getting weather information:";
				switch (response) {
					case 401: logger.severe(message + "API key incorrect");
					case 404: logger.severe(message + "Zip/Country code incorrect");
					default: logger.severe("Unknown error");
				}
				logger.severe("Please check that the values set in the config file are correct");

				throw new Exception("Configuration error");
			}
		} catch (Exception e) {
			debug(e.getMessage());
			logger.severe("Disabling weather sync...");

			weatherEnabled = false;
			return;
		}

		OpenWeatherMapClient owm = new OpenWeatherMapClient(apiKey);

		getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
			debug("Syncing weather...");

			int weatherId = 0;
			try {
				weatherId = owm.currentWeather().single().byZipCodeAndCountry(zipCode, countryCode).retrieve().asJava().getWeatherState().getId();
			} catch (Exception e) {
				logger.severe("There was an error when attempting to get weather information");
				debug(e.getMessage());
			}

			while(weatherId >= 10)
				weatherId /= 10;

			boolean rain = weatherId == 2 || weatherId == 3 || weatherId == 5 || weatherId == 6;
			boolean thunder = weatherId == 2;

			debug("Setting weather (Rain: " + rain + ", Thunder: " + thunder + ")...");
			for (World world : getServer().getWorlds())
				if (world.getEnvironment().equals(World.Environment.NORMAL)) {
					world.setStorm(rain);
					world.setThundering(thunder);
				}
		}, 0L, 6000L);
	}

	private void debug(String message) {
		if (debug) {
			logger.info(message);
		}
	}
}
