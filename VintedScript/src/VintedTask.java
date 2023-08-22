import java.awt.Color;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.sound.sampled.LineUnavailableException;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.google.common.base.Stopwatch;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import net.dongliu.requests.BasicAuth;
import net.dongliu.requests.Proxies;
import net.dongliu.requests.RawResponse;
import net.dongliu.requests.Requests;
import net.dongliu.requests.Session;

//String Modes: Preload, Normal, Register
public class VintedTask extends Thread {

	private HashSet<String> loadedProducts = new HashSet<String>();

	private String ip;
	private int port;

	private long retryDelayMS;
	private String url;
	private boolean useProxy = false;
	private String webhookUrl;

	private static DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
	private static LocalDateTime now = LocalDateTime.now();

	public VintedTask(String url, long retryDelayMS, String useProxy, String webhookUrl) {

		this.url = url;
		this.retryDelayMS = retryDelayMS;

		if (useProxy.equals("y")) {
			this.useProxy = true;
		}

		this.webhookUrl = webhookUrl;

	}

	public void run() {
		System.out.println("[VINTED] - [" + dtf.format(now.now()) + "] - [" + url + "] - Starting Task...");

		while (true) {
			try {
				setProxy();
				monitorProducts();
				Thread.sleep(retryDelayMS);

			} catch (Exception e) {
				e.printStackTrace();
			}
		}

	}

	public void monitorProducts() throws InterruptedException, IOException, LineUnavailableException {
		Session session = Requests.session();

		System.out.println("[VINTED] - [" + dtf.format(now.now()) + "] - [" + url + "] - Monitoring Products...");
		Map<String, Object> request = new HashMap<>();
		request.put("Authority", "www.vinted.de");
		request.put("Cache-Control", "max-age=0");
		request.put("Sec-Ch-Ua", "\" Not;A Brand\";v=\"99\", \"Google Chrome\";v=\"97\", \"Chromium\";v=\"97\"");
		request.put("Sec-Ch-Ua-Mobile", "?0");
		request.put("Sec-Ch-Ua-Platform", "\"Windows\"");
		request.put("Upgrade-Insecure-Requests", "1");
		request.put("User-Agent",
				"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/97.0.4692.99 Safari/537.36");
		request.put("Accept",
				"text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9");
		request.put("Sec-Fetch-Site", "none");
		request.put("Sec-Fetch-Mode", "navigate");
		request.put("Sec-Fetch-User", "?1");
		request.put("Sec-Fetch-Dest", "document");
		request.put("Accept-Language", "de,en-GB;q=0.9,en;q=0.8,en-US;q=0.7,es;q=0.6,ca;q=0.5");

		RawResponse newSession = null;
		if (useProxy) {
			newSession = session.get(url).headers(request).socksTimeout(60_000).connectTimeout(60_000)
					.proxy(Proxies.httpProxy(ip, port)).send();
		} else {
			newSession = session.get(url).headers(request).socksTimeout(60_000).connectTimeout(60_000).send();
		}

		String response = newSession.readToText();

		if (newSession.statusCode() != 200) {
			setProxy();
			System.out.println("[VINTED] - [" + dtf.format(now.now()) + "] - [" + url + "] - Retrying: "
					+ newSession.statusCode());
			Thread.sleep(retryDelayMS);
			monitorProducts();

		}

		String json = getJSON(response);

		JSONTokener tokener = new JSONTokener(json);
		JSONObject o = new JSONObject(tokener).getJSONObject("items").getJSONObject("catalogItems")
				.getJSONObject("byId");

		for (Object key : o.keySet()) {
			// based on you key types
			String productID = (String) key;
			JSONObject singleObject = (JSONObject) o.get(productID);

			String title = singleObject.getString("title");
			String imageUrl = singleObject.getJSONObject("photo").getString("full_size_url");
			String productUrl = singleObject.getString("url");
			String brand = singleObject.getString("brand_title");
			String price = singleObject.getString("price");
			String userUrl = singleObject.getJSONObject("user").getString("profile_url");
			String userName = singleObject.getJSONObject("user").getString("login");

			String size = singleObject.getString("size_title");

			if (!loadedProducts.contains(productID)) {
				System.out.println(
						"[VINTED] - [" + dtf.format(now.now()) + "] - [" + url + "] - NEW PRODUCT: " + productUrl);
				sendWebhook(title, productUrl, brand, price, size, userName, userUrl, imageUrl);
				loadedProducts.add(productID);
			} else {
				System.out.println(
						"[VINTED] - [" + dtf.format(now.now()) + "] - [" + url + "] - ALREADY SENT: " + productUrl);

			}

		}

	}

	public String getJSON(String response) {
		String[] arr = response
				.split(Pattern.quote("<script type=\"application/json\" data-js-react-on-rails-store=\"MainStore\">"));
		response = arr[1];

		arr = response.split(Pattern.quote("</script>"));
		response = arr[0];
		return response;
	}

	public String getProxy() throws FileNotFoundException {
		File f = new File(System.getProperty("user.home") + "\\Desktop" + "\\Vinted\\proxies.txt");
		String result = null;
		Random rand = new Random();
		int n = 0;
		Scanner sc = null;
		for (sc = new Scanner(f); sc.hasNext();) {
			++n;
			String line = sc.nextLine();
			if (rand.nextInt(n) == 0)
				result = line;
		}

		sc.close();

		return result;
	}

	public void setProxy() throws FileNotFoundException {
		String[] array = getProxy().split(":");
		ip = array[0];
		port = Integer.valueOf(array[1]);
	}

	public void write(String response) throws IOException {
		FileWriter writer = new FileWriter(new File("hii.txt"));
		writer.write(response);
		writer.close();
	}

	public void sendWebhook(String title, String productUrl, String brand, String price, String size, String userName,
			String userUrl, String imageUrl) throws IOException, LineUnavailableException, InterruptedException {

		DiscordWebhook webhook = new DiscordWebhook(webhookUrl);
		webhook.setUsername("VINTED MONITOR");
		webhook.setTts(false);
		webhook.addEmbed(new DiscordWebhook.EmbedObject().setTitle(title).setUrl(productUrl).setColor(Color.BLUE)
				.setThumbnail(imageUrl).addField("Brand", brand, false).addField("Size", size, false)
				.setFooter(dtf.format(now.now()) + " CET" + " | Vinted Monitor", "")
				.addField("Price", price + " EUR", false)
				.addField("Member", "[" + userName + "](" + userUrl + ")", false));

		try {
			webhook.execute();
			System.out.println("[VINTED] - [" + dtf.format(now.now()) + "] - [" + url + "] - Sent Webhook.");

		} catch (Exception e) {
			if (e.toString().contains("Server returned HTTP response code: 429 for URL")) {
				System.out.println("[VINTED] - [" + dtf.format(now.now()) + "] - [" + url
						+ "] - Webhook ratelimited! Retrying in 10s...");
				Thread.sleep(10000);
				sendWebhook(title, productUrl, brand, price, size, userName, userUrl, imageUrl);
			}
		}
	}
}
