package com.centyllion.model

/** Graphical predicates rebuilt from a formula. */
data class GraphicalPredicates(
    val agePredicates: List<Predicate<Int>> = emptyList(),
    val agentCountPredicates: List<AgentCountPredicate> = emptyList(),
    val fieldPredicates: List<Pair<Int, Predicate<Float>>> = emptyList(),
) {
    val isEmpty: Boolean get() = agePredicates.isEmpty() && agentCountPredicates.isEmpty() && fieldPredicates.isEmpty()
}

/**
 * Conversions between the graphical predicates of a behaviour and the predicate formula.
 *
 * The two forms are *alternatives*: the simulator ANDs the graphical predicates with the
 * formula, so keeping both after switching to expert mode would silently add a condition
 * the user no longer sees. Entering expert mode therefore folds the graphical predicates
 * into the formula, and leaving it rebuilds them from a formula that is a pure conjunction.
 *
 * Formula atoms understood here (and nothing else):
 * - `age(n) <op> k` — age of the n-th reactive (`age(1)` is the main agent);
 * - `step <op> k` — for a generation behaviour, where the step plays the age;
 * - `count(g) <op> k` — occupied neighbours of agent `g`;
 * - `field(f) <op> v` — value of field `f` at the current cell.
 *
 * A neighbour count over "all agents" (`grainId < 0`) has no formula equivalent: `count()`
 * is per agent type. Such a predicate blocks the conversion instead of changing its meaning.
 */
object BehaviourPredicates {

    private val atomRegex = Regex(
        """^(age\(\s*1\s*\)|step|count\(\s*-?\d+\s*\)|countEmpty\(\s*\)|field\(\s*\d+\s*\))\s*(==|!=|<=|>=|<|>)\s*(-?\d+(?:\.\d+)?)$"""
    )

    private fun Operator.token(): String = when (this) {
        Operator.Equals -> "=="
        Operator.NotEquals -> "!="
        Operator.LessThan -> "<"
        Operator.LessThanOrEquals -> "<="
        Operator.GreaterThan -> ">"
        Operator.GreaterThanOrEquals -> ">="
    }

    private fun tokenOperator(token: String): Operator = when (token) {
        "==" -> Operator.Equals
        "!=" -> Operator.NotEquals
        "<" -> Operator.LessThan
        "<=" -> Operator.LessThanOrEquals
        ">" -> Operator.GreaterThan
        else -> Operator.GreaterThanOrEquals
    }

    /**
     * True when every graphical predicate has an exact formula equivalent, i.e. when the
     * behaviour can move to expert mode without changing its meaning.
     */
    fun convertible(behaviour: Behaviour): Boolean = true

    /** Formula equivalent to the graphical predicates; empty when the behaviour has none. */
    fun toFormula(behaviour: Behaviour): String {
        val ageVariable = if (behaviour.mainReactiveId >= 0) "age(1)" else "step"
        val parts = mutableListOf<String>()
        if (!behaviour.hasDefaultAgePredicate) {
            parts += "$ageVariable ${behaviour.agePredicate.op.token()} ${behaviour.agePredicate.constant}"
        }
        behaviour.agePredicates.forEach { parts += "$ageVariable ${it.op.token()} ${it.constant}" }
        behaviour.agentCountPredicates.forEach {
            // -1 and -2 are real keys of the counts map: count(-1) every agent, countEmpty() none
            val call = when (it.grainId) {
                COUNT_EMPTY_CELLS -> "countEmpty()"
                COUNT_ALL_AGENTS -> "count(-1)"
                else -> "count(${it.grainId})"
            }
            parts += "$call ${it.op.token()} ${it.count}"
        }
        behaviour.fieldPredicates.forEach { (id, predicate) ->
            parts += "field($id) ${predicate.op.token()} ${predicate.constant}"
        }
        return parts.joinToString(" && ")
    }

    /**
     * Parses a formula made only of conjunctions of the atoms above. Returns `null` as soon
     * as something else appears (a disjunction, a negation, arithmetic, another function),
     * which is how the return to the graphical mode is refused.
     */
    fun fromFormula(formula: String): GraphicalPredicates? {
        if (formula.isBlank()) return GraphicalPredicates()

        val ages = mutableListOf<Predicate<Int>>()
        val counts = mutableListOf<AgentCountPredicate>()
        val fields = mutableListOf<Pair<Int, Predicate<Float>>>()

        for (rawPart in formula.split("&&")) {
            val part = rawPart.trim().removeSurrounding("(", ")").trim()
            val match = atomRegex.matchEntire(part) ?: return null
            val head = match.groupValues[1].replace(" ", "")
            val op = tokenOperator(match.groupValues[2])
            val number = match.groupValues[3]

            when {
                head == "step" || head == "age(1)" -> {
                    val constant = number.toIntOrNull() ?: return null
                    ages += Predicate(op, constant)
                }

                head == "countEmpty()" -> {
                    val constant = number.toIntOrNull() ?: return null
                    counts += AgentCountPredicate(COUNT_EMPTY_CELLS, op, constant)
                }

                head.startsWith("count(") -> {
                    val grainId = head.removePrefix("count(").removeSuffix(")").toIntOrNull() ?: return null
                    val constant = number.toIntOrNull() ?: return null
                    counts += AgentCountPredicate(grainId, op, constant)
                }

                head.startsWith("field(") -> {
                    val id = head.removePrefix("field(").removeSuffix(")").toIntOrNull() ?: return null
                    val constant = number.toFloatOrNull() ?: return null
                    fields += id to Predicate(op, constant)
                }

                else -> return null
            }
        }

        return GraphicalPredicates(ages, counts, fields)
    }
}
