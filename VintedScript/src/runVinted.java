import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

public class runVinted {

	public static void main(String[] args) throws IOException, InterruptedException {
		// TODO Auto-generated method stub

		System.out.println(" __      __ _____  _   _  _______  ______  _____   \n"
				+ " \\ \\    / /|_   _|| \\ | ||__   __||  ____||  __ \\  \n"
				+ "  \\ \\  / /   | |  |  \\| |   | |   | |__   | |  | | \n"
				+ "   \\ \\/ /    | |  | . ` |   | |   |  __|  | |  | | \n"
				+ "    \\  /    _| |_ | |\\  |   | |   | |____ | |__| | \n"
				+ "     \\/    |_____||_| \\_|   |_|   |______||_____/  ");
		System.out.println("\nBy @VA#0001");

		Path path = Paths.get(System.getProperty("user.home") + "\\Desktop" + "\\Vinted\\tasksVinted.csv");
		Reader in = new FileReader(System.getProperty("user.home") + "\\Desktop" + "\\Vinted\\tasksVinted.csv");
		Iterable<CSVRecord> records = CSVFormat.EXCEL.withHeader().parse(in);

		System.out
				.println("\n\nInitializing " + Integer.valueOf((int) Files.lines(path).count() - 1) + " task(s)...\n");
		Thread.sleep(1000);

		for (CSVRecord record : records) {

			String url = record.get("url");
			long retryDelayMS = Long.valueOf(record.get("retryDelaySeconds")) * 1000;
			String useProxy = record.get("proxy [y/n]");
			String webhookUrl = record.get("webhookUrl");

			VintedTask task = new VintedTask(url, retryDelayMS, useProxy, webhookUrl);
			task.start();

		}
	}

}
