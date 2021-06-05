package org.mountm.mlb.backtracking;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

public class LPDistanceRunner {
	
	private static List<Game> gameList = new ArrayList<Game>(2430);
	private static Map<Game, Set<Game>> gamesLeavingGame = new HashMap<>();
	private static Set<Game> lastGamePerStadium = new HashSet<>();
	private static Set<Game> firstGamePerStadium = new HashSet<>();
	private static List<String> magicArcs = new ArrayList<>();
	private static List<String> regularArcs = new ArrayList<>();
	private static Map<Stadium, List<String>> arcsLeavingStadium = new HashMap<>();
	private static Map<Stadium, List<String>> arcsArrivingStadium = new HashMap<>();
	private static Map<Game, List<String>> arcsLeavingGame = new HashMap<>();
	private static Map<Game, List<String>> arcsArrivingGame = new HashMap<>();
	
	public static void main(String[] args) {
		BufferedReader input = null;
		String currentLine;
		try {
			input = new BufferedReader(new FileReader("GamesPruned.csv"));
			while ((currentLine = input.readLine()) != null) {
				int delimiter = currentLine.indexOf(",");
				DateTimeFormatter format = DateTimeFormat.forPattern("M/d/yyyy kk:mm");
				DateTime test = format.parseDateTime(currentLine.substring(0, delimiter));
				Stadium stadium = Stadium.valueOf(currentLine.substring(delimiter + 1));
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
		
		populateMaps();
		
		try {
			String constraint, objective, declaration;
			
			File file = new File("MLBTSP.lp");
			if (!file.exists()) {
				file.createNewFile();
			}
			
			FileWriter fw = new FileWriter(file.getAbsoluteFile());
			BufferedWriter bw = new BufferedWriter(fw);
			
			objective = "MINIMIZE ";
			for (Entry<Game, Set<Game>> entry : gamesLeavingGame.entrySet()) {
				Game g1 = entry.getKey();
				for (Game g2: entry.getValue()) {
					objective += g1.getMinutesTo(g2) + " " + g1.lpString() + "to" + g2.lpString();
					if (objective.length() >= 500) {
						bw.write(objective);
						bw.newLine();
						objective = "";
					}
					objective += " + ";
				}
			}
			objective = objective.substring(0, objective.length() - 3);
			bw.write(objective);
			bw.newLine();
			constraint = "SUBJECT TO";
			bw.write(constraint);
			bw.newLine();
			
			// must be exactly one arc leaving each stadium
			// (including magic arcs)
			for (List<String> arcs : arcsLeavingStadium.values()) {
				constraint = "";
				for (String arc : arcs) {
					constraint += arc;
					if (constraint.length() >= 500) {
						bw.write(constraint);
						bw.newLine();
						constraint = "";
					}
					constraint += " + ";
				}
				constraint = constraint.substring(0, constraint.length() - 3) + " = 1";
				bw.write(constraint);
				bw.newLine();
			}
			
			// must be exactly one arc arriving at each stadium
			// (including magic arcs)
			for (List<String> arcs : arcsArrivingStadium.values()) {
				constraint = "";
				for (String arc : arcs) {
					constraint += arc;
					if (constraint.length() >= 500) {
						bw.write(constraint);
						bw.newLine();
						constraint = "";
					}
					constraint += " + ";
				}
				constraint = constraint.substring(0, constraint.length() - 3) + " = 1";
				bw.write(constraint);
				bw.newLine();
			}
			
			// for each game, 
			for (Game g: gameList) {
				// sum of arcs arriving at game must be less than or equal to one
				constraint = "";
				for (String arc : arcsArrivingGame.getOrDefault(g, new ArrayList<>())) {
					constraint += arc;
					if (constraint.length() >= 500) {
						bw.write(constraint);
						bw.newLine();
						constraint = "";
					}
					constraint += " + ";
				}
				if (!constraint.isEmpty()) {
					constraint = constraint.substring(0, constraint.length() - 3) + " <= 1";
					bw.write(constraint);
					bw.newLine();
				}
				
				// sum of arcs departing game must be less than or equal to one
				constraint = "";
				for (String arc : arcsLeavingGame.getOrDefault(g, new ArrayList<>())) {
					constraint += arc;
					if (constraint.length() >= 500) {
						bw.write(constraint);
						bw.newLine();
						constraint = "";
					}
					constraint += " + ";
				}
				if (!constraint.isEmpty()) {
					constraint = constraint.substring(0, constraint.length() - 3) + " <= 1";
					bw.write(constraint);
					bw.newLine();
				}
				
				
				// sum of arriving arcs must equal sum of departing arcs
				constraint = "";
				boolean hasArrivingArcs = false;
				for (String arc : arcsArrivingGame.getOrDefault(g, new ArrayList<>())) {
					hasArrivingArcs = true;
					constraint += arc;
					if (constraint.length() >= 500) {
						bw.write(constraint);
						bw.newLine();
						constraint = "";
					}
					constraint += " + ";
				}
				if (hasArrivingArcs) {
					constraint = constraint.substring(0, constraint.length() - 3) + " - ";
				}
				
				for (String arc : arcsLeavingGame.getOrDefault(g, new ArrayList<>())) {
					constraint += arc;
					if (constraint.length() >= 500) {
						bw.write(constraint);
						bw.newLine();
						constraint = "";
					}
					constraint += hasArrivingArcs ? " - " : " + ";
				}
				constraint = constraint.substring(0, constraint.length() - 3) + " = 0";
				bw.write(constraint);
				bw.newLine();
			}
			
			// must have exactly one magic arc "closing the loop"
			constraint = "";
			for (String arc : magicArcs) {
				constraint += arc;
				if (constraint.length() >= 500) {
					bw.write(constraint);
					bw.newLine();
					constraint = "";
				}
				constraint += " + ";
			}
			constraint = constraint.substring(0, constraint.length() - 3) + " = 1";
			bw.write(constraint);
			bw.newLine();
			
			declaration = "BINARY";
			bw.write(declaration);
			bw.newLine();
			
			for (String arc : regularArcs) {
				bw.write(arc);
				bw.newLine();
			}
			for (String arc : magicArcs) {
				bw.write(arc);
				bw.newLine();
			}
			
			declaration = "END";
			bw.write(declaration);
			
			
			bw.close();

			System.out.println("Done");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void populateMaps() {
		Game[] lastGameHere = new Game[Stadium.values().length];
		for (int i = 0; i < gameList.size(); i++) {
			Game g1 = gameList.get(i);
			if (!firstGamePerStadium.stream().map(Game::getStadium).anyMatch(s -> s.equals(g1.getStadium()))) {
				firstGamePerStadium.add(g1);
			}
			lastGameHere[g1.getStadium().getIndex()] = g1;
			for (int j = i+1; j < gameList.size(); j++) {
				Game g2 = gameList.get(j);
				if (!g1.getStadium().equals(g2.getStadium()) && g1.canReach(g2)) {
					String arc = g1.lpString() + "to" + g2.lpString();
					regularArcs.add(arc);
					
					Set<Game> innerSet = gamesLeavingGame.getOrDefault(g1, new HashSet<>());
					innerSet.add(g2);
					gamesLeavingGame.put(g1, innerSet);
					
					List<String> innerList = arcsLeavingStadium.getOrDefault(g1.getStadium(), new ArrayList<>());
					innerList.add(arc);
					arcsLeavingStadium.put(g1.getStadium(), innerList);
					
					innerList = arcsArrivingStadium.getOrDefault(g2.getStadium(), new ArrayList<>());
					innerList.add(arc);
					arcsArrivingStadium.put(g2.getStadium(), innerList);
					
					innerList = arcsLeavingGame.getOrDefault(g1, new ArrayList<>());
					innerList.add(arc);
					arcsLeavingGame.put(g1, innerList);
					
					innerList = arcsArrivingGame.getOrDefault(g2, new ArrayList<>());
					innerList.add(arc);
					arcsArrivingGame.put(g2, innerList);
				}
			}
		}
		
		for (int i = 0; i < Stadium.values().length; i++) {
			lastGamePerStadium.add(lastGameHere[i]);
		}
		
		for (Game g1: lastGamePerStadium) {
			for (Game g2: firstGamePerStadium) {
				if (!g1.getStadium().equals(g2.getStadium())) {
					String arc = g1.lpString() + "to" + g2.lpString();
					magicArcs.add(arc);
					
					List<String> innerList = arcsLeavingStadium.getOrDefault(g1.getStadium(), new ArrayList<>());
					innerList.add(arc);
					arcsLeavingStadium.put(g1.getStadium(), innerList);
					
					innerList = arcsArrivingStadium.getOrDefault(g2.getStadium(), new ArrayList<>());
					innerList.add(arc);
					arcsArrivingStadium.put(g2.getStadium(), innerList);
					
					innerList = arcsLeavingGame.getOrDefault(g1, new ArrayList<>());
					innerList.add(arc);
					arcsLeavingGame.put(g1, innerList);
					
					innerList = arcsArrivingGame.getOrDefault(g2, new ArrayList<>());
					innerList.add(arc);
					arcsArrivingGame.put(g2, innerList);
				}
			}
		}
		
	}


}
