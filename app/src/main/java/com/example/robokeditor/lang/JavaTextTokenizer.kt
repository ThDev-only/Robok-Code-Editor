package com.example.robokeditor.lang

import io.github.rosemoe.sora.util.MyCharacter
import io.github.rosemoe.sora.util.TrieTree

class JavaTextTokenizer(private var source: CharSequence) {

    private var bufferLen = source.length
    private var line = 0
    private var column = 0
    private var index = 0
    private var offset = 0
    private var length = 0
    private var currToken = Tokens.WHITESPACE
    private var lcCal = false

    init {
        require(source.isNotEmpty()) { "src cannot be null or empty" }
    }

    fun setCalculateLineColumn(cal: Boolean) {
        this.lcCal = cal
    }

    fun pushBack(length: Int) {
        require(length <= getTokenLength()) { "pushBack length too large" }
        this.length -= length
    }

    private fun isIdentifierPart(ch: Char): Boolean {
        return MyCharacter.isJavaIdentifierPart(ch)
    }

    private fun isIdentifierStart(ch: Char): Boolean {
        return MyCharacter.isJavaIdentifierStart(ch)
    }

    fun getTokenText(): CharSequence {
        return source.subSequence(offset, offset + length)
    }

    fun getTokenLength(): Int = length

    fun getLine(): Int = line

    fun getColumn(): Int = column

    fun getIndex(): Int = index

    fun getToken(): Tokens = currToken

    private fun charAt(i: Int): Char {
        return source[i]
    }

    private fun charAt(): Char {
        return source[offset + length]
    }

    fun nextToken(): Tokens {
        return nextTokenInternal().also { currToken = it }
    }

    private fun nextTokenInternal(): Tokens {
        if (lcCal) {
            var r = false
            for (i in offset until offset + length) {
                when (val ch = charAt(i)) {
                    '\r' -> {
                        r = true
                        line++
                        column = 0
                    }
                    '\n' -> {
                        if (r) {
                            r = false
                            continue
                        }
                        line++
                        column = 0
                    }
                    else -> {
                        r = false
                        column++
                    }
                }
            }
        }
        index += length
        offset += length
        if (offset >= bufferLen) return Tokens.EOF
        val ch = source[offset]
        length = 1
        return when {
            ch == '\n' -> Tokens.NEWLINE
            ch == '\r' -> {
                scanNewline()
                Tokens.NEWLINE
            }
            isWhitespace(ch) -> {
                while (offset + length < bufferLen && isWhitespace(charAt(offset + length))) {
                    length++
                }
                Tokens.WHITESPACE
            }
            isIdentifierStart(ch) -> scanIdentifier(ch)
            isPrimeDigit(ch) -> scanNumber()
            else -> scanSymbols(ch)
        }
    }

    private fun scanNewline() {
        if (offset + length < bufferLen && charAt(offset + length) == '\n') {
            length++
        }
    }

    private fun scanIdentifier(ch: Char): Tokens {
        var n = keywords.root.map[ch]
        while (offset + length < bufferLen && isIdentifierPart(charAt(offset + length))) {
            length++
            n = n?.map?.get(charAt(offset + length))
        }
        return n?.token ?: Tokens.IDENTIFIER
    }

    private fun scanNumber(): Tokens {
        if (offset + length == bufferLen) return Tokens.INTEGER_LITERAL
        var flag = false
        var ch = charAt(offset)
        if (ch == '0' && charAt() == 'x') {
            length++
            flag = true
        }
        while (offset + length < bufferLen && isDigit(charAt())) length++
        if (offset + length == bufferLen) return Tokens.INTEGER_LITERAL
        ch = charAt()
        return when {
            ch == '.' && !flag -> {
                length++
                while (offset + length < bufferLen && isDigit(charAt())) length++
                Tokens.FLOATING_POINT_LITERAL
            }
            ch in listOf('l', 'L', 'F', 'f', 'D', 'd') -> {
                length++
                Tokens.FLOATING_POINT_LITERAL
            }
            else -> Tokens.INTEGER_LITERAL
        }
    }

    private fun scanSymbols(ch: Char): Tokens {
        return when (ch) {
            ';' -> Tokens.SEMICOLON
            '(' -> Tokens.LPAREN
            ')' -> Tokens.RPAREN
            '<' -> scanLT()
            '>' -> scanGT()
            '.' -> Tokens.DOT
            '@' -> Tokens.AT
            '{' -> Tokens.LBRACE
            '}' -> Tokens.RBRACE
            '/' -> scanDIV()
            '\'' -> {
                scanCharLiteral()
                Tokens.CHARACTER_LITERAL
            }
            '\"' -> {
                scanStringLiteral()
                Tokens.STRING
            }
            else -> Tokens.UNKNOWN
        }
    }

    private fun scanDIV(): Tokens {
        if (offset + 1 == bufferLen) return Tokens.DIV
        return when (charAt()) {
            '/' -> {
                length++
                while (offset + length < bufferLen && charAt() != '\n') length++
                Tokens.LINE_COMMENT
            }
            '*' -> {
                length++
                while (offset + length < bufferLen && charAt(offset + length) != '/') length++
                Tokens.LONG_COMMENT_COMPLETE
            }
            else -> Tokens.DIV
        }
    }

    private fun scanLT() = Tokens.LT
    private fun scanGT() = Tokens.GT

    fun reset(src: CharSequence) {
        require(src.isNotEmpty()) { "src cannot be null or empty" }
        source = src
        bufferLen = src.length
        line = 0
        column = 0
        length = 0
        index = 0
        offset = 0
        currToken = Tokens.WHITESPACE
    }

    companion object {
        private lateinit var keywords: TrieTree<Tokens>

        init {
            doStaticInit()
        }

        private fun doStaticInit() {
            val sKeywords = arrayOf(
                "abstract", "assert", "boolean", "byte", "char", "class", "do",
                "double", "final", "float", "for", "if", "int", "long", "new",
                "public", "private", "protected", "package", "return", "static",
                "short", "super", "switch", "else", "volatile", "synchronized", "strictfp",
                "goto", "continue", "break", "transient", "void", "try", "catch",
                "finally", "while", "case", "default", "const", "enum", "extends",
                "implements", "import", "instanceof", "interface", "native",
                "this", "throw", "throws", "true", "false", "null", "var", "sealed", "permits"
            )
            val sTokens = sKeywords.map { Tokens.valueOf(it.uppercase()) }.toTypedArray()
            keywords = TrieTree()
            for (i in sKeywords.indices) {
                keywords.put(sKeywords[i], sTokens[i])
            }
        }

        private fun isDigit(c: Char) = c in '0'..'9' || c in 'A'..'F' || c in 'a'..'f'
        private fun isPrimeDigit(c: Char) = c in '0'..'9'
        private fun isWhitespace(c: Char) = c in listOf('\t', ' ', '\f', '\n', '\r')
    }
}