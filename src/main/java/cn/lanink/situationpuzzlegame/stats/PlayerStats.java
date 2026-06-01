package cn.lanink.situationpuzzlegame.stats;

import cn.nukkit.utils.ConfigSection;

public class PlayerStats {

    private final String playerName;
    private long lastActiveTime;

    private int soloGamesPlayed;
    private int soloGamesCompleted;
    private int soloGamesAbandoned;
    private int soloQuestionsAsked;
    private int soloQuestionsHit;
    private int soloCurrentStreak;
    private int soloBestStreak;

    private int multiGamesPlayed;
    private int multiGamesCompleted;
    private int multiGamesAbandoned;
    private int multiQuestionsAsked;
    private int multiQuestionsHit;

    private int hostCount;

    public PlayerStats(String playerName) {
        this.playerName = playerName;
    }

    public PlayerStats(String playerName, ConfigSection section) {
        this.playerName = section.getString("playerName", playerName);
        this.lastActiveTime = section.getLong("lastActiveTime", 0);

        ConfigSection solo = section.getSection("solo");
        this.soloGamesPlayed = solo.getInt("gamesPlayed", 0);
        this.soloGamesCompleted = solo.getInt("gamesCompleted", 0);
        this.soloGamesAbandoned = solo.getInt("gamesAbandoned", 0);
        this.soloQuestionsAsked = solo.getInt("questionsAsked", 0);
        this.soloQuestionsHit = solo.getInt("questionsHit", 0);
        this.soloCurrentStreak = solo.getInt("currentStreak", 0);
        this.soloBestStreak = solo.getInt("bestStreak", 0);

        ConfigSection multi = section.getSection("multi");
        this.multiGamesPlayed = multi.getInt("gamesPlayed", 0);
        this.multiGamesCompleted = multi.getInt("gamesCompleted", 0);
        this.multiGamesAbandoned = multi.getInt("gamesAbandoned", 0);
        this.multiQuestionsAsked = multi.getInt("questionsAsked", 0);
        this.multiQuestionsHit = multi.getInt("questionsHit", 0);

        ConfigSection host = section.getSection("host");
        this.hostCount = host.getInt("count", 0);
    }

    public void saveTo(ConfigSection section) {
        section.put("playerName", playerName);
        section.put("lastActiveTime", lastActiveTime);

        ConfigSection solo = new ConfigSection();
        solo.put("gamesPlayed", soloGamesPlayed);
        solo.put("gamesCompleted", soloGamesCompleted);
        solo.put("gamesAbandoned", soloGamesAbandoned);
        solo.put("questionsAsked", soloQuestionsAsked);
        solo.put("questionsHit", soloQuestionsHit);
        solo.put("currentStreak", soloCurrentStreak);
        solo.put("bestStreak", soloBestStreak);
        section.put("solo", solo);

        ConfigSection multi = new ConfigSection();
        multi.put("gamesPlayed", multiGamesPlayed);
        multi.put("gamesCompleted", multiGamesCompleted);
        multi.put("gamesAbandoned", multiGamesAbandoned);
        multi.put("questionsAsked", multiQuestionsAsked);
        multi.put("questionsHit", multiQuestionsHit);
        section.put("multi", multi);

        ConfigSection host = new ConfigSection();
        host.put("count", hostCount);
        section.put("host", host);
    }

    // --- 增量方法 ---

    public void recordSoloGamePlayed() {
        soloGamesPlayed++;
        lastActiveTime = System.currentTimeMillis();
    }

    public void recordSoloGameCompleted() {
        soloGamesCompleted++;
        soloCurrentStreak++;
        if (soloCurrentStreak > soloBestStreak) {
            soloBestStreak = soloCurrentStreak;
        }
        lastActiveTime = System.currentTimeMillis();
    }

    public void recordSoloGameAbandoned() {
        soloGamesAbandoned++;
        soloCurrentStreak = 0;
    }

    public void recordSoloQuestion() {
        soloQuestionsAsked++;
    }

    public void recordSoloHit() {
        soloQuestionsHit++;
    }

    public void recordMultiGamePlayed() {
        multiGamesPlayed++;
        lastActiveTime = System.currentTimeMillis();
    }

    public void recordMultiGameCompleted() {
        multiGamesCompleted++;
        lastActiveTime = System.currentTimeMillis();
    }

    public void recordMultiGameAbandoned() {
        multiGamesAbandoned++;
    }

    public void recordMultiQuestion() {
        multiQuestionsAsked++;
    }

    public void recordMultiHit() {
        multiQuestionsHit++;
    }

    public void recordHostGame() {
        hostCount++;
        lastActiveTime = System.currentTimeMillis();
    }

    // --- 计算方法 ---

    public int getTotalGamesPlayed() {
        return soloGamesPlayed + multiGamesPlayed;
    }

    public int getTotalGamesCompleted() {
        return soloGamesCompleted + multiGamesCompleted;
    }

    public int getTotalQuestionsAsked() {
        return soloQuestionsAsked + multiQuestionsAsked;
    }

    public double getSoloCompletionRate() {
        return soloGamesPlayed == 0 ? 0 : (double) soloGamesCompleted / soloGamesPlayed;
    }

    public double getMultiCompletionRate() {
        return multiGamesPlayed == 0 ? 0 : (double) multiGamesCompleted / multiGamesPlayed;
    }

    public double getHitRate() {
        int total = soloQuestionsAsked + multiQuestionsAsked;
        if (total == 0) return 0;
        return (double) (soloQuestionsHit + multiQuestionsHit) / total;
    }

    // --- getter ---

    public String getPlayerName() { return playerName; }
    public long getLastActiveTime() { return lastActiveTime; }
    public int getSoloGamesPlayed() { return soloGamesPlayed; }
    public int getSoloGamesCompleted() { return soloGamesCompleted; }
    public int getSoloGamesAbandoned() { return soloGamesAbandoned; }
    public int getSoloQuestionsAsked() { return soloQuestionsAsked; }
    public int getSoloQuestionsHit() { return soloQuestionsHit; }
    public int getSoloCurrentStreak() { return soloCurrentStreak; }
    public int getSoloBestStreak() { return soloBestStreak; }
    public int getMultiGamesPlayed() { return multiGamesPlayed; }
    public int getMultiGamesCompleted() { return multiGamesCompleted; }
    public int getMultiGamesAbandoned() { return multiGamesAbandoned; }
    public int getMultiQuestionsAsked() { return multiQuestionsAsked; }
    public int getMultiQuestionsHit() { return multiQuestionsHit; }
    public int getHostCount() { return hostCount; }
}
