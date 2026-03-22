package com.example.backend.check;

import com.example.backend.model.domain.ParagraphInfo;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Какие абзацы считаются «основным текстом» для ФТ-8 и ФТ-9: не таблица, не формула, не заголовок
 * по outline, не оглавление и не подписи к рисунку/таблице.
 */
public final class BodyParagraphRules {

    /** Без \\b: в Java граница слова не считает кириллицу «словом», и «Рисунок 1» не отсекался. */
    private static final Pattern FIGURE_TABLE_CAPTION = Pattern.compile(
            "^\\s*(Рисунок|Таблица|Листинг|Listing)(\\s|[-–—]|$)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    /** Строка оглавления: таб или ≥2 пробела перед номером страницы в конце (п. 3.4). */
    private static final Pattern TOC_TAIL_PAGE_NUM = Pattern.compile("(?:\\t|\\s{2,})\\d{1,3}\\s*$");

    private BodyParagraphRules() {
    }

    public static boolean isMainBodyTextForFormatting(ParagraphInfo p) {
        if (p == null) {
            return false;
        }
        if (p.isInTable()) {
            return false;
        }
        if (p.isContainsFormula()) {
            return false;
        }
        if (p.getOutlineLevel() != null) {
            return false;
        }
        String t = p.getText();
        if (t == null || t.trim().isEmpty()) {
            return false;
        }
        if (isTocParagraphStyle(p)) {
            return false;
        }
        if (FIGURE_TABLE_CAPTION.matcher(t.trim()).find()) {
            return false;
        }
        if (looksLikeTocEntryLine(t)) {
            return false;
        }
        if (isCodeLikeFont(p.getFontName())) {
            return false;
        }
        return true;
    }

    private static boolean isCodeLikeFont(String fontName) {
        if (fontName == null) {
            return false;
        }
        String n = fontName.toLowerCase(Locale.ROOT);
        return n.contains("courier") || n.contains("consolas") || n.contains("monospace");
    }

    static boolean looksLikeTocEntryLine(String text) {
        if (text == null) {
            return false;
        }
        String t = text.replace('\u00A0', ' ').trim();
        if (t.length() < 8) {
            return false;
        }
        return TOC_TAIL_PAGE_NUM.matcher(t).find();
    }

    private static boolean isTocParagraphStyle(ParagraphInfo p) {
        String sid = p.getStyleId();
        if (sid != null && sid.toUpperCase(Locale.ROOT).contains("TOC")) {
            return true;
        }
        String sn = p.getStyleName();
        if (sn == null) {
            return false;
        }
        String u = sn.toUpperCase(Locale.ROOT);
        return u.contains("TOC") || sn.contains("Оглавлен");
    }
}
