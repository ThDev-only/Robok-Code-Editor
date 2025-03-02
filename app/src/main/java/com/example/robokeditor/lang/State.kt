package com.example.robokeditor.lang

class State {
    var state: Int = 0
    var hasBraces: Boolean = false
    var identifiers: MutableList<String>? = null

    fun addIdentifier(idt: CharSequence) {
        if (identifiers == null) {
            identifiers = mutableListOf()
        }
        identifiers!!.add(idt.toString())
    }

    override fun equals(other: Any?): Boolean {
        // `identifiers` é ignorado porque não está relacionado à tokenização para a próxima linha
        if (this === other) return true
        if (other !is State) return false
        return state == other.state && hasBraces == other.hasBraces
    }

    override fun hashCode(): Int {
        return Objects.hash(state, hasBraces)
    }
}