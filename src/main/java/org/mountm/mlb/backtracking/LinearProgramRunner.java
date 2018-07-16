package org.mountm.mlb.backtracking;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;


public class LinearProgramRunner {
	
	private static List<Game> gameList = new ArrayList<Game>(2430);
	private static List<Game> westCoast = new ArrayList<Game>(567);
	private static List<Interval> dateRanges = new ArrayList<Interval>();

	private static final Set<Stadium> westCoastStadiums = new HashSet<Stadium>(
			Arrays.asList(Stadium.LAA, Stadium.OAK, Stadium.SEA, Stadium.LAD, Stadium.SDP, Stadium.SFG, Stadium.ARI));
	private static final int TWENTY_NINE_DAYS = 41760;

	public static void main(String[] args) {
		BufferedReader input = null;
		String currentLine;
		try {
			input = new BufferedReader(new FileReader("Games.csv"));
			while ((currentLine = input.readLine()) != null) {
				int delimiter = currentLine.indexOf(",");
				DateTimeFormatter format = DateTimeFormat.forPattern("MM/dd/yyyy kk:mm");
				DateTime test = format.parseDateTime(currentLine.substring(0, delimiter)).minusMinutes(30);
				Stadium stadium = Stadium.valueOf(currentLine.substring(delimiter + 1));
				gameList.add(new Game(stadium, test));
				if (westCoastStadiums.contains(stadium)) {
					westCoast.add(new Game(stadium, test));
				}
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

		Interval allStarBreak = findAllStarBreak(gameList);
		System.out.println("ASB starts on " + allStarBreak.getStart().toString("MMM dd") + " and ends on "
				+ allStarBreak.getEnd().toString("MMM dd"));

		backtrack(new ArrayList<Game>(7));
		int counter = 1;
		for (Interval westCoastRange : dateRanges) {
			int index = 0;
			List<Game> gameRange = new ArrayList<Game>(735);
			boolean isAfterASB = westCoastRange.getStart().isAfter(allStarBreak.getStart());
			while ((gameList.get(index).dayOfYear() + 26) < westCoastRange.getStart().getDayOfYear()) {
				index++;
			}
			if (isAfterASB) {
				while (gameList.get(index).getDate().isBefore(allStarBreak.getStart())) {
					index++;
				}
			}
			while (index < gameList.size()
					&& westCoastRange.getEnd().getDayOfYear() + 26 >= gameList.get(index).dayOfYear()) {
				if ((isAfterASB || gameList.get(index).getDate().isBefore(allStarBreak.getStart()))
						&& (!westCoastStadiums.contains(gameList.get(index).getStadium())
								|| westCoastRange.contains(gameList.get(index).getDate()))) {
					gameRange.add(gameList.get(index));
				}
				index++;
			}

			try {

				String objective, constraint, declaration;

				int firstStartTime = gameRange.get(0).getStartTime();
				int lastEndTime = gameRange.get(gameRange.size() - 1).getStartTime() + 240;

				File file = new File("MLBTSP" + (counter++) + ".lp");

				// if file doesnt exists, then create it
				if (!file.exists()) {
					file.createNewFile();
				}

				FileWriter fw = new FileWriter(file.getAbsoluteFile());
				BufferedWriter bw = new BufferedWriter(fw);

				objective = "MINIMIZE endTime - startTime + timeToStart + timeFromEnd";
				bw.write(objective);
				bw.newLine();
				constraint = "SUBJECT TO";
				bw.write(constraint);
				bw.newLine();

				// startTime must be before any game in the solution:
				// startTime - g.getStartTime() + M*isVisited(g) <= M
				for (Game g : gameRange) {
					constraint = "startTime" + g.lpString() + ": startTime + 500000 " + g.lpString() + " <= " + (500000 + g.getStartTime());
					bw.write(constraint);
					bw.newLine();
				}
				bw.newLine();

				// endTime must be after any game in the solution:
				// games[i][j].getStartTime() - endTime +
				// M*isVisited(games[i][j]) <= M + gameLength
				for (Game g : gameRange) {
					constraint = "endTime" + g.lpString() + ": 500000 " + g.lpString() + " - endTime <= " + (500240 - g.getStartTime());
					bw.write(constraint);
					bw.newLine();
				}
				bw.newLine();

				// each ballpark must be visited once
				// for each park, sum of all decision variables = 1
				for (Stadium s : Stadium.values()) {
					constraint = "didVisit" + s + ": ";
					for (Game g : gameRange) {
						if (g.getStadium() == s) {
							constraint += g.lpString();
							if (constraint.length() >= 500) {
								bw.write(constraint);
								bw.newLine();
								constraint = "";
							}
							constraint += " + ";
						}
					}
					constraint = constraint.substring(0, constraint.length() - 3);
					constraint += " = 1";
					bw.write(constraint);
					bw.newLine();
				}
				bw.newLine();

				// if you can't get from one game to another, they can't
				// both be
				// in
				// the solution. This is a big section.
				// isVisited(g1) + isVisited(g2) <= 1
				for (Game g1 : gameRange) {
					for (Game g2 : gameRange.subList(gameRange.indexOf(g1) + 1, gameRange.size())) {
						if (!g1.canReach(g2)) {
							constraint = g1.lpString() + " + " + g2.lpString() + " <= 1";
							bw.write(constraint);
							bw.newLine();
						}
					}
				}
				bw.newLine();

				// First stadium visited must be the earliest game in the
				// solution.
				// for each pair of stadiums,
				// sum(isVisited(s1,n)*startTime(s1,n))
				// + M*isFirst(s1) - sum(isVisited(s2,n)*startTime(s2, n))
				// <= M
				for (Stadium s1 : Stadium.values()) {
					for (Stadium s2 : Stadium.values()) {
						if (s1 != s2) {
							constraint = "isFirst" + s1 + s2 + ": ";
							for (Game g : gameRange) {
								if (g.getStadium() == s1) {
									constraint += g.getStartTime() + " " + g.lpString();
									if (constraint.length() >= 500) {
										bw.write(constraint);
										bw.newLine();
										constraint = "";
									}
									constraint += " + ";
								}
							}
							constraint = constraint.substring(0, constraint.length() - 3);
							if (constraint.length() >= 500) {
								bw.write(constraint);
								bw.newLine();
								constraint = "";
							}
							constraint += " + 500000 isFirst" + s1 + " - ";
							for (Game g : gameRange) {
								if (g.getStadium() == s2) {
									constraint += g.getStartTime() + " " + g.lpString();
									if (constraint.length() >= 500) {
										bw.write(constraint);
										bw.newLine();
										constraint = "";
									}
									constraint += " - ";
								}
							}
							constraint = constraint.substring(0, constraint.length() - 3);
							if (constraint.length() >= 500) {
								bw.write(constraint);
								bw.newLine();
								constraint = "";
							}
							constraint += " <= 500000";
							bw.write(constraint);
							bw.newLine();
						}
					}
				}
				bw.newLine();

				// Last stadium visited must be the latest game in the
				// solution
				// for each pair of stadiums, M*isLast(s1) -
				// sum(isVisited(s1,
				// n)*startTime(s1, n)) + sum(isVisited(s2, n)*startTime(s2,
				// n))
				// <=
				// M
				for (Stadium s1 : Stadium.values()) {
					for (Stadium s2 : Stadium.values()) {
						if (s1 != s2) {
							constraint = "isLast" + s1 + s2 + ": 500000 isLast" + s1 + " - ";
							for (Game g : gameRange) {
								if (g.getStadium() == s1) {
									constraint += g.getStartTime() + " " + g.lpString();
									if (constraint.length() >= 500) {
										bw.write(constraint);
										bw.newLine();
										constraint = "";
									}
									constraint += " - ";
								}
							}
							constraint = constraint.substring(0, constraint.length() - 3);
							if (constraint.length() >= 500) {
								bw.write(constraint);
								bw.newLine();
								constraint = "";
							}
							constraint += " + ";
							for (Game g : gameRange) {
								if (g.getStadium() == s2) {
									constraint += g.getStartTime() + " " + g.lpString();
									if (constraint.length() >= 500) {
										bw.write(constraint);
										bw.newLine();
										constraint = "";
									}
									constraint += " + ";
								}
							}
							constraint = constraint.substring(0, constraint.length() - 3);
							if (constraint.length() >= 500) {
								bw.write(constraint);
								bw.newLine();
								constraint = "";
							}
							constraint += " <= 500000";
							bw.write(constraint);
							bw.newLine();
						}
					}
				}
				bw.newLine();

				// Only one park can be the first visited
				// sum(isFirst(n)) = 1
				constraint = "oneFirst: ";
				for (Stadium s : Stadium.values()) {
					constraint += "isFirst" + s;
					if (s.getIndex() < 29) {
						constraint += " + ";
					} else {
						constraint += " = 1";
					}
				}
				bw.write(constraint);
				bw.newLine();

				// Only one park can be the last visited
				// sum(isLast(n)) = 1
				constraint = "oneLast: ";
				for (Stadium s : Stadium.values()) {
					constraint += "isLast" + s;
					if (s.getIndex() < 29) {
						constraint += " + ";
					} else {
						constraint += " = 1";
					}
				}
				bw.write(constraint);
				bw.newLine();

				// timeToStart is the time from BAL to the first stadium
				// sum(isFirst(n)*BAL.minutesTo(n)) - timeToStart <= 0
				constraint = "timeToStart: ";
				for (Stadium s : Stadium.values()) {
					int travelTime = Stadium.BAL.getMinutesTo(s);
					if (travelTime > 300) {
						travelTime += Math.max(480, travelTime * 2 / 3);
					}
					constraint += travelTime + " isFirst" + s;
					if (s.getIndex() < 29) {
						constraint += " + ";
					} else {
						constraint += " - timeToStart <= 0";
						bw.write(constraint);
						bw.newLine();
					}
				}
				bw.newLine();

				// timeFromEnd is the time from the last stadium to BAL
				// sum(isLast(n)*n.minutesTo(BAL)) - timeFromEnd <= 0
				constraint = "timeFromEnd: ";
				for (Stadium s : Stadium.values()) {
					int travelTime = s.getMinutesTo(Stadium.BAL);
					if (travelTime > 130) {
						travelTime += Math.max(480, travelTime * 2 / 3);
					}
					constraint += travelTime + " isLast" + s;
					if (s.getIndex() < 29) {
						constraint += " + ";
					} else {
						constraint += " - timeFromEnd <= 0";
						bw.write(constraint);
						bw.newLine();
					}
				}
				bw.newLine();

				declaration = "BOUNDS";
				bw.write(declaration);
				bw.newLine();

				declaration = firstStartTime + "<= startTime <= " + (lastEndTime - TWENTY_NINE_DAYS);
				bw.write(declaration);
				bw.newLine();
				declaration = (firstStartTime + TWENTY_NINE_DAYS) + "<= endTime <= " + lastEndTime;
				bw.write(declaration);
				bw.newLine();
				declaration = "0 <= timeToStart <= 2880";
				bw.write(declaration);
				bw.newLine();
				declaration = "0 <= timeFromEnd <= 2880";
				bw.write(declaration);
				bw.newLine();

				declaration = "GENERAL";
				bw.write(declaration);
				bw.newLine();
				declaration = "endTime";
				bw.write(declaration);
				bw.newLine();
				declaration = "startTime";
				bw.write(declaration);
				bw.newLine();
				declaration = "timeToStart";
				bw.write(declaration);
				bw.newLine();
				declaration = "timeFromEnd";
				bw.write(declaration);
				bw.newLine();

				declaration = "BINARY";
				bw.write(declaration);
				bw.newLine();
				for (Stadium s : Stadium.values()) {
					declaration = "isFirst" + s;
					bw.write(declaration);
					bw.newLine();
					declaration = "isLast" + s;
					bw.write(declaration);
					bw.newLine();
				}
				for (Game g : gameRange) {
					bw.write(g.lpString());
					bw.newLine();
				}
				bw.write("END");
				bw.newLine();

				bw.close();

				System.out.println("Done");

			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

	private static Interval findAllStarBreak(List<Game> games) {
		int startIndex = 0;
		while (games.get(startIndex).getDate().getDayOfYear() + 1 >= games.get(startIndex + 1).getDate()
				.getDayOfYear()) {
			startIndex++;
		}
		return new Interval(games.get(startIndex).getDate().plusDays(1).withTimeAtStartOfDay(),
				games.get(startIndex + 1).getDate().withTimeAtStartOfDay());
	}

	private static void backtrack(List<Game> partial) {
		if (badSolution(partial)) {
			return;
		}
		if (validSolution(partial)) {
			partial = processSolution(partial);

		}
		partial = firstExtension(partial);
		while (partial != null) {
			backtrack(partial);
			partial = nextExtension(partial);
		}

	}

	private static List<Game> nextExtension(List<Game> partial) {
		if (partial.size() == 0) {
			return null;
		}
		int index = westCoast.indexOf(partial.remove(partial.size() - 1)) + 1;
		return extendSolution(partial, index);
	}

	private static List<Game> extendSolution(List<Game> partial, int index) {
		if (index >= westCoast.size()) {
			return null;
		}
		if (partial.size() == 0) {
			partial.add(westCoast.get(index));
			return partial;
		}
		while (index < westCoast.size() && partial.get(partial.size() - 1).getDate().getDayOfYear() + 2 >= westCoast
				.get(index).getDate().getDayOfYear()) {
			if (!haveVisitedStadium(partial, westCoast.get(index).getStadium())
					&& partial.get(partial.size() - 1).canReach(westCoast.get(index))) {
				partial.add(westCoast.get(index));
				return partial;
			}
			index++;
		}
		return null;
	}

	private static boolean haveVisitedStadium(List<Game> partial, Stadium stadium) {
		for (Game g : partial) {
			if (g.getStadium() == stadium) {
				return true;
			}
		}
		return false;
	}

	private static List<Game> firstExtension(List<Game> partial) {
		if (partial.size() > 0) {
			return extendSolution(partial, westCoast.indexOf(partial.get(partial.size() - 1)) + 1);
		}
		return extendSolution(partial, 0);
	}

	private static List<Game> processSolution(List<Game> partial) {
		dateRanges.add(new Interval(partial.get(0).getDate().withTimeAtStartOfDay(),
				partial.get(partial.size() - 1).getDate().plusDays(1).withTimeAtStartOfDay()));
		int index = westCoast.indexOf(partial.get(0)) + 1;
		while (index < westCoast.size()
				&& westCoast.get(index).getDate().getDayOfYear() == partial.get(0).getDate().getDayOfYear()) {
			index++;
		}
		partial.clear();
		partial.add(westCoast.get(index));
		return partial;

	}

	private static boolean validSolution(List<Game> partial) {
		// all stadium-related error checking is done prior to this point - we
		// only need to check the size of the solution.
		return partial.size() == 7;
	}

	private static boolean badSolution(List<Game> partial) {
		return travelDays(partial) > 9;
	}

	private static int travelDays(List<Game> partial) {
		if (partial.size() < 2) {
			return 1;
		}
		return partial.get(partial.size() - 1).getDate().getDayOfYear() - partial.get(0).getDate().getDayOfYear() + 1;
	}

}
