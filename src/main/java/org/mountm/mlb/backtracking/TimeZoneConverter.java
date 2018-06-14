package org.mountm.mlb.backtracking;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

public class TimeZoneConverter {

	static List<Game> gameList = new ArrayList<Game>(2430);

	public static void main(String[] args) {
		BufferedReader input = null;
		String currentLine;
		try {
			input = new BufferedReader(new FileReader("GamesRaw.csv"));
			while ((currentLine = input.readLine()) != null) {
				int delimiter = currentLine.indexOf(",");
				DateTimeFormatter format = DateTimeFormat
						.forPattern("MM/dd/yyyy kk:mm");
				DateTime test = format.parseDateTime(currentLine.substring(0,
						delimiter));
				Stadium stadium = Stadium.valueOf(currentLine
						.substring(delimiter + 1));
				test = test.plusHours(stadium.getTimeZone());
				gameList.add(new Game(stadium, test));
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (input != null)
					input.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		Collections.sort(gameList);

		try {

			File file = new File("Games.csv");

			// if file doesn't exists, then create it
			if (!file.exists()) {
				file.createNewFile();
			}

			FileWriter fw = new FileWriter(file.getAbsoluteFile());
			BufferedWriter bw = new BufferedWriter(fw);

			for (Game g : gameList) {
				bw.write(g.getDate().toString("MM/dd/yyyy kk:mm") + ",");
				bw.write(g.getStadium().toString());
				bw.newLine();
			}

			bw.close();

			System.out.println("Done");

		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
