This project contains class files for the `Game` and `Stadium` objects, along with three runner classes.

`TimeZoneConverter` reads a raw data file (GamesRaw.csv) with the MLB master schedule by team, with all times local. No program arguments are required. It converts times to EST, sorts the complete game list by start date, and writes it to a new file (Games.csv). This should be run once after the master schedule is released. It does not need to be run again.

`LinearProgramRunner` reads the sorted `Games.csv` file and constructs a number of linear programs based on slices of the schedule. No program arguments are required. Travel restrictions are set using the constraints defined in the `Game` class. The season slices are constructed by scanning for time periods where all 7 west coast teams (Anaheim, Oakland, Seattle, Los Angeles, San Diego, San Francisco, and Phoenix) can be visited within a 9-day span. The linear programs are written to numbered data files `MLBTSPxx.lp` which start from the earliest part of the season and range to the latest part. These models can be used as input to a third party solver that understands the LP format and optimized for minimum trip duration.

`BacktrackingRunner` reads the sorted `Games.csv` file and uses the backtracking algorithm to find the route that requires the least driving time. **At least 2 program arguments are required.** It prints data to the console while searching for solutions, and also writes a data file containing information about known invalid candidates to allow for multiple executions that rely on processing output from previous runs. (This would be an interesting way to implement multi-threading)

* args[0] **(Required)** - The maximum allowed days for a valid solution.
* args[1] **(Required)** - The maximum allowed driving time for a valid solution (if no solution is known, this can be set to an arbitrarily large value like 50000
* args[2] through args[31] *(Optional)* - You may specify the index of games that appear in the root candidate, and therefore must appear as the first *n* games in any solution. It is strongly recommended to specify at least one value.

In addition, it is strongly recommended to increase the memory allocation to the JVM as high as possible - particularly if you are not reducing the search space by providing several games for the root candidate. 

For the 2018 schedule, the optimal solution can be found by specifying program arguments of `35 50000 1455` and waiting for a while.


## Possible Improvements

* Dynamic driving time calculations based on day of week and time of day instead of static values that assume minimal traffic
* Improved memory usage
* Multithreading of the backtracking algorithm to allow several subtrees to be searched simultaneously
* Python port
* Make the optimization more complex - factor in things like who the visiting team is (see lots of Orioles games, or prioritize classic rivalries like Cubs-Cardinals, Giants-Dodgers, or Red Sox-Yankees), consider allowing longer duration trips if the driving time can be cut significantly, etc.