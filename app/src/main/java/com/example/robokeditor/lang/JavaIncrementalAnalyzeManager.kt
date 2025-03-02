package com.example.robokeditor.lang

import android.os.Bundle
import io.github.rosemoe.sora.lang.analysis.AsyncIncrementalAnalyzeManager
import io.github.rosemoe.sora.lang.brackets.SimpleBracketsCollector
import io.github.rosemoe.sora.lang.completion.IdentifierAutoComplete
import io.github.rosemoe.sora.lang.styling.CodeBlock
import io.github.rosemoe.sora.lang.styling.Span
import io.github.rosemoe.sora.lang.styling.SpanFactory
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.lang.styling.color.EditorColor
import io.github.rosemoe.sora.lang.styling.span.SpanClickableUrl
import io.github.rosemoe.sora.lang.styling.span.SpanExtAttrs
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.util.ArrayList
import io.github.rosemoe.sora.util.IntPair
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import java.util.*
import java.util.regex.Pattern

open class JavaIncrementalAnalyzeManager : AsyncIncrementalAnalyzeManager<State, JavaIncrementalAnalyzeManager.HighlightToken>() {

    companion object {
        private const val STATE_NORMAL = 0
        private const val STATE_INCOMPLETE_COMMENT = 1
        private val URL_PATTERN: Pattern = Pattern.compile("https?:\\/\\/(www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&//=]*)")
    }

    private val tokenizerProvider = ThreadLocal<JavaTextTokenizer>()
    protected val identifiers = IdentifierAutoComplete.SyncIdentifiers()

    @Synchronized
    private fun obtainTokenizer(): JavaTextTokenizer {
        return tokenizerProvider.get() ?: JavaTextTokenizer("").also { tokenizerProvider.set(it) }
    }



    override fun computeBlocks(
        text: Content,
        delegate: CodeBlockAnalyzeDelegate
    ): List<CodeBlock> {
        val stack = Stack<CodeBlock>()
        val blocks = ArrayList<CodeBlock>()
        var maxSwitch = 0
        var currSwitch = 0
        val brackets = SimpleBracketsCollector()
        val bracketsStack = Stack<Long>()

        for (i in 0 until text.lineCount) {
            if (delegate.isNotCancelled()) {
                val state = getState(i)
                val checkForIdentifiers = state.state.state == STATE_NORMAL || 
                    (state.state.state == STATE_INCOMPLETE_COMMENT && state.tokens.size > 1)
                
                if (state.state.hasBraces || checkForIdentifiers) {
                    for (tokenRecord in state.tokens) {
                        val token = tokenRecord.token
                        val offset = tokenRecord.offset

                        when (token) {
                            Tokens.LBRACE -> {
                                if (stack.isEmpty() && currSwitch > maxSwitch) {
                                    maxSwitch = currSwitch
                                }
                                currSwitch++
                                val block = CodeBlock().apply {
                                    startLine = i
                                    startColumn = offset
                                }
                                stack.push(block)
                            }
                            Tokens.RBRACE -> {
                                if (stack.isNotEmpty()) {
                                    val block = stack.pop().apply {
                                        endLine = i
                                        endColumn = offset
                                    }
                                    if (block.startLine != block.endLine) {
                                        blocks.add(block)
                                    }
                                }
                            }
                        }

                        val type = getType(token)
                        if (type > 0) {
                            if (isStart(token)) {
                                bracketsStack.push(IntPair.pack(type, text.getCharIndex(i, offset)))
                            } else if (bracketsStack.isNotEmpty()) {
                                var record = bracketsStack.pop()
                                val typeRecord = IntPair.getFirst(record)
                                if (typeRecord == type) {
                                    brackets.add(IntPair.getSecond(record), text.getCharIndex(i, offset))
                                } else if (type == 3) {
                                    while (bracketsStack.isNotEmpty()) {
                                        record = bracketsStack.pop()
                                        if (IntPair.getFirst(record) == 3) {
                                            brackets.add(IntPair.getSecond(record), text.getCharIndex(i, offset))
                                            break
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (delegate.isNotCancelled()) {
            withReceiver { it.updateBracketProvider(this, brackets) }
        }
        return blocks
    }

    private fun getType(token: Tokens) = when (token) {
        Tokens.LBRACE, Tokens.RBRACE -> 3
        Tokens.LBRACK, Tokens.RBRACK -> 2
        Tokens.LPAREN, Tokens.RPAREN -> 1
        else -> 0
    }

    private fun isStart(token: Tokens) = token in listOf(Tokens.LBRACE, Tokens.LBRACK, Tokens.LPAREN)

    override fun getInitialState(): State = State()

    override fun stateEquals(state: State, another: State): Boolean = state == another

    override fun onAddState(state: State) {
        state.identifiers?.forEach { identifiers.identifierIncrease(it) }
    }

    override fun onAbandonState(state: State) {
        state.identifiers?.forEach { identifiers.identifierDecrease(it) }
    }

    override fun reset(content: ContentReference, extraArguments: Bundle) {
        super.reset(content, extraArguments)
        identifiers.clear()
    }

    override fun tokenizeLine(line: CharSequence, state: State, lineIndex: Int): LineTokenizeResult<State, HighlightToken> {
        val tokens = ArrayList<HighlightToken>()
        val stateObj = State()
        val newState = if (state.state == STATE_NORMAL) {
            tokenizeNormal(line, 0, tokens, stateObj)
        } else {
            val res = tryFillIncompleteComment(line, tokens)
            if (IntPair.getFirst(res) == STATE_NORMAL) {
                tokenizeNormal(line, IntPair.getSecond(res), tokens, stateObj)
            } else STATE_INCOMPLETE_COMMENT
        }

        if (tokens.isEmpty()) {
            tokens.add(HighlightToken(Tokens.UNKNOWN, 0))
        }

        stateObj.state = newState
        return LineTokenizeResult(stateObj, tokens)
    }

    private fun tryFillIncompleteComment(line: CharSequence, tokens: MutableList<HighlightToken>): Long {
        var pre = '\u0000'
        var cur = '\u0000'
        var offset = 0

        while ((pre != '*' || cur != '/') && offset < line.length) {
            pre = cur
            cur = line[offset]
            offset++
        }

        val newState = if (pre == '*' && cur == '/') {
            detectHighlightUrls(line.subSequence(0, offset), 0, Tokens.LONG_COMMENT_COMPLETE, tokens)
            STATE_NORMAL
        } else {
            detectHighlightUrls(line.subSequence(0, offset), 0, Tokens.LONG_COMMENT_INCOMPLETE, tokens)
            STATE_INCOMPLETE_COMMENT
        }

        return IntPair.pack(newState, offset)
    }
    
    override fun generateSpansForLine(lineResult: LineTokenizeResult<State, HighlightToken>): List<Span> {
    val spans = ArrayList<Span>()
    val tokens = lineResult.tokens
    var previous = Tokens.UNKNOWN
    var classNamePrevious = false

    for (tokenRecord in tokens) {
        val token = tokenRecord.token
        val offset = tokenRecord.offset
        val span: Span

        // Adicionando o 'when' exaustivo com o caso 'else'
        when (token) {
            Tokens.WHITESPACE, Tokens.NEWLINE -> span = SpanFactory.obtain(offset, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL))
            Tokens.CHARACTER_LITERAL, Tokens.FLOATING_POINT_LITERAL, Tokens.INTEGER_LITERAL, Tokens.STRING -> {
                classNamePrevious = false
                span = SpanFactory.obtain(offset, TextStyle.makeStyle(EditorColorScheme.LITERAL, true))
            }
            Tokens.INT, Tokens.LONG, Tokens.BOOLEAN, Tokens.BYTE, Tokens.CHAR, Tokens.FLOAT, Tokens.DOUBLE, Tokens.SHORT, Tokens.VOID, Tokens.VAR -> {
                classNamePrevious = true
                span = SpanFactory.obtain(offset, TextStyle.makeStyle(EditorColorScheme.KEYWORD, 0, true, false, false))
            }
            else -> {
                classNamePrevious = false
                span = SpanFactory.obtain(offset, TextStyle.makeStyle(EditorColorScheme.OPERATOR))
            }
        }

        // Atualização da variável 'previous' apenas para casos específicos
        if (token != Tokens.LINE_COMMENT && token != Tokens.LONG_COMMENT_COMPLETE && token != Tokens.LONG_COMMENT_INCOMPLETE && token != Tokens.WHITESPACE && token != Tokens.NEWLINE) {
            previous = token
        }

        // Verificando a URL do token e aplicando o estilo
        if (tokenRecord.url != null) {
            span.setSpanExt(SpanExtAttrs.EXT_INTERACTION_INFO, SpanClickableUrl(tokenRecord.url))
            span.setUnderlineColor(EditorColor(span.foregroundColorId))
        }

        spans.add(span)
    }
    return spans
}

    private fun tokenizeNormal(text: CharSequence, offset: Int, tokens: MutableList<HighlightToken>, st: State): Int {
        val tokenizer = obtainTokenizer().apply { reset(text); this.offset = offset }
        var state = STATE_NORMAL

        while (true) {
            val token = tokenizer.nextToken()
            if (token == Tokens.EOF) break

            tokens.add(HighlightToken(token, tokenizer.offset))
            if (token == Tokens.LONG_COMMENT_INCOMPLETE) {
                state = STATE_INCOMPLETE_COMMENT
                break
            }
        }

        return state
    }

    class HighlightToken(val token: Tokens, val offset: Int, val url: String? = null)
}