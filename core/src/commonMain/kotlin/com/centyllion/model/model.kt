package com.centyllion.model

import com.benasher44.uuid.Uuid
import com.benasher44.uuid.uuid4
import com.centyllion.expression.parseExpression
import com.centyllion.expression.v
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.math.pow

/** Update plan values for [GrainModel.updatePlan]. */
const val PARALLEL_UPDATE_PLAN = "Parallel"
const val SEQUENTIAL_UPDATE_PLAN = "Sequential"

enum class Direction {
    Left, Right, Up, Down, LeftUp, RightUp, LeftDown, RightDown;

    val deltaX
        get() = when (this) {
            Left, LeftUp, LeftDown -> -1
            Right, RightUp, RightDown -> 1
            else -> 0
        }

    val deltaY
        get() = when (this) {
            Up, LeftUp, RightUp -> -1
            Down, LeftDown, RightDown -> 1
            else -> 0
        }

    val opposite
        get() = when (this) {
            Left -> Right
            Right -> Left
            Up -> Down
            Down -> Up
            LeftUp -> RightDown
            RightUp -> LeftDown
            LeftDown -> RightUp
            RightDown -> LeftUp
        }

    companion object {

        val default = setOf(Left, Up, Right, Down)
        val extended = setOf(LeftUp, LeftDown, RightUp, RightDown)

    }
}

fun <T> List<T>.identityFirstIndexOf(value: T): Int {
    val identity = this.indexOfFirst { it === value }
    return if (identity < 0) this.indexOf(value) else identity
}

enum class Operator(val label: String) {
    Equals("="), NotEquals("!="), LessThan("<"), LessThanOrEquals("<="), GreaterThan(">"), GreaterThanOrEquals(">=")
}

@Serializable
data class Predicate<C : Comparable<C>>(
    val op: Operator,
    val constant: C
) {
    fun check(value: C) = when (op) {
        Operator.Equals -> value == constant
        Operator.NotEquals -> value != constant
        Operator.LessThan -> value < constant
        Operator.LessThanOrEquals -> value <= constant
        Operator.GreaterThan -> value > constant
        Operator.GreaterThanOrEquals -> value >= constant
    }
}

@Serializable
data class Asset3d(
    val url: String, val opacity: Double = 1.0,
    val x: Double = 0.0, val y: Double = 0.0, val z: Double = 0.0,
    val xScale: Double = 1.0, val yScale: Double = 1.0, val zScale: Double = 1.0,
    val xRotation: Double = 0.0, val yRotation: Double = 0.0, val zRotation: Double = 0.0
)

sealed interface ModelElement {
    val uuid: Uuid
    val name: String
    val description: String
}

@Serializable
data class Field(
    val id: Int = 0,
    override val name: String = "",
    val color: String = "SkyBlue",
    val invisible: Boolean = false,
    override val description: String = "",
    val speed: Float = 0.8f,
    val halfLife: Int = 10,
    val allowedDirection: Set<Direction> = Direction.default,
    /** Opacity of the field rendering, in percent: 0 = invisible, 100 = fully opaque. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val opacity: Int = 100,

    /** Toric boundary (wrap-around, default). When false the field has an
     *  impermeable boundary: it does not diffuse across the grid edges
     *  (best-effort: no exchange through the walls). */
    val toric: Boolean = true,
    val formula: String = "",
    @Transient override val uuid: Uuid = uuid4(),
) : ModelElement {
    /** Label for field */
    fun label(long: Boolean = false) = when {
        long && description.isNotEmpty() -> description
        name.isNotEmpty() -> name
        else -> "$id"
    }

    @Transient
    val deathProbability = if (halfLife > 0) 1f - 2f.pow(-1f / halfLife) else 0f

    @Transient
    val oppositeDirections = allowedDirection.map { it.opposite }
}

@Serializable
data class Grain(
    val id: Int = 0,
    override val name: String = "",
    val color: String = "red",
    val icon: String = "square-full",
    val invisible: Boolean = false,
    val size: Double = 1.0,
    override val description: String = "",
    val halfLife: Int = 0,
    val movementProbability: Double = 0.0,
    val allowedDirection: Set<Direction> = Direction.default,
    val fieldProductions: Map<Int, Float> = emptyMap(),
    val fieldInfluences: Map<Int, Float> = emptyMap(),
    val fieldPermeable: Map<Int, Float> = emptyMap(),
    /** Per-wall authorisation for agents of this grain to cross the grid walls
     *  (toric wrap-around). Default (all true) is toric everywhere. */
    val boundaryConditions: BoundaryConditions = BoundaryConditions(),
    @Transient override val uuid: Uuid = uuid4(),
) : ModelElement {

    @Transient
    val iconName = icon.replace("-", "").lowercase()

    /** Label for grain */
    fun label(long: Boolean = false) = when {
        long && description.isNotEmpty() -> description
        name.isNotEmpty() -> name
        else -> "$id"
    }

    /** True if an agent of this Grain can move */
    @Transient
    val canMove = movementProbability > 0.0 && allowedDirection.isNotEmpty()

    @Transient
    val deathProbability = if (halfLife > 0) 1.0 - 2.0.pow(-1.0 / halfLife) else 0.0

    @Transient
    val iconString = solidIconNames[icon] ?: "\uf45c"

    fun updateFieldProduction(id: Int, value: Float): Grain {
        val newFields = fieldProductions.toMutableMap()
        newFields[id] = value
        return copy(fieldProductions = newFields)
    }

    fun updateFieldInfluence(id: Int, value: Float): Grain {
        val newFields = fieldInfluences.toMutableMap()
        newFields[id] = value
        return copy(fieldInfluences = newFields)
    }

    fun updateFieldPermeable(id: Int, value: Float): Grain {
        val newFields = fieldPermeable.toMutableMap()
        newFields[id] = value
        return copy(fieldPermeable = newFields)
    }

    fun moveBehaviour() =
        if (canMove) Behaviour(
            name = "Move ${label()}", probability = movementProbability, mainReactiveId = id, sourceReactive = -1,
            reaction = listOf(Reaction(productId = id, sourceReactive = 0, allowedDirection = allowedDirection)),
            fieldInfluences = fieldInfluences
        )
        else null
}

@Serializable
data class Reaction(
    val reactiveId: Int = -1,
    val productId: Int = -1,
    val sourceReactive: Int = -1,
    val allowedDirection: Set<Direction> = Direction.default
)

/**
 * Predicate on the number of neighbours.
 * Used to express conditions like "number of Agent_B > 3".
 * If [grainId] is negative, the predicate counts any neighbouring agent
 * (occupied neighbour cells, 8 directions) instead of a specific grain.
 */
@Serializable
data class AgentCountPredicate(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val grainId: Int = -1,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val op: Operator = Operator.GreaterThanOrEquals,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val count: Int = 0
) {
    fun check(actualCount: Int): Boolean = when (op) {
        Operator.Equals -> actualCount == count
        Operator.NotEquals -> actualCount != count
        Operator.LessThan -> actualCount < count
        Operator.LessThanOrEquals -> actualCount <= count
        Operator.GreaterThan -> actualCount > count
        Operator.GreaterThanOrEquals -> actualCount >= count
    }
}

/**
 * Per-wall authorisation for an agent to cross that wall (toric wrap-around).
 *
 * Each flag authorises crossing the corresponding wall: [left] / [right] for
 * the vertical walls (left / right edges) and [top] / [bottom] for the
 * horizontal walls (top / bottom edges). Default (all true) is toric in all
 * directions.
 *
 * Example of an asymmetric boundary: `left = false` and `right = true` means
 * an agent at the left edge cannot wrap to the right edge when moving left,
 * but an agent at the right edge can wrap to the left edge when moving right.
 *
 * Serialization: when a grain is not toric (any wall closed) the whole
 * boundary-conditions object is written with ALL four walls explicit
 * ([EncodeDefault.Mode.ALWAYS]); when the grain is toric everywhere the
 * object is omitted entirely (default value).
 */
@Serializable
data class BoundaryConditions(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val left: Boolean = true,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val right: Boolean = true,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val top: Boolean = true,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS) val bottom: Boolean = true,
) {
    /**
     * True if a one-step move from [position] in [direction] crosses a CLOSED
     * (false) wall. A diagonal move at a corner may cross two walls: if any
     * of the crossed walls is closed, the move is considered crossed.
     */
    fun crossed(position: Position, direction: Direction, width: Int, height: Int): Boolean =
        (crossLeftWall(position, direction) && !left) ||
                (crossRightWall(position, direction, width) && !right) ||
                (crossTopWall(position, direction) && !top) ||
                (crossBottomWall(position, direction, height) && !bottom)
}

private fun crossLeftWall(position: Position, direction: Direction) =
    direction.deltaX < 0 && position.x == 0

private fun crossRightWall(position: Position, direction: Direction, width: Int) =
    direction.deltaX > 0 && position.x == width - 1

private fun crossTopWall(position: Position, direction: Direction) =
    direction.deltaY < 0 && position.y == 0

private fun crossBottomWall(position: Position, direction: Direction, height: Int) =
    direction.deltaY > 0 && position.y == height - 1

/** True if a one-step move from [position] in [direction] would wrap around the grid (cross a border), regardless of authorisation. */
fun crossesBorder(position: Position, direction: Direction, width: Int, height: Int): Boolean =
    crossLeftWall(position, direction) ||
            crossRightWall(position, direction, width) ||
            crossTopWall(position, direction) ||
            crossBottomWall(position, direction, height)

@Serializable
data class Behaviour(
    override val name: String = "",
    override val description: String = "",
    val probability: Double = 1.0,
    val agePredicate: Predicate<Int> = Predicate(Operator.GreaterThanOrEquals, 0),
    /** Optional condition formula (e.g. "count(0)>=3 && count(0)<5"). Serialized as "formula". */
    val formula: String = "",
    /** Optional imperative actions executed when the behaviour is applied
     *  (e.g. "Add(0, 1, 0), Remove(1, 0, 0), Move(0, 0, 0, 1, 0)"). */
    val actions: String = "",
    val fieldPredicates: List<Pair<Int, Predicate<Float>>> = emptyList(),
    val agentCountPredicates: List<AgentCountPredicate> = emptyList(),
    /**
     * Additional "simulation age" predicates, ANDed together with [agePredicate]
     * and with the other graphical predicates. The first line of the age
     * predicates entered in normal mode is stored here (the legacy single
     * [agePredicate] is kept for backward compatibility and is folded into this
     * list by the editor when the behaviour is opened).
     */
    val agePredicates: List<Predicate<Int>> = emptyList(),
    val mainReactiveId: Int = -1, val mainProductId: Int = -1, val sourceReactive: Int = -1,
    val fieldInfluences: Map<Int, Float> = emptyMap(),
    val reaction: List<Reaction> = emptyList(),
    /** Identifier of the behaviour, like those of the grains and the fields. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val id: Int = 0,
    /** False when the behaviour is deactivated: it is never applied. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val enabled: Boolean = true,
    @Transient override val uuid: Uuid = uuid4(),
) : ModelElement {

    /** Parsed condition expression (cached) */
    @Transient
    var parsedCondition: com.centyllion.expression.Expression? = null
        private set

    @Transient
    val reactiveGrainIds = buildList {
        add(mainReactiveId)
        reaction.forEach { add(it.reactiveId) }
    }

    @Transient
    val productGrainIds = buildList {
        add(mainProductId)
        reaction.forEach { add(it.productId) }
    }

    @Transient
    val fieldInfluenced = fieldInfluences.any { it.value != 0f }

    fun reactionIndex(reaction: Reaction) = this.reaction.identityFirstIndexOf(reaction)

    fun updateReaction(old: Reaction, new: Reaction): Behaviour {
        val index = reactionIndex(old)
        if (index < 0) return this

        val newBehaviours = reaction.toMutableList()
        newBehaviours[index] = new
        return copy(reaction = newBehaviours)
    }

    fun dropReaction(reaction: Reaction): Behaviour {
        val index = reactionIndex(reaction)
        if (index < 0) return this

        val newReactions = this.reaction.toMutableList()
        // removes the field
        newReactions.removeAt(index)

        return copy(reaction = newReactions)
    }

    fun fieldPredicateIndex(predicate: Pair<Int, Predicate<Float>>) = fieldPredicates.identityFirstIndexOf(predicate)

    fun updateFieldPredicate(old: Pair<Int, Predicate<Float>>, new: Pair<Int, Predicate<Float>>): Behaviour {
        val index = fieldPredicateIndex(old)
        if (index < 0) return this

        val newList = fieldPredicates.toMutableList()
        newList[index] = new
        return copy(fieldPredicates = newList)
    }

    fun dropFieldPredicate(predicate: Pair<Int, Predicate<Float>>): Behaviour {
        val index = fieldPredicateIndex(predicate)
        if (index < 0) return this

        val newList = fieldPredicates.toMutableList()
        // removes the field
        newList.removeAt(index)

        return copy(fieldPredicates = newList)
    }

    fun updateFieldInfluence(id: Int, value: Float): Behaviour {
        val newFields = fieldInfluences.toMutableMap()
        newFields[id] = value
        return copy(fieldInfluences = newFields)
    }

    fun agentCountPredicateIndex(predicate: AgentCountPredicate) =
        agentCountPredicates.identityFirstIndexOf(predicate)

    fun updateAgentCountPredicate(old: AgentCountPredicate, new: AgentCountPredicate): Behaviour {
        val index = agentCountPredicateIndex(old)
        if (index < 0) return this

        val newList = agentCountPredicates.toMutableList()
        newList[index] = new
        return copy(agentCountPredicates = newList)
    }

    fun dropAgentCountPredicate(predicate: AgentCountPredicate): Behaviour {
        val index = agentCountPredicateIndex(predicate)
        if (index < 0) return this

        val newList = agentCountPredicates.toMutableList()
        newList.removeAt(index)
        return copy(agentCountPredicates = newList)
    }

    /** True when [agePredicate] is the neutral default (`>= 0`, always true). */
    @Transient
    val hasDefaultAgePredicate: Boolean
        get() = agePredicate == Predicate(Operator.GreaterThanOrEquals, 0)

    fun agePredicateIndex(predicate: Predicate<Int>) = agePredicates.identityFirstIndexOf(predicate)

    fun updateAgePredicate(old: Predicate<Int>, new: Predicate<Int>): Behaviour {
        val index = agePredicateIndex(old)
        if (index < 0) return this

        val newList = agePredicates.toMutableList()
        newList[index] = new
        return copy(agePredicates = newList)
    }

    fun dropAgePredicate(predicate: Predicate<Int>): Behaviour {
        val index = agePredicateIndex(predicate)
        if (index < 0) return this

        val newList = agePredicates.toMutableList()
        newList.removeAt(index)
        return copy(agePredicates = newList)
    }

    /**
     * Folds the legacy single [agePredicate] into [agePredicates] as its first
     * line, leaving the neutral default behind. Used by the behaviour editor so
     * that every "simulation age" line is stored and edited the same way.
     * Idempotent.
     */
    fun migrateLegacyAgePredicate(): Behaviour = if (hasDefaultAgePredicate) this else copy(
        agePredicate = Predicate(Operator.GreaterThanOrEquals, 0),
        agePredicates = listOf(agePredicate) + agePredicates
    )

    /**
     * Is behavior applicable for given [grain], [age] and [neighbours] ?
     *
     * [simulator] provides the grid state so the condition formula can use
     * `field(id)`, `field(id, dx, dy)` and `agent(x, y)` at any position, in
     * addition to `count(grainId)`, `age(n)`, `step` (the current simulation
     * step) and `x`/`y`/`z` (the [position] of the cell).
     */
    /**
     * True when this behaviour may apply to the cell described by [c].
     *
     * [c] is filled once per cell per step and reused by every candidate behaviour of that
     * cell: no neighbour list, no per-behaviour map and no evaluator allocation.
     */
    fun applicable(c: CellContext): Boolean {
        val grain = c.grain
        val age = c.age
        // checks main reactive and age
        // mainReactiveId == -1 means the cell must be empty (None -> something)
        if (mainReactiveId == -1) {
            if (grain != null) return false
        } else {
            if (grain == null || mainReactiveId != grain.id) return false
        }
        if (!agePredicate.check(age)) return false
        // additional "simulation age" predicates: all of them must hold (AND)
        for (p in agePredicates) {
            if (!p.check(age)) return false
        }
        // checks field predicate
        for (p in fieldPredicates) {
            val value = c.fieldValue(p.first).flatten(minFieldLevel)
            if (!p.second.check(value)) return false
        }
        // checks agent count predicates (counts are computed once per cell)
        for (p in agentCountPredicates) {
            val actualCount = when {
                // counting empty cells, then every agent, then one grain type
                p.grainId == COUNT_EMPTY_CELLS -> c.emptyCount
                p.grainId == COUNT_ALL_AGENTS -> c.occupiedCount
                else -> c.counts[p.grainId] ?: 0
            }
            if (!p.check(actualCount)) return false
        }
        // checks condition formula if present
        if (formula.isNotBlank()) {
            try {
                val evaluator = c.evaluator
                evaluator.agentCounts = c.counts
                evaluator.emptyCount = c.emptyCount
                evaluator.step = c.step.v
                // ages for age(n), 1-based "reactive" numbering (same as the
                // age-inheritance column): [0] main agent (0 for generation
                // behaviours), [1..] one entry per reaction. When a reaction
                // has several matching neighbours, the first one in direction
                // order is used (the actual one is chosen later, by random or
                // field influence); no match (or out of range) -> 0.
                val agesList = c.agesBuffer
                agesList.clear()
                agesList.add(if (grain != null) age else 0)
                for (r in reaction) {
                    var found = 0
                    for (k in c.neighbourIds.indices) {
                        if (c.neighbourIds[k] == r.reactiveId &&
                            r.allowedDirection.contains(CellContext.DIRECTIONS[k])
                        ) {
                            found = c.neighbourAges[k]
                            break
                        }
                    }
                    agesList.add(found)
                }
                evaluator.ages = agesList
                evaluator.x = c.x.v
                evaluator.y = c.y.v
                evaluator.z = c.z.v
                val expr: com.centyllion.expression.Expression =
                    parsedCondition ?: formula.parseExpression()
                parsedCondition = expr
                val result = evaluator.evaluate(expr)
                if (!result.boolean) return false
            } catch (_: Exception) {
                return false
            }
        }
        // checks reactions
        // a reaction with reactiveId == -1 places the product agent into an
        // EMPTY neighbour cell: when that cell lies across a wall, the move is
        // only allowed if the product grain authorises crossing that wall
        // (missing product grain -> no restriction). Reactions with
        // reactiveId >= 0 transform an agent in place: never restricted.
        for (r in reaction) {
            val bc = if (r.reactiveId == -1) c.simulator.model.grainForId(r.productId)?.boundaryConditions else null
            if (r.allowedDirection.none { d ->
                    val k = CellContext.DIRECTIONS.indexOf(d)
                    k >= 0 && c.neighbourIds[k] == r.reactiveId &&
                            (bc == null || !bc.crossed(c.position, d, c.simulator.simulation.width, c.simulator.simulation.height))
                }) return false
        }
        return true
    }

    fun usedGrains(model: GrainModel) =
        (reaction.flatMap { listOf(it.reactiveId, it.productId) } + mainReactiveId + mainProductId +
                agentCountPredicates.map { it.grainId })
            .filter { it >= 0 }.mapNotNull { model.grainForId(it) }.toSet()

}

/**
 * A block of execution: an ordered group of [Behaviour]s evaluated together during a
 * simulation step.
 *
 * A model owns a list of blocks; during a step they are considered in list order (the
 * first block of the tree first). A block is only executed when it is [enabled], its
 * [probability] won the draw of the step, and its predicate holds for the step (same
 * answer for every cell of the grid). Inside a block, behaviours have no priority among
 * themselves, as in NetBioDyn Forever.
 *
 * Transitional note: [behaviours] is the reference list of the model for now; each block
 * mirrors the behaviours it owns. Execution is unchanged until blocks are actually
 * executed one after the other.
 */
@Serializable
data class ExecutionBlock(
    val name: String = "",
    val description: String = "",
    /** Unchecked: the block is never executed (same as a probability of 0). */
    val enabled: Boolean = true,
    /** Probability (0..1) that the block is executed on a given step: 0 never, 1 always. */
    val probability: Double = 1.0,
    /** Expert-mode predicate: raw formula, empty when unused. */
    val formula: String = "",
    /** Normal-mode predicate: age predicates, ANDed together. */
    val agePredicates: List<Predicate<Int>> = emptyList(),
    val agentCountPredicates: List<AgentCountPredicate> = emptyList(),
    val fieldPredicates: List<Pair<Int, Predicate<Float>>> = emptyList(),
    /** 0..n behaviours, executed without any priority among themselves. */
    val behaviours: List<Behaviour> = emptyList(),
) {
    /** True when the block may be executed at all (enabled and probability above 0). */
    val executable: Boolean get() = enabled && probability > 0.0

    /**
     * True when this block carries no information of its own: unnamed, enabled, probability
     * 1 and no predicate. Such a block, when it holds every behaviour of the model, is the
     * implicit block of a file written before execution blocks existed, so it does not need
     * to appear in the saved file (see [GrainModel.forSerialization]).
     */
    val isTrivial: Boolean
        get() = name.isBlank() && description.isBlank() && enabled && probability == 1.0 &&
                formula.isBlank() && agePredicates.isEmpty() && agentCountPredicates.isEmpty() &&
                fieldPredicates.isEmpty()
}

@Serializable
data class Position(
    val x: Int, val y: Int, val z: Int = 0
) {
    fun move(direction: Direction, step: Int = 1) = when (direction) {
        Direction.Left -> copy(x = x + step)
        Direction.Right -> copy(x = x - step)
        Direction.Up -> copy(y = y - step)
        Direction.Down -> copy(y = y + step)
        Direction.LeftUp -> copy(x = x + step, y = y - step)
        Direction.RightUp -> copy(x = x - step, y = y - step)
        Direction.LeftDown -> copy(x = x + step, y = y + step)
        Direction.RightDown -> copy(x = x - step, y = y + step)
    }
}

/**
 * Simulation modifier: a condition and a list of meta-actions, evaluated **once per step**,
 * outside any cell. It is the only place where the meta-actions may be used.
 */
@Serializable
data class SimulationModifier(
    override val name: String = "",
    override val description: String = "",
    /** Condition formula: step, agent(x, y), age(x, y), count(id, x, y), field(id, x, y). */
    val formula: String = "",
    /** Meta-actions executed once per step when the condition holds. */
    val actions: String = "",
    /** Probability that the modifier runs, drawn once per step (1.0 = always). */
    val probability: Double = 1.0,
    /** False when the modifier is deactivated: its radio is off and it never runs. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val enabled: Boolean = true,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val id: Int = 0,
    @Transient override val uuid: Uuid = uuid4(),
) : ModelElement

@Serializable
data class GrainModel(
    override val name: String = "",
    override val description: String = "",
    /** Update plan for simulations using this model: "Parallel" (default) or "Sequential".
     *  Serialized only when it differs from the default. */
    val updatePlan: String = "Parallel",
    val grains: List<Grain> = emptyList(),
    val behaviours: List<Behaviour> = emptyList(),
    /** Execution blocks, in execution order. Absent in files written before blocks existed. */
    val executionBlocks: List<ExecutionBlock> = emptyList(),
    val fields: List<Field> = emptyList(),
    /** Simulation modifiers, applied once per step outside any cell. */
    val modifiers: List<SimulationModifier> = emptyList(),
    @Transient override val uuid: Uuid = uuid4(),
) : ModelElement {

    /** True when the update plan is "Sequential". */
    val isSequentialUpdate: Boolean get() = updatePlan == SEQUENTIAL_UPDATE_PLAN

    /**
     * Returns a copy whose [executionBlocks] are populated, without changing anything else.
     *
     * Files written before execution blocks existed carry no `executionBlocks` key: every
     * behaviour then becomes the content of a single block (enabled, probability 1), which
     * reproduces exactly the previous behaviour because the simulator still reads
     * [behaviours]. A model without any behaviour still gets one empty block, which is the
     * state of a freshly created model. Idempotent: calling it twice changes nothing.
     */
    fun migrated(): GrainModel {
        if (executionBlocks.any { it.behaviours.isNotEmpty() }) return this
        return when {
            behaviours.isNotEmpty() -> copy(executionBlocks = listOf(ExecutionBlock(behaviours = behaviours)))
            executionBlocks.isEmpty() -> copy(executionBlocks = listOf(ExecutionBlock()))
            else -> this
        }
    }

    /**
     * True when [executionBlocks] is exactly the implicit block: one single block, without
     * parameter of its own, holding every behaviour of the model in the reference order.
     * Such a model is indistinguishable from a file written before blocks existed.
     */
    val hasImplicitBlock: Boolean
        get() = executionBlocks.size == 1 &&
                executionBlocks[0].isTrivial &&
                executionBlocks[0].behaviours.map { it.uuid } == behaviours.map { it.uuid }

    /**
     * Copy to encode: an implicit block is left out of the file, so that a project with a
     * single always-executed block keeps the exact structure of the files written before
     * execution blocks, and stays readable by the previous readers. Loading it back
     * re-creates the same block through [migrated], so the round trip is stable.
     */
    fun forSerialization(): GrainModel =
        if (hasImplicitBlock) copy(executionBlocks = emptyList()) else this

    val label: String
        get() = when {
            name.isNotBlank() -> name
            description.isNotBlank() -> description
            else -> ""
        }

    fun findElement(uuid: Uuid): ModelElement? =
        if (uuid == this.uuid) this
        else grains.find { it.uuid == uuid }
            ?: behaviours.find { it.uuid == uuid }
            ?: fields.find { it.uuid == uuid }

    fun grainForId(id: Int) = grains.find { it.id == id }

    fun fieldForId(id: Int) = fields.find { it.id == id }

    fun availableGrainName(prefix: String = "Grain"): String = availableName(grains.map(Grain::name), prefix)

    fun availableGrainId(): Int = availableId(grains.map(Grain::id))

    fun availableColor() = availableColor(
        grains.map(Grain::color) + fields.map(Field::color)
    )

    fun grainIndex(grain: Grain) = grains.identityFirstIndexOf(grain)

    fun newGrain(prefix: String = "Grain") =
        Grain(id = availableGrainId(), name = availableGrainName(prefix), color = availableColor())

    fun updateGrain(old: Grain, new: Grain): GrainModel {
        val grainIndex = grainIndex(old)
        if (grainIndex < 0) return this

        val newGrains = grains.toMutableList()
        newGrains[grainIndex] = new
        return copy(grains = newGrains)
    }

    fun dropGrain(grain: Grain): GrainModel {
        val index = grainIndex(grain)
        if (index < 0) return this

        val newGrains = grains.toMutableList()
        // removes the grain
        newGrains.removeAt(index)

        // clears reference to this grain in behaviours
        val newBehaviours = behaviours.map { behaviour ->
            val new = behaviour.copy(
                mainReactiveId = if (grain.id == behaviour.mainReactiveId) -1 else behaviour.mainReactiveId,
                mainProductId = if (grain.id == behaviour.mainProductId) -1 else behaviour.mainProductId,
                reaction = behaviour.reaction.map {
                    val newReaction = it.copy(
                        reactiveId = if (grain.id == it.reactiveId) -1 else it.reactiveId,
                        productId = if (grain.id == it.productId) -1 else it.productId
                    )
                    if (newReaction == it) it else newReaction
                },
                agentCountPredicates = behaviour.agentCountPredicates.map {
                    if (it.grainId == grain.id) it.copy(grainId = -1) else it
                }
            )
            if (new == behaviour) behaviour else new
        }

        return copy(grains = newGrains, behaviours = newBehaviours)
    }

    fun availableFieldId(): Int = availableId(fields.map(Field::id))

    fun availableFieldName(prefix: String = "Field"): String =
        availableName(fields.map(Field::name), prefix)

    fun newField(prefix: String = "Field") =
        Field(id = availableFieldId(), name = availableFieldName(prefix), color = availableColor())

    fun fieldIndex(field: Field) = fields.identityFirstIndexOf(field)

    fun updateField(old: Field, new: Field): GrainModel {
        val newFields = fields.toMutableList()
        newFields[fieldIndex(old)] = new
        return copy(fields = newFields)
    }

    fun dropField(field: Field): GrainModel {
        val index = fieldIndex(field)
        if (index < 0) return this

        val fields = fields.toMutableList()
        // removes the field
        fields.removeAt(index)

        // clears reference to this field in grains
        val newGrains = grains.map { grain ->
            val new = grain.copy(
                fieldProductions = grain.fieldProductions.filter { it.key != field.id },
                fieldInfluences = grain.fieldInfluences.filter { it.key != field.id },
                fieldPermeable = grain.fieldPermeable.filter { it.key != field.id }
            )
            if (new == grain) grain else new
        }

        val newBehaviours = behaviours.map { behaviour ->
            val new = behaviour.copy(fieldInfluences = behaviour.fieldInfluences.filter { it.key != field.id })
            if (new == behaviour) behaviour else new
        }

        return copy(fields = fields, grains = newGrains, behaviours = newBehaviours)
    }

    fun availableBehaviourName(prefix: String = "Behaviour"): String =
        availableName(behaviours.map(Behaviour::name), prefix)

    /** Adds a modifier with a free name and the next identifier. */
    fun newModifier(prefix: String = "Modifier") = SimulationModifier(
        name = availableName(modifiers.map { it.name }, prefix),
        id = (modifiers.maxOfOrNull { it.id } ?: -1) + 1
    )

    fun updateModifier(old: SimulationModifier, new: SimulationModifier): GrainModel =
        copy(modifiers = modifiers.map { if (it.uuid == old.uuid) new else it })

    fun dropModifier(modifier: SimulationModifier): GrainModel =
        copy(modifiers = modifiers.filter { it.uuid != modifier.uuid })

    fun newBehaviour(prefix: String = "Behaviour") = (grains.firstOrNull()?.id ?: -1).let {
        Behaviour(name = availableBehaviourName(prefix), mainReactiveId = it, mainProductId = it, sourceReactive = 0, id = (behaviours.maxOfOrNull { b -> b.id } ?: -1) + 1)
    }

    fun behaviourIndex(behaviour: Behaviour) = behaviours.identityFirstIndexOf(behaviour)

    fun updateBehaviour(old: Behaviour, new: Behaviour): GrainModel {
        val behaviourIndex = behaviourIndex(old)
        if (behaviourIndex < 0) return this

        val newBehaviours = behaviours.toMutableList()
        newBehaviours[behaviourIndex] = new
        return copy(behaviours = newBehaviours)
    }

    fun dropBehaviour(behaviour: Behaviour): GrainModel {
        val index = behaviourIndex(behaviour)
        if (index < 0) return this

        val newBehaviours = behaviours.toMutableList()
        // removes the field
        newBehaviours.removeAt(index)

        return copy(behaviours = newBehaviours)
    }

    /**
     * Does the given [grain] may change count over the course of a simulation ?
     */
    fun doesGrainCountCanChange(grain: Grain): Boolean {
        return grain.halfLife > 0 || behaviours.map {
            val reactiveCount =
                it.reaction.count { it.reactiveId == grain.id } + (if (it.mainReactiveId == grain.id) 1 else 0)
            val productCount =
                it.reaction.count { it.productId == grain.id } + (if (it.mainProductId == grain.id) 1 else 0)
            reactiveCount != productCount
        }.fold(false) { a, p -> a || p }
    }

    companion object {
        val empty = GrainModel()

        fun new(name: String) = GrainModel(name = name)
    }
}

private fun emptyList(size: Int): List<Int> = ArrayList<Int>(size).apply { repeat(size) { add(-1) } }

/** [AgentCountPredicate.grainId] counting every occupied neighbour cell. */
/**
 * Mutable, reusable description of one cell, filled once per cell per step and shared by
 * every candidate behaviour of that cell. It replaces the former per-cell allocations
 * (`List<Pair<Direction, Agent>>`, eight `Agent` objects, one `FloatArray` per neighbour,
 * one `NetbioDynEvaluator` and two counting maps per formula evaluation).
 */
class CellContext(val simulator: Simulator) {
    var grain: Grain? = null
    var age: Int = 0
    var step: Int = 0
    var x: Int = 0
    var y: Int = 0
    var z: Int = 0
    var position: Position = Position(0, 0, 0)

    /** Field values at this cell, indexed by field slot. */
    var fieldValues: FloatArray = FloatArray(0)

    /** Field id -> slot (or -1 when the field is unknown). */
    var fieldSlot: IntArray = IntArray(0)

    /** Grain id -> number of neighbours of that grain (occupied and empty aggregates included). */
    val counts: MutableMap<Int, Int> = HashMap()

    var emptyCount: Int = 0
    var occupiedCount: Int = 0

    /** Neighbour grain ids, indexed like [DIRECTIONS]. */
    val neighbourIds: IntArray = IntArray(DIRECTIONS.size)

    /** Neighbour ages, indexed like [DIRECTIONS]. */
    val neighbourAges: IntArray = IntArray(DIRECTIONS.size)

    /** Neighbour cell indexes, indexed like [DIRECTIONS]. */
    val neighbourIndices: IntArray = IntArray(DIRECTIONS.size)

    /** Reused buffer for the `age(n)` values of a formula. */
    val agesBuffer: MutableList<Int> = ArrayList(4)

    /** Evaluator of the current thread, reused for every formula evaluation. */
    var evaluator: NetbioDynEvaluator = NetbioDynEvaluator(simulator)

    fun fieldValue(fieldId: Int): Float {
        if (fieldId < 0 || fieldId >= fieldSlot.size) return 0f
        val slot = fieldSlot[fieldId]
        return if (slot in fieldValues.indices) fieldValues[slot] else 0f
    }

    companion object {
        /** Neighbour order of [neighbourIds] and [neighbourAges]. */
        val DIRECTIONS: List<Direction> = Direction.entries
    }
}

const val COUNT_ALL_AGENTS: Int = -1

/** [AgentCountPredicate.grainId] counting the empty neighbour cells. */
const val COUNT_EMPTY_CELLS: Int = -2

/** Seed used by every new simulation, and by the files that do not carry one. */
const val DEFAULT_SIMULATION_SEED: Long = 42

fun createSimulation(
    name: String = "", description: String = "", size: Int = 100,
    agentsInit: (Int) -> Int = { -1 }
) = Simulation(
    name = name, description = description,
    settings = SimulationSettings(size = size),
    agents = List(size * size, agentsInit)
)

val availableSimulationSizes = listOf(10, 25, 50, 100, 200, 300, 400, 500)

/** Bounds and step of the custom grid size, in cells. */
const val MIN_SIMULATION_SIZE: Int = 10
const val MAX_SIMULATION_SIZE: Int = 1000
const val SIMULATION_SIZE_STEP: Int = 10

/** Default width and height of the custom grid. */
const val DEFAULT_CUSTOM_SIZE: Int = 100

@Serializable
data class SimulationSettings(
    /** Square preset, used when [customWidth] or [customHeight] is zero. */
    val size: Int = 100,
    val showGrid: Boolean = true,
    val gridTextureUrl: String? = null,
    val backgroundColor: String? = null,
    /** Custom width, in cells (0 when the square preset is used). */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val customWidth: Int = 0,
    /** Custom height, in cells (0 when the square preset is used). */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val customHeight: Int = 0,
) {
    /** True when the grid uses the custom width and height instead of the square preset. */
    val isCustomSize: Boolean get() = customWidth > 0 && customHeight > 0

    val effectiveWidth: Int get() = if (isCustomSize) customWidth else size

    val effectiveHeight: Int get() = if (isCustomSize) customHeight else size

    @Transient
    val agentsSize = effectiveWidth * effectiveHeight

    /** Copy with the custom size, both values clamped to the allowed bounds and to the step. */
    fun withCustomSize(width: Int, height: Int): SimulationSettings = copy(
        customWidth = clampSimulationSize(width),
        customHeight = clampSimulationSize(height),
    )

    /** Copy that goes back to a square preset. */
    fun withPreset(size: Int): SimulationSettings = copy(size = size, customWidth = 0, customHeight = 0)
}

/** Clamps a grid dimension to the allowed bounds and aligns it on the step. */
fun clampSimulationSize(value: Int): Int {
    val clamped = value.coerceIn(MIN_SIMULATION_SIZE, MAX_SIMULATION_SIZE)
    val stepped = MIN_SIMULATION_SIZE + ((clamped - MIN_SIMULATION_SIZE) / SIMULATION_SIZE_STEP) * SIMULATION_SIZE_STEP
    return stepped.coerceIn(MIN_SIMULATION_SIZE, MAX_SIMULATION_SIZE)
}

@Serializable
data class Simulation(
    val name: String, val description: String,
    val settings: SimulationSettings = SimulationSettings(),
    val agents: List<Int> = emptyList(settings.agentsSize),
    val assets: List<Asset3d> = emptyList(),
    // TODO should be removed from serialization (need migration)
    val width: Int = settings.size,
    val height: Int = settings.size,
    val depth: Int = 1,
    /** Seed of the simulation: two runs sharing a seed follow the same trajectory. */
    val seed: Long = DEFAULT_SIMULATION_SEED,
    /** If true, all behaviours are computed from the state at the start of the step (parallel update).
     *  If false, behaviours are applied immediately, so later behaviours see earlier changes.
     *  Derived from [GrainModel.updatePlan]; not serialized (the plan is stored on the model). */
    @Transient
    val parallelUpdate: Boolean = true,
) {
    val label: String
        get() = when {
            name.isNotBlank() -> name
            description.isNotBlank() -> description
            else -> ""
        }

    val levelSize = width * height

    val dataSize = levelSize * depth

    /** Move [index] on given [direction] of [step] cases, whatever the index, it will always remains inside the simulation. */
    fun moveIndex(index: Int, direction: Direction, step: Int = 1): Int {
        var z = index / levelSize
        val yRest = index - z * levelSize
        var y = yRest / width
        var x = yRest - y * width

        when (direction) {
            Direction.Left -> x = (x + width - step) % width
            Direction.Right -> x = (x + step) % width
            Direction.Up -> y = (y + height - step) % height
            Direction.Down -> y = (y + step) % height
            Direction.LeftUp -> {
                x = (x + width - step) % width
                y = (y + height - step) % height
            }

            Direction.LeftDown -> {
                x = (x + width - step) % width
                y = (y + step) % height
            }

            Direction.RightUp -> {
                x = (x + step) % width
                y = (y + height - step) % height
            }

            Direction.RightDown -> {
                x = (x + step) % width
                y = (y + step) % height
            }
        }

        if (x < 0) x += width
        if (y < 0) y += height
        if (z < 0) z += depth

        return z * levelSize + y * width + x
    }

    /** Transform given [position] to index. */
    fun toIndex(position: Position) = toIndex(position.x, position.y, position.z)

    fun toIndex(x: Int, y: Int, z: Int = 0) = z * (height * width) + y * width + x

    fun indexInside(index: Int) = index in 0 until dataSize

    /** Transforms index to position, only to be used for printing, it's slow */
    fun toPosition(index: Int): Position {
        val zDelta = height * width
        val z = index / zDelta
        val yRest = index - z * zDelta
        val y = yRest / width
        return Position(yRest - y * width, y, z)
    }

    fun positionInside(position: Position) =
        position.z in 0 until depth && position.y in 0 until height && position.x in 0 until width

    fun positionInside(x: Int, y: Int, z: Int = 0) =
        z in 0 until depth && y in 0 until height && x in 0 until width

    fun assetIndex(asset: Asset3d) = assets.identityFirstIndexOf(asset)

    fun updateAsset(old: Asset3d, new: Asset3d): Simulation {
        val newAssets = assets.toMutableList()
        newAssets[assetIndex(old)] = new
        return copy(assets = newAssets)
    }

    fun dropAsset(asset: Asset3d): Simulation {
        val index = assetIndex(asset)
        if (index < 0) return this

        val assets = assets.toMutableList()
        // removes the asset
        assets.removeAt(index)

        return copy(assets = assets)
    }

    val valid
        get() = width > 0 && height > 0 && depth > 0

    /** Cleans the simulation to remove non existing grains */
    fun cleaned(model: GrainModel): Simulation {
        val newAgents = agents.map {
            val new = when {
                it < 0 -> -1
                model.grainForId(it) == null -> -1
                else -> it
            }
            new
        }
        return if (newAgents != agents) copy(agents = newAgents) else this
    }

    fun updateSettings(newSettings: SimulationSettings): Simulation {
        val currentSettings = this.settings
        val newAgents = if (currentSettings.agentsSize == newSettings.agentsSize) agents
        else List(newSettings.agentsSize) { -1 }

        return Simulation(
            name = name,
            description = description,
            seed = seed,
            settings = newSettings,
            assets = assets,
            agents = newAgents,
            width = newSettings.effectiveWidth,
            height = newSettings.effectiveHeight,
            parallelUpdate = parallelUpdate
        )
    }

    companion object {
        val empty = createSimulation()
    }
}

@Serializable
data class ModelAndSimulation(
    val model: GrainModel,
    val simulation: Simulation,
    val thumbnail: String = ""
) {

    /** Returns a copy where [Simulation.parallelUpdate] reflects [GrainModel.updatePlan].
     *  Call after loading a file. */
    fun applyUpdatePlan(): ModelAndSimulation {
        val parallel = !model.isSequentialUpdate
        return if (simulation.parallelUpdate == parallel) this
        else copy(simulation = simulation.copy(parallelUpdate = parallel))
    }

    /** Returns a copy whose model carries its execution blocks. Call after loading a file. */
    fun migrated(): ModelAndSimulation = copy(model = model.migrated())

    /** Copy to encode: the implicit block of a single-block model is not written. */
    fun forSerialization(): ModelAndSimulation = copy(model = model.forSerialization())


    /**
     * Model needs expert mode if a behaviour uses a raw predicate formula or a raw
     * list of actions: these are only editable in expert mode.
     *
     * Fields (creation, field thresholds predicate, field influences), the age
     * predicate, the neighbour count predicates (with their grain) and the age
     * inheritance are editable in normal mode too, so they do not force expert mode.
     */
    val expert: Boolean
        get() = model.behaviours.any { it.formula.isNotBlank() || it.actions.isNotBlank() }

    fun updateInfo(name: String = model.name, description: String = model.description): ModelAndSimulation = copy(
        model = model.copy(name = name, description = description)
    )

    fun updateModel(model: GrainModel): ModelAndSimulation = copy(
        model = model,
        simulation = simulation.cleaned(model)
    )

    fun updateSimulation(simulation: Simulation): ModelAndSimulation = copy(simulation = simulation)

    fun updateGrain(old: Grain, new: Grain): ModelAndSimulation =
        updateModel(model.updateGrain(old, new))

    fun addGrain(grain: Grain): ModelAndSimulation =
        updateModel(model.copy(grains = model.grains + grain))

    fun dropGrain(grain: Grain): ModelAndSimulation =
        copy(
            model = model.dropGrain(grain),
            simulation = simulation.cleaned(model.dropGrain(grain))
        )

    fun addField(field: Field): ModelAndSimulation =
        updateModel(model.copy(fields = model.fields + field))

    fun updateField(old: Field, new: Field): ModelAndSimulation =
        updateModel(model.updateField(old, new))

    fun dropField(field: Field): ModelAndSimulation =
        updateModel(model.dropField(field))

    fun addBehaviour(behaviour: Behaviour): ModelAndSimulation =
        updateModel(model.copy(behaviours = model.behaviours + behaviour))

    fun updateBehaviour(old: Behaviour, new: Behaviour): ModelAndSimulation =
        updateModel(model.updateBehaviour(old, new))

    fun dropBehaviour(behaviour: Behaviour): ModelAndSimulation =
        updateModel(model.dropBehaviour(behaviour))

    companion object {
        val empty = ModelAndSimulation(GrainModel.empty, Simulation.empty)
    }
}