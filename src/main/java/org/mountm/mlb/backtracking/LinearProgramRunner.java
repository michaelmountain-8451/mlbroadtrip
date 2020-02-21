package org.mountm.mlb.backtracking;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;


public class LinearProgramRunner {
	private static List<Game> gameList = new ArrayList<Game>(2430);

	public static void main(String[] args) {
		BufferedReader input = null;
		String currentLine;
		try {
			input = new BufferedReader(new FileReader("Games.csv"));
			while ((currentLine = input.readLine()) != null) {
				int delimiter = currentLine.indexOf(",");
				DateTimeFormatter format = DateTimeFormat.forPattern("MM/dd/yyyy kk:mm");
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

		Interval allStarBreak = findAllStarBreak(gameList);
		System.out.println("ASB starts on " + allStarBreak.getStart().toString("MMM dd") + " and ends on "
				+ allStarBreak.getEnd().toString("MMM dd"));

		int counter = 1;
		int firstGameDay = gameList.get(0).dayOfYear();
		int lastGameDay = gameList.get(gameList.size() - 1).dayOfYear();
		for (int startDay = firstGameDay; startDay <= lastGameDay - 30; startDay++) {
			int index = 0;
			List<Game> gameRange = new ArrayList<Game>(450);
			boolean isAfterASB = allStarBreak.getEnd().getDayOfYear() <= startDay;
			if (!isAfterASB && allStarBreak.getStart().getDayOfYear() <= startDay + 30) {
				continue;
			}
			while (gameList.get(index).dayOfYear() < startDay) {
				index++;
			}
			while (index < gameList.size() && startDay + 30 >= gameList.get(index).dayOfYear()) {
				gameRange.add(gameList.get(index++));
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

				objective = "MINIMIZE endTime - startTime";
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


				declaration = "BOUNDS";
				bw.write(declaration);
				bw.newLine();

				declaration = firstStartTime + "<= startTime <= " + lastEndTime;
				bw.write(declaration);
				bw.newLine();
				declaration = firstStartTime + "<= endTime <= " + lastEndTime;
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

}
