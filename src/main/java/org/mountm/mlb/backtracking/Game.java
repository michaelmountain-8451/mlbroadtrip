package org.mountm.mlb.backtracking;

import org.joda.time.DateTime;
import org.joda.time.Minutes;

/**
 * Immutable object representing a baseball game. Implements Comparable so that
 * a List of games can be sorted.
 * 
 */
public class Game implements Comparable<Game> {

	private Stadium stadium;
	private DateTime startTime;

	private static final int TIME_OF_GAME = 240;
	private static final int NINE_AM = 32400000;
	private static final int TEN_PM = 79200000;
	private static final int MAX_DRIVING = 720;

	public Game(Stadium home, DateTime startTime) {
		this.stadium = home;
		this.startTime = startTime;
	}

	public Game() {
		this.stadium = Stadium.ARI;
		this.startTime = DateTime.now();
	}

	public Stadium getStadium() {
		return stadium;
	}

	public DateTime getStartTime() {
		return startTime;
	}

	/**
	 * @return the day of the year that this game occurs on
	 */
	public int dayOfYear() {
		// used as a more meaningful key in the missedStadiums map
		return startTime.getDayOfYear();
	}

	public int stadiumIndex() {
		return stadium.getIndex();
	}

	/**
	 * 
	 * @param g
	 *            The game you are going to next.
	 * @return The strict driving time between this game and the next game.
	 */
	public int getMinutesTo(Game g) {
		return stadium.getMinutesTo(g.stadium);
	}

	/**
	 * Determines if the specified game can be reached from this game in a
	 * reasonable amount of time.
	 * 
	 * @param g
	 *            The game we are attempting to reach
	 * @return <code>true</code> if the specified game can be reached from this
	 *         game; <code>false</code> otherwise
	 */
	public boolean canReach(Game g) {
		// This is a loose interpretation of canReach, where the argument must
		// be after the game which calls the function (but not more than 2
		// calendars days after).
		if (startTime.isAfter(g.startTime.minusHours(4))
				|| (g.startTime.getDayOfYear() - startTime.getDayOfYear()) > 2) {
			return false;
		}
		// It is assumed that each game lasts 4 hours
		int timeAvailable = Minutes.minutesBetween(startTime, g.startTime)
				.getMinutes() - 240;
		int travelTime = stadium.getMinutesTo(g.stadium);
		int daysBetween = g.startTime.getDayOfYear() - startTime.getDayOfYear();
		if (daysBetween == 0) {
			return timeAvailable > travelTime;
		}
		int drivingAfterGame = Minutes.minutesBetween(
				startTime.plusMinutes(TIME_OF_GAME),
				startTime.withMillisOfDay(TEN_PM).plusHours(
						stadium.getTimeZone())).getMinutes();
		travelTime = Math.min(travelTime, travelTime - drivingAfterGame);
		boolean useDestinationTimeZone = ((daysBetween > 1) || drivingAfterGame > 0);
		if (daysBetween == 2) {
			travelTime -= MAX_DRIVING;
		}
		return (useDestinationTimeZone ? Minutes.minutesBetween(
				g.startTime.withMillisOfDay(NINE_AM).plusHours(
						g.stadium.getTimeZone()), g.startTime).getMinutes()
				: Minutes.minutesBetween(
						g.startTime.withMillisOfDay(NINE_AM).plusHours(
								stadium.getTimeZone()), g.startTime)
						.getMinutes()) > travelTime;
		// Example 1: Game 1 starts Tuesday night at 7 PM. Game 2 starts
		// Thursday night at 7 PM, 16 hours away. The required travel time is 32
		// hours - the function returns true.
		//
		// Example 2: Game 1 starts Wednesday afternoon at 2 PM. Game 2 starts
		// Thursday night at 7 PM, 16 hours away. The required travel time is
		// not 24, but 26.67 hours - the function returns false.
		//
		// Example 3: Game 1 starts Wednesday afternoon at 1 PM. Game 2 starts
		// Wednesday night at 7 PM, 2 hours away. The required travel time is 2
		// hours - the function returns true.
	}

	@Override
	public String toString() {
		return stadium + " " + startTime.toString("M/dd hh:mm aa");
	}

	/**
	 * Compares two games based on their start times and locations
	 * 
	 * @return 0 if the games start at the same time and stadium; a value less
	 *         than 0 if this game starts before the specified game; and a value
	 *         greater than 0 if this game starts after the specified game.
	 */
	public int compareTo(Game g) {
		int startTimeDiff = Long.compare(startTime.getMillis(),
				g.startTime.getMillis());
		return startTimeDiff == 0 ? stadium.compareTo(g.stadium)
				: startTimeDiff;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((stadium == null) ? 0 : stadium.hashCode());
		result = prime * result
				+ ((startTime == null) ? 0 : startTime.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Game other = (Game) obj;
		if (stadium != other.stadium)
			return false;
		if (startTime == null) {
			if (other.startTime != null)
				return false;
		} else if (!startTime.equals(other.startTime))
			return false;
		return true;
	}

}
