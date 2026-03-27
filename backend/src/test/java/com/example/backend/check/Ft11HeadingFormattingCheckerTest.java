package com.example.backend.check;

import com.example.backend.domain.ParagraphInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ft11HeadingFormattingCheckerTest {

    @Test
    void skippedMessage_whenNoOutlineOrHeadingStyle() {
        List<String> issues =
                Ft11HeadingFormattingChecker.check(
                        List.of(
                                ParagraphInfo.builder()
                                        .text("Обычный абзац без уровня структуры.")
                                        .outlineLevel(null)
                                        .inTable(false)
                                        .containsFormula(false)
                                        .build()));
        assertFalse(issues.isEmpty());
        assertTrue(issues.get(0).contains("ФТ-11") && issues.get(0).contains("пропущена"));
    }
}
