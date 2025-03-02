package com.example.robokeditor.lang

import android.os.Bundle
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import io.github.rosemoe.sora.lang.*
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.completion.*
import io.github.rosemoe.sora.lang.completion.snippet.CodeSnippet
import io.github.rosemoe.sora.lang.completion.snippet.parser.CodeSnippetParser
import io.github.rosemoe.sora.lang.format.Formatter
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandleResult
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.lang.styling.StylesUtils
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.text.TextUtils
import io.github.rosemoe.sora.util.MyCharacter
import io.github.rosemoe.sora.widget.SymbolPairMatch

class JavaLanguage : Language {

    companion object {
        private val FOR_SNIPPET = CodeSnippetParser.parse("for(int ${"$"}{1:i} = 0;$1 < ${"$"}{2:count};$1++) {\n    $0\n}")
        private val STATIC_CONST_SNIPPET = CodeSnippetParser.parse("private final static ${"$"}{1:type} ${"$"}{2/(.*)/${"$"}{1:/upcase}/} = ${"$"}{3:value};")
        private val CLIPBOARD_SNIPPET = CodeSnippetParser.parse("${"$"}{1:${"$"}{CLIPBOARD}}")
    }

    private var autoComplete: IdentifierAutoComplete? = IdentifierAutoComplete(JavaTextTokenizer.sKeywords)
    private val manager = JavaIncrementalAnalyzeManager()
    private val javaQuoteHandler = JavaQuoteHandler()

    override fun getAnalyzeManager(): AnalyzeManager = manager

    override fun getQuickQuoteHandler(): QuickQuoteHandler? = javaQuoteHandler

    override fun destroy() {
        autoComplete = null
    }

    override fun getInterruptionLevel(): Int = INTERRUPTION_LEVEL_STRONG

    override fun requireAutoComplete(
        @NonNull content: ContentReference,
        @NonNull position: CharPosition,
        @NonNull publisher: CompletionPublisher,
        @NonNull extraArguments: Bundle
    ) {
        val prefix = CompletionHelper.computePrefix(content, position, MyCharacter::isJavaIdentifierPart)
        manager.identifiers?.let {
            autoComplete?.requireAutoComplete(content, position, prefix, publisher, it)
        }
        if ("fori".startsWith(prefix) && prefix.isNotEmpty()) {
            publisher.addItem(SimpleSnippetCompletionItem("fori", "Snippet - For loop on index", SnippetDescription(prefix.length, FOR_SNIPPET, true)))
        }
        if ("sconst".startsWith(prefix) && prefix.isNotEmpty()) {
            publisher.addItem(SimpleSnippetCompletionItem("sconst", "Snippet - Static Constant", SnippetDescription(prefix.length, STATIC_CONST_SNIPPET, true)))
        }
        if ("clip".startsWith(prefix) && prefix.isNotEmpty()) {
            publisher.addItem(SimpleSnippetCompletionItem("clip", "Snippet - Clipboard contents", SnippetDescription(prefix.length, CLIPBOARD_SNIPPET, true)))
        }
    }

    override fun getIndentAdvance(@NonNull text: ContentReference, line: Int, column: Int): Int {
        val content = text.getLine(line).substring(0, column)
        return getIndentAdvance(content)
    }

    private fun getIndentAdvance(content: String): Int {
        val tokenizer = JavaTextTokenizer(content)
        var advance = 0
        while (true) {
            val token = tokenizer.nextToken()
            if (token == Tokens.EOF) break
            if (token == Tokens.LBRACE) advance++
        }
        return (advance * 4).coerceAtLeast(0)
    }

    private val newlineHandlers = arrayOf(BraceHandler())

    override fun useTab(): Boolean = false

    override fun getFormatter(): Formatter = EmptyLanguage.EmptyFormatter.INSTANCE

    override fun getSymbolPairs(): SymbolPairMatch = SymbolPairMatch.DefaultSymbolPairs()

    override fun getNewlineHandlers(): Array<NewlineHandler> = newlineHandlers

    private fun getNonEmptyTextBefore(text: CharSequence, index: Int, length: Int): String {
        var idx = index
        while (idx > 0 && text[idx - 1].isWhitespace()) {
            idx--
        }
        return text.subSequence((idx - length).coerceAtLeast(0), idx).toString()
    }

    private fun getNonEmptyTextAfter(text: CharSequence, index: Int, length: Int): String {
        var idx = index
        while (idx < text.length && text[idx].isWhitespace()) {
            idx++
        }
        return text.subSequence(idx, (idx + length).coerceAtMost(text.length)).toString()
    }

    inner class BraceHandler : NewlineHandler {

        override fun matchesRequirement(
            @NonNull text: Content,
            @NonNull position: CharPosition,
            @Nullable style: Styles?
        ): Boolean {
            val line = text.getLine(position.line)
            return !StylesUtils.checkNoCompletion(style, position)
                    && getNonEmptyTextBefore(line, position.column, 1) == "{"
                    && getNonEmptyTextAfter(line, position.column, 1) == "}"
        }

        @NonNull
        override fun handleNewline(
            @NonNull text: Content,
            @NonNull position: CharPosition,
            @Nullable style: Styles?,
            tabSize: Int
        ): NewlineHandleResult {
            val line = text.getLine(position.line)
            val index = position.column
            val beforeText = line.subSequence(0, index).toString()
            val afterText = line.subSequence(index, line.length).toString()
            return handleNewline(beforeText, afterText, tabSize)
        }

        @NonNull
        fun handleNewline(beforeText: String, afterText: String, tabSize: Int): NewlineHandleResult {
            val count = TextUtils.countLeadingSpaceCount(beforeText, tabSize)
            val advanceBefore = getIndentAdvance(beforeText)
            val advanceAfter = getIndentAdvance(afterText)
            val indent = TextUtils.createIndent(count + advanceAfter, tabSize, useTab())
            val sb = StringBuilder("\n")
                .append(TextUtils.createIndent(count + advanceBefore, tabSize, useTab()))
                .append("\n")
                .append(indent)
            val shiftLeft = indent.length + 1
            return NewlineHandleResult(sb, shiftLeft)
        }
    }
}