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
import org.joda.time.format.DateTimeFormat;

import static java.lang.Integer.*;

public class BacktrackingRunner {

	private static int maxNumDays = 30;
	private static int bestTripLength = Integer.MAX_VALUE;
	private static List<Game> games = new ArrayList<>(2430);
	private static TShortObjectMap<TIntSet> noExtensions = new TShortObjectHashMap<>(510);
	private static TShortObjectMap<Set<Game>> missedStadiums = new TShortObjectHashMap<>(30);
	private static int maxSize = 0;
	private static List<Game> bestSolution = new ArrayList<>(30);
	private static boolean foundSolution = false;

	private static final String NO_EXTENSIONS_FILE_NAME = "noExtensions.dat";
	private static Set<EnumSet<Stadium>> possibleDHs = new HashSet<>(8);
	

	public static void main(String[] args) {

		readGameInputFile();

		maxNumDays = Integer.parseInt(args[0]);
		
		bestTripLength = Integer.parseInt(args[1]);

		List<Game> partial = new ArrayList<>(30);
		for (int i = 2; i < args.length; i++) {
			Game g = games.get(parseInt(args[i]));
			if (haveVisitedStadium(partial, g.getStadium())) {
				System.out.println("Trying to visit " + g.getStadium() + " twice!");
				return;
			}
			partial.add(g);
		}

		if (verifyInitialData(partial)) {
			
			System.out.println(partial);

			// initialize the missedStadiums collection
			if (partial.isEmpty()) {
				recalculateFailureCriteria(0);
			} else {
				recalculateFailureCriteria(games.indexOf(partial.get(0)));
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
			} else {
				printSolution(bestSolution);
				System.out.println(tripLength(bestSolution));
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
		int index2 = index;
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
		
		Game g1 = games.get(index2++);
		while (g1.dayOfYear() < lastDay && index2 < games.size()) {
			int j = index2;
			Game g2 = games.get(j++);
			while (g2.dayOfYear() == g1.dayOfYear() && j < games.size()) {
				if (g1.canReach(g2)) {
					EnumSet<Stadium> possibleDH = EnumSet.of(g1.getStadium(), g2.getStadium());
					boolean shouldAdd = true;
					for (EnumSet<Stadium> set : possibleDHs) {
						shouldAdd = shouldAdd && !set.containsAll(possibleDH);
					}
					if (shouldAdd) {
						possibleDHs.add(possibleDH);
					}
				}
				g2 = games.get(j++);
			}
			g1 = games.get(index2++);
		}
		
		System.out.println("There are " + getPossibleRemainingDHs(new ArrayList<Game>()) + " possible DHs on this trip:");
		for (EnumSet<Stadium> possibleDH : possibleDHs) {
			System.out.println(possibleDH.toString());
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
		if (travelDays(partial) > maxNumDays
				|| (foundSolution && tripLength(partial) > bestTripLength)) {
			return true;
		}

		if (validSolution(partial) || partial.isEmpty()) {
			return false;
		}
		
		int numRestDays = getRestDays(partial);
		int numDHs = getDHs(partial);
		int possibleRemainingDHs = getPossibleRemainingDHs(partial);
		
		if ((numDHs + possibleRemainingDHs - numRestDays) < 30 - maxNumDays) {
			return true;
		}

		Game last = partial.get(partial.size() - 1);

		// Check if any stadiums are missing that must be present based on
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

	private static int getPossibleRemainingDHs(List<Game> partial) {
		Set<EnumSet<Stadium>> options = new HashSet<>(possibleDHs.size());
		Set<EnumSet<Stadium>> prunedOptions = new HashSet<>();
		for (EnumSet<Stadium> set : possibleDHs) {
			options.add(set.clone());
		}
		
		int result = 0;
		boolean isLastDayDH = false;
		if (partial.size() > 1 && partial.get(partial.size() - 2).dayOfYear() == partial.get(partial.size() - 1).dayOfYear()) {
			isLastDayDH = true;
		}
		if (isLastDayDH) {
			for (int i = 0; i < partial.size(); i++) {
				for (EnumSet<Stadium> set: options) {
					set.remove(partial.get(i).getStadium());
				}
			}
		} else {
			for (int i = 0; i < partial.size() - 1; i++) {
				for (EnumSet<Stadium> set: options) {
					set.remove(partial.get(i).getStadium());
				}
			}
		}
		for (EnumSet<Stadium> set: options) {
			if (set.size() > 1) {
				prunedOptions.add(set);
			}
		}
		
		for (Set<EnumSet<Stadium>> candidate : getPowerSet(prunedOptions)) {
			if (candidate.size() > result && testCandidate(candidate)) {
				result = candidate.size();
			}
		}
		return result;
	}

	private static boolean testCandidate(Set<EnumSet<Stadium>> candidate) {
		EnumSet<Stadium> stadiums = EnumSet.noneOf(Stadium.class);
		for (EnumSet<Stadium> set : candidate) {
			if (EnumSet.complementOf(stadiums).containsAll(set)) {
				stadiums.addAll(set);
			} else {
				return false;
			}
		}
		return true;
	}

	private static <T> Set<Set<T>> getPowerSet(Set<T> originalSet) {
	    Set<Set<T>> sets = new HashSet<Set<T>>();
	    if (originalSet.isEmpty()) {
	        sets.add(new HashSet<T>());
	        return sets;
	    }
	    List<T> list = new ArrayList<T>(originalSet);
	    T head = list.get(0);
	    Set<T> rest = new HashSet<T>(list.subList(1, list.size())); 
	    for (Set<T> set : getPowerSet(rest)) {
	        Set<T> newSet = new HashSet<T>();
	        newSet.add(head);
	        newSet.addAll(set);
	        sets.add(newSet);
	        sets.add(set);
	    }       
	    return sets;
	}

	private static int getDHs(List<Game> partial) {
		int result = 0;
		for (int i = 0; i < partial.size() - 1; i++) {
			if (partial.get(i).dayOfYear() == partial.get(i + 1).dayOfYear()) {
				result++;
			}
		}
		return result;
	}

	private static int getRestDays(List<Game> partial) {
		int result = 0;
		for (int i = 0; i < partial.size() - 1; i++) {
			if (partial.get(i).dayOfYear() + 1 < partial.get(i + 1).dayOfYear()) {
				result = result + (partial.get(i + 1).dayOfYear() - partial.get(i).dayOfYear()) - 1;
			}
		}
		return result;
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
			int tripLength = tripLength(bestSolution);
			bestTripLength = Math.min(tripLength, bestTripLength);
			System.out.println(tripLength);
			writePruningData();
		} else {
			int newTripLength = tripLength(partial);
			if (newTripLength < bestTripLength) {
				bestSolution.clear();
				bestSolution.addAll(partial);
				System.out.println("Best solution is " + newTripLength + ", prev was " + bestTripLength);
				bestTripLength = newTripLength;
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
		while (last.dayOfYear() + 2 >= candidate.dayOfYear() && index < games.size()) {
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
			notVisited.remove(partial.get(i).getStadium());
			if (i > 0) {
				tripLength += partial.get(i - 1).getMinutesTo(partial.get(i));
			}
		}
		int padding = 0;
		Stadium last = partial.get(partial.size() - 1).getStadium();
		for (Stadium s : notVisited) {
			padding = Math.max(padding, last.getMinutesTo(s));
		}
		return tripLength + padding;
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
		return partial.get(partial.size() - 1).dayOfYear() - partial.get(0).dayOfYear() + 1;
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
