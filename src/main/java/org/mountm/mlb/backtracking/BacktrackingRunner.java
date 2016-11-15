package org.mountm.mlb.backtracking;

import gnu.trove.map.TShortObjectMap;
import gnu.trove.map.hash.TShortObjectHashMap;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import org.joda.time.DateTime;
import org.joda.time.Minutes;
import org.joda.time.format.DateTimeFormat;

import static java.lang.Integer.*;

public class BacktrackingRunner {

	private static int maxNumDays = 30;
	private static int[] maxDaysPerGame;
	private static List<Game> games = new ArrayList<>(2430);
	private static TShortObjectMap<TIntSet> noExtensions = new TShortObjectHashMap<>(510);
	private static TShortObjectMap<Set<Game>> missedStadiums = new TShortObjectHashMap<>(30);
	private static int maxSize = 0;
	private static List<Game> bestSolution = new ArrayList<>(30);
	private static boolean foundSolution = false;

	private static final EnumSet<Stadium> WEST_COAST_STADIUMS = EnumSet.of(Stadium.LAA, Stadium.OAK, Stadium.SEA,
			Stadium.ARI, Stadium.LAD, Stadium.SDP, Stadium.SFG);
	private static final int NINE_AM = 32400000;
	private static final int TEN_PM = 79200000;
	private static final String NO_EXTENSIONS_FILE_NAME = "noExtensions.dat";

	public static void main(String[] args) {

		readGameInputFile();

		initializeConstraints(Integer.parseInt(args[0]));

		List<Game> partial = new ArrayList<>(30);
		for (int i = 1; i < args.length; i++) {
			partial.add(games.get(parseInt(args[i])));
		}

		if (verifyInitialData(partial)) {

			// initialize the missedStadiums collection
			if (partial.isEmpty()) {
				recalculateFailureCriteria(0);
			} else {
				recalculateFailureCriteria(games.indexOf(partial.get(partial.size() - 1)));
			}

			readPruningData();
			if (badSolution(partial)) {
				System.out.println("Infeasible starting point.");
				return;
			}

			// decrement maxSize once per minute to increase output
			Timer timer = new Timer(true);
			timer.schedule(new TimerTask() {
				public void run() {
					if (maxSize > 0) {
						maxSize--;
					}
				}
			}, 60000, 60000);

			backtrack(partial);
			if (!foundSolution) {
				writePruningData();
			}
			System.out.println(partial);
		}

	}

	private static void readGameInputFile() {
		BufferedReader br = null;

		try {
			String currentLine;
			String[] gameData;
			br = new BufferedReader(new FileReader("Games.csv"));
			while ((currentLine = br.readLine()) != null) {
				gameData = currentLine.split(",");
				DateTime startTime = DateTimeFormat.forPattern("MM/dd/yyyy kk:mm").parseDateTime(gameData[0]);
				Stadium stadium = Stadium.valueOf(gameData[1]);
				games.add(new Game(stadium, startTime));
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (br != null)
					br.close();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
	}

	private static void initializeConstraints(int input) {
		maxNumDays = input;
		maxDaysPerGame = new int[maxNumDays + 1];
		maxDaysPerGame[0] = 0;
		maxDaysPerGame[maxNumDays] = 29;
		for (int i = 1; i < (maxNumDays - 27); i++) {
			maxDaysPerGame[(2 * i) - 1] = i;
			maxDaysPerGame[2 * i] = i;
		}
		for (int i = (2 * maxNumDays - 55); i < maxNumDays; i++) {
			maxDaysPerGame[i] = maxDaysPerGame[i - 1] + 1;
		}
	}

	private static boolean verifyInitialData(List<Game> partial) {
		if (partial.size() < 2) {
			return true;
		}
		for (int i = 0; i < partial.size() - 1; i++) {
			if (!partial.get(i).canReach(partial.get(i + 1))) {
				System.out.println("Can't get from " + partial.get(i) + " to " + partial.get(i + 1));
				return false;
			}
		}
		return true;
	}

	@SuppressWarnings("unchecked")
	private static void readPruningData() {
		try {
			FileInputStream fis = new FileInputStream(NO_EXTENSIONS_FILE_NAME);
			ObjectInputStream ois = new ObjectInputStream(fis);
			noExtensions = (TShortObjectMap<TIntSet>) ois.readObject();
			ois.close();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

	// missedStadiums stores information about the latest point in time at which
	// each stadium can be visited. The keys are days of the year. The values
	// are a set of games. There is one game for each stadium that must have
	// been visited by the key date.
	//
	// noExtensions also depends on the starting date of all partial solutions
	// being the same. It must be cleared when the starting date changes.
	private static void recalculateFailureCriteria(int index) {
		noExtensions.clear();
		Game[] lastGameHere = new Game[30];
		Game g = games.get(index++);
		int lastDay = g.dayOfYear() + maxNumDays;
		int firstDay = g.dayOfYear();
		while (g.dayOfYear() < lastDay && index < games.size()) {
			lastGameHere[g.stadiumIndex()] = g;
			g = games.get(index++);
		}
		for (int i = 0; i < maxNumDays; i++) {
			Set<Game> mapEntry = new HashSet<Game>(30);
			for (int j = 0; j < 30; j++) {
				if (lastGameHere[j].dayOfYear() - firstDay <= i) {
					mapEntry.add(lastGameHere[j]);
				}
			}
			missedStadiums.put((short) (firstDay + i), mapEntry);
		}
	}

	// standard backtracking algorithm - just added the printPartial logic after
	// returning from a bad solution.
	private static void backtrack(List<Game> partial) {
		if (badSolution(partial)) {
			return;
		}
		if (validSolution(partial)) {
			processSolution(partial);

		} else if (maxSize < partial.size()) {
			maxSize = partial.size();
			printPartial(partial);
		}
		partial = firstExtension(partial);
		while (partial != null) {
			backtrack(partial);
			partial = nextExtension(partial);
		}
	}

	private static boolean badSolution(List<Game> partial) {
		if (travelDays(partial) > maxNumDays || partial.size() < maxDaysPerGame[travelDays(partial)]
				|| foundSolution && tripLength(partial) > tripLength(bestSolution)) {
			return true;
		}

		if (validSolution(partial) || partial.isEmpty()) {
			return false;
		}

		// If the trip has gone to the West Coast, it must hit all West Coast
		// stadiums before leaving.
		Game last = partial.get(partial.size() - 1);
		if (!WEST_COAST_STADIUMS.contains(last.getStadium())) {
			EnumSet<Stadium> needed = EnumSet.copyOf(WEST_COAST_STADIUMS);
			for (Game g : partial) {
				needed.remove(g.getStadium());
			}
			// After removing all West Coast stadiums that have been visited,
			// the remainder should be all or nothing.
			if (!needed.containsAll(WEST_COAST_STADIUMS) && !needed.isEmpty()) {
				return true;
			}
		}

		// Next, check if any stadiums are missing that must be present based on
		// the time limits (i.e. teams leaving for a long road trip).
		for (Game g : missedStadiums.get((short) last.dayOfYear())) {
			if (!(haveVisitedStadium(partial, g.getStadium()) || last.canReach(g))) {
				return true;
			}
		}

		// Finally, check to see if an equivalent path was already discarded
		short key = (short) games.indexOf(last);
		return noExtensions.containsKey(key) && noExtensions.get(key).contains(calculateValue(partial));
	}

	private static boolean validSolution(List<Game> partial) {
		// all stadium-related error checking is done prior to this point - we
		// only need to check the size of the solution.
		return partial.size() == 30;
	}

	// keep track of the current best solution
	private static void processSolution(List<Game> partial) {
		printSolution(partial);
		if (!foundSolution) {
			foundSolution = true;
			bestSolution.clear();
			bestSolution.addAll(partial);
			System.out.println(tripLength(bestSolution));
			writePruningData();
		} else {
			int bestTripLength = tripLength(bestSolution);
			int newTripLength = tripLength(partial);
			if (newTripLength < bestTripLength) {
				bestSolution.clear();
				bestSolution.addAll(partial);
				System.out.println("Best solution is " + newTripLength + ", prev was " + bestTripLength);
			}
		}
	}

	// The first extension of a given partial solution. Looks for the very next
	// game that can be added.
	private static List<Game> firstExtension(List<Game> partial) {
		int index = 0;
		if (partial.size() > 0) {
			index = games.indexOf(partial.get(partial.size() - 1)) + 1;
		}
		return extendSolution(partial, index);
	}

	// Replace the current endpoint of this partial solution with the "next"
	// one. Returns null if no other options are available.
	private static List<Game> nextExtension(List<Game> partial) {
		int index = games.indexOf(partial.remove(partial.size() - 1)) + 1;
		if (partial.isEmpty()) {
			maxSize = 0;
			if (games.get(index - 1).dayOfYear() != games.get(index).dayOfYear()) {
				recalculateFailureCriteria(index);
			}
		}
		return extendSolution(partial, index);
	}

	// Extends the partial solution by looking in the master game list starting
	// at the specified index. The first valid extension is returned. If no
	// valid extension exists, the partial solution is added to noExtensions and
	// null is returned.
	private static List<Game> extendSolution(List<Game> partial, int index) {
		if (partial.size() == 0) {
			partial.add(games.get(index));
			return partial;
		}
		Game last = partial.get(partial.size() - 1);
		Game candidate = games.get(index++);
		while (last.dayOfYear() + 3 >= candidate.dayOfYear() && index < games.size()) {
			if (!haveVisitedStadium(partial, candidate.getStadium()) && last.canReach(candidate)) {
				partial.add(candidate);
				return partial;
			}
			candidate = games.get(index++);
		}
		if (!foundSolution) {
			addToParity(partial);
		}
		return null;
	}

	// noExtensions stores known invalid partial solutions. The key is the
	// master index of the last game in the solution. The value in the map is a
	// set of integers - each integer represents a set of stadiums visited in a
	// partial solution that ends at the game specified by the key. If two
	// partial solutions end with the same game and visit the same set of
	// stadiums, they are equivalent (as long as both start on the same day!)
	private static void addToParity(List<Game> partial) {
		if (partial.size() < 3) {
			return;
		}
		short key = (short) games.indexOf(partial.get(partial.size() - 1));
		if (noExtensions.containsKey(key)) {
			noExtensions.get(key).add(calculateValue(partial));
		} else {
			TIntSet entry = new TIntHashSet();
			entry.add(calculateValue(partial));
			noExtensions.put(key, entry);
		}
	}

	// The value of a partial is an int representing which stadiums have been
	// visited. The nth bit of the int represents whether the nth stadium has
	// been visited.
	private static int calculateValue(List<Game> partial) {
		int val = 0;
		for (Game g : partial) {
			val ^= g.getStadium().getMask();
		}
		return val;
	}

	private static void printPartial(List<Game> partial) {
		StringBuilder sb = new StringBuilder(tripLength(partial).toString());
		while (sb.length() < 6) {
			sb.append(" ");
		}
		int startDay = partial.get(0).dayOfYear();
		int endDay = partial.get(partial.size() - 1).dayOfYear();
		int currentIndex = 0;
		for (int i = startDay; i <= endDay; i++) {
			if (currentIndex + 1 < partial.size() && partial.get(currentIndex + 1).dayOfYear() == i) {
				sb.append("(").append(partial.get(currentIndex++).getStadium().toString()).append(" ")
						.append(partial.get(currentIndex++).getStadium().toString()).append(") ");
			} else if (partial.get(currentIndex).dayOfYear() == i) {
				sb.append(partial.get(currentIndex++).getStadium().toString()).append(" ");
			} else {
				sb.append("drive ");
			}
		}
		sb.append(partial.size()).append(" in ").append(travelDays(partial));

		System.out.println(sb);

	}

	// The total trip length is padded with distance from Baltimore to the
	// starting stadium, and from the ending stadium to Baltimore. However, if
	// the partial solution is not complete, the "post-trip" padding can be
	// increased. For any stadium not in the trip, the padding must be at least
	// the distance from the current endpoint to the unvisited stadium, plus
	// the distance from the unvisited stadium to Baltimore.
	private static Integer tripLength(List<Game> partial) {
		if (partial.size() < 2) {
			return 0;
		}
		EnumSet<Stadium> notVisited = EnumSet.allOf(Stadium.class);
		Integer tripLength = 0;
		for (int i = 0; i < partial.size(); i++) {
			if (i == 0) {
				tripLength += Stadium.BAL.getMinutesTo(partial.get(0).getStadium());
			} else {
				tripLength += partial.get(i - 1).getMinutesTo(partial.get(i));
			}
			notVisited.remove(partial.get(i).getStadium());
		}
		int padding = 0;
		Stadium last = partial.get(partial.size() - 1).getStadium();
		if (!notVisited.isEmpty()) {
			for (Stadium s : notVisited) {
				padding = Math.max(padding, last.getMinutesTo(s) + s.getMinutesTo(Stadium.BAL));
			}
		} else {
			padding = last.getMinutesTo(Stadium.BAL);
		}
		tripLength += padding;
		return tripLength;
	}

	private static boolean haveVisitedStadium(List<Game> partial, Stadium stadium) {
		for (Game g : partial) {
			if (g.getStadium() == stadium) {
				return true;
			}
		}
		return false;
	}

	private static void printSolution(List<Game> partial) {
		for (Game g : partial) {
			System.out.println(g);
		}
	}

	private static int travelDays(List<Game> partial) {
		int partialSize = partial.size();
		if (partialSize == 0) {
			return 0;
		}
		int offset = 1;
		Game firstGame = partial.get(0);
		int travelToStart = Stadium.BAL.getMinutesTo(firstGame.getStadium());
		int firstTimeAvailable = Minutes
				.minutesBetween(firstGame.getStartTime().withMillisOfDay(NINE_AM), firstGame.getStartTime())
				.getMinutes();
		while (firstTimeAvailable < travelToStart) {
			offset++;
			travelToStart -= 720;
		}
		if (partial.size() == 1) {
			return offset;
		}
		if (partialSize == 30) {
			Game lastGame = partial.get(partial.size() - 1);
			int travelFromEnd = lastGame.getStadium().getMinutesTo(Stadium.BAL);
			if (!lastGame.getStadium().equals(Stadium.BAL) && !lastGame.getStadium().equals(Stadium.WAS)
					&& !lastGame.getStadium().equals(Stadium.PHI)) {
				int lastTimeAvailable = Minutes.minutesBetween(lastGame.getStartTime().plusHours(4),
						lastGame.getStartTime().withMillisOfDay(TEN_PM)).getMinutes();
				while (lastTimeAvailable < travelFromEnd) {
					offset++;
					travelFromEnd -= 720;
				}
			}
		}
		return partial.get(partial.size() - 1).dayOfYear() - partial.get(0).dayOfYear() + offset;
	}

	private static void writePruningData() {
			try {
				File file = new File(NO_EXTENSIONS_FILE_NAME);
				if (file.exists()) {
					file.delete();
				}
				FileOutputStream fos = new FileOutputStream(NO_EXTENSIONS_FILE_NAME);
				ObjectOutputStream oos = new ObjectOutputStream(fos);
				oos.writeObject(noExtensions);
				oos.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
	}
}
