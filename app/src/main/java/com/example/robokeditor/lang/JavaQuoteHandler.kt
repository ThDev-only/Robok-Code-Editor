package com.example.robokeditor.lang

import io.github.rosemoe.sora.lang.QuickQuoteHandler
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.lang.styling.StylesUtils.checkNoCompletion
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.TextRange

class JavaQuoteHandler : QuickQuoteHandler {

    override fun onHandleTyping(
        candidateCharacter: String,
        text: Content,
        cursor: TextRange,
        style: Styles?
    ): QuickQuoteHandler.HandleResult {
        if (!checkNoCompletion(style, cursor.start) &&
            !checkNoCompletion(style, cursor.end) &&
            candidateCharacter == "\"" &&
            cursor.start.line == cursor.end.line
        ) {
            text.insert(cursor.start.line, cursor.start.column, "\"")
            text.insert(cursor.end.line, cursor.end.column + 1, "\"")
            return QuickQuoteHandler.HandleResult(
                true,
                TextRange(
                    text.indexer.getCharPosition(cursor.startIndex + 1),
                    text.indexer.getCharPosition(cursor.endIndex + 1)
                )
            )
        }
        return QuickQuoteHandler.HandleResult.NOT_CONSUMED
    }
}