package cn.lanink.situationpuzzlegame.i18n;

import cn.lanink.situationpuzzlegame.SituationPuzzleGame;
import cn.lanink.situationpuzzlegame.game.GameRoom;
import cn.nukkit.Player;
import cn.nukkit.lang.LangCode;
import cn.nukkit.lang.PluginI18n;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Texts {

    private static volatile PluginI18n sharedI18n;

    public static void bind(PluginI18n i18n) {
        sharedI18n = i18n;
    }

    public static LangCode lang(Player player) {
        if (player == null || player.getLanguageCode() == null) {
            return LangCode.zh_CN;
        }
        return normalize(player.getLanguageCode());
    }

    public static LangCode lang(GameRoom room) {
        if (room == null || room.getLanguageCode() == null) {
            return LangCode.zh_CN;
        }
        return normalize(room.getLanguageCode());
    }

    public static String t(SituationPuzzleGame plugin, Player player, String key, Object... args) {
        return t(plugin, lang(player), key, args);
    }

    public static String t(SituationPuzzleGame plugin, GameRoom room, String key, Object... args) {
        return t(plugin, lang(room), key, args);
    }

    public static String t(SituationPuzzleGame plugin, LangCode lang, String key, Object... args) {
        PluginI18n i18n = plugin == null ? sharedI18n : plugin.getI18n();
        if (i18n == null) {
            i18n = sharedI18n;
        }
        if (i18n == null) {
            return key;
        }
        String text = i18n.getOrOriginal(normalize(lang), key);
        if (text == null) {
            return key;
        }
        for (int i = 0; i < args.length; i++) {
            text = text.replace("{%" + i + "}", String.valueOf(args[i]));
        }
        return text.replace("\\n", "\n");
    }

    public static boolean isEnglish(LangCode lang) {
        return normalize(lang) == LangCode.en_US;
    }

    public static LangCode normalize(LangCode lang) {
        if (lang == null) {
            return LangCode.zh_CN;
        }
        return switch (lang) {
            case en_GB -> LangCode.en_US;
            case zh_TW -> LangCode.zh_CN;
            default -> lang;
        };
    }

    public static String answerLabel(GameRoom.AnswerType answerType, LangCode lang) {
        return code(answerType)
                + label(
                        lang,
                        switch (answerType) {
                            case YES -> "answer.yes";
                            case NO -> "answer.no";
                            case IRRELEVANT -> "answer.irrelevant";
                        },
                        fallbackLabel(answerType, lang));
    }

    public static String waitingAnswer(LangCode lang) {
        return label(lang, "qa.waiting-answer", isEnglish(lang) ? "Waiting for answer..." : "等待回答...");
    }

    private static String code(GameRoom.AnswerType answerType) {
        return switch (answerType) {
            case YES -> "§a";
            case NO -> "§c";
            case IRRELEVANT -> "§7";
        };
    }

    private static String fallbackLabel(GameRoom.AnswerType answerType, LangCode lang) {
        if (isEnglish(lang)) {
            return switch (answerType) {
                case YES -> "Yes";
                case NO -> "No";
                case IRRELEVANT -> "Irrelevant";
            };
        }
        return switch (answerType) {
            case YES -> "是";
            case NO -> "不是";
            case IRRELEVANT -> "与此无关";
        };
    }

    private static String label(LangCode lang, String key, String fallback) {
        PluginI18n i18n = sharedI18n;
        if (i18n == null) {
            return fallback;
        }
        String text = i18n.getOrOriginal(normalize(lang), key);
        if (text == null || text.equals(key)) {
            return fallback;
        }
        return text;
    }
}
