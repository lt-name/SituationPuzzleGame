package cn.lanink.situationpuzzlegame.game;

import cn.lanink.situationpuzzlegame.i18n.Texts;
import cn.nukkit.Player;
import cn.nukkit.ddui.Observable;
import cn.nukkit.lang.LangCode;
import java.util.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@RequiredArgsConstructor
public class GameRoom {

    public enum AnswerType {
        YES,
        NO,
        IRRELEVANT
    }

    @Getter
    @RequiredArgsConstructor
    public static class Question {
        private final String askerName;
        private final String question;
        @Setter private AnswerType answerType;

        public boolean isAnswered() {
            return answerType != null;
        }
    }

    private final String roomId;
    private final Player host;
    private final Set<Player> players = new LinkedHashSet<>();
    private final String puzzleTitle;
    private final String puzzleTruth;
    private final boolean singlePlayer;
    private LangCode languageCode = LangCode.zh_CN;
    private GameState state = GameState.WAITING;
    private final List<Question> questions = new ArrayList<>();

    @Getter(AccessLevel.NONE)
    private final Map<LangCode, Observable<String>> qaObservables = new EnumMap<>(LangCode.class);

    public GameRoom(String roomId, Player host, String puzzleTitle, String puzzleTruth) {
        this(roomId, host, puzzleTitle, puzzleTruth, false);
    }

    public void addPlayer(Player player) {
        players.add(player);
    }

    public void removePlayer(Player player) {
        players.remove(player);
    }

    public boolean isHost(Player player) {
        return host.equals(player);
    }

    public boolean hasPlayer(Player player) {
        return host.equals(player) || players.contains(player);
    }

    public void startGame() {
        state = GameState.PLAYING;
    }

    public void finishGame() {
        state = GameState.FINISHED;
    }

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

    public void answerQuestion(Question question, AnswerType answerType) {
        question.setAnswerType(answerType);
        refreshQaObservable();
    }

    public String buildQAText() {
        return buildQAText(languageCode);
    }

    public String buildQAText(LangCode lang) {
        if (questions.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Question q : questions) {
            sb.append("§b").append(q.getAskerName()).append("§f：").append(q.getQuestion()).append("\n");
            if (q.isAnswered()) {
                sb.append("  §7→ ").append(Texts.answerLabel(q.getAnswerType(), lang)).append("\n");
            } else {
                sb.append("  §7→ ").append(Texts.waitingAnswer(lang)).append("\n");
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

    public Observable<String> getQaObservable() {
        return getQaObservable(languageCode);
    }

    public Observable<String> getQaObservable(LangCode lang) {
        LangCode targetLang = lang == null ? languageCode : lang;
        return qaObservables.computeIfAbsent(targetLang, code -> new Observable<>(buildQAText(code)));
    }

    public void setLanguageCode(LangCode languageCode) {
        this.languageCode = languageCode == null ? LangCode.zh_CN : languageCode;
        refreshQaObservable();
    }

    public void refreshQaObservable() {
        for (Map.Entry<LangCode, Observable<String>> entry : qaObservables.entrySet()) {
            entry.getValue().setValue(buildQAText(entry.getKey()));
        }
    }
}
