package com.example.core

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

data class CalculatorHistoryItem(
    val id: Long,
    val expression: String,
    val result: String,
    val numericValue: BigDecimal
)

data class CalculatorUiState(
    val expression: String = "",
    val currentInput: String = "0",
    val resultDisplay: String = "",
    val errorMessage: String? = null,
    val justEvaluated: Boolean = false,
    val awaitingNextOperand: Boolean = false,
    val lastResult: BigDecimal? = null,
    val history: List<CalculatorHistoryItem> = emptyList()
)

object CalculatorEngine {
    private val MATH_CTX = MathContext(16, RoundingMode.HALF_UP)

    fun evaluate(rawExpression: String): Result<BigDecimal> {
        return try {
            val cleaned = rawExpression
                .replace("×", "*")
                .replace("÷", "/")
                .replace("−", "-")
                .replace(" ", "")
            if (cleaned.isBlank()) {
                return Result.failure(IllegalArgumentException("Enter a calculation"))
            }
            val tokens = tokenize(cleaned)
            val rpn = toRpn(tokens)
            val value = evalRpn(rpn)
            Result.success(normalize(value))
        } catch (e: ArithmeticException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("Invalid expression"))
        }
    }

    fun formatDecimal(value: BigDecimal): String {
        val clean = normalize(value)
        val plain = clean.toPlainString()
        return if (plain.length > 18) {
            val rounded = clean.round(MathContext(10, RoundingMode.HALF_UP))
            rounded.toString()
        } else {
            plain
        }
    }

    private fun normalize(value: BigDecimal): BigDecimal {
        if (value.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO
        return value.stripTrailingZeros()
    }

    private sealed interface Token {
        data class Num(val value: BigDecimal) : Token
        data class Op(val symbol: Char) : Token
        data object Percent : Token
        data object LParen : Token
        data object RParen : Token
    }

    private fun tokenize(expr: String): List<Token> {
        val out = mutableListOf<Token>()
        var i = 0
        while (i < expr.length) {
            val ch = expr[i]
            val expectUnaryMinus = ch == '-' && (
                out.isEmpty() ||
                    out.last() is Token.Op ||
                    out.last() is Token.LParen
                )

            if (ch.isDigit() || ch == '.' || expectUnaryMinus) {
                val sb = StringBuilder()
                if (expectUnaryMinus) {
                    sb.append('-')
                    i++
                }
                var dotSeen = false
                var digitSeen = false
                while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) {
                    if (expr[i] == '.') {
                        if (dotSeen) throw IllegalArgumentException("Invalid decimal")
                        dotSeen = true
                    } else {
                        digitSeen = true
                    }
                    sb.append(expr[i])
                    i++
                }
                if (!digitSeen) throw IllegalArgumentException("Invalid number")
                out.add(Token.Num(BigDecimal(sb.toString())))
                continue
            }

            when (ch) {
                '+', '-', '*', '/' -> out.add(Token.Op(ch))
                '%' -> out.add(Token.Percent)
                '(' -> out.add(Token.LParen)
                ')' -> out.add(Token.RParen)
                else -> throw IllegalArgumentException("Unexpected character: $ch")
            }
            i++
        }
        return out
    }

    private fun precedence(op: Char): Int = when (op) {
        '+', '-' -> 1
        '*', '/' -> 2
        else -> 0
    }

    private fun toRpn(tokens: List<Token>): List<Token> {
        val output = mutableListOf<Token>()
        val stack = ArrayDeque<Token>()
        for (token in tokens) {
            when (token) {
                is Token.Num -> output.add(token)
                is Token.Percent -> {
                    // Highest precedence postfix operator
                    output.add(token)
                }
                is Token.Op -> {
                    while (stack.isNotEmpty()) {
                        val top = stack.last()
                        if (top is Token.Op && precedence(top.symbol) >= precedence(token.symbol)) {
                            output.add(stack.removeLast())
                        } else {
                            break
                        }
                    }
                    stack.addLast(token)
                }
                is Token.LParen -> stack.addLast(token)
                is Token.RParen -> {
                    while (stack.isNotEmpty() && stack.last() !is Token.LParen) {
                        output.add(stack.removeLast())
                    }
                    if (stack.isEmpty() || stack.last() !is Token.LParen) {
                        throw IllegalArgumentException("Mismatched parentheses")
                    }
                    stack.removeLast()
                }
            }
        }
        while (stack.isNotEmpty()) {
            val top = stack.removeLast()
            if (top is Token.LParen || top is Token.RParen) {
                throw IllegalArgumentException("Mismatched parentheses")
            }
            output.add(top)
        }
        return output
    }

    private fun evalRpn(rpn: List<Token>): BigDecimal {
        val stack = ArrayDeque<BigDecimal>()
        val hundred = BigDecimal(100)
        for (token in rpn) {
            when (token) {
                is Token.Num -> stack.addLast(token.value)
                is Token.Percent -> {
                    if (stack.isEmpty()) throw IllegalArgumentException("Missing operand for %")
                    val a = stack.removeLast()
                    stack.addLast(a.divide(hundred, MATH_CTX))
                }
                is Token.Op -> {
                    if (stack.size < 2) throw IllegalArgumentException("Missing operand")
                    val b = stack.removeLast()
                    val a = stack.removeLast()
                    val res = when (token.symbol) {
                        '+' -> a.add(b, MATH_CTX)
                        '-' -> a.subtract(b, MATH_CTX)
                        '*' -> a.multiply(b, MATH_CTX)
                        '/' -> {
                            if (b.compareTo(BigDecimal.ZERO) == 0) {
                                throw ArithmeticException("Cannot divide by zero")
                            }
                            a.divide(b, 10, RoundingMode.HALF_UP)
                        }
                        else -> throw IllegalArgumentException("Unknown op")
                    }
                    stack.addLast(res)
                }
                else -> Unit
            }
        }
        if (stack.size != 1) throw IllegalArgumentException("Invalid expression")
        return stack.single()
    }
}
