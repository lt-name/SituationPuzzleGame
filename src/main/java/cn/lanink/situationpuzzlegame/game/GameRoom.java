package cn.lanink.situationpuzzlegame.game;

import cn.nukkit.Player;
import cn.nukkit.ddui.Observable;

import java.util.*;

public class GameRoom {

    public static class Question {
        private final String askerName;
        private final String question;
        private String answer;

        public Question(String askerName, String question) {
            this.askerName = askerName;
            this.question = question;
        }

        public String getAskerName() { return askerName; }
        public String getQuestion() { return question; }
        public String getAnswer() { return answer; }
        public boolean isAnswered() { return answer != null; }
        public void setAnswer(String answer) { this.answer = answer; }
    }

    private final String roomId;
    private final Player host;
    private final Set<Player> players = new LinkedHashSet<>();
    private final String puzzleTitle;
    private final String puzzleTruth;
    private final boolean singlePlayer;
    private GameState state = GameState.WAITING;
    private final List<Question> questions = new ArrayList<>();
    private final Observable<String> qaObservable = new Observable<>("");

    public GameRoom(String roomId, Player host, String puzzleTitle, String puzzleTruth) {
        this(roomId, host, puzzleTitle, puzzleTruth, false);
    }

    public GameRoom(String roomId, Player host, String puzzleTitle, String puzzleTruth, boolean singlePlayer) {
        this.roomId = roomId;
        this.host = host;
        this.puzzleTitle = puzzleTitle;
        this.puzzleTruth = puzzleTruth;
        this.singlePlayer = singlePlayer;
    }

    public void addPlayer(Player player) { players.add(player); }

    public void removePlayer(Player player) { players.remove(player); }

    public boolean isHost(Player player) { return host.equals(player); }

    public boolean hasPlayer(Player player) {
        return host.equals(player) || players.contains(player);
    }

    public void startGame() { state = GameState.PLAYING; }

    public void finishGame() { state = GameState.FINISHED; }

    public Question askQuestion(Player asker, String question) {
        Question q = new Question(asker.getName(), question);
        questions.add(q);
        refreshQaObservable();
        return q;
    }

    public Question getFirstUnansweredQuestion() {
        for (Question q : questions) {
            if (!q.isAnswered()) return q;
        }
        return null;
    }

    public int getUnansweredCount() {
        int count = 0;
        for (Question q : questions) {
            if (!q.isAnswered()) count++;
        }
        return count;
    }

    public void answerQuestion(Question question, String answer) {
        question.setAnswer(answer);
        refreshQaObservable();
    }

    public String buildQAText() {
        if (questions.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Question q : questions) {
            sb.append("§b").append(q.getAskerName()).append("§f：").append(q.getQuestion()).append("\n");
            if (q.isAnswered()) {
                sb.append("  §7→ ").append(q.getAnswer()).append("\n");
            } else {
                sb.append("  §7→ 等待回答...\n");
            }
        }
        return sb.toString();
    }

    public Set<Player> getAllPlayers() {
        Set<Player> all = new LinkedHashSet<>();
        all.add(host);
        all.addAll(players);
        return all;
    }

    public String getRoomId() { return roomId; }
    public Player getHost() { return host; }
    public Set<Player> getPlayers() { return players; }
    public String getPuzzleTitle() { return puzzleTitle; }
    public String getPuzzleTruth() { return puzzleTruth; }
    public GameState getState() { return state; }
    public List<Question> getQuestions() { return questions; }
    public boolean isSinglePlayer() { return singlePlayer; }
    public Observable<String> getQaObservable() { return qaObservable; }

    public void refreshQaObservable() {
        qaObservable.setValue(buildQAText());
    }
}
