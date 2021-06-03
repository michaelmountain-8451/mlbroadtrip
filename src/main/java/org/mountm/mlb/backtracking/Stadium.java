package org.mountm.mlb.backtracking;

/**
 * An enum representing the 30 current Major League Baseball stadiums.
 * 
 */
public enum Stadium {

	BAL (0,0), BOS (1,0), NYY (2,0), TBR (3,0), TOR (4,0), 
    CWS (5,1), CLE (6,0), DET (7,0), KCR (8,1), MIN (9,1), 
    HOU(10,1), LAA(11,3), OAK(12,3), SEA(13,3), TEX(14,1), 
    ATL(15,0), MIA(16,0), NYM(17,0), PHI(18,0), WAS(19,0), 
    CHC(20,1), CIN(21,0), MIL(22,1), PIT(23,0), STL(24,1), 
    ARI(25,3), COL(26,2), LAD(27,3), SDP(28,3), SFG(29,3);

	private final int index;
	
	// Time zone is stored if the input file contains local times instead of a
	// constant time zone. Not currently used.
	private final int timeZone;

	// A 2D array storing the driving times (in minutes) between each pair of
	// stadiums. This is an asymmetric matrix.
	private static final int[][] minutesBetween = {
		//  0,   1,   2,   3,   4,   5,   6,   7,   8,   9,  10,  11,  12,  13,  14,  15,  16,  17,  18,  19,  20,  21,  22,  23,  24,  25,  26,  27,  28,  29
		{   0, 381, 190, 860, 480, 631, 344, 481, 955,1007,1269,2337,2474,2434,1226, 619, 946, 201,  96,  49, 650, 474, 727, 236, 751,2041,1465,2346,2349,2478},
		{ 381,   0, 201,1233, 503, 880, 578, 662,1267,1255,1638,2598,2722,2682,1595, 992,1320, 198, 302, 424, 898, 791, 976, 553,1063,2354,1724,2607,2660,2727},
		{ 188, 199,   0,1040, 466, 718, 431, 567,1085,1093,1447,2435,2560,2520,1404, 799,1126,  16, 108, 230, 736, 605, 814, 366, 882,2172,1562,2444,2480,2564},
		{ 855,1230,1040,   0,1242,1039,1006,1044,1096,1388, 855,2172,2509,2715,1008, 420, 225,1051, 946, 818,1058, 808,1132, 956, 885,1859,1606,2194,2112,2529},
		{ 482, 505, 468,1243,   0, 472, 281, 236, 900, 844,1406,2187,2311,2271,1297, 863,1329, 479, 471, 518, 490, 471, 566, 300, 707,1997,1313,2195,2249,2316},
		{ 634, 880, 722,1042, 472,   0, 308, 250, 457, 383, 964,1745,1869,1810, 856, 628,1196, 733, 690, 644,  22, 271, 100, 415, 264,1554, 871,1753,1807,1874},
		{ 346, 577, 434,1006, 281, 308,   0, 157, 700, 683,1169,2026,2150,2110,1060, 617,1093, 445, 402, 356, 326, 224, 404, 127, 496,1787,1152,2034,2088,2155},
		{ 483, 663, 571,1047, 235, 251, 157,   0, 679, 624,1178,1966,2091,2051,1069, 634,1201, 582, 539, 493, 270, 242, 346, 264, 484,1774,1093,1975,2029,2095},
		{ 952,1266,1090,1098, 899, 456, 700, 677,   0, 390, 682,1390,1592,1624, 485, 684,1252,1101,1013, 962, 473, 520, 494, 747, 215,1130, 514,1398,1437,1597},
		{1007,1253,1095,1388, 841, 377, 681, 619, 389,   0,1067,1658,1743,1437, 866, 974,1541,1106,1063,1017, 375, 637, 292, 788, 512,1510, 785,1667,1721,1747},
		{1270,1641,1452, 859,1404, 963,1166,1175, 676,1061,   0,1323,1659,2078, 228, 693,1012,1463,1360,1244, 982, 948,1048,1208, 727,1009, 925,1344,1262,1679},
		{2335,2599,2441,2170,2188,1745,2027,1966,1390,1659,1316,   0, 370,1080,1211,1898,2323,2452,2396,2337,1764,1901,1763,2130,1590, 321, 887,  47,  91, 390},
		{2470,2716,2558,2503,2305,1862,2144,2083,1582,1735,1650, 361,   0, 748,1509,2164,2657,2569,2526,2480,1881,2075,1880,2251,1794, 655,1101, 328, 445,  25},
		{2437,2683,2525,2719,2272,1807,2111,2050,1624,1442,2072,1072, 750,   0,1855,2305,2873,2536,2493,2447,1806,2061,1722,2218,1836,1292,1156,1039,1157, 755},
		{1223,1593,1404,1011,1293, 852,1054,1063, 483, 865, 227,1220,1511,1861,   0, 702,1165,1415,1312,1196, 870, 836, 923,1097, 601, 906, 708,1241,1159,1531},
		{ 617, 991, 801, 422, 862, 628, 615, 632, 684, 977, 691,1900,2164,2304, 703,   0, 576, 812, 707, 579, 646, 397, 720, 636, 474,1598,1194,1909,1851,2184},
		{ 940,1315,1124, 227,1326,1192,1090,1197,1249,1541,1009,2326,2662,2869,1161, 573,   0,1135,1030, 902,1211, 962,1285,1041,1038,2012,1759,2347,2265,2682},
		{ 199, 197,  16,1051, 477, 729, 442, 578,1097,1104,1459,2447,2571,2531,1415, 810,1137,   0, 119, 241, 747, 616, 825, 377, 893,2183,1573,2455,2491,2576},
		{  95, 299, 108, 947, 471, 690, 403, 539,1015,1065,1358,2397,2532,2492,1315, 706,1033, 120,   0, 137, 708, 534, 786, 295, 811,2102,1525,2406,2409,2537},
		{  45, 420, 229, 817, 514, 637, 350, 486, 960,1012,1238,2335,2479,2439,1195, 576, 903, 240, 135,   0, 655, 480, 733, 242, 757,2039,1470,2344,2345,2484},
		{ 652, 898, 740,1060, 490,  22, 326, 268, 472, 380, 983,1757,1881,1807, 874, 647,1214, 751, 708, 662,   0, 289,  97, 433, 279,1569, 883,1765,1819,1886},
		{ 470, 788, 608, 812, 472, 271, 222, 242, 523, 641, 950,1904,2088,2060, 842, 398, 966, 619, 530, 480, 289,   0, 363, 265, 318,1608,1033,1912,1916,2092},
		{ 732, 978, 820,1132, 565, 100, 406, 343, 493, 295,1047,1762,1886,1721, 927, 719,1286, 831, 788, 742,  98, 361,   0, 513, 326,1614, 888,1771,1824,1891},
		{ 234, 552, 367, 956, 300, 414, 127, 263, 747, 789,1211,2129,2256,2216,1103, 635,1042, 379, 290, 245, 432, 266, 510,   0, 543,1833,1257,2138,2141,2261},
		{ 748,1062, 886, 887, 706, 263, 496, 481, 214, 515, 724,1590,1802,1834, 605, 474,1041, 897, 809, 759, 280, 316, 325, 543,   0,1294, 724,1598,1602,1807},
		{2035,2348,2172,1856,1992,1550,1782,1767,1122,1504,1003, 321, 658,1291, 897,1591,2009,2183,2095,2037,1567,1601,1608,1830,1290,   0, 766, 342, 319, 677},
		{1460,1723,1565,1606,1311, 869,1151,1089, 511, 783, 922, 883,1105,1160, 705,1193,1760,1576,1521,1471, 888,1028, 887,1255, 723, 767,   0, 892, 945,1109},
		{2343,2607,2449,2189,2195,1753,2035,1973,1398,1667,1335,  39, 330,1040,1230,1906,2342,2460,2403,2345,1772,1909,1771,2138,1598, 340, 895,   0, 123, 350},
		{2346,2662,2484,2110,2251,1808,2090,2029,1433,1722,1257,  89, 449,1159,1152,1846,2264,2495,2406,2342,1827,1912,1826,2141,1601, 322, 950, 128,   0, 469},
		{2471,2717,2559,2518,2306,1864,2145,2084,1584,1736,1665, 376,  21, 749,1524,2180,2672,2570,2527,2481,1882,2076,1882,2252,1795, 670,1102, 343, 461,   0}
	};
	
	private static final boolean[][] canFly = {
		//   0,    1,    2,    3,    4,    5,    6,    7,    8,    9,   10,   11,   12,   13,   14,   15,   16,   17,   18,   19,   20,   21,   22,   23,   24,   25,   26,   27,   28,   29
		{false, true, true,false, true,false, true,false,false,false,false,false,false,false,false,false,false, true,false,false,false, true,false, true,false,false,false,false,false,false}, //0
		{ true,false, true,false,false,false,false,false,false,false,false,false,false,false,false,false,false, true, true, true,false,false,false,false,false,false,false,false,false,false}, //1
		{ true, true,false,false, true,false, true,false,false,false,false,false,false,false,false,false,false,false,false, true,false,false,false, true,false,false,false,false,false,false}, //2
		{false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false, true,false,false,false,false,false,false,false,false,false,false,false,false,false}, //3
		{ true,false, true,false,false,false, true, true,false,false,false,false,false,false,false,false,false, true, true, true,false,false,false,false,false,false,false,false,false,false}, //4
		{false,false,false,false,false,false, true, true, true, true,false,false,false,false,false,false,false,false,false,false,false, true,false,false, true,false,false,false,false,false}, //5
		{ true,false, true,false, true, true,false, true,false,false,false,false,false,false,false,false,false, true, true, true, true, true, true,false,false,false,false,false,false,false}, //6
		{false,false,false,false, true, true, true,false,false,false,false,false,false,false,false,false,false,false,false,false, true, true, true, true,false,false,false,false,false,false}, //7
		{false,false,false,false,false, true,false,false,false,false,false,false,false,false,false,false,false,false,false,false, true,false, true,false, true,false,false,false,false,false}, //8
		{false,false,false,false,false, true,false,false,false,false,false,false,false,false,false,false,false,false,false,false, true,false, true,false,false,false,false,false,false,false}, //9
		{false,false,false,false,false,false,false,false,false,false,false,false,false,false, true,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false}, //10
		{false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false, true,false,false,false,false}, //11
		{false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false}, //12
		{false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false}, //13
		{false,false,false,false,false,false,false,false,false,false, true,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false}, //14
		{false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false}, //15
		{false,false,false, true,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false}, //16
		{ true, true,false,false, true,false, true,false,false,false,false,false,false,false,false,false,false,false,false, true,false,false,false, true,false,false,false,false,false,false}, //17
		{false, true,false,false, true,false, true,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false, true,false,false,false,false,false,false}, //18
		{false, true, true,false, true,false, true,false,false,false,false,false,false,false,false,false,false, true,false,false,false, true,false, true,false,false,false,false,false,false}, //19
		{false,false,false,false,false,false, true, true, true, true,false,false,false,false,false,false,false,false,false,false,false, true,false,false, true,false,false,false,false,false}, //20
		{ true,false,false,false,false, true, true, true,false,false,false,false,false,false,false,false,false,false,false, true, true,false,false, true,false,false,false,false,false,false}, //21
		{false,false,false,false,false,false, true, true, true, true,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false}, //22
		{ true,false, true,false,false,false,false, true,false,false,false,false,false,false,false,false,false, true, true, true,false, true,false,false,false,false,false,false,false,false}, //23
		{false,false,false,false,false, true,false,false, true,false,false,false,false,false,false,false,false,false,false,false, true,false,false,false,false,false,false,false,false,false}, //24
		{false,false,false,false,false,false,false,false,false,false,false, true,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false, true, true,false}, //25
		{false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false}, //26
		{false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false, true,false,false,false,false}, //27
		{false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false, true,false,false,false,false}, //28
		{false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false}  //29
			
	};

	Stadium(int index, int timeZone) {
		this.index = index;
		this.timeZone = timeZone;
	}

	public int getIndex() {
		return index;
	}

	public int getTimeZone() {
		return timeZone;
	}

	public int getMinutesTo(Stadium s) {
		return minutesBetween[index][s.index];
	}

	/**
	 * 
	 * @return An int whose binary representation is a <code>1</code> followed
	 *         by a number of <code>0</code>'s equal to this stadium's index
	 *         (between 0 and 30).
	 */
	public int getMask() {
		return 1 << index;
	}

	public boolean canFlyTo(Stadium s) {
		return canFly[index][s.index];
	}

}
