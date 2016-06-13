package org.mountm.mlb.backtracking;

import gnu.trove.map.TShortObjectMap;
import gnu.trove.map.hash.TShortObjectHashMap;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
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

	private static int MAX_NUM_DAYS = 30;
	private static List<Game> games = new ArrayList<>(2430);
	private static TShortObjectMap<TIntSet> noExtensions;
	private static TShortObjectMap<Set<Game>> missedStadiums = new TShortObjectHashMap<>(30);
	private static int maxSize = 0;
	private static List<Game> bestSolution = new ArrayList<>(30);
	private static boolean foundSolution = false;

	private static final int NINE_AM = 32400000;
	private static final int TEN_PM = 79200000;
	private static final EnumSet<Stadium> WEST_COAST_STADIUMS = EnumSet.of(Stadium.LAA, Stadium.OAK, Stadium.SEA,
			Stadium.ARI, Stadium.LAD, Stadium.SDP, Stadium.SFG);

	public static void main(String[] args) {
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
		MAX_NUM_DAYS = parseInt(args[0]);
		noExtensions = new TShortObjectHashMap<>(15 * MAX_NUM_DAYS);

		List<Game> partial = new ArrayList<>(30);

		for (int i = 1; i < args.length; i++) {
			partial.add(games.get(parseInt(args[i])));
		}

		// initialize the missedStadiums collection
		if (partial.isEmpty()) {
			recalculateFailureCriteria(0);
		} else {
			recalculateFailureCriteria(games.indexOf(partial.get(partial.size() - 1)));
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
		System.out.println(partial);

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
			System.out.println("Checking game " + index);
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
		while (last.dayOfYear() + 2 >= candidate.dayOfYear()) {
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

	private static boolean validSolution(List<Game> partial) {
		// all stadium-related error checking is done prior to this point - we
		// only need to check the size of the solution.
		return partial.size() == 30;
	}

	private static boolean badSolution(List<Game> partial) {
		if (travelDays(partial) > MAX_NUM_DAYS || foundSolution && tripLength(partial) > tripLength(bestSolution)) {
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
		int lastDay = g.dayOfYear() + MAX_NUM_DAYS;
		int firstDay = g.dayOfYear();
		while (g.dayOfYear() < lastDay && index < games.size()) {
			lastGameHere[g.stadiumIndex()] = g;
			g = games.get(index++);
		}
		for (int i = 0; i < MAX_NUM_DAYS; i++) {
			Set<Game> mapEntry = new HashSet<Game>(30);
			for (int j = 0; j < 30; j++) {
				if (lastGameHere[j].dayOfYear() - firstDay <= i) {
					mapEntry.add(lastGameHere[j]);
				}
			}
			missedStadiums.put((short) (firstDay + i), mapEntry);
		}

		System.out.println(missedStadiums);

	}

	// keep track of the current best solution
	private static void processSolution(List<Game> partial) {
		printSolution(partial);
		if (!foundSolution) {
			foundSolution = true;
			bestSolution.clear();
			bestSolution.addAll(partial);
			System.out.println(tripLength(bestSolution));
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

	private static void printPartial(List<Game> partial) {
		StringBuilder sb = new StringBuilder(tripLength(partial).toString());
		while (sb.length() < 6) {
			sb.append(" ");
		}
		for (int i = 0; i < partial.size(); i++) {
			if (i < partial.size() - 1) {
				if (partial.get(i).dayOfYear() == partial.get(i + 1).dayOfYear()) {
					sb.append("(").append(partial.get(i).getStadium()).append(" ")
							.append(partial.get(i + 1).getStadium()).append(") ");
				} else if (partial.get(i).dayOfYear() + 2 == partial.get(i + 1).dayOfYear()) {
					sb.append(partial.get(i).getStadium()).append(" drive ");
				} else if (sb.indexOf(partial.get(i).getStadium().toString()) == -1) {
					sb.append(partial.get(i).getStadium().toString()).append(" ");
				}
			} else if (sb.indexOf((partial.get(i).getStadium().toString())) == -1) {
				sb.append(partial.get(i).getStadium().toString()).append(" ");
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
		if (partial.size() < 2) {
			return 1;
		}
		int offset = 1;
		Game firstGame = partial.get(0);
		Game lastGame = partial.get(partial.size() - 1);
		int travelToStart = Stadium.BAL.getMinutesTo(firstGame.getStadium());
		int travelFromEnd = lastGame.getStadium().getMinutesTo(Stadium.BAL);
		int firstTimeAvailable = Minutes
				.minutesBetween(firstGame.getStartTime().withMillisOfDay(NINE_AM), firstGame.getStartTime())
				.getMinutes();

		while (firstTimeAvailable < travelToStart) {
			offset++;
			travelToStart -= 720;
		}
		if (!lastGame.getStadium().equals(Stadium.BAL) && !lastGame.getStadium().equals(Stadium.WAS)
				&& !lastGame.getStadium().equals(Stadium.PHI)) {
			int lastTimeAvailable = Minutes.minutesBetween(lastGame.getStartTime().plusHours(4),
					lastGame.getStartTime().withMillisOfDay(TEN_PM)).getMinutes();
			while (lastTimeAvailable < travelFromEnd) {
				offset++;
				travelFromEnd -= 720;
			}
		}

		return partial.get(partial.size() - 1).dayOfYear() - partial.get(0).dayOfYear() + offset;
	}
}
